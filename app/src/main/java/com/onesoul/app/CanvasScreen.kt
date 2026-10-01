package com.onesoul.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The shared canvas: either person adds strokes whenever; each stroke keeps its local time and colour. */
@Composable
fun CanvasScreen(vm: AppViewModel, now: Long, onBack: () -> Unit) {
    val p = vm.profile ?: return
    val myColor = dotColorC(hourOf(now, vm.myTz))
    val current = remember { mutableStateListOf<Float>() }
    val lastTheirs = vm.strokes.lastOrNull { it.author == Author.PARTNER }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Coral.copy(alpha = 0.25f), Color.White, Teal.copy(alpha = 0.25f))))
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("←", fontSize = 24.sp, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Column {
                Text("Our canvas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    lastTheirs?.let { "${p.partnerName} drew ${ago(it.t, now)} · ${clock(it.t, it.tz)} their time" }
                        ?: "Draw anything. ${p.partnerName} will find it later.",
                    fontSize = 12.sp, color = Ink.copy(alpha = 0.6f),
                )
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> current.clear(); current.add(o.x / size.width); current.add(o.y / size.height) },
                        onDrag = { change, _ ->
                            current.add(change.position.x / size.width); current.add(change.position.y / size.height)
                        },
                        onDragEnd = { vm.addStroke(current.toList()); current.clear() },
                        onDragCancel = { current.clear() },
                    )
                },
            ) {
                vm.strokes.forEach { drawStroke(it.pts, colorOf(it.color), seed = it.seed) }
                drawStroke(current, myColor)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(myColor, "${p.myName} now")
            Legend(dotColorC(hourOf(now, p.partnerTz)), "${p.partnerName} now")
            Text("Ink follows the sun where you each are.", fontSize = 11.sp, color = Ink.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 12.sp)
    }
}

/** Draws a normalised stroke as textured ink (see Ink.kt); [upTo] lets playback reveal only part of it. */
fun DrawScope.drawStroke(pts: List<Float>, color: Color, upTo: Int = pts.size / 2, width: Float = 6.dp.toPx(), seed: Int = 0) {
    drawInk(pts, color, upTo, width, seed)
}
