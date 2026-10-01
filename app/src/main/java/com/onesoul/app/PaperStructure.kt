package com.onesoul.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The paper colour under the vector structure (sampled from the handmade-paper photo). */
val PaperBase = Color(0xFFF3EFE7)

/**
 * A handmade-paper structure generated as vectors, in canvas units (1 = one starting screen),
 * covering [from]..[to] in both directions. Because it's drawn in canvas space it scales and pans
 * with the scribbles, so zooming out visibly shrinks the paper too. Seeded, so it's the same every time.
 *  - fibres: short curved hairs, a few longer ones
 *  - flecks: tiny dark specks
 *  - a faint dot grid every 1/9 of the screen width, echoing the hour dots
 */
fun Modifier.paperStructure(from: Float, to: Float, seed: Int = 7): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    val span = to - from
    val screens = span * span
    val dp = density

    val fibres = Path()
    val longFibres = Path()
    repeat((90 * screens).toInt()) { i ->
        val x = (from + rnd.nextFloat() * span) * w
        val y = (from + rnd.nextFloat() * span) * h
        val long = i % 9 == 0
        val len = (if (long) 18f + rnd.nextFloat() * 26f else 4f + rnd.nextFloat() * 12f) * dp
        val a = rnd.nextFloat() * 6.2832f
        val bend = (rnd.nextFloat() - 0.5f) * len * 0.8f
        val ex = x + cos(a) * len
        val ey = y + sin(a) * len
        val cx = (x + ex) / 2 - sin(a) * bend
        val cy = (y + ey) / 2 + cos(a) * bend
        val p = if (long) longFibres else fibres
        p.moveTo(x, y); p.quadraticTo(cx, cy, ex, ey)
    }

    val flecks = List((260 * screens).toInt()) {
        Offset((from + rnd.nextFloat() * span) * w, (from + rnd.nextFloat() * span) * h)
    }
    val bigFlecks = List((40 * screens).toInt()) {
        Offset((from + rnd.nextFloat() * span) * w, (from + rnd.nextFloat() * span) * h)
    }

    val step = w / 9f
    val grid = buildList {
        var gy = from * h
        while (gy <= to * h) {
            var gx = from * w
            while (gx <= to * w) { add(Offset(gx, gy)); gx += step }
            gy += step
        }
    }

    onDrawBehind {
        drawPoints(grid, PointMode.Points, Color.Black.copy(alpha = 0.07f), strokeWidth = 3f * dp, cap = StrokeCap.Round)
        drawPath(fibres, Color(0xFF8C8172).copy(alpha = 0.45f), style = Stroke(0.7f * dp, cap = StrokeCap.Round))
        drawPath(longFibres, Color(0xFF9A8F80).copy(alpha = 0.35f), style = Stroke(0.6f * dp, cap = StrokeCap.Round))
        drawPoints(flecks, PointMode.Points, Color(0xFF5E564C).copy(alpha = 0.45f), strokeWidth = 1.2f * dp, cap = StrokeCap.Round)
        drawPoints(bigFlecks, PointMode.Points, Color(0xFF4A433B).copy(alpha = 0.5f), strokeWidth = 2.2f * dp, cap = StrokeCap.Round)
    }
}
