package com.pos.app.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.pos.app.data.db.entity.OrderItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object PrinterManager {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ── 掃描 ──────────────────────────────────────────────────────────────────

    /**
     * 掃描可用的印表機裝置：USB 裝置 + 手機已配對藍芽裝置。
     * API 31+ 無 BLUETOOTH_CONNECT 權限時，藍芽清單回傳空列表（不 crash）。
     */
    fun scanPrinters(context: Context): List<PrinterDevice> {
        val result = mutableListOf<PrinterDevice>()

        // USB
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager.deviceList.values.forEach { result.add(PrinterDevice.Usb(it)) }

        // 藍芽已配對裝置
        result.addAll(getPairedBluetoothDevices(context))

        return result
    }

    /** 取已配對藍芽裝置；API 31+ 需 BLUETOOTH_CONNECT，缺失時回傳空列表。 */
    @Suppress("MissingPermission")
    private fun getPairedBluetoothDevices(context: Context): List<PrinterDevice.Bt> {
        return try {
            val adapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            adapter?.bondedDevices?.map { PrinterDevice.Bt(it) } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── 裝置解析 ──────────────────────────────────────────────────────────────

    /**
     * 根據持久化的 type + id 解析回 PrinterDevice。
     * - type = "usb" → 在 USB 清單找 deviceName == id
     * - type = "bt"  → 在已配對清單找 address == id
     * - 找不到 → null
     */
    fun resolveDevice(context: Context, type: String, id: String): PrinterDevice? {
        if (type.isBlank() || id.isBlank()) return null
        return when (type) {
            "usb" -> {
                val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
                mgr.deviceList.values.firstOrNull { it.deviceName == id }
                    ?.let { PrinterDevice.Usb(it) }
            }
            "bt" -> {
                getPairedBluetoothDevices(context).firstOrNull { it.device.address == id }
            }
            else -> null
        }
    }

    // ── USB 權限（委派 UsbPrinterManager）────────────────────────────────────

    fun hasUsbPermission(context: Context, device: UsbDevice): Boolean =
        UsbPrinterManager.hasPermission(context, device)

    fun requestUsbPermission(context: Context, device: UsbDevice, onResult: (Boolean) -> Unit) =
        UsbPrinterManager.requestPermission(context, device, onResult)

    // ── 列印（統一入口）──────────────────────────────────────────────────────

    suspend fun printTestPage(context: Context, printer: PrinterDevice): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (printer) {
                    is PrinterDevice.Usb -> UsbPrinterManager.printTestPage(context, printer.device).getOrThrow()
                    is PrinterDevice.Bt  -> sendViaBluetooth(printer.device, UsbPrinterManager.buildTestPageBytes())
                }
            }
        }

    suspend fun printCheckoutReceipt(
        context: Context,
        printer: PrinterDevice,
        tableName: String,
        items: List<OrderItemEntity>,
        total: Double,
        remark: String,
        orderId: Long = 0L,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = UsbPrinterManager.buildCheckoutBytes(tableName, items, total, remark, orderId, createdAt)
            when (printer) {
                is PrinterDevice.Usb -> {
                    if (!UsbPrinterManager.hasPermission(context, printer.device))
                        error("未取得 USB 權限，請先在設定頁完成測試列印")
                    UsbPrinterManager.sendToDevice(context, printer.device, data)
                }
                is PrinterDevice.Bt  -> sendViaBluetooth(printer.device, data)
            }
        }
    }

    suspend fun printOrderDetail(
        context: Context,
        printer: PrinterDevice,
        orderId: Long,
        tableName: String,
        createdAt: Long,
        items: List<OrderItemEntity>,
        total: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = UsbPrinterManager.buildDetailBytes(orderId, tableName, createdAt, items, total)
            when (printer) {
                is PrinterDevice.Usb -> {
                    if (!UsbPrinterManager.hasPermission(context, printer.device))
                        error("未取得 USB 權限，請先在設定頁完成測試列印")
                    UsbPrinterManager.sendToDevice(context, printer.device, data)
                }
                is PrinterDevice.Bt  -> sendViaBluetooth(printer.device, data)
            }
        }
    }

    suspend fun printReport(
        context: Context,
        printer: PrinterDevice,
        snapshot: UsbPrinterManager.ReportPrintSnapshot
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = UsbPrinterManager.buildReportBytes(snapshot)
            when (printer) {
                is PrinterDevice.Usb -> {
                    if (!UsbPrinterManager.hasPermission(context, printer.device))
                        error("未取得 USB 權限，請先在設定頁完成測試列印")
                    UsbPrinterManager.sendToDevice(context, printer.device, data)
                }
                is PrinterDevice.Bt  -> sendViaBluetooth(printer.device, data)
            }
        }
    }

    // ── 藍芽傳輸（SPP）───────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun sendViaBluetooth(device: BluetoothDevice, data: ByteArray) {
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket.connect()
            socket.outputStream.write(data)
            socket.outputStream.flush()
        } finally {
            runCatching { socket.close() }
        }
    }
}
