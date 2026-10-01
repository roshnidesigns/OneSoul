package com.onesoul.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val MAX_SCALE = 1f // never larger than the size the scribbles were drawn at
/** Gap between day sheets, in screens. */
private const val GAP = 0.18f
private const val COLUMNS = 5
/** Margin of paper around the month, in screens. */
private const val MARGIN = 0.4f

/**
 * One big canvas holding the whole month. Every date is its own sheet, laid out five to a row like
 * the days-together grid; today's sheet is the one you start on and draw into. One finger draws;
 * two fingers pan and zoom (down to the whole month, never past 100%). As you zoom out, the other
 * days' scribbles fade in around today's — fully there by 33% (300% out). Once zoomed out it's a
 * collection: no scribbling, one finger just moves around.
 * Strokes are stored in today's "screen units" (0..1 is the sheet you first see), so they stay put.
 */
@Composable
fun ScribbleSpace(vm: AppViewModel, myColor: Color, now: Long) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val current = remember { mutableStateListOf<Float>() }
    // A fresh, unpredictable texture for every stroke — picked as the finger lands, kept once drawn.
    var strokeSeed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // Shake to clear: your strokes from today fall off the bottom one by one (in no particular order).
    val falling = remember { androidx.compose.runtime.mutableStateMapOf<Stroke, Float>() } // stroke → seconds falling
    // Pen-on-paper scratch while drawing; released when the page goes away.
    val sound = remember { ScratchSound() }
    // Paper slip as each shaken-off scribble lets go.
    val slip = remember { SlipSound() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { sound.release(); slip.release() } }

    // --- the month as a grid of sheets, positioned relative to today's sheet ------------------------
    val zone = java.time.ZoneId.of(vm.myTz)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val month = java.time.YearMonth.from(today)
    val days = month.lengthOfMonth()
    val rows = (days + COLUMNS - 1) / COLUMNS
    val tCol = (today.dayOfMonth - 1) % COLUMNS
    val tRow = (today.dayOfMonth - 1) / COLUMNS
    fun sheet(day: Int) = Offset(
        ((day - 1) % COLUMNS - tCol) * (1 + GAP),
        ((day - 1) / COLUMNS - tRow) * (1 + GAP),
    )
    val x0 = -tCol * (1 + GAP) - MARGIN
    val x1 = (COLUMNS - 1 - tCol) * (1 + GAP) + 1 + MARGIN
    val y0 = -tRow * (1 + GAP) - MARGIN
    val y1 = (rows - 1 - tRow) * (1 + GAP) + 1 + MARGIN
    val byDay = vm.strokes.groupBy { java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }
    // Smallest zoom: the whole month fits the screen.
    val minScale = (1f / maxOf(x1 - x0, y1 - y0)).coerceAtMost(0.5f)
    // Other days appear as you zoom out: hidden at 100%, fully there at 33% (300% out).
    val reveal = ((1f - scale) / (1f - 1f / 3f)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    val todayMine = byDay[today].orEmpty().filter { it.author == Author.ME }
    // Keep shaking: after 1s of constant shaking your strokes start dropping, one every ~140ms.
    // Stop: nothing new drops; strokes already falling finish falling off and are erased.
    val shaking by rememberShaking(enabled = scale >= 0.97f)
    val shakeActive = shaking && todayMine.isNotEmpty()
    LaunchedEffect(shakeActive || falling.isNotEmpty()) {
        if (!(shakeActive || falling.isNotEmpty())) return@LaunchedEffect
        val fallFor = 1.6f // seconds until it's well below the screen
        var shakeStart = 0L
        var lastRelease = 0L
        var lastFrame = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (lastFrame == 0L) 0f else (t - lastFrame) / 1e9f
                lastFrame = t
                if (shaking) { if (shakeStart == 0L) shakeStart = t } else shakeStart = 0L
                val dropping = shaking && shakeStart != 0L && t - shakeStart >= 1_000_000_000L
                if (dropping && t - lastRelease > 140_000_000) {
                    vm.strokes.filter { it.author == Author.ME && it !in falling &&
                        java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() == today }
                        .randomOrNull()?.let { falling[it] = 0f; lastRelease = t; slip.slip() }
                }
                // Once a stroke has started to fall it always falls all the way off (and is erased).
                for ((st, sec) in falling.entries.toList()) {
                    val next = sec + dt
                    if (next > fallFor) { falling.remove(st); vm.removeStroke(st) } else falling[st] = next
                }
            }
            if (!shaking && falling.isEmpty()) break
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(minScale, x0, x1, y0, y1) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                // Keep the month on screen: centre it on an axis where it's smaller than the screen.
                fun clampAxis(o: Float, s: Float, lo: Float, hi: Float, view: Float): Float {
                    val span = (hi - lo) * view * s
                    return if (span <= view) (view - (lo + hi) * view * s) / 2
                    else o.coerceIn(view - hi * view * s, -lo * view * s)
                }
                fun clamp(o: Offset, s: Float) = Offset(clampAxis(o.x, s, x0, x1, w), clampAxis(o.y, s, y0, y1, h))
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Collection mode (other days showing): no scribbling — one finger just moves around.
                    val collecting = scale < 0.97f
                    fun world(p: Offset) = listOf((p.x - offset.x) / scale / w, (p.y - offset.y) / scale / h)
                    var transforming = false
                    current.clear(); if (!collecting) current.addAll(world(down.position))
                    strokeSeed = kotlin.random.Random.nextInt()
                    var inked = 0f // canvas px of line laid in this stroke, for the ink running out
                    var lastT = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!transforming) { transforming = true; current.clear(); sound.speed = 0f }
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val next = (scale * zoom).coerceIn(minScale, MAX_SCALE)
                            offset = clamp(centroid - (centroid - offset) * (next / scale) + pan, next)
                            scale = next
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && !transforming && collecting) {
                            val c = event.changes.first { it.pressed }
                            offset = clamp(offset + c.positionChange(), scale)
                            c.consume()
                        } else if (pressed == 1 && !transforming) {
                            val c = event.changes.first { it.pressed }
                            val moved = c.positionChange()
                            if (moved != Offset.Zero) {
                                current.addAll(world(c.position)); c.consume()
                                // Scratch follows finger speed; it thins and crackles as the ink (1.5 widths) runs out.
                                inked += moved.getDistance() / scale
                                val total = 1.5f * w
                                val dry = ((inked - 0.6f * total) / (0.4f * total)).coerceIn(0f, 1f)
                                val dt = (c.uptimeMillis - lastT).coerceAtLeast(1L)
                                lastT = c.uptimeMillis
                                sound.dryness = dry
                                sound.speed = if (dry >= 1f) 0f else ScratchSound.speedOf(moved.getDistance() / dt, density)
                            } else {
                                // Finger resting on the page: let the scratch die away.
                                sound.speed *= 0.6f
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    sound.speed = 0f
                    if (!transforming && !collecting && current.size >= 4) vm.addStroke(current.toList(), strokeSeed)
                    current.clear()
                }
            },
    ) {
        // The prompt is part of the page, not the canvas: it stays put, and fades as the month comes in.
        if (byDay[today].isNullOrEmpty() && current.isEmpty() && reveal < 0.99f) {
            Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - reveal },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("leave a scribble", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 20.sp,
                    color = Color(0xFF222222).copy(alpha = 0.4f))
                SvgIcon(SQUIGGLE, 110, 41, Color(0xFF452E30).copy(alpha = 0.3f), 3f,
                    Modifier.padding(top = 4.dp).size(102.dp, 46.dp).rotate(6.844f))
            }
        }
        // Everything in here lives in canvas space and moves/zooms together.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        ) {
            // Vector paper across the whole month, so the page itself visibly zooms and pans.
            Box(Modifier.fillMaxSize().paperStructure(x0, x1, y0, y1))
            Canvas(Modifier.fillMaxSize()) {
                val sw = size.width; val sh = size.height
                // Other days' scribbles, each on its own sheet, fading in with the zoom.
                if (reveal > 0.01f) {
                    for ((date, list) in byDay) {
                        if (date == today || java.time.YearMonth.from(date) != month) continue
                        val o = sheet(date.dayOfMonth)
                        translate(o.x * sw, o.y * sh) {
                            list.forEach { drawStroke(it.pts, colorOf(it.color).copy(alpha = reveal), seed = it.seed) }
                        }
                    }
                }
                // Today's sheet: always fully there (except strokes being shaken off, which fall away).
                byDay[today].orEmpty().forEach { st ->
                    val t = falling[st]
                    if (t == null) drawStroke(st.pts, colorOf(st.color), seed = st.seed)
                    else {
                        // Gravity, a little sideways drift and a tumble, all varied per stroke.
                        val r = kotlin.random.Random(st.seed)
                        val g = 2600.dp.toPx()
                        val dy = 0.5f * g * t * t
                        val dx = (r.nextFloat() - 0.5f) * 160.dp.toPx() * t
                        val spin = (r.nextFloat() - 0.5f) * 140f * t
                        val cx = st.pts.filterIndexed { i, _ -> i % 2 == 0 }.average().toFloat() * sw
                        val cy = st.pts.filterIndexed { i, _ -> i % 2 == 1 }.average().toFloat() * sh
                        withTransform({ translate(dx, dy); rotate(spin, Offset(cx, cy)) }) {
                            drawStroke(st.pts, colorOf(st.color), seed = st.seed)
                        }
                    }
                }
                drawStroke(current, myColor, seed = strokeSeed)
            }
            // Date numbers above each sheet; sized for reading when zoomed out.
            if (reveal > 0.01f) {
                for (d in 1..days) {
                    val o = sheet(d)
                    Text(
                        "$d", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 72.sp,
                        color = Color(0xFF222222).copy(alpha = 0.35f * reveal),
                        modifier = Modifier.offset {
                            androidx.compose.ui.unit.IntOffset((o.x * viewSize.width + 24.dp.toPx()).toInt(),
                                (o.y * viewSize.height - 100.dp.toPx()).toInt())
                        },
                    )
                }
            }
        }
    }
}
