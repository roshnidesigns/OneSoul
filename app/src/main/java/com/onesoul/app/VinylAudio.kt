package com.onesoul.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Record-player sounds. The little effects are synthesised live (no files):
 *  - [pickUp]: a short, soft scratch as a record leaves the table
 *  - [settle]: a satisfying low "thunk" + click as it drops onto the platter
 *  - [play]: 1.2s of needle crackle, then the record's song
 * Songs are read from app assets at `music/<file>` (e.g. assets/music/only_for_you.mp3). If a file
 * isn't there yet, the record just keeps a soft crackle going.
 */
class VinylAudio(private val context: Context) {
    data class Track(val title: String, val artist: String, val file: String)

    val tracks = listOf(
        Track("Only for You", "Heartless Bastards", "only_for_you"),
        Track("Fast Car", "Tracy Chapman", "fast_car"),
    )

    private val rate = 44_100
    private val lock = Any()
    private val rnd = Random(17)

    private abstract class Voice { var i = 0; abstract val length: Int; abstract fun sample(n: Random): Float }
    private val voices = ArrayList<Voice>()
    @Volatile private var crackle = 0f        // continuous crackle level 0..1
    @Volatile private var running = true
    @Volatile var volume = 0.6f
        set(v) { field = v; player?.setVolume(v, v) }

    private var player: MediaPlayer? = null
    private var playToken = 0

    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private val thread = Thread({ loop() }, "vinyl-fx").apply { isDaemon = true; start() }

    /** Soft scratch: band-passed noise, ~0.28s, quick in, long out. */
    fun pickUp() = add(object : Voice() {
        override val length = (rate * 0.28f).toInt()
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        override fun sample(n: Random): Float {
            val p = i.toFloat() / length
            val f = 2400f - 1200f * p
            val (b0, a1, a2) = bandpass(f, 0.9f)
            val x = n.nextFloat() * 2 - 1
            val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y * (1 - exp(-p * 30)) * exp(-p * 5f) * 0.55f
        }
    })

    /**
     * "Can't go there": a harsh, quick back-and-forth scratch (like dragging a needle across a record),
     * ~0.5s — two rough swipes whose pitch whips up and down, with gritty clicks.
     */
    fun reject() = add(object : Voice() {
        override val length = (rate * 0.5f).toInt()
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        override fun sample(n: Random): Float {
            val p = i.toFloat() / length
            // two swipes: pitch sweeps 900→3800→900 Hz twice
            val sw = sin(PI.toFloat() * 2f * p * 2f)
            val f = 900f + 2900f * (sw * sw)
            val (b0, a1, a2) = bandpass(f, 2.2f)
            val x = n.nextFloat() * 2 - 1
            val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            val grit = if (n.nextFloat() < 0.02f) (n.nextFloat() * 2 - 1) * 0.6f else 0f
            val env = (1 - exp(-p * 40)) * (1f - p) * (0.6f + 0.4f * kotlin.math.abs(sw))
            return (y * 2.4f + grit) * env
        }
    })

    /**
     * Detent as the volume dial snaps onto a tick: a deep, soft "thock" — a short low tone (~160Hz
     * dropping a little) with a muffled touch of attack, rather than a bright click. Plus a tiny vibration.
     */
    fun tick() {
        add(object : Voice() {
            override val length = (rate * 0.07f).toInt()
            var lp = 0f
            override fun sample(n: Random): Float {
                val t = i.toFloat() / rate
                val body = sin(2 * PI.toFloat() * (150f + 60f * exp(-t * 90f)) * t) * exp(-t * 55f) * 0.95f
                lp += ((n.nextFloat() * 2 - 1) - lp) * 0.12f        // muffled, low-passed attack
                val knock = lp * exp(-t * 400f) * 0.6f
                return (body + knock) * (1 - exp(-t * 3000f))
            }
        })
        vibrateTick()
    }

    private val vibrator: android.os.Vibrator? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
    }.getOrNull()

    /** A small, short buzz in the hand for each detent. */
    private fun vibrateTick() {
        val v = vibrator ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                v.vibrate(android.os.VibrationEffect.startComposition()
                    .addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f).compose())
            else v.vibrate(android.os.VibrationEffect.createOneShot(14, 140))
        }
    }

    /** Settling onto the platter: a low thunk (decaying ~90Hz sine) with a crisp click on top. */
    fun settle() = add(object : Voice() {
        override val length = (rate * 0.32f).toInt()
        override fun sample(n: Random): Float {
            val t = i.toFloat() / rate
            val thunk = sin(2 * PI.toFloat() * (90f + 40f * exp(-t * 40)) * t) * exp(-t * 18) * 0.9f
            val click = if (i < rate * 0.006f) (n.nextFloat() * 2 - 1) * exp(-i / (rate * 0.0015f)) * 0.8f else 0f
            return thunk + click
        }
    })

    /** Needle down: 1.2s of crackle, then the song (if its file is in assets/music/). */
    fun play(index: Int) {
        stop()
        val token = ++playToken
        crackle = 1f
        Thread {
            Thread.sleep(1200)
            if (token != playToken) return@Thread
            val mp = openTrack(tracks[index].file)
            if (mp == null) { crackle = 0.35f; return@Thread }   // no file yet: keep a soft crackle going
            crackle = 0.12f                                     // a whisper of surface noise under the music
            mp.setVolume(volume, volume)
            mp.isLooping = true
            mp.start()
            synchronized(lock) { if (token == playToken) player = mp else mp.release() }
        }.start()
    }

    fun stop() {
        playToken++
        crackle = 0f
        synchronized(lock) { player?.run { stop(); release() }; player = null }
    }

    fun release() { stop(); running = false; thread.join(300); track.release() }

    private fun openTrack(name: String): MediaPlayer? {
        val files = runCatching { context.assets.list("music")?.toList() }.getOrNull().orEmpty()
        val file = files.firstOrNull { it.substringBeforeLast('.') == name } ?: return null
        return runCatching {
            context.assets.openFd("music/$file").use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    prepare()
                }
            }
        }.getOrNull()
    }

    private fun add(v: Voice) { synchronized(lock) { if (voices.size < 6) voices += v } }

    private fun bandpass(f: Float, q: Float): Triple<Float, Float, Float> {
        val k = tan(PI.toFloat() * f / rate)
        val norm = 1f / (1f + k / q + k * k)
        return Triple(k / q * norm, 2f * (k * k - 1f) * norm, (1f - k / q + k * k) * norm)
    }

    private fun loop() {
        val n = Random(3)
        val block = ShortArray(256)
        var playing = false
        var idle = 0
        var hiss = 0f
        var level = 0f
        while (running) {
            val active = synchronized(lock) { voices.toList() }
            if (active.isEmpty() && crackle == 0f && level < 0.001f) {
                if (playing && ++idle > 40) { track.pause(); track.flush(); playing = false }
                if (!playing) { Thread.sleep(10); continue }
            } else { idle = 0; if (!playing) { track.play(); playing = true } }
            for (k in block.indices) {
                var mix = 0f
                for (v in active) if (v.i < v.length) { mix += v.sample(n); v.i++ }
                // Crackle: soft low-passed hiss + sparse pops, following the needle level.
                level += (crackle - level) * 0.0008f
                if (level > 0.0005f) {
                    hiss += ((n.nextFloat() * 2 - 1) - hiss) * 0.08f
                    val pop = if (n.nextFloat() < 0.0009f) (n.nextFloat() * 2 - 1) * 0.9f else 0f
                    mix += (hiss * 0.25f + pop) * level * 0.6f
                }
                block[k] = (mix * volume.coerceAtLeast(0.15f)).coerceIn(-1f, 1f).times(9000).toInt().toShort()
            }
            synchronized(lock) { voices.removeAll { it.i >= it.length } }
            if (playing) track.write(block, 0, block.size)
        }
    }
}
