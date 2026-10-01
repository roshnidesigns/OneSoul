package com.onesoul.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** End-of-day wraps (a new day starts at 00:00 UTC for both people) plus the week's canvas playback. */
@Composable
fun WrapScreen(vm: AppViewModel, now: Long, onBack: () -> Unit) {
    val today = remember(vm.strokes.size, vm.songs.size, vm.snippets.size, vm.presence.size) { vm.previewTodayWrap() }
    val past = vm.wraps.filter { it.dayKey != today.dayKey }.sortedByDescending { it.dayKey }.take(7)

    LazyColumn(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("←", fontSize = 24.sp, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                Text("Day wraps", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Text("Today so far", fontSize = 12.sp, color = Ink.copy(alpha = 0.5f)) }
        item { WrapCard(today, vm.profile?.partnerName ?: "them") }
        item { WeekPlayback(vm, now) }
        if (past.isNotEmpty()) item { Text("The last seven days", fontSize = 12.sp, color = Ink.copy(alpha = 0.5f)) }
        items(past, key = { it.dayKey }) { WrapCard(it, vm.profile?.partnerName ?: "them") }
    }
}

@Composable
private fun WrapCard(w: Wrap, partnerName: String) {
    val motion = rememberInfiniteTransition(label = "wrap")
    val t by motion.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4 * 60_000 / w.bpm.coerceAtLeast(40), easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val a = colorOf(w.myColor)
    val b = colorOf(w.partnerColor)
    val day = runCatching { LocalDate.parse(w.dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM")) }.getOrDefault(w.dayKey)

    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(
                listOf(b, Color.White, a),
                start = Offset(0f, 600f * t), end = Offset(900f, 600f * (1 - t)),
            )),
    ) {
        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(day, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            Text(w.text, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    "${w.strokes} strokes".takeIf { w.strokes > 0 },
                    "${w.songs} songs".takeIf { w.songs > 0 },
                    "${w.snippets} snippets".takeIf { w.snippets > 0 },
                    "same song with $partnerName".takeIf { w.sameSong },
                    "together".takeIf { w.bothHere },
                ).joinToString(" · ").ifEmpty { " " },
                fontSize = 12.sp, color = Ink.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun WeekPlayback(vm: AppViewModel, now: Long) {
    val week = vm.strokes.filter { now - it.t < 7L * 24 * 60 * 60 * 1000 }.sortedBy { it.t }
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The week's canvas", fontSize = 12.sp, color = Ink.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
            OutlinedButton(enabled = week.isNotEmpty(), onClick = {
                scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween(6000, easing = LinearEasing)) }
            }) { Text("Play it back") }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(24.dp)).background(Color(0xFFF5F5F5))) {
            if (week.isEmpty()) {
                Text("No strokes this week yet.", fontSize = 13.sp, color = Ink.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center))
            }
            Canvas(Modifier.fillMaxSize()) {
                val total = week.sumOf { it.pts.size / 2 }
                var budget = (total * progress.value).toInt()
                for (s in week) {
                    if (budget <= 0) break
                    val n = s.pts.size / 2
                    drawStroke(s.pts, colorOf(s.color), upTo = minOf(n, budget), seed = s.seed)
                    budget -= n
                }
            }
        }
    }
}
