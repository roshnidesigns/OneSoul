package com.onesoul.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The music corner (Figma "vinyl - off" / "vinyl - on"), laid out in the frame's own 360 × 800 dp
 * space. Records lie around; drag one onto the turntable's platter and it snaps on, the arm swings
 * over and it spins. Drag it off and it stops. The knob turns for volume. UI + gestures only for now.
 */
class Vinyl {
    /** Turntable (Figma "overlay"): 180.6 square, centre in frame dp, rotated −4.663°. */
    val deck = Offset(179.4f, 303.5f)
    val platter = Offset(179.4f, 303.5f) // centred in the turntable
    val knob = Offset(180f, 625f)
    private val rest = listOf(Offset(134f, 527f), Offset(249.4f, 527f))
    val labels = listOf(Color(0xFFECC833), Color(0xFF2B5D75))

    /** Where each record is (frame dp). */
    val pos = mutableStateListOf(*rest.toTypedArray())
    /** Index of the record on the turntable, if any. */
    var playing by mutableStateOf<Int?>(null)
    var dragging by mutableStateOf<Int?>(null)
    /** 0..1 */
    var volume by mutableFloatStateOf(0.6f)

    /** Picked up (or on the platter) a record is platter-sized; lying around it's smaller. */
    fun radius(i: Int) = if (dragging == i || playing == i) ON_PLATTER_R else LYING_R

    /** Topmost record under the finger (frame dp), if any. */
    fun hitRecord(p: Offset): Int? =
        pos.indices.sortedByDescending { if (it == playing) 0 else 1 }
            .firstOrNull { hypot(p.x - pos[it].x, p.y - pos[it].y) <= radius(it) + 10f }

    fun hitKnob(p: Offset) = hypot(p.x - knob.x, p.y - knob.y) <= KNOB_R + 14f

    fun drag(i: Int, by: Offset) { pos[i] = pos[i] + by }

    /**
     * Let go of a record. On an empty platter it plays; anywhere else it rests where it was dropped.
     * If another record is already playing, this one doesn't go on — it slides back to its place.
     * (Take the playing record off first, then put the new one on.)
     */
    fun drop(i: Int) {
        val onPlatter = hypot(pos[i].x - platter.x, pos[i].y - platter.y) < 72f
        val occupied = playing != null && playing != i
        if (onPlatter && occupied) {
            pos[i] = rest[i]
        } else if (onPlatter) {
            playing = i
            pos[i] = platter
        } else if (playing == i) {
            playing = null
        }
        dragging = null
    }

    /** Turn the knob towards the finger: the indicator sweeps −150°…−30° (left to right over the top). */
    fun turnKnob(p: Offset) {
        val a = Math.toDegrees(atan2((p.y - knob.y).toDouble(), (p.x - knob.x).toDouble())).toFloat()
        val clamped = when {
            a in -150f..-30f -> a
            a > -30f && a < 90f -> -30f
            else -> -150f
        }
        volume = (clamped + 150f) / 120f
    }

    companion object {
        const val LYING_R = 40f        // Figma records: 80 × 80
        const val ON_PLATTER_R = 47.27f // platter "container": 94.53 × 94.43
        const val KNOB_R = 34f
    }
}

// --- Figma vectors ---------------------------------------------------------------------------
private val RECORD_DISC = "M5.27391 20.1666C16.2312 0.984161 40.6647 -5.6833 59.8472 5.27403C79.0293 16.2314 85.6969 40.664 74.7397 59.8463C63.7824 79.0287 39.3489 85.6972 20.1665 74.7399C0.984225 63.7825 -5.68324 39.349 5.27391 20.1666ZM44.3462 39.4068C44.1466 36.9593 42.0008 35.1369 39.5532 35.3365C37.1057 35.5362 35.2833 37.682 35.4829 40.1295C35.6826 42.5769 37.8285 44.3992 40.2759 44.1998C42.7234 44.0002 44.5456 41.8543 44.3462 39.4068Z"
private val RECORD_LABEL = "M39.1533 20.0065C50.199 20.0065 59.1533 28.9608 59.1533 40.0065C59.1533 51.0521 50.199 60.0065 39.1533 60.0065C28.1077 60.0065 19.1534 51.0521 19.1533 40.0065C19.1533 28.9608 28.1076 20.0065 39.1533 20.0065ZM43.8047 39.8649C43.6049 37.4175 41.4592 35.5949 39.0117 35.7946C36.5642 35.9942 34.7418 38.1409 34.9414 40.5885C35.1412 43.0358 37.2871 44.8582 39.7344 44.6588C42.182 44.4592 44.0043 42.3125 43.8047 39.8649Z"
/** Tonearm ("nobe"), in its 98 × 137 SVG space; pivot disc at (61.177, 13.618). */
private val ARM = "M57.8744 8.48272C60.7096 6.65804 64.4871 7.47784 66.3119 10.3131C68.1363 13.1482 67.3174 16.9253 64.4824 18.75C63.8284 19.1709 63.1238 19.4496 62.4051 19.5969L57.5577 37.5422C57.4319 38.008 57.4321 38.4995 57.5594 38.9649L71.7933 90.9972C72.2482 92.6604 72.1994 94.4216 71.6528 96.0569L67.9703 107.07C70.215 108.588 71.3341 111.431 70.5392 114.174L67.5943 124.337C66.6075 127.742 63.047 129.703 59.6416 128.716L57.8173 128.188C54.4118 127.201 52.4504 123.64 53.4371 120.235L56.3821 110.072C57.1608 107.384 59.5426 105.598 62.186 105.448L65.9624 94.1553C66.1325 93.6464 66.1472 93.098 66.0057 92.5805L51.7727 40.5477C51.3636 39.0522 51.3603 37.4741 51.7646 35.9772L56.6875 17.7522C56.4539 17.498 56.2378 17.2212 56.0446 16.921C54.2198 14.0858 55.0391 10.3075 57.8744 8.48272Z"

private val pathCache = HashMap<String, androidx.compose.ui.graphics.Path>()
private fun svg(d: String) = pathCache.getOrPut(d) {
    androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
        .apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }
}

/** A record exactly as in Figma (80-unit SVG), scaled to radius [r] around [c]. */
fun DrawScope.drawRecord(c: Offset, r: Float, label: Color, alpha: Float, spin: Float) {
    val s = r / 40f
    rotate(spin, c) {
        withTransform({
            translate(c.x - 40f * s, c.y - 40f * s); scale(s, s, Offset.Zero)
        }) {
            drawPath(svg(RECORD_DISC), Color(0xFF222222).copy(alpha = alpha))
            drawPath(svg(RECORD_LABEL), label.copy(alpha = alpha))
            for ((x, y) in listOf(21.1719f to 73.4987f, 54.9004f to 14.4528f)) {
                rotate(-150.264f, Offset(x, y)) {
                    drawRoundRect(label.copy(alpha = alpha), Offset(x, y), Size(3f, 8f), CornerRadius(1.5f))
                }
            }
        }
    }
}

/**
 * Draws the turntable, records and knob. [o] is the frame's top-left in canvas px and [k] canvas px
 * per frame dp. [shown]/[radii] are the (animated) record centres and sizes, [arm] 0 = resting,
 * 1 = on the record, [spin] the platter's rotation in degrees.
 */
fun DrawScope.drawVinyl(
    v: Vinyl, o: Offset, k: Float, shown: List<Offset>, radii: List<Float>,
    arm: Float, alpha: Float, layer: Int,
) {
    fun p(x: Float, y: Float) = Offset(o.x + x * k, o.y + y * k)
    val shadow = Color.Black.copy(alpha = 0.10f * alpha)
    val dc = p(v.deck.x, v.deck.y)

    if (layer == 0) {
    // --- turntable (Figma "overlay"): rotated −4.663°, 180.6 square, radius 28.56 -----------------
    rotate(-4.663f, dc) {
        val half = 90.308f * k
        val tl = dc - Offset(half, half); val sz = Size(half * 2, half * 2); val cr = CornerRadius(28.556f * k)
        // drop shadows: 0 1.43 1.43 @22%, 0 0 1.43 @25%
        drawRoundRect(Color.Black.copy(alpha = 0.22f * alpha), tl + Offset(0f, 1.428f * k), sz, cr)
        drawRoundRect(Color.Black.copy(alpha = 0.12f * alpha), tl - Offset(0.7f * k, 0.7f * k), Size(sz.width + 1.4f * k, sz.height + 1.4f * k),
            CornerRadius(29.3f * k))
        // radial-gradient(58.97% 59.88% at 50% 50%, #EEEEEC → #D6D5D5)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFEEEEEC), Color(0xFFD6D5D5)).map { it.copy(alpha = alpha) },
            dc, 0.594f * half * 2), tl, sz, cr)
        // inset highlights / shade: white from the left & top, a little dark at the bottom
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f * alpha), Color.Transparent,
            Color.Black.copy(alpha = 0.11f * alpha)), tl.y, tl.y + sz.height), tl + Offset(1f * k, 1f * k),
            Size(sz.width - 2f * k, sz.height - 2f * k), CornerRadius(27.5f * k), style = Stroke(2.2f * k))
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.44f * alpha), Color.Transparent),
            tl.x, tl.x + 12f * k), tl + Offset(1f * k, 1f * k), Size(sz.width - 2f * k, sz.height - 2f * k),
            CornerRadius(27.5f * k), style = Stroke(2.2f * k))

        // platter ("contents"): #C4C4C4 disc r 47.27 with drop-shadow 0 0.714 2.142 @70%, spindle #DFDFDE r 4.45
        val pc = dc // platter centred on the deck
        drawCircle(Color.Black.copy(alpha = 0.30f * alpha), 47.27f * k + 1.2f * k, pc + Offset(0f, 0.714f * k))
        drawCircle(Color(0xFFC4C4C4).copy(alpha = alpha), 47.27f * k, pc)
        drawCircle(Color(0xFFDFDFDE).copy(alpha = alpha), 4.446f * k, pc)
        drawCircle(Color.Black.copy(alpha = 0.12f * alpha), 4.446f * k, pc + Offset(0f, 0.5f * k), style = Stroke(0.8f * k))

        // mini speed knob (Group 875988), bottom-left: disc r 9.85 #F5F4F4, conic r 6.1, ticks
        val mk = dc + Offset(-61.6f * k, 66.2f * k)
        drawCircle(Color.Black.copy(alpha = 0.18f * alpha), 10.2f * k, mk + Offset(0f, 0.8f * k))
        drawCircle(Color(0xFFF5F4F4).copy(alpha = alpha), 9.853f * k, mk)
        drawCircle(Brush.sweepGradient(listOf(Color(0xFFDADADA), Color(0xFFF4F4F4), Color(0xFFDADADA)).map { it.copy(alpha = alpha) }, mk),
            6.105f * k, mk)
        for (t in 0 until 5) {
            val a = Math.toRadians(-94.0 + 15.0 * t)
            val col = when (t) { 0 -> Color.Black; 4 -> Color(0xFFEB5D5C); else -> Color(0xFFABABA9) }
            val r0 = 12.6f * k; val r1 = 14.7f * k
            drawLine(col.copy(alpha = alpha), mk + Offset((cos(a) * r0).toFloat(), (sin(a) * r0).toFloat()),
                mk + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), 0.71f * k)
        }
    }

    // --- records (lying ones first, the one on the platter, then the one in hand, on top) ---------
    val order = shown.indices.sortedBy { if (it == v.dragging) 2 else if (it == v.playing) 1 else 0 }
    for (i in order) {
        val c = Offset(o.x + shown[i].x * k, o.y + shown[i].y * k)
        val r = radii[i] * k
        // Shadow uses the disc path too, so the centre hole stays clear (paper/platter shows through).
        if (i == v.playing && v.dragging != i) continue
        if (true) {
            val ss = r / 40f
            withTransform({ translate(c.x - 40f * ss, c.y - 40f * ss + 2.5f * k); scale(ss, ss, Offset.Zero) }) {
                drawPath(svg(RECORD_DISC), Color.Black.copy(alpha = 0.16f * alpha))
            }
        }
        // The spinning record is drawn on its own rotating layer (smooth, no full redraw).
        if (i == v.playing && v.dragging != i) continue
        drawRecord(c, r, v.labels[i], alpha, 0f)
    }
    return
    }

    // --- tonearm ("nobe"): exact Figma path, pivots about its disc; swings onto the record ---------
    rotate(-4.663f, dc) {
        val pivotLocal = dc + Offset(66f * k, -64f * k) // tucked into the top-right corner
        rotate(28f * arm, pivotLocal) {
            withTransform({
                translate(pivotLocal.x - 61.177f * k, pivotLocal.y - 13.618f * k); scale(k, k, Offset.Zero)
            }) {
                // soft shadow, then the arm with its #F5F4F4 → #DBDBDB gradient
                translate(1.2f, 1.6f) { drawPath(svg(ARM), Color.Black.copy(alpha = 0.16f * alpha)) }
                drawPath(svg(ARM), Brush.linearGradient(listOf(Color(0xFFF5F4F4), Color(0xFFDBDBDB)).map { it.copy(alpha = alpha) },
                    Offset(55.62f, 13.55f), Offset(64.31f, 18.98f)))
                drawPath(svg(ARM), Color.Black.copy(alpha = 0.10f * alpha), style = Stroke(0.5f))
                // pivot disc r 9.85 #F5F4F4
                drawCircle(Color.Black.copy(alpha = 0.14f * alpha), 10.1f, Offset(61.4f, 14.2f))
                drawCircle(Color(0xFFF5F4F4).copy(alpha = alpha), 9.853f, Offset(61.177f, 13.618f))
                // red needle mark
                rotate(-32.7651f, Offset(59.7471f, 112.539f)) {
                    drawRoundRect(Color(0xFFEB5D5C).copy(alpha = alpha), Offset(59.7471f, 112.539f), Size(4.551f, 4.733f), CornerRadius(2.276f))
                }
            }
        }
    }

    // --- volume knob + ticks ------------------------------------------------------------------
    val kc = p(v.knob.x, v.knob.y)
    val ticks = 7
    for (t in 0 until ticks) {
        val a = Math.toRadians((-150.0 + 120.0 * t / (ticks - 1)))
        val r0 = (Vinyl.KNOB_R + 12f) * k; val r1 = (Vinyl.KNOB_R + 21f) * k
        val col = when (t) { 0 -> Color(0xFF222222); ticks - 1 -> Color(0xFFE6623F); else -> Color(0xFFABABA9) }
        drawLine(col.copy(alpha = alpha),
            kc + Offset((cos(a) * r0).toFloat(), (sin(a) * r0).toFloat()),
            kc + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), 2.4f * k, StrokeCap.Round)
    }
    for (s in 1..4) drawCircle(shadow.copy(alpha = shadow.alpha * 1.4f / s), (Vinyl.KNOB_R + 1.5f * s) * k, kc + Offset(0f, (2f + 2f * s) * k))
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD9D8D6)).map { it.copy(alpha = alpha) },
        kc + Offset(-8f * k, -10f * k), Vinyl.KNOB_R * 1.4f * k), Vinyl.KNOB_R * k, kc)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE4E3E1), Color.White).map { it.copy(alpha = alpha) },
        kc + Offset(-4f * k, -6f * k), Vinyl.KNOB_R * 0.8f * k), Vinyl.KNOB_R * 0.66f * k, kc)
    val va = Math.toRadians((-150.0 + 120.0 * v.volume))
    drawCircle(Color(0xFF8E8D8A).copy(alpha = alpha), 2.6f * k,
        kc + Offset((cos(va) * Vinyl.KNOB_R * 0.82f * k).toFloat(), (sin(va) * Vinyl.KNOB_R * 0.82f * k).toFloat()))
}
