package com.onesoul.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The paper colour under the vector structure (sampled from the handmade-paper photo). */
val PaperBase = Color(0xFFF3EFE7)

/**
 * A handmade-paper structure generated as vectors, in canvas units (1 = one starting screen),
 * covering [x0]..[x1] × [y0]..[y1]. Because it's drawn in canvas space it scales and pans
 * with the scribbles, so zooming out visibly shrinks the paper too. Seeded, so it's the same every time.
 *  - fibres: short curved hairs, a few longer ones
 *  - flecks: tiny dark specks
 *  - a faint dot grid every 1/9 of the screen width, echoing the hour dots
 */
fun Modifier.paperStructure(x0: Float, x1: Float, y0: Float, y1: Float, seed: Int = 7): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    val spanX = x1 - x0
    val spanY = y1 - y0
    val screens = spanX * spanY
    fun rx() = (x0 + rnd.nextFloat() * spanX) * w
    fun ry() = (y0 + rnd.nextFloat() * spanY) * h
    val dp = density

    val fibres = Path()
    val longFibres = Path()
    repeat((70 * screens).toInt()) { i ->
        val x = rx()
        val y = ry()
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

    val flecks = List((180 * screens).toInt()) { Offset(rx(), ry()) }
    val bigFlecks = List((30 * screens).toInt()) { Offset(rx(), ry()) }

    val step = w / 9f
    val grid = buildList {
        var gy = y0 * h
        while (gy <= y1 * h) {
            var gx = x0 * w
            while (gx <= x1 * w) { add(Offset(gx, gy)); gx += step }
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

/**
 * Separate paper sheets lying on the white table: one per [sheets] origin (canvas units, each
 * sheet 1 × 1 screen). Each gets a soft shadow, the paper colour and the vector fibre texture
 * (built once for a single sheet, then reused for every page).
 */
fun Modifier.paperSheets(sheets: List<Offset>, bleedTop: Float = 0f, bleedBottom: Float = 0f, seed: Int = 7): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    val dp = density
    val fibres = Path()
    val longFibres = Path()
    repeat(70) { i ->
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val long = i % 9 == 0
        val len = (if (long) 18f + rnd.nextFloat() * 26f else 4f + rnd.nextFloat() * 12f) * dp
        val a = rnd.nextFloat() * 6.2832f
        val bend = (rnd.nextFloat() - 0.5f) * len * 0.8f
        val ex = x + cos(a) * len
        val ey = y + sin(a) * len
        val p = if (long) longFibres else fibres
        p.moveTo(x, y); p.quadraticTo((x + ex) / 2 - sin(a) * bend, (y + ey) / 2 + cos(a) * bend, ex, ey)
    }
    val flecks = List(180) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
    val bigFlecks = List(30) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
    val step = w / 9f
    val grid = buildList {
        var gy = step / 2
        while (gy < h) { var gx = step / 2; while (gx < w) { add(Offset(gx, gy)); gx += step }; gy += step }
    }
    onDrawBehind {
        for (o in sheets) {
            // Each sheet bleeds under the status/navigation bars, so at 100% today's page fills the phone.
            val tl = Offset(o.x * w, o.y * h)
            val st = tl - Offset(0f, bleedTop)
            val ss = androidx.compose.ui.geometry.Size(w, h + bleedTop + bleedBottom)
            // a soft lift off the table
            for (s in 1..3) drawRect(Color.Black.copy(alpha = 0.035f / s), st + Offset(0f, (6f + 6f * s) * dp), ss)
            drawRect(PaperBase, st, ss)
            translate(tl.x, tl.y) {
                drawPoints(grid, PointMode.Points, Color.Black.copy(alpha = 0.07f), strokeWidth = 3f * dp, cap = StrokeCap.Round)
                drawPath(fibres, Color(0xFF8C8172).copy(alpha = 0.45f), style = Stroke(0.7f * dp, cap = StrokeCap.Round))
                drawPath(longFibres, Color(0xFF9A8F80).copy(alpha = 0.35f), style = Stroke(0.6f * dp, cap = StrokeCap.Round))
                drawPoints(flecks, PointMode.Points, Color(0xFF5E564C).copy(alpha = 0.45f), strokeWidth = 1.2f * dp, cap = StrokeCap.Round)
                drawPoints(bigFlecks, PointMode.Points, Color(0xFF4A433B).copy(alpha = 0.5f), strokeWidth = 2.2f * dp, cap = StrokeCap.Round)
            }
        }
    }
}
