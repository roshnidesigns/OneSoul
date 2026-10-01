package com.onesoul.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.random.Random

/**
 * Pen-on-paper scratch, synthesised live (no sound files): band-passed noise for the nib's hiss
 * plus small random clicks for the paper's tooth. [speed] (0..1) sets how loud and bright it is;
 * [dryness] (0..1) follows the ink running out — less hiss, more crackle, quieter.
 * Runs on its own thread and parks the audio track when nobody is drawing.
 */
class ScratchSound {
    @Volatile var speed = 0f
    @Volatile var dryness = 0f
    @Volatile private var running = true

    private val rate = 44_100
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val thread = Thread({ loop() }, "scratch-sound").apply { isDaemon = true; start() }

    fun release() { running = false; thread.join(300); track.release() }

    private fun loop() {
        val rnd = Random(11)
        val block = ShortArray(256)
        // Band-pass biquad, centre moves with speed (faster = brighter scratch).
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        var level = 0f
        var lp = 0f
        var playing = false
        var idleBlocks = 0
        while (running) {
            val target = speed.coerceIn(0f, 1f)
            if (target < 0.01f && level < 0.001f) {
                if (playing && ++idleBlocks > 40) { track.pause(); track.flush(); playing = false }
                if (!playing) { Thread.sleep(15); continue }
            } else {
                idleBlocks = 0
                if (!playing) { track.play(); playing = true }
            }
            val dry = dryness.coerceIn(0f, 1f)
            val centre = 1300f + 1700f * target // softer, lower nib than before
            val q = 0.7f
            val w0 = 2f * PI.toFloat() * centre / rate
            val k = tan(w0 / 2f)
            val norm = 1f / (1f + k / q + k * k)
            val b0 = k / q * norm; val b2 = -b0
            val a1 = 2f * (k * k - 1f) * norm; val a2 = (1f - k / q + k * k) * norm
            for (i in block.indices) {
                // Ease towards the finger's speed so starts and stops aren't clicky.
                level += (target - level) * (if (target > level) 0.004f else 0.0015f)
                val n = rnd.nextFloat() * 2f - 1f
                val y = b0 * n + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = n; y2 = y1; y1 = y
                // Extra one-pole low-pass to take the edge off the top end.
                lp += (y - lp) * 0.35f
                // Paper tooth: sparse clicks, more of them as the pen dries.
                val click = if (rnd.nextFloat() < 0.0008f + 0.006f * dry * level) (rnd.nextFloat() * 2f - 1f) * 0.45f else 0f
                val hiss = lp * (1f - 0.75f * dry)
                val s = (hiss * 0.55f + click) * level * (0.85f - 0.35f * dry)
                block[i] = (s.coerceIn(-1f, 1f) * 5200).toInt().toShort()
            }
            if (playing) track.write(block, 0, block.size)
        }
    }

    companion object {
        /** Finger speed in px/ms → 0..1 loudness, with a soft knee. */
        fun speedOf(pxPerMs: Float, density: Float): Float {
            val dpPerMs = abs(pxPerMs) / density
            return (dpPerMs / 1.6f).coerceIn(0f, 1f).let { it * (2f - it) }
        }
    }
}
