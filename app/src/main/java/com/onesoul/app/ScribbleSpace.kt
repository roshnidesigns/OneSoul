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
import androidx.compose.ui.text.drawText
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Big volume dial (Figma "vinyl - on"): radius 91dp, centred on the screen's bottom edge. */
private val DIAL_R = 91.dp
private val KNOB_FROM_BOTTOM = 0.dp
private const val MAX_SCALE = 1f // never larger than the size the scribbles were drawn at
/** Gap between day sheets, in screens. */
private const val GAP = 0.4f // more air between the day pages
private const val COLUMNS = 6
/** Margin of paper around the month, in screens (more on top for the header and the time tracks). */
private const val MARGIN = 0.4f
private const val MARGIN_TOP = 2.3f
/** Extra paper-free table on the far left, so the month doesn't hug the screen edge. */
private const val MARGIN_LEFT = 1.3f
private const val MARGIN_BOTTOM = 1.0f

/**
 * One big canvas holding the whole month. Every date is its own sheet, laid out six to a row like
 * the days-together grid; today's sheet is the one you start on and draw into. One finger draws;
 * two fingers pan and zoom (down to the whole month, never past 100%). As you zoom out, the other
 * days' scribbles fade in around today's — fully there by 33% (300% out). Once zoomed out it's a
 * collection: no scribbling, one finger just moves around.
 * Strokes are stored in today's "screen units" (0..1 is the sheet you first see), so they stay put.
 */
@Composable
fun ScribbleSpace(
    vm: AppViewModel, myColor: Color, now: Long, onReveal: (Float) -> Unit = {},
    /** How far the volume dial has popped up (0..1), so the screen can make room for it. */
    onDial: (Float) -> Unit = {},
    /** Screen-space layer drawn on the paper but under the ink (the skies). */
    underInk: @Composable () -> Unit = {},
) {
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
    // The month grid, and breathing room around it: the table is twice the grid's size, so the
    // collection sits in the middle with a margin of half its width/height on every side.
    val gridLeft = -tCol * (1 + GAP)
    val gridRight = (COLUMNS - 1 - tCol) * (1 + GAP) + 1
    val gridTop = -tRow * (1 + GAP)
    val gridBottom = (rows - 1 - tRow) * (1 + GAP) + 1
    val padX = (gridRight - gridLeft) / 2f
    val padY = (gridBottom - gridTop) / 2f
    val x0 = gridLeft - padX
    val x1 = gridRight + padX
    val y0 = gridTop - padY
    val y1 = gridBottom + padY
    val byDay = vm.strokes.groupBy { java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() }
    // Smallest zoom: the whole month fits the screen.
    val minScale = (1f / maxOf(x1 - x0, y1 - y0)).coerceAtMost(0.5f)
    // Other days appear as you zoom out: hidden at 100%, fully there at 33% (300% out).
    val reveal = ((1f - scale) / (1f - 1f / 3f)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    androidx.compose.runtime.SideEffect { onReveal(reveal) }

    // --- the music corner, right next to the month (one "zoomed-out screen" wide) ------------------
    val frameScreens = 1f / minScale            // the Figma 360×800 frame = one screen at full zoom-out
    val aspect = if (viewSize.height > 0) viewSize.width.toFloat() / viewSize.height else 0.45f
    val mx0 = gridRight + MARGIN                // music frame sits just right of the month
    // Titles sit just above the grid; my0 is where a 360-wide frame's top would be for that.
    val my0 = gridTop - 170f * frameScreens * aspect / 360f
    // The player is drawn at half the size of a zoomed-out screen, so it sits in scale with the day
    // pages; its "some music?" title stays level with "days together".
    val musicScreens = frameScreens * 0.33f // keeps the player in scale with the (now smaller) day pages
    val my0m = my0 + 150f * (frameScreens - musicScreens) / 360f * aspect
    val xEnd = maxOf(x1, mx0 + musicScreens + MARGIN)
    val vinyl = remember { Vinyl() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vinylAudio = remember { VinylAudio(ctx.applicationContext) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vinylAudio.release() } }
    // Volume knob drives the music.
    androidx.compose.runtime.LaunchedEffect(vinyl.volume) { vinylAudio.volume = vinyl.volume }
    val shownRecords = vinyl.pos.indices.map { i ->
        val target = vinyl.pos[i]
        val anim by androidx.compose.animation.core.animateOffsetAsState(target,
            androidx.compose.animation.core.spring(0.75f, 380f), label = "rec$i")
        if (vinyl.dragging == i) target else anim
    }
    val shownRadii = vinyl.pos.indices.map { i ->
        val r by androidx.compose.animation.core.animateFloatAsState(vinyl.radius(i), label = "recR$i")
        r
    }
    val armPos by androidx.compose.animation.core.animateFloatAsState(
        if (vinyl.playing != null && vinyl.dragging != vinyl.playing) 1f else 0f,
        androidx.compose.animation.core.tween(700), label = "arm")
    val spinT = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val spin = spinT.animateFloat(0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3600, // one turn every 3.6s — a slow, calm spin
            easing = androidx.compose.animation.core.LinearEasing)), label = "spinA")
    val titles = androidx.compose.ui.text.rememberTextMeasurer()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The volume dial only appears once a record is on the platter.
    // …and only while the player is actually in view: pan or zoom back to the scribbles and it tucks
    // away on its own (the music keeps playing).
    val playerInView = run {
        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
        if (vw <= 0f) false else {
            val kh = musicScreens * vw / 360f
            val left = offset.x + mx0 * vw * scale
            val right = left + 360f * kh * scale
            val top = offset.y + my0m * vh * scale
            val bottom = top + 800f * kh * scale
            val overlapX = (minOf(right, vw) - maxOf(left, 0f)).coerceAtLeast(0f)
            val overlapY = (minOf(bottom, vh) - maxOf(top, 0f)).coerceAtLeast(0f)
            // at least half of the player's own frame is on screen
            reveal > 0.6f && overlapX > (right - left) * 0.5f && overlapY > (bottom - top) * 0.5f
        }
    }
    val dialIn by androidx.compose.animation.core.animateFloatAsState(
        if (vinyl.playing != null && vinyl.dragging != vinyl.playing && playerInView) 1f else 0f,
        // pops up from below the screen with a gentle spring
        androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 140f), label = "dial")
    androidx.compose.runtime.SideEffect { onDial((dialIn * reveal).coerceIn(0f, 1f)) }

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
            .pointerInput(minScale, x0, xEnd, y0, y1) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                // Keep the month on screen: centre it on an axis where it's smaller than the screen.
                fun clampAxis(o: Float, s: Float, lo: Float, hi: Float, view: Float): Float {
                    val span = (hi - lo) * view * s
                    return if (span <= view) (view - (lo + hi) * view * s) / 2
                    else o.coerceIn(view - hi * view * s, -lo * view * s)
                }
                fun clamp(o: Offset, s: Float) = Offset(clampAxis(o.x, s, x0, xEnd, w), clampAxis(o.y, s, y0, y1, h))
                // Canvas px per frame dp, and the music frame's top-left in canvas px.
                val k = musicScreens * w / 360f
                val mo = Offset(mx0 * w, my0m * h)
                fun frameDp(p: Offset) = ((p - offset) / scale - mo) / k
                // Volume knob: fixed on screen, centred, just above the bottom time track; 1.3× size.
                val knobC = Offset(w / 2f, h - KNOB_FROM_BOTTOM.toPx())
                val knobR = DIAL_R.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Collection mode (other days showing): no scribbling — one finger just moves around.
                    val collecting = scale < 0.97f
                    fun world(p: Offset) = listOf((p.x - offset.x) / scale / w, (p.y - offset.y) / scale / h)
                    var transforming = false
                    // Only today's sheet can be drawn on: ink outside it is ignored.
                    fun onToday(p: List<Float>) = p[0] in 0f..1f && p[1] in 0f..1f
                    current.clear(); if (!collecting) world(down.position).takeIf(::onToday)?.let { current.addAll(it) }
                    strokeSeed = kotlin.random.Random.nextInt()
                    var inked = 0f // canvas px of line laid in this stroke, for the ink running out
                    var lastT = down.uptimeMillis
                    // In the collection: a finger on a record grabs it, on the knob turns it; else it pans.
                    val grabbed = if (collecting) vinyl.hitRecord(frameDp(down.position)) else null
                    val turning = collecting && grabbed == null && vinyl.playing != null && dialIn > 0.5f &&
                        (down.position - knobC).getDistance() <= knobR + 16.dp.toPx()
                    var lastTick = Math.round(vinyl.volume * 6f)
                    if (grabbed != null) {
                        vinyl.dragging = grabbed; down.consume()
                        vinylAudio.pickUp()
                        // Lifting the playing record off stops the music straight away.
                        if (vinyl.playing == grabbed) vinylAudio.stop()
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!transforming) { transforming = true; current.clear(); sound.speed = 0f }
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val next = (scale * zoom).coerceIn(minScale, MAX_SCALE)
                            // At full size the page is always today's sheet — no drifting onto other dates.
                            offset = if (next >= MAX_SCALE - 0.001f) Offset.Zero
                                     else clamp(centroid - (centroid - offset) * (next / scale) + pan, next)
                            scale = next
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && !transforming && collecting) {
                            val c = event.changes.first { it.pressed }
                            when {
                                grabbed != null -> {
                                    val before = vinyl.blockedOver(grabbed)
                                    vinyl.drag(grabbed, c.positionChange() / scale / k)
                                    // Brought over a record that's already playing: an annoying scratch says "no".
                                    if (!before && vinyl.blockedOver(grabbed)) vinylAudio.reject()
                                }
                                turning -> {
                                    // Snap to the next line: click + a light tap in the hand.
                                    val t = vinyl.turnKnob(c.position, knobC)
                                    if (t != lastTick) {
                                        lastTick = t
                                        vinylAudio.tick() // deep thock + a little vibration
                                    }
                                }
                                else -> offset = clamp(offset + c.positionChange(), scale)
                            }
                            c.consume()
                        } else if (pressed == 1 && !transforming) {
                            val c = event.changes.first { it.pressed }
                            val moved = c.positionChange()
                            if (moved != Offset.Zero) {
                                world(c.position).takeIf(::onToday)?.let { current.addAll(it) }; c.consume()
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
                    if (grabbed != null) {
                        val wasPlaying = vinyl.playing
                        if (transforming) vinyl.dragging = null else vinyl.drop(grabbed)
                        if (vinyl.playing == grabbed) {
                            // Settled on the platter: thunk, then needle crackle, then the song.
                            vinylAudio.settle()
                            vinylAudio.play(grabbed)
                        } else if (wasPlaying == grabbed) vinylAudio.stop()
                    }
                    if (!transforming && !collecting && current.size >= 4) vm.addStroke(current.toList(), strokeSeed)
                    current.clear()
                }
            },
    ) {
        // White table (#FEFEFE, behind) with one paper sheet per day lying on it.
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }) {
            val insets = WindowInsets.safeDrawing
            val dens = androidx.compose.ui.platform.LocalDensity.current
            Box(Modifier.fillMaxSize().paperSheets((1..days).map { sheet(it) },
                bleedTop = insets.getTop(dens).toFloat(), bleedBottom = insets.getBottom(dens).toFloat()))
        }
        underInk()
        // The prompt is part of the page, not the canvas: it stays put, and fades as the month comes in.
        if (byDay[today].isNullOrEmpty() && current.isEmpty() && reveal < 0.99f) {
            Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - reveal },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("leave a scribble", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 20.sp,
                    color = Color(0xFF222222).copy(alpha = 0.4f))
                SquiggleInk(Modifier.padding(top = 4.dp).size(102.dp, 46.dp).rotate(6.844f))
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
            Canvas(Modifier.fillMaxSize()) {
                val sw = size.width; val sh = size.height
                // Titles and the music corner live on the canvas too, sized for the zoomed-out view.
                if (reveal > 0.01f) {
                    val k = frameScreens * sw / 360f
                    val titleStyle = androidx.compose.ui.text.TextStyle(fontFamily = Cormorant,
                        fontWeight = FontWeight.SemiBold, fontSize = (24f * frameScreens).sp)
                    fun title(text: String, centreX: Float, a: Float) {
                        val m = titles.measure(text, titleStyle)
                        drawText(m, color = Color(0xFF222222).copy(alpha = a * reveal),
                            topLeft = Offset(centreX - m.size.width / 2f, my0 * sh + 100f * k))
                    }
                    title("days together", (gridLeft + gridRight) / 2f * sw, 0.6f)
                    val kh = musicScreens * sw / 360f
                    title("some music?", mx0 * sw + 180f * kh, 0.6f)
                    drawVinyl(vinyl, Offset(mx0 * sw, my0m * sh), kh, shownRecords, shownRadii, armPos, reveal, layer = 0)
                }
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
            if (reveal > 0.01f) {
                // The playing record on its own layer: only the layer rotates each frame, nothing redraws.
                val pi = vinyl.playing
                if (pi != null && vinyl.dragging != pi) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer {
                        val kk = musicScreens * size.width / 360f
                        val cx = mx0 * size.width + shownRecords[pi].x * kk
                        val cy = my0m * size.height + shownRecords[pi].y * kk
                        transformOrigin = TransformOrigin(cx / size.width, cy / size.height)
                        rotationZ = spin.value
                    }) {
                        val kk = musicScreens * size.width / 360f
                        val c = Offset(mx0 * size.width + shownRecords[pi].x * kk, my0m * size.height + shownRecords[pi].y * kk)
                        drawRecord(c, shownRadii[pi] * kk, vinyl.labels[pi], reveal, 0f)
                    }
                }
                // Tonearm above the record.
                Canvas(Modifier.fillMaxSize()) {
                    val kk = musicScreens * size.width / 360f
                    drawVinyl(vinyl, Offset(mx0 * size.width, my0m * size.height), kk, shownRecords, shownRadii, armPos, reveal, layer = 1)
                }
            }
            // Date numbers above each sheet; sized for reading when zoomed out.
            if (reveal > 0.01f) {
                for (d in 1..days) {
                    val o = sheet(d)
                    Text(
                        // Same colour as the home page's time text (#222F36 at 60%), bigger so it reads zoomed out.
                        "$d", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 120.sp,
                        color = TimeText.copy(alpha = 0.6f * reveal),
                        modifier = Modifier.offset {
                            androidx.compose.ui.unit.IntOffset((o.x * viewSize.width + 24.dp.toPx()).toInt(),
                                (o.y * viewSize.height - 160.dp.toPx()).toInt())
                        },
                    )
                }
            }
        }
        // The volume knob lives on the screen, centred above the bottom track, while the collection is out.
        if (reveal > 0.01f && dialIn > 0.01f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                // rises in from just below as it appears
                translationY = (1f - dialIn) * DIAL_R.toPx() * 1.7f
            }) {
                drawVolumeKnob(Offset(size.width / 2f, size.height - KNOB_FROM_BOTTOM.toPx()),
                    DIAL_R.toPx(), vinyl.volume, reveal * dialIn.coerceIn(0f, 1f))
            }
        }
    }
}

/** The "leave a scribble" squiggle, drawn in the same textured ink as real scribbles (and never dry). */
@Composable
private fun SquiggleInk(modifier: Modifier) {
    // Sample the Figma path (viewBox 110 × 41) into normalised points once.
    val pts = remember {
        val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(SQUIGGLE).toPath()
        val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val n = 140
        (0..n).flatMap { i ->
            val p = m.getPosition(m.length * i / n)
            listOf(p.x / 110f, p.y / 41f)
        }
    }
    Canvas(modifier) {
        drawInk(pts, Color(0xFF452E30).copy(alpha = 0.3f), pts.size / 2, 3.dp.toPx(), seed = 4242, neverDry = true)
    }
}
