package com.onesoul.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** One stroke's worth of ink: about 1.5 screen-widths of line before the pen is dry. */
private const val INK_LENGTH = 1.5f
/** The pen writes fully for this share of its ink, then starts to dry out. */
private const val WET_SHARE = 0.6f
/** How far ink fibres can poke out past the line's edge (Figma texture radius 2). */
private const val FRAY_DP = 2f

/**
 * Ballpoint on handmade paper, as in the references: the line is a translucent wash of ink with
 * fibre-shaped deposits (same colour, denser) caught across it — short streaks at random angles, clumps, and
 * fragments that fray past the edges. Each stroke's texture comes from its own random [seed]
 * (chosen as the finger lands), so no two strokes look alike, but every segment is seeded on its
 * own so ink already laid never changes: it imprints as you draw and stays exactly the same after.
 */
private class InkGeometry(
    val wash: Path,          // the soft body of the line (wet part only)
    val fibresDark: Path,    // heavy deposits
    val fibresMid: Path,     // ordinary fibre streaks
    val fibresLight: Path,   // faint, thin streaks
    val specks: List<Offset>,
)

/** Finished strokes don't change, so their ink is built once per (points, size, reveal, width, seed). */
private object InkCache {
    private data class Key(val size: Size, val upTo: Int, val width: Float, val seed: Int)
    private val map = IdentityHashMap<List<Float>, Pair<Key, InkGeometry>>()
    fun get(pts: List<Float>, key: Pair<Size, Triple<Int, Float, Int>>, build: () -> InkGeometry): InkGeometry {
        val k = Key(key.first, key.second.first, key.second.second, key.second.third)
        map[pts]?.let { (old, g) -> if (old == k) return g }
        if (map.size > 1500) map.clear()
        return build().also { map[pts] = k to it }
    }
}

fun DrawScope.drawInk(pts: List<Float>, color: Color, upTo: Int, width: Float, seed: Int, neverDry: Boolean = false) {
    val n = upTo.coerceAtMost(pts.size / 2)
    if (n < 1) return
    // The stroke being drawn changes every frame (a live SnapshotStateList); only cache finished ones.
    val live = pts is androidx.compose.runtime.snapshots.SnapshotStateList<*>
    val geo = if (live) buildInk(pts, n, size, width, density, seed, neverDry)
    else InkCache.get(pts, size to Triple(n, width, seed)) { buildInk(pts, n, size, width, density, seed, neverDry) }

    // Fibres stay in the ink's own colour — no darker deposits.
    val deep = color
    // Fibres scale with the line: a thin line (thumbnails, the prompt squiggle) gets finer grain.
    val d = density * (width / (6f * density)).coerceIn(0.35f, 1f)
    drawPath(geo.wash, color.copy(alpha = color.alpha * 0.55f), style = Stroke(width * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(geo.fibresLight, color.copy(alpha = color.alpha * 0.55f), style = Stroke(0.5f * d, cap = StrokeCap.Round))
    drawPath(geo.fibresMid, color, style = Stroke(0.9f * d, cap = StrokeCap.Round))
    drawPath(geo.fibresDark, deep, style = Stroke(1.3f * d, cap = StrokeCap.Round))
    drawPoints(geo.specks, PointMode.Points, deep, strokeWidth = 1.1f * d, cap = StrokeCap.Round)
}

private fun buildInk(pts: List<Float>, n: Int, size: Size, width: Float, density: Float, strokeSeed: Int, neverDry: Boolean): InkGeometry {
    // Older strokes (saved before seeds existed) fall back to a seed from where they start.
    val base = if (strokeSeed != 0) strokeSeed else (pts[0] * 100_000).toInt() * 31 + (pts[1] * 100_000).toInt()
    val total = if (neverDry) Float.MAX_VALUE else INK_LENGTH * size.width
    val wetUntil = total * WET_SHARE
    val k = (width / (6f * density)).coerceIn(0.35f, 1f) // texture scale for thinner lines
    val fray = FRAY_DP * density * k
    val half = width / 2
    val step = 1.4f * density * k

    val wash = Path()
    val dark = Path(); val mid = Path(); val light = Path()
    val specks = ArrayList<Offset>()

    fun at(i: Int) = Offset(pts[i * 2] * size.width, pts[i * 2 + 1] * size.height)
    var prev = at(0)
    var travelled = 0f
    var washOpen = true
    wash.moveTo(prev.x, prev.y)
    if (n == 1) wash.lineTo(prev.x + 0.01f, prev.y)

    // A short fibre streak centred at (x, y): random angle, slight bias along the stroke direction.
    fun fibre(path: Path, rnd: Random, x: Float, y: Float, dx: Float, dy: Float, len: Float) {
        val along = kotlin.math.atan2(dy, dx)
        val a = if (rnd.nextFloat() < 0.45f) along + (rnd.nextFloat() - 0.5f) * 0.9f else rnd.nextFloat() * 6.2832f
        val hx = cos(a) * len / 2; val hy = sin(a) * len / 2
        // A tiny bend so streaks look like fibres, not dashes.
        val bx = -hy * (rnd.nextFloat() - 0.5f) * 0.6f; val by = hx * (rnd.nextFloat() - 0.5f) * 0.6f
        path.moveTo(x - hx, y - hy); path.quadraticTo(x + bx, y + by, x + hx, y + hy)
    }

    for (i in 1 until n) {
        val p = at(i)
        val seg = hypot(p.x - prev.x, p.y - prev.y)
        if (seg <= 0f) continue
        // Per-segment seed: grain laid here never changes as the stroke grows.
        val rnd = Random(base * 1_000_003 + i)
        val dx = (p.x - prev.x) / seg
        val dy = (p.y - prev.y) / seg
        var d = rnd.nextFloat() * step
        while (d < seg) {
            val x = prev.x + dx * d
            val y = prev.y + dy * d
            val pos = travelled + d
            val ink = when {
                pos < wetUntil -> 1f
                pos >= total -> 0f
                else -> 1f - (pos - wetUntil) / (total - wetUntil)
            }
            if (ink > 0f) {
                // Ink catches unevenly: some steps are heavy, some almost bare.
                val pressure = 0.35f + rnd.nextFloat() * 0.65f
                val count = (3.5f * ink * pressure * (width / (6f * density * k)) + rnd.nextFloat()).toInt()
                repeat(count) {
                    // Spread across the width, sometimes fraying a little past the edge.
                    val across = (rnd.nextFloat() * 2f - 1f) * (half + fray * rnd.nextFloat() * if (rnd.nextFloat() < 0.25f) 1f else 0.2f)
                    val cx = x - dy * across + (rnd.nextFloat() - 0.5f) * fray
                    val cy = y + dx * across + (rnd.nextFloat() - 0.5f) * fray
                    val len = (1.2f + rnd.nextFloat() * rnd.nextFloat() * 5f) * density * k
                    val r = rnd.nextFloat()
                    when {
                        r < 0.18f * ink -> fibre(dark, rnd, cx, cy, dx, dy, len)
                        r < 0.7f -> fibre(mid, rnd, cx, cy, dx, dy, len)
                        else -> fibre(light, rnd, cx, cy, dx, dy, len * 1.4f)
                    }
                }
                if (rnd.nextFloat() < 0.25f * ink) {
                    specks += Offset(x + (rnd.nextFloat() - 0.5f) * (width + fray * 2), y + (rnd.nextFloat() - 0.5f) * (width + fray * 2))
                }
            }
            d += step
        }
        val end = travelled + seg
        if (end < wetUntil) wash.lineTo(p.x, p.y)
        else if (washOpen) {
            // The wash ends where the pen starts to dry; beyond that it's fibres only.
            val t = ((wetUntil - travelled) / seg).coerceIn(0f, 1f)
            wash.lineTo(prev.x + dx * seg * t, prev.y + dy * seg * t)
            washOpen = false
        }
        travelled = end
        prev = p
    }
    return InkGeometry(wash, dark, mid, light, specks)
}
