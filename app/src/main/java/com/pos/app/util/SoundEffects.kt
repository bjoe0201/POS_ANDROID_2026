package com.pos.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 以程式合成短音效，不依賴任何音檔資源。
 *
 * 目前提供一組「收款完成」歡快音效：C5 → E5 → G5 → C6 四音快速上行琶音，
 * 帶淡入/淡出包絡避免爆音。
 */
object SoundEffects {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val SAMPLE_RATE = 44100

    fun playPaymentSuccess() {
        scope.launch { runCatching { playChimeBlocking() } }
    }

    private fun playChimeBlocking() {
        // 大三和弦上行琶音（C5, E5, G5, C6）
        val notes = listOf(
            Note(freq = 523.25, durationMs = 110),  // C5
            Note(freq = 659.25, durationMs = 110),  // E5
            Note(freq = 783.99, durationMs = 110),  // G5
            Note(freq = 1046.50, durationMs = 260)  // C6（收尾拉長一點）
        )

        val samples = ShortArray(notes.sumOf { msToSamples(it.durationMs) })
        var offset = 0
        notes.forEach { note ->
            val count = msToSamples(note.durationMs)
            renderTone(samples, offset, count, note.freq)
            offset += count
        }

        val bufferSizeBytes = samples.size * 2
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSizeBytes, minBuffer))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()

        // 播放完自動釋放
        scope.launch {
            val totalMs = notes.sumOf { it.durationMs } + 80L
            kotlinx.coroutines.delay(totalMs)
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    private fun msToSamples(ms: Int): Int = SAMPLE_RATE * ms / 1000

    /** 將單一音符（正弦波 + 淡入淡出包絡）寫入 samples[offset..offset+count)。 */
    private fun renderTone(samples: ShortArray, offset: Int, count: Int, freqHz: Double) {
        val amplitude = 0.45 * Short.MAX_VALUE // 避免過響
        val fadeSamples = min(count / 6, SAMPLE_RATE / 100) // 約 10ms 淡入淡出
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            // 簡單 ADSR：淡入 / 持平 / 淡出
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val sample = sin(2.0 * PI * freqHz * t) * amplitude * env
            samples[offset + i] = sample.toInt().toShort()
        }
    }

    private data class Note(val freq: Double, val durationMs: Int)

    /** 保留舊 API 相容（若其他呼叫方使用此方法，維持不變）。 */
    @Suppress("unused")
    fun resolveStreamType(): Int = AudioManager.STREAM_NOTIFICATION
}

