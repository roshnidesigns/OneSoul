package com.onesoul.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.time.ZoneId
import kotlin.random.Random

/**
 * Local-only prototype: everything lives on this phone and "the other person" is simulated
 * (see [simulate…] functions). The shape of the data is what a real backend would sync.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "onesoul.json")

    var profile by mutableStateOf<Profile?>(null)
        private set
    val strokes = mutableStateListOf<Stroke>()
    val songs = mutableStateListOf<SongEvent>()
    val snippets = mutableStateListOf<Snippet>()
    val presence = mutableStateListOf<Presence>()
    val wraps = mutableStateListOf<Wrap>()

    /** While in the future, the partner is "in the app right now". */
    var partnerHereUntil by mutableStateOf(0L)
        private set

    val myTz: String get() = profile?.myTz ?: ZoneId.systemDefault().id

    init {
        runCatching { Codec.decode(file.readText()) }.getOrNull()?.let { s ->
            profile = s.profile
            strokes.addAll(s.strokes); songs.addAll(s.songs); snippets.addAll(s.snippets)
            presence.addAll(s.presence); wraps.addAll(s.wraps)
        }
        if (profile != null) {
            presence.add(Presence(Author.ME, now()))
            fillMonthWithSamples()
            closeFinishedDays()
            save()
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun save() {
        runCatching {
            file.writeText(Codec.encode(Snapshot(profile, strokes.toList(), songs.toList(),
                snippets.toList(), presence.toList(), wraps.toList())))
        }
    }

    // ---- onboarding -------------------------------------------------------------------------

    /** Both locations are in: the space exists and onboarding never shows again. */
    fun pair(me: Place, them: Place) {
        val code = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        profile = Profile(me.city, them.city, them.tz, code, me.tz)
        presence.add(Presence(Author.ME, now()))
        save()
        fillMonthWithSamples()
    }

    fun unpair() {
        profile = null
        strokes.clear(); songs.clear(); snippets.clear(); presence.clear(); wraps.clear()
        save()
    }

    // ---- things you do ----------------------------------------------------------------------

    fun addStroke(pts: List<Float>, seed: Int = Random.nextInt()) {
        if (pts.size < 4) return
        val t = now()
        strokes.add(Stroke(Author.ME, dotColor(hourOf(t, myTz)), pts, t, myTz, seed))
        save()
    }

    /**
     * For now: put a few placeholder sketches on every other day of this month that has none,
     * so the month view looks lived-in. They're marked as samples and can be removed from the demo menu.
     */
    fun fillMonthWithSamples() {
        val zone = ZoneId.of(myTz)
        val today = java.time.LocalDate.now(zone)
        val month = java.time.YearMonth.from(today)
        // Samples left over on days that should stay empty (from an older fill) go away.
        var added = strokes.removeAll { it.sample &&
            java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate().let { d -> java.time.YearMonth.from(d) == month && d.dayOfMonth in KEEP_EMPTY } }
        val used = strokes.map { java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }.toSet()
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            if (date == today || date in used || d in KEEP_EMPTY) continue
            val r = Random(date.toEpochDay().toInt() * 7919)
            repeat(1 + r.nextInt(4)) {
                val hour = r.nextInt(24)
                val t = date.atTime(hour, r.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                val who = if (r.nextBoolean()) Author.ME else Author.PARTNER
                strokes.add(Stroke(who, dotColor(hour + 0.5f), sampleShape(r), t, myTz, r.nextInt(), sample = true))
            }
            added = true
        }
        if (added) save()
    }

    fun removeSamples() {
        if (strokes.removeAll { it.sample }) save()
    }

    /** A hand-drawn-looking doodle: spiral, wave, loop, heart, zigzag or flower, in screen units. */
    private fun sampleShape(r: Random): List<Float> {
        val cx = 0.25f + r.nextFloat() * 0.5f
        val cy = 0.3f + r.nextFloat() * 0.4f
        val s = 0.08f + r.nextFloat() * 0.12f
        val aspect = 0.45f // screen units are taller than wide; keep shapes round-ish
        val pts = ArrayList<Float>()
        fun add(x: Float, y: Float) {
            // a little hand wobble
            pts += cx + x * s + (r.nextFloat() - 0.5f) * 0.004f
            pts += cy + y * s * aspect + (r.nextFloat() - 0.5f) * 0.003f
        }
        val n = 70
        when (r.nextInt(6)) {
            0 -> for (i in 0..n) { val a = i / n.toFloat() * 12.5f; val rr = 0.15f + i / n.toFloat(); add(kotlin.math.cos(a) * rr, kotlin.math.sin(a) * rr) }
            1 -> for (i in 0..n) { val x = -1.5f + 3f * i / n; add(x, kotlin.math.sin(x * 4f) * 0.45f) }
            2 -> for (i in 0..n) { val a = i / n.toFloat() * 18.85f; add(-1.4f + 2.8f * i / n + kotlin.math.cos(a) * 0.35f, kotlin.math.sin(a) * 0.5f) }
            3 -> for (i in 0..n) { val a = i / n.toFloat() * 6.283f; val x = 16 * kotlin.math.sin(a).let { it * it * it }; val y = -(13 * kotlin.math.cos(a) - 5 * kotlin.math.cos(2 * a) - 2 * kotlin.math.cos(3 * a) - kotlin.math.cos(4 * a)); add(x / 16f, y / 16f) }
            4 -> for (i in 0..12) { add(-1.4f + 2.8f * i / 12, if (i % 2 == 0) -0.5f else 0.5f) }
            else -> for (i in 0..n) { val a = i / n.toFloat() * 6.283f; val rr = 0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(a * 2.5f)); add(kotlin.math.cos(a) * rr, kotlin.math.sin(a) * rr) }
        }
        return pts
    }

    /** Shaken off the page: gone from today's sheet and the month view. */
    fun removeStroke(s: Stroke) {
        if (strokes.remove(s)) save()
    }

    fun songChanged(info: NowPlayingInfo) {
        val last = songs.lastOrNull { it.author == Author.ME }
        if (last?.title == info.title && last.artist == info.artist) return
        songs.add(SongEvent(Author.ME, info.title, info.artist, now()))
        save()
    }

    fun sendSnippet(title: String, artist: String, startSec: Int, lenSec: Int, bpm: Int) {
        snippets.add(Snippet(Author.ME, title, artist, startSec, lenSec, bpm, now()))
        save()
    }

    fun setMySong(title: String, artist: String) {
        songs.add(SongEvent(Author.ME, title, artist, now()))
        save()
    }

    // ---- derived state ----------------------------------------------------------------------

    fun partnerSong(): SongEvent? = songs.lastOrNull { it.author == Author.PARTNER }
    fun mySong(): SongEvent? = songs.lastOrNull { it.author == Author.ME }

    fun sameSong(): Boolean {
        val a = partnerSong() ?: return false
        val b = mySong() ?: return false
        return a.title.equals(b.title, true) && a.artist.equals(b.artist, true)
    }

    /** Snippet from the partner that is still shaping this screen (fades over a few hours). */
    fun activeSnippet(at: Long): Pair<Snippet, Float>? {
        val s = snippets.lastOrNull { it.author == Author.PARTNER } ?: return null
        val strength = 1f - ((at - s.t) / FADE_MS.toFloat())
        return if (strength > 0f) s to strength else null
    }

    fun partnerHere(at: Long) = at < partnerHereUntil

    /**
     * Hours (0–23, in that person's own time zone) of their current local day in which they did
     * anything: opened the app, drew, played a song or sent a snippet.
     */
    fun activeHours(who: Author, tz: String, at: Long): Set<Int> {
        val zone = ZoneId.of(tz)
        val today = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val times = presence.filter { it.author == who }.map { it.t } +
            strokes.filter { it.author == who }.map { it.t } +
            songs.filter { it.author == who }.map { it.t } +
            snippets.filter { it.author == who }.map { it.t }
        return times.mapNotNull { t ->
            val z = java.time.Instant.ofEpochMilli(t).atZone(zone)
            if (z.toLocalDate() == today) z.hour else null
        }.toSet()
    }

    // ---- day wraps --------------------------------------------------------------------------

    /** Wraps are built for every finished day (UTC) that had at least a little something in it. */
    private fun closeFinishedDays() {
        val today = dayKey(now())
        val days = (strokes.map { dayKey(it.t) } + songs.map { dayKey(it.t) } +
            snippets.map { dayKey(it.t) } + presence.map { dayKey(it.t) }).toSet()
        days.filter { it < today && wraps.none { w -> w.dayKey == it } }.sorted().forEach { wraps.add(buildWrap(it)) }
    }

    fun previewTodayWrap(): Wrap = buildWrap(dayKey(now()))

    fun buildWrap(day: String): Wrap {
        val p = profile
        val name = p?.partnerName ?: "them"
        val s = strokes.filter { dayKey(it.t) == day }
        val so = songs.filter { dayKey(it.t) == day }
        val sn = snippets.filter { dayKey(it.t) == day }
        val pr = presence.filter { dayKey(it.t) == day }
        val myActs = s.count { it.author == Author.ME } + so.count { it.author == Author.ME } +
            sn.count { it.author == Author.ME }
        val theirActs = s.count { it.author == Author.PARTNER } + so.count { it.author == Author.PARTNER } +
            sn.count { it.author == Author.PARTNER }
        val myTitles = so.filter { it.author == Author.ME }.map { it.title.lowercase() }.toSet()
        val same = so.filter { it.author == Author.PARTNER }.any { it.title.lowercase() in myTitles }
        val both = pr.any { it.author == Author.ME } && pr.any { it.author == Author.PARTNER } &&
            pr.filter { it.author == Author.ME }.any { m -> pr.any { it.author == Author.PARTNER && kotlin.math.abs(it.t - m.t) < 10 * 60_000 } }
        val text = when {
            same -> "Same song, different rooms."
            both -> "You were both here at the same time."
            myActs > 0 && theirActs > 0 -> "You each left something. That's plenty."
            theirActs > 0 -> "$name left something for you."
            myActs > 0 -> "Your marks are waiting for $name. No rush."
            else -> "A quiet day. Quiet days count too."
        }
        val lastMe = (s.filter { it.author == Author.ME }.map { it.t } + so.filter { it.author == Author.ME }.map { it.t })
            .maxOrNull()
        val lastThem = (s.filter { it.author == Author.PARTNER }.map { it.t } + so.filter { it.author == Author.PARTNER }.map { it.t })
            .maxOrNull()
        val bpm = (sn.map { it.bpm }.takeIf { it.isNotEmpty() }?.average()?.toInt()) ?: 70
        return Wrap(
            dayKey = day,
            myColor = dotColor(lastMe?.let { hourOf(it, myTz) } ?: 12f),
            partnerColor = dotColor(lastThem?.let { hourOf(it, p?.partnerTz ?: myTz) } ?: 12f),
            bpm = bpm, text = text, strokes = s.size, songs = so.size, snippets = sn.size,
            sameSong = same, bothHere = both,
        )
    }

    /** Demo helper: closes today right now so you can see the wrap that would appear at 00:00 UTC. */
    fun closeTodayForDemo() {
        val day = dayKey(now())
        wraps.removeAll { it.dayKey == day }
        wraps.add(buildWrap(day))
        wraps.sortBy { it.dayKey }
        save()
    }

    // ---- simulated partner (stand-in for the real other phone) ------------------------------

    private val demoSongs = listOf(
        "Dreams" to "Fleetwood Mac", "Nights" to "Frank Ocean", "Teardrop" to "Massive Attack",
        "Heat Waves" to "Glass Animals", "Sofia" to "Clairo", "Mundian To Bach Ke" to "Panjabi MC",
    )

    fun simulateSong(copyMine: Boolean = false) {
        val mine = mySong()
        val (t, a) = if (copyMine && mine != null) mine.title to mine.artist else demoSongs.random()
        songs.add(SongEvent(Author.PARTNER, t, a, now())); save()
    }

    fun simulateSnippet() {
        val (t, a) = partnerSong()?.let { it.title to it.artist } ?: demoSongs.random()
        snippets.add(Snippet(Author.PARTNER, t, a, Random.nextInt(0, 90), 15, Random.nextInt(65, 170), now())); save()
    }

    fun simulateScribble() {
        val tz = profile?.partnerTz ?: myTz
        val t = now()
        val cx = Random.nextFloat() * 0.6f + 0.2f
        val cy = Random.nextFloat() * 0.6f + 0.2f
        val r = Random.nextFloat() * 0.12f + 0.05f
        val turns = Random.nextInt(1, 3)
        val pts = (0..40).flatMap {
            val a = it / 40f * 2f * Math.PI.toFloat() * turns
            val rr = r * (1f + it / 60f)
            listOf(cx + kotlin.math.cos(a) * rr, cy + kotlin.math.sin(a) * rr)
        }
        strokes.add(Stroke(Author.PARTNER, dotColor(hourOf(t, tz)), pts, t, tz, Random.nextInt())); save()
    }

    fun simulateHere() {
        partnerHereUntil = now() + 2 * 60_000
        presence.add(Presence(Author.PARTNER, now())); save()
    }

    companion object {
        /** Dates the sample fill leaves blank, so the month still has room to scribble. */
        val KEEP_EMPTY = setOf(2, 3, 4, 9, 17, 26)
        const val FADE_MS = 4 * 60 * 60 * 1000L
    }
}
