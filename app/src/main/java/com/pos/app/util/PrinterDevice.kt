package com.pos.app.util

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice

sealed class PrinterDevice {
    data class Usb(val device: UsbDevice) : PrinterDevice()
    data class Bt(val device: BluetoothDevice) : PrinterDevice()

    val displayName: String
        get() = when (this) {
            is Usb -> device.productName ?: device.deviceName ?: "USB 裝置"
            is Bt  -> device.name ?: device.address
        }

    val typeKey: String
        get() = when (this) {
            is Usb -> "usb"
            is Bt  -> "bt"
        }

    /** USB: deviceName；BT: MAC address */
    val identifier: String
        get() = when (this) {
            is Usb -> device.deviceName
            is Bt  -> device.address
        }

    val typeLabel: String
        get() = when (this) {
            is Usb -> "USB"
            is Bt  -> "藍芽"
        }
}
