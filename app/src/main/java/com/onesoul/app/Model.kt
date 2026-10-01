package com.onesoul.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Author { ME, PARTNER }

data class Profile(
    val myName: String,
    val partnerName: String,
    val partnerTz: String,
    val pairCode: String,
    val myTz: String,
)

/** One scribble. pts is a flat list x0,y0,x1,y1... normalised to 0..1 of the canvas. */
data class Stroke(
    val author: Author,
    val color: Long,
    val pts: List<Float>,
    val t: Long,
    val tz: String,
    /** Random per stroke, picked when drawing starts: makes its ink texture unique but stable. */
    val seed: Int = 0,
)

data class SongEvent(
    val author: Author,
    val title: String,
    val artist: String,
    val t: Long,
)

/** A slice of a song; the bpm gently drives the receiver's screen for a few hours. */
data class Snippet(
    val author: Author,
    val title: String,
    val artist: String,
    val startSec: Int,
    val lenSec: Int,
    val bpm: Int,
    val t: Long,
)

data class Presence(val author: Author, val t: Long)

data class Wrap(
    val dayKey: String,
    val myColor: Long,
    val partnerColor: Long,
    val bpm: Int,
    val text: String,
    val strokes: Int,
    val songs: Int,
    val snippets: Int,
    val sameSong: Boolean,
    val bothHere: Boolean,
)

data class Snapshot(
    val profile: Profile?,
    val strokes: List<Stroke>,
    val songs: List<SongEvent>,
    val snippets: List<Snippet>,
    val presence: List<Presence>,
    val wraps: List<Wrap>,
)

private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** A day is the same moment for everyone: it rolls over at 00:00 UTC. */
fun dayKey(t: Long): String = dayFmt.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneOffset.UTC))

fun hourOf(t: Long, tz: String): Float {
    val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.of(tz))
    return z.hour + z.minute / 60f
}

fun clock(t: Long, tz: String): String =
    DateTimeFormatter.ofPattern("HH:mm").format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.of(tz)))

fun ago(t: Long, now: Long): String {
    val m = ((now - t) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 60 * 24 -> "${m / 60}h ago"
        else -> "${m / (60 * 24)}d ago"
    }
}

object Codec {
    fun encode(s: Snapshot): String = JSONObject().apply {
        s.profile?.let {
            put("profile", JSONObject().put("me", it.myName).put("partner", it.partnerName)
                .put("tz", it.partnerTz).put("code", it.pairCode).put("mtz", it.myTz))
        }
        put("strokes", JSONArray(s.strokes.map {
            JSONObject().put("a", it.author.name).put("c", it.color).put("t", it.t).put("tz", it.tz).put("sd", it.seed)
                .put("p", JSONArray(it.pts.map { f -> f.toDouble() }))
        }))
        put("songs", JSONArray(s.songs.map {
            JSONObject().put("a", it.author.name).put("ti", it.title).put("ar", it.artist).put("t", it.t)
        }))
        put("snippets", JSONArray(s.snippets.map {
            JSONObject().put("a", it.author.name).put("ti", it.title).put("ar", it.artist)
                .put("s", it.startSec).put("l", it.lenSec).put("b", it.bpm).put("t", it.t)
        }))
        put("presence", JSONArray(s.presence.map { JSONObject().put("a", it.author.name).put("t", it.t) }))
        put("wraps", JSONArray(s.wraps.map {
            JSONObject().put("d", it.dayKey).put("mc", it.myColor).put("pc", it.partnerColor).put("b", it.bpm)
                .put("x", it.text).put("st", it.strokes).put("so", it.songs).put("sn", it.snippets)
                .put("same", it.sameSong).put("both", it.bothHere)
        }))
    }.toString()

    fun decode(json: String): Snapshot {
        val o = JSONObject(json)
        fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }
        return Snapshot(
            profile = o.optJSONObject("profile")?.let {
                Profile(it.getString("me"), it.getString("partner"), it.getString("tz"), it.getString("code"),
                    it.optString("mtz", ZoneId.systemDefault().id))
            },
            strokes = o.getJSONArray("strokes").map { s ->
                val p = s.getJSONArray("p")
                Stroke(Author.valueOf(s.getString("a")), s.getLong("c"),
                    (0 until p.length()).map { p.getDouble(it).toFloat() }, s.getLong("t"), s.getString("tz"), s.optInt("sd", 0))
            },
            songs = o.getJSONArray("songs").map {
                SongEvent(Author.valueOf(it.getString("a")), it.getString("ti"), it.getString("ar"), it.getLong("t"))
            },
            snippets = o.getJSONArray("snippets").map {
                Snippet(Author.valueOf(it.getString("a")), it.getString("ti"), it.getString("ar"),
                    it.getInt("s"), it.getInt("l"), it.getInt("b"), it.getLong("t"))
            },
            presence = o.getJSONArray("presence").map { Presence(Author.valueOf(it.getString("a")), it.getLong("t")) },
            wraps = o.getJSONArray("wraps").map {
                Wrap(it.getString("d"), it.getLong("mc"), it.getLong("pc"), it.getInt("b"), it.getString("x"),
                    it.getInt("st"), it.getInt("so"), it.getInt("sn"), it.getBoolean("same"), it.getBoolean("both"))
            },
        )
    }
}
