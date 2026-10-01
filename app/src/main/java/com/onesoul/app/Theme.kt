package com.onesoul.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

val Coral = Color(0xFFE6623F)
val Teal = Color(0xFF2B5D75)
val Sun = Color(0xFFECC833)
val Ink = Color(0xFF111111)

/** The Dawn / Day / Dusk / Evening palette: each phase is a top → middle → bottom gradient. */
data class Phase(val name: String, val top: Color, val mid: Color, val glow: Color)

val Dawn = Phase("Dawn", Color(0xFF1B3A4B), Color(0xFF008ED9), Color(0xFFFDC492))
val Day = Phase("Day", Color(0xFF79C1E5), Color(0xFFC8D3EF), Color(0xFFFFD990))
val Dusk = Phase("Dusk", Color(0xFF271F2F), Color(0xFF865AB4), Color(0xFFE4631D))
val Evening = Phase("Evening", Color(0xFF111111), Color(0xFF271F2F), Color(0xFF452E30))

fun phaseOf(hour: Float): Phase = when (hour) {
    in 5f..<8f -> Dawn
    in 8f..<17f -> Day
    in 17f..<20f -> Dusk
    else -> Evening
}

/** Hour colours: four, one per 6-hour stretch of the person's local day. */
private val dotSlots = listOf(
    0xFF291F30, // 00–06 night
    0xFF0091DF, // 06–12 morning
    0xFFFFA900, // 12–18 afternoon
    0xFFEB813B, // 18–24 evening
).map { Color(it) }

/** "text color for time" from the same grid, used at 60% opacity. */
val TimeText = Color(0xFF222F36)

/** The colour of a person's dot at their local hour. */
fun dotColor(hour: Float): Long = dotColorC(hour).toArgb().toLong() and 0xFFFFFFFFL

fun dotColorC(hour: Float): Color = dotSlots[(hour.toInt() / 6).coerceIn(0, 3)]

fun colorOf(argb: Long): Color = Color(argb.toInt())

/** Slow songs feel like evening, mid-tempo dusk, fast ones like the day. */
fun bpmColor(bpm: Int): Color {
    val f = ((bpm - 60) / 120f).coerceIn(0f, 1f)
    return if (f < 0.5f) lerp(Dusk.mid, Dusk.glow, f * 2f) else lerp(Dusk.glow, Day.glow, (f - 0.5f) * 2f)
}
