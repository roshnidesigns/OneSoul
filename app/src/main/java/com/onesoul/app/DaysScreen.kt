package com.onesoul.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val COLUMNS = 4

/**
 * Figma "collection-scribbles": one dot per date of this month (28–31), four to a row.
 * A date with scribbles shows them, small, over its dot; tap one to see that day's paper.
 */
@Composable
fun DaysScreen(vm: AppViewModel, now: Long, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val zone = java.time.ZoneId.of(vm.myTz)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val month = java.time.YearMonth.from(today)
    // Strokes land on the calendar date where you are.
    val byDay = vm.strokes.groupBy { java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate().toString() }
    var open by remember { mutableStateOf<String?>(null) }

    // Spread two fingers to zoom back into today's paper.
    Box(Modifier.fillMaxSize().background(Color.White).pinchToNavigate(onZoomIn = onBack)) {
        Image(painterResource(R.drawable.paper), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            // "days together": Cormorant Garamond 24 Medium, #222 at 40%
            Text("days together", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 24.sp,
                color = Color(0xFF222222).copy(alpha = 0.4f), modifier = Modifier.padding(top = 78.dp).clickable(onClick = onBack))

            // Frame 7334 — rows of 4 dots (the last row holds what's left), 60dp side padding, 58dp between rows
            Column(
                Modifier.padding(top = 55.dp).fillMaxWidth().padding(horizontal = 60.dp),
                verticalArrangement = Arrangement.spacedBy(58.dp),
            ) {
                (1..month.lengthOfMonth()).chunked(COLUMNS).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        week.forEach { d ->
                            val key = month.atDay(d).toString()
                            DayCell(byDay[key].orEmpty()) { open = key }
                        }
                    }
                }
            }
        }
    }

    open?.let { key ->
        Dialog(onDismissRequest = { open = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                    fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Box(Modifier.fillMaxWidth().aspectRatio(360f / 800f).clip(RoundedCornerShape(24.dp))
                    .clickable { open = null }) {
                    Image(painterResource(R.drawable.paper), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) { byDay[key].orEmpty().forEach { drawStroke(it.pts, colorOf(it.color), seed = it.seed) } }
                }
            }
        }
    }
}

/** A 10dp #D9D9D9 day dot; if that day has scribbles, they're drawn small, centred over it. */
@Composable
private fun DayCell(strokes: List<Stroke>, onOpen: () -> Unit) {
    Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD9D9D9)))
        if (strokes.isNotEmpty()) {
            // Scribbles cover the whole 360 × 800 page, so the thumbnail keeps that shape: 36 × 80 around the dot.
            Box(Modifier.requiredSize(36.dp, 80.dp).clickable(onClick = onOpen)) {
                Canvas(Modifier.fillMaxSize()) {
                    strokes.forEach { drawStroke(it.pts, colorOf(it.color), width = 2.dp.toPx(), seed = it.seed) }
                }
            }
        }
    }
}
