package com.onesoul.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cormorant_garamond, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cormorant_garamond, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cormorant_garamond, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

data class Place(val city: String, val tz: String)

private val aliases = mapOf(
    // cities the zone database doesn't name
    "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata", "mumbai" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata",
    "bengaluru" to "Asia/Kolkata", "ahmedabad" to "Asia/Kolkata", "pune" to "Asia/Kolkata", "chennai" to "Asia/Kolkata",
    "hyderabad" to "Asia/Kolkata", "aarhus" to "Europe/Copenhagen", "san francisco" to "America/Los_Angeles",
    "seattle" to "America/Los_Angeles", "beijing" to "Asia/Shanghai", "boston" to "America/New_York",
    "washington" to "America/New_York", "miami" to "America/New_York", "munich" to "Europe/Berlin", "milan" to "Europe/Rome",
    "florence" to "Europe/Rome", "barcelona" to "Europe/Madrid", "manchester" to "Europe/London",
    "melbourne" to "Australia/Melbourne", "osaka" to "Asia/Tokyo", "kyoto" to "Asia/Tokyo",
    // countries (one representative zone each)
    "india" to "Asia/Kolkata", "italy" to "Europe/Rome", "denmark" to "Europe/Copenhagen", "sweden" to "Europe/Stockholm",
    "norway" to "Europe/Oslo", "finland" to "Europe/Helsinki", "germany" to "Europe/Berlin", "france" to "Europe/Paris",
    "spain" to "Europe/Madrid", "portugal" to "Europe/Lisbon", "netherlands" to "Europe/Amsterdam",
    "belgium" to "Europe/Brussels", "switzerland" to "Europe/Zurich", "austria" to "Europe/Vienna", "poland" to "Europe/Warsaw",
    "greece" to "Europe/Athens", "ireland" to "Europe/Dublin", "united kingdom" to "Europe/London", "uk" to "Europe/London",
    "england" to "Europe/London", "turkey" to "Europe/Istanbul", "japan" to "Asia/Tokyo", "china" to "Asia/Shanghai",
    "korea" to "Asia/Seoul", "south korea" to "Asia/Seoul", "singapore" to "Asia/Singapore", "thailand" to "Asia/Bangkok",
    "vietnam" to "Asia/Ho_Chi_Minh", "indonesia" to "Asia/Jakarta", "philippines" to "Asia/Manila",
    "pakistan" to "Asia/Karachi", "bangladesh" to "Asia/Dhaka", "nepal" to "Asia/Kathmandu", "sri lanka" to "Asia/Colombo",
    "uae" to "Asia/Dubai", "united arab emirates" to "Asia/Dubai", "israel" to "Asia/Jerusalem", "egypt" to "Africa/Cairo",
    "kenya" to "Africa/Nairobi", "nigeria" to "Africa/Lagos", "south africa" to "Africa/Johannesburg",
    "usa" to "America/New_York", "united states" to "America/New_York", "canada" to "America/Toronto",
    "mexico" to "America/Mexico_City", "brazil" to "America/Sao_Paulo", "argentina" to "America/Argentina/Buenos_Aires",
    "chile" to "America/Santiago", "colombia" to "America/Bogota", "peru" to "America/Lima",
    "australia" to "Australia/Sydney", "new zealand" to "Pacific/Auckland",
)

private fun titleCase(s: String) = s.split(' ').joinToString(" ") { w ->
    if (w.length <= 3 && w in setOf("uk", "usa", "uae")) w.uppercase() else w.replaceFirstChar(Char::uppercase)
}

private val cityZones: List<Place> by lazy {
    val regions = setOf("Africa", "America", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
    ZoneId.getAvailableZoneIds().filter { it.substringBefore('/') in regions }
        .map { Place(it.substringAfterLast('/').replace('_', ' '), it) }.sortedBy { it.city }
}

/** Matches a typed city to a time zone, offline: zone-database cities plus a few common aliases. */
fun findPlaces(q: String): List<Place> {
    val s = q.trim().lowercase()
    if (s.length < 2) return emptyList()
    val alias = aliases.filterKeys { it.startsWith(s) }.map { Place(titleCase(it.key), it.value) }
    val zones = cityZones.filter { it.city.lowercase().startsWith(s) }
    return (alias + zones).distinctBy { it.city.lowercase() }.take(3)
}

/**
 * Figma frame 39 + "button state": "one soul", a soft glow and "Where do you live?".
 * The first person adds their place; the second sees "one soul from … is already here" and adds theirs.
 * Once both are in, onboarding is gone for good.
 */
@Composable
fun OnboardingScreen(onDone: (me: Place, them: Place) -> Unit) {
    var mine by remember { mutableStateOf<Place?>(null) }
    var text by remember(mine) { mutableStateOf("") }
    var chosen by remember(mine) { mutableStateOf<Place?>(null) }
    val focus = remember { FocusRequester() }
    val found = findPlaces(text)
    // A place is "detected" when what you typed names it exactly, or only one place fits.
    val detected = chosen
        ?: found.firstOrNull { it.city.equals(text.trim(), ignoreCase = true) }
        ?: found.singleOrNull()?.takeIf { text.trim().length >= 3 }
    val suggestions = if (detected != null) emptyList() else found

    fun join() {
        val p = detected ?: return
        val first = mine
        if (first == null) mine = p else onDone(first, p)
    }

    LaunchedEffect(mine) { focus.requestFocus() }

    // Positions copied from the Figma "onboarding" section (390 × 800 frames).
    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("one soul", fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Color(0xFF666666))
            // The first person is in: tell the second one, in orange.
            if (mine != null) Text(
                "one soul  from ${mine!!.city} is already here",
                fontFamily = Cormorant, fontSize = 14.sp, color = Coral,
            )
        }

        Column(Modifier.align(Alignment.TopCenter).padding(top = 214.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Group 31 — three soft, blurred circles around the question
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Glow(220, 0.8f)
                Glow(126, 0.9f)
                Glow(90, 1f)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; chosen = null },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Cormorant, fontSize = 14.sp, color = Color(0xFF666666), textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color(0xFF777777)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (detected != null) join() else suggestions.firstOrNull()?.let { chosen = it; text = it.city }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) Text("Where do you live?", fontFamily = Cormorant, fontSize = 14.sp, color = Color(0xFF777777))
                            inner()
                        }
                    },
                )
            }
            suggestions.forEach { p ->
                Text(
                    p.city, fontFamily = Cormorant, fontSize = 14.sp, color = Color(0xFF999999),
                    modifier = Modifier.clickable { chosen = p; text = p.city }.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = 24.dp)) {
            JoinButton(enabled = detected != null, onClick = ::join)
        }
    }
}

/** Figma "button state": 312 × 44 pill, orange outline when active, faded when not. */
@Composable
private fun JoinButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.requiredSize(312.dp, 44.dp).clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, if (enabled) Coral else Color(0xFFD0D0D0), RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text("Join", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center,
            color = if (enabled) Color(0xFF222222) else Color(0xFFAAAAAA),
            modifier = Modifier.padding(start = 60.dp, top = 12.dp, end = 52.dp, bottom = 12.dp).width(200.dp))
    }
}

@Composable
private fun Glow(d: Int, alpha: Float) {
    Box(
        Modifier.size(d.dp).blur((d / 6).dp)
            .background(Brush.radialGradient(listOf(Color(0xFFE4E4E4).copy(alpha = alpha), Color.White.copy(alpha = 0f))))
    )
}
