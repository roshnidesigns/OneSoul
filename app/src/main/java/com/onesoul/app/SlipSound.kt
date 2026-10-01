package com.onesoul.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.tan
import kotlin.random.Random

/**
 * A soft paper "slip" each time a scribble starts to fall off the page: band-passed noise whose pitch
 * slides downward while it swells and fades (~0.45s). Synthesised live, no sound files.
 * Several can overlap; each [slip] is a little different in pitch, length and loudness.
 */
class SlipSound {
    private class Voice(val start: Float, val end: Float, val length: Int, val gain: Float) {
        var i = 0
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f; var lp = 0f
    }

    private val rate = 44_100
    private val voices = ArrayList<Voice>()
    private val lock = Any()
    @Volatile private var running = true
    private val rnd = Random(29)

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

    private val thread = Thread({ loop() }, "slip-sound").apply { isDaemon = true; start() }

    /** One stroke letting go of the page. */
    fun slip() {
        synchronized(lock) {
            if (voices.size >= 8) return
            val len = (rate * (0.35f + rnd.nextFloat() * 0.2f)).toInt()
            voices += Voice(
                start = 2600f + rnd.nextFloat() * 1200f,
                end = 500f + rnd.nextFloat() * 300f,
                length = len,
                gain = 0.7f + rnd.nextFloat() * 0.3f,
            )
        }
    }

    fun release() { running = false; thread.join(300); track.release() }

    private fun loop() {
        val noise = Random(5)
        val block = ShortArray(256)
        var playing = false
        var idle = 0
        while (running) {
            val active = synchronized(lock) { voices.toList() }
            if (active.isEmpty()) {
                if (playing && ++idle > 40) { track.pause(); track.flush(); playing = false }
                if (!playing) { Thread.sleep(10); continue }
            } else {
                idle = 0
                if (!playing) { track.play(); playing = true }
            }
            for (k in block.indices) {
                var mix = 0f
                for (v in active) {
                    if (v.i >= v.length) continue
                    val p = v.i.toFloat() / v.length
                    // Pitch slides down; quick swell, long soft tail.
                    val f = v.start + (v.end - v.start) * p
                    val env = (1f - exp(-p * 18f)) * exp(-p * 3.2f) * (1f - p)
                    val w0 = 2f * PI.toFloat() * f / rate
                    val kk = tan(w0 / 2f); val q = 1.1f
                    val norm = 1f / (1f + kk / q + kk * kk)
                    val b0 = kk / q * norm; val b2 = -b0
                    val a1 = 2f * (kk * kk - 1f) * norm; val a2 = (1f - kk / q + kk * kk) * norm
                    val n = noise.nextFloat() * 2f - 1f
                    val y = b0 * n + b2 * v.x2 - a1 * v.y1 - a2 * v.y2
                    v.x2 = v.x1; v.x1 = n; v.y2 = v.y1; v.y1 = y
                    v.lp += (y - v.lp) * 0.4f
                    mix += v.lp * env * v.gain
                    v.i++
                }
                block[k] = ((mix * 0.5f).coerceIn(-1f, 1f) * 6000).toInt().toShort()
            }
            synchronized(lock) { voices.removeAll { it.i >= it.length } }
            if (playing) track.write(block, 0, block.size)
        }
    }
}
