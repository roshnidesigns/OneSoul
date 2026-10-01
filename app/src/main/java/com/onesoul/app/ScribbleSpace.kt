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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Canvas extent in screen-widths/heights around the starting view: 2 out to each side, so 5 × 5 screens. */
private const val REACH = 2f
private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 1f // never larger than the size the scribbles were drawn at

/**
 * The shared scribble space as one big canvas. One finger draws; two fingers pan and zoom it.
 * Strokes are stored in "screen units" of the starting view (0..1 is what you first see; the canvas
 * reaches [REACH] screens beyond in every direction), so older scribbles stay where they were.
 * Pinching in past the furthest zoom-out keeps shrinking the page and, on release, steps back to the month.
 */
@Composable
fun ScribbleSpace(vm: AppViewModel, myColor: Color, onDays: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val current = remember { mutableStateListOf<Float>() }
    // A fresh, unpredictable texture for every stroke — picked as the finger lands, kept once drawn.
    var strokeSeed by remember { mutableIntStateOf(0) }
    val overshoot = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // Pen-on-paper scratch while drawing; released when the page goes away.
    val sound = remember { ScratchSound() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { sound.release() } }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                fun clamp(o: Offset, s: Float) = Offset(
                    o.x.coerceIn(w - (1 + REACH) * w * s, REACH * w * s),
                    o.y.coerceIn(h - (1 + REACH) * h * s, REACH * h * s),
                )
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun world(p: Offset) = listOf((p.x - offset.x) / scale / w, (p.y - offset.y) / scale / h)
                    var transforming = false
                    var squeeze = 1f
                    var startedOut = false // only a fresh pinch from the fully zoomed-out view steps to the month
                    current.clear(); current.addAll(world(down.position))
                    strokeSeed = kotlin.random.Random.nextInt()
                    var inked = 0f // canvas px of line laid in this stroke, for the ink running out
                    var lastT = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!transforming) {
                                transforming = true; current.clear(); sound.speed = 0f
                                startedOut = scale <= MIN_SCALE + 0.001f
                            }
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            // Already as far out as it goes and still pinching in → build up the "step back".
                            if (startedOut && scale <= MIN_SCALE + 0.001f && zoom < 1f) squeeze = (squeeze * zoom).coerceAtLeast(0.6f)
                            else if (zoom > 1f) squeeze = (squeeze * zoom).coerceAtMost(1f)
                            offset = clamp(centroid - (centroid - offset) * (next / scale) + pan, next)
                            scale = next
                            scope.launch { overshoot.snapTo(squeeze) }
                            event.changes.forEach { it.consume() }
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

                    when {
                        transforming && squeeze < 0.85f -> onDays()
                        transforming -> scope.launch { overshoot.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                        current.size >= 4 -> vm.addStroke(current.toList(), strokeSeed)
                    }
                    current.clear()
                }
            },
    ) {
        // The prompt is part of the page, not the canvas: it stays put while you pan and zoom.
        if (vm.strokes.isEmpty() && current.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("leave a scribble", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 20.sp,
                    color = Color(0xFF222222).copy(alpha = 0.4f))
                SvgIcon(SQUIGGLE, 110, 41, Color(0xFF452E30).copy(alpha = 0.3f), 3f,
                    Modifier.padding(top = 4.dp).size(102.dp, 46.dp).rotate(6.844f))
            }
        }
        // Only the scribbles live in canvas space and move/zoom together.
        Box(
            Modifier.fillMaxSize()
                // Overshoot: past the furthest zoom-out the scribbles keep shrinking (around the centre) and fade.
                .graphicsLayer {
                    scaleX = overshoot.value; scaleY = overshoot.value
                    alpha = 1f - ((1f - overshoot.value) * 1.6f).coerceIn(0f, 0.5f)
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        ) {
            // Vector paper across the whole canvas, so the page itself visibly zooms and pans.
            Box(Modifier.fillMaxSize().paperStructure(-REACH, 1 + REACH))
            Canvas(Modifier.fillMaxSize()) {
                vm.strokes.forEach { drawStroke(it.pts, colorOf(it.color), seed = it.seed) }
                drawStroke(current, myColor, seed = strokeSeed)
            }
        }
    }
}
