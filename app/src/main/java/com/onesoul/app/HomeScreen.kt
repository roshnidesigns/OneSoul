package com.onesoul.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home — Figma section "home" (frame 360 × 800): the other person's day-line on top, yours at the
 * bottom (the dot nearest you is you), and between them the shared card you scribble on.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    now: Long,
    musicAccess: Boolean,
    onCanvas: () -> Unit,
    onWraps: () -> Unit,
    onDays: () -> Unit,
) {
    val p = vm.profile ?: return
    val partnerHour = hourOf(now, p.partnerTz)
    val myHour = hourOf(now, vm.myTz)
    val partnerColor = dotColorC(partnerHour)
    val myColor = dotColorC(myHour)
    val together = vm.partnerHere(now)

    val active = vm.activeSnippet(now)
    val bpm = active?.first?.bpm ?: 40
    val strength = active?.second ?: 0f
    val beat = rememberInfiniteTransition(label = "beat")
    val pulse by beat.animateFloat(
        0f, 1f, infiniteRepeatable(tween(60_000 / bpm, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    var showSnippet by remember { mutableStateOf(false) }
    var showDemo by remember { mutableStateOf(false) }
    val current = remember { mutableStateListOf<Float>() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            // Plain paper colour underneath; the paper's fibres are vectors in the canvas (PaperStructure)
            .background(PaperBase)
            .safeDrawingPadding(),
    ) {
        val w = maxWidth
        val h = maxHeight
        // Frame 7317: 24 hour dots, 8 × 8, spread edge to edge with 8dp side padding; rows centred 44dp in.
        val topRow = 44.dp
        val bottomRow = h - 44.dp
        // Track inset so a dot at either end still has room for its time label centred on it.
        val first = 32.dp
        val span = w - 64.dp
        // Centre x of a person's 32dp dot for their local hour (00:00 at the first hour dot, 24:00 at the last).
        val partnerX by animateDpAsState(first + span * (partnerHour / 24f), tween(1200), label = "partnerX")
        val myX by animateDpAsState(first + span * (myHour / 24f), tween(1200), label = "myX")

        // Each person's sky: a large disc set off from their dot, filled with an angled fade
        // (colour on the dot's side → clear), as in the Figma frame. Not clipped, so it runs under the bars.
        Canvas(Modifier.fillMaxSize()) {
            sky(Offset(partnerX.toPx(), topRow.toPx()), partnerColor, towardsBottom = true)
            sky(Offset(myX.toPx(), bottomRow.toPx()), myColor, towardsBottom = false)
            if (strength > 0f) drawRect(Brush.radialGradient(
                listOf(bpmColor(bpm).copy(alpha = strength * (0.2f + 0.3f * pulse)), Color.Transparent),
                radius = size.maxDimension * (0.45f + 0.1f * pulse),
            ))
        }

        // The shared scribble space: the whole month on one canvas behind the fixed time tracks (see ScribbleSpace).
        ScribbleSpace(vm, myColor, now)

        // Hour dots sit at the back of the time track: above the sky, beneath the person dots and labels.
        HourDots(topRow - 4.dp, vm.activeHours(Author.PARTNER, p.partnerTz, now))
        HourDots(bottomRow - 4.dp, vm.activeHours(Author.ME, vm.myTz, now))

        // Group 33 (them, top) and Group 32 (you, bottom): 32dp glowing dots that travel the 24 hours
        DayDot(partnerX - 16.dp, topRow - 16.dp, partnerColor, together, pulse)
        DayDot(myX - 16.dp, bottomRow - 16.dp, myColor, together, pulse)

        // Each person's local time, centred on their dot: under the top dot, over the bottom one.
        TimeLabel(clock(now, p.partnerTz), Modifier.offset(x = partnerX - 40.dp, y = topRow + 24.dp))
        TimeLabel(clock(now, vm.myTz), Modifier.offset(x = myX - 40.dp, y = bottomRow - 48.dp))

        Text(
            "demo", fontSize = 11.sp, color = Ink.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                .clickable { showDemo = true }.padding(6.dp),
        )

    }

    if (showSnippet) SnippetDialog(vm, onDismiss = { showSnippet = false })
    if (showDemo) DemoDialog(vm, onDismiss = { showDemo = false },
        onSnippet = { showSnippet = true }, onWraps = onWraps)
}

/**
 * A person's sky travels with their dot. The disc's centre sits 248dp in from the dot and swings
 * smoothly from 133dp to its right (dot at 00:00) to 133dp to its left (dot at 24:00), so the
 * glow always opens into the screen. The angled fade starts just behind the dot and clears past the centre.
 */
private fun DrawScope.sky(dot: Offset, color: Color, towardsBottom: Boolean, blend: BlendMode = BlendMode.SrcOver) {
    val r = size.width * 1.5f
    val f = (dot.x / size.width).coerceIn(0f, 1f)
    val c = Offset(dot.x + 133.dp.toPx() * (1f - 2f * f), dot.y + (if (towardsBottom) 248.dp else (-248).dp).toPx())
    val dir = c - dot
    drawCircle(
        Brush.linearGradient(
            // Linear, stop 0% = dot colour at 80% opacity, stop 80% = #FFFFFF at 0% (clear beyond).
            0f to color.copy(alpha = 0.8f), 0.8f to Color.White.copy(alpha = 0f),
            start = dot - dir * 0.6f,
            end = c + dir * 0.3f,
        ),
        radius = r, center = c, blendMode = blend,
    )
}

/** Draws a stroked SVG path (viewBox w × h) scaled into the modifier's size. */
@Composable
internal fun SvgIcon(d: String, w: Int, h: Int, color: Color, strokeWidth: Float, modifier: Modifier) {
    val path = remember(d) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }
    Canvas(modifier) {
        scale(size.width / w, size.height / h, pivot = Offset.Zero) {
            drawPath(path, color, style = DrawStroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal const val SQUIGGLE = "M1.49996 30.4312C1.87778 29.832 9.9138 24.812 15.7734 22.1115C24.6098 18.0392 30.8495 15.9265 39.6689 15.06C42.4019 14.7915 45.8002 15.2645 47.643 15.7223C49.4857 16.1801 49.6834 16.879 49.6497 18.2293C49.4838 24.8687 46.4939 29.422 43.7761 33.3599C41.4469 36.7347 38.3829 38.4547 35.9328 39.2302C34.9018 39.5564 35.6054 36.5507 36.1486 34.5901C36.7702 32.3461 39.4414 28.8365 43.653 23.9191C48.2081 18.6006 52.9514 15.2211 57.0768 12.508C67.7418 5.49393 74.0382 4.62303 77.3393 4.35407C78.4579 4.26293 79.9726 4.54275 80.8479 4.99989C81.7231 5.45703 81.9162 6.2189 81.9635 6.9684C82.1523 9.95854 80.4304 13.4091 80.0158 16.007C80.3556 16.7096 81.8124 16.1621 86.4959 13.5932C91.1794 11.0242 99.0455 6.45022 107.71 1.50022"

@Composable
private fun TimeLabel(text: String, modifier: Modifier) {
    Text(
        text, modifier = modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 18.sp,
        color = TimeText.copy(alpha = 0.6f),
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "lnum, tnum"),
    )
}

/** One row of 24 hour dots: 8 × 8, #000000 at 10% (or the hour's colour once active), space-between, inset 28dp so the first/last dot centres sit 32dp in. */
@Composable
private fun HourDots(y: Dp, active: Set<Int>) {
    Row(
        Modifier.offset(y = y).fillMaxWidth().padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // An hour this person was active in today takes that hour's colour from the time-of-day grid.
        repeat(24) { h ->
            val c = if (h in active) dotColorC(h + 0.5f) else Color.Black.copy(alpha = 0.1f)
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun DayDot(x: Dp, y: Dp, color: Color, ring: Boolean, pulse: Float) {
    Box(Modifier.offset(x = x, y = y)) {
        if (ring) {
            Box(Modifier.offset((-6).dp - (4 * pulse).dp, (-6).dp - (4 * pulse).dp)
                .size(44.dp + (8 * pulse).dp).border(2.dp, color.copy(alpha = 0.6f), CircleShape))
        }
        // Group 32/33: the dot plus a blurred copy of itself (Gaussian σ 6) as a glow
        Box(Modifier.size(32.dp).blur(12.dp, BlurredEdgeTreatment.Unbounded).clip(CircleShape).background(color))
        Box(Modifier.size(32.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun PencilIcon(tint: Color = Ink) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        val path = Path().apply {
            moveTo(15f * s, 5f * s); lineTo(19f * s, 9f * s)
            moveTo(21.17f * s, 6.81f * s)
            lineTo(7.83f * s, 20.16f * s); lineTo(2.64f * s, 21.98f * s); lineTo(2.02f * s, 21.36f * s)
            lineTo(3.84f * s, 16.17f * s); lineTo(17.19f * s, 2.83f * s)
            cubicTo(18.3f * s, 1.7f * s, 20.05f * s, 1.7f * s, 21.17f * s, 2.83f * s)
            cubicTo(22.28f * s, 3.94f * s, 22.28f * s, 5.7f * s, 21.17f * s, 6.81f * s)
        }
        drawPath(path, tint, style = DrawStroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CopyIcon(tint: Color = Ink) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        val st = DrawStroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(tint, Offset(8f * s, 8f * s), androidx.compose.ui.geometry.Size(14f * s, 14f * s),
            androidx.compose.ui.geometry.CornerRadius(2f * s), style = st)
        drawPath(Path().apply {
            moveTo(4f * s, 16f * s); cubicTo(2.9f * s, 16f * s, 2f * s, 15.1f * s, 2f * s, 14f * s)
            lineTo(2f * s, 4f * s); cubicTo(2f * s, 2.9f * s, 2.9f * s, 2f * s, 4f * s, 2f * s)
            lineTo(14f * s, 2f * s); cubicTo(15.1f * s, 2f * s, 16f * s, 2.9f * s, 16f * s, 4f * s)
        }, tint, style = st)
    }
}

fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun SnippetDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val np = NowPlaying.info
    val fallback = vm.mySong()
    val title = np?.title ?: fallback?.title
    val artist = np?.artist ?: fallback?.artist ?: ""
    val duration = (np?.durationSec ?: 0).takeIf { it > 0 } ?: 240
    var start by remember { mutableFloatStateOf((np?.positionSec ?: 0).coerceAtMost(duration - 5).toFloat()) }
    var len by remember { mutableFloatStateOf(15f) }
    var bpm by remember { mutableFloatStateOf(100f) }
    val name = vm.profile?.partnerName ?: "them"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send a snippet") },
        text = {
            if (title == null) {
                Text("Play a song first (or set one manually on the home card).")
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$title — $artist", fontWeight = FontWeight.Medium)
                Text("From ${mmss(start.toInt())} for ${len.toInt()}s", fontSize = 13.sp)
                Slider(start, { start = it }, valueRange = 0f..(duration - 5).toFloat())
                Slider(len, { len = it }, valueRange = 5f..30f)
                Text("Feels like ${bpm.toInt()} bpm", fontSize = 13.sp)
                Slider(bpm, { bpm = it }, valueRange = 60f..180f)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(bpmColor(bpm.toInt())))
                Text("Its tempo will gently move $name's screen for a few hours.", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            }
        },
        confirmButton = {
            TextButton(enabled = title != null, onClick = {
                vm.sendSnippet(title!!, artist, start.toInt(), len.toInt(), bpm.toInt()); onDismiss()
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun SetSongDialog(onDismiss: () -> Unit, onSet: (String, String) -> Unit) {
    var t by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What are you listening to?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Song") }, singleLine = true)
                OutlinedTextField(a, { a = it }, label = { Text("Artist") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = t.isNotBlank(), onClick = { onSet(t.trim(), a.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DemoDialog(vm: AppViewModel, onDismiss: () -> Unit, onSnippet: () -> Unit, onWraps: () -> Unit) {
    val name = vm.profile?.partnerName ?: "Them"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pretend to be $name") },
        text = {
            Column {
                Text("Stand-ins for the other phone until syncing exists.", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                listOf<Pair<String, () -> Unit>>(
                    "Send $name a snippet" to onSnippet,
                    "Open day wraps" to onWraps,
                    "$name plays a song" to { vm.simulateSong() },
                    "$name plays the same song as you" to { vm.simulateSong(copyMine = true) },
                    "$name sends a snippet" to vm::simulateSnippet,
                    "$name scribbles" to vm::simulateScribble,
                    "$name opens the app now" to vm::simulateHere,
                    "Close today (make a wrap)" to vm::closeTodayForDemo,
                    "Fill the month with sample sketches" to vm::fillMonthWithSamples,
                    "Remove sample sketches" to vm::removeSamples,
                    "Reset everything" to vm::unpair,
                ).forEach { (label, action) ->
                    Text(label, fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                        .clickable { action(); onDismiss() }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
