package com.onesoul.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Two-finger pinch that follows the fingers: while pinching, the screen scales and fades with them;
 * on release it either commits ([onZoomOut] / [onZoomIn], then the screen transition takes over) or
 * springs back. Only takes over once a second finger lands, so one-finger drawing keeps working.
 */
@Composable
fun Modifier.pinchToNavigate(onZoomOut: (() -> Unit)? = null, onZoomIn: (() -> Unit)? = null): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
            // Fade a little as it shrinks or grows, so the next screen can come through.
            alpha = 1f - (kotlin.math.abs(1f - scale.value) * 1.6f).coerceIn(0f, 0.5f)
        }
        .pointerInput(onZoomOut, onZoomIn) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var pinching = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        pinching = true
                        zoom *= event.calculateZoom()
                        event.changes.forEach { it.consume() }
                        // Only move in the direction this screen can go; resist the other way.
                        val target = when {
                            zoom < 1f && onZoomOut != null -> zoom.coerceAtLeast(0.6f)
                            zoom > 1f && onZoomIn != null -> zoom.coerceAtMost(1.4f)
                            else -> 1f + (zoom - 1f) * 0.15f
                        }
                        scope.launch { scale.snapTo(target) }
                    }
                } while (event.changes.any { it.pressed })
                if (!pinching) return@awaitEachGesture
                when {
                    zoom < 0.85f && onZoomOut != null -> onZoomOut()
                    zoom > 1.18f && onZoomIn != null -> onZoomIn()
                    else -> scope.launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                }
            }
        }
}
