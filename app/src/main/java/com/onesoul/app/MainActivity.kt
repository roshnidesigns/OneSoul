package com.onesoul.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

enum class Screen { Home, Canvas, Wraps, Days }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Teal, secondary = Coral, tertiary = Sun),
                typography = cormorantTypography(),
            ) {
                OneSoulApp()
            }
        }
    }
}

@Composable
fun OneSoulApp(vm: AppViewModel = viewModel()) {
    // Ticks so dots move along the day and snippet effects fade.
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(15_000); value = System.currentTimeMillis() }
    }
    var musicAccess by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.Home) }

    LifecycleResumeEffect(Unit) {
        musicAccess = NowPlaying.hasAccess(vm.getApplication())
        onPauseOrDispose { }
    }
    LaunchedEffect(NowPlaying.info) { NowPlaying.info?.let(vm::songChanged) }

    if (vm.profile == null) {
        OnboardingScreen(onDone = vm::pair)
        return
    }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    // Zooming out to the month and back in to the day: the outgoing screen keeps shrinking (or growing)
    // from wherever the pinch left it and fades, while the incoming one settles in from the other side.
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val ease = tween<Float>(520, easing = FastOutSlowInEasing)
            // The home page itself (paper, skies, dots, times) never scales — it only fades. The scribbles
            // have already shrunk with the pinch; the month grid settles in from slightly larger.
            if (targetState == Screen.Days) {
                (fadeIn(ease) + scaleIn(ease, initialScale = 1.12f)) togetherWith fadeOut(ease)
            } else {
                fadeIn(ease) togetherWith (fadeOut(ease) + scaleOut(ease, targetScale = 1.15f))
            }
        },
        label = "screen",
    ) { s ->
        when (s) {
            Screen.Home -> HomeScreen(vm, now, musicAccess,
                onCanvas = { screen = Screen.Canvas }, onWraps = { screen = Screen.Wraps }, onDays = { screen = Screen.Days })
            Screen.Canvas -> CanvasScreen(vm, now, onBack = { screen = Screen.Home })
            Screen.Wraps -> WrapScreen(vm, now, onBack = { screen = Screen.Home })
            Screen.Days -> DaysScreen(vm, now, onBack = { screen = Screen.Home })
        }
    }
}

/** Every Material text style in Cormorant Garamond, so all screens share the onboarding font. */
private fun cormorantTypography(): Typography {
    val t = Typography()
    fun TextStyle.c() = copy(fontFamily = Cormorant, shadow = null)
    return Typography(
        displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
        headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
        titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
        bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
        labelLarge = t.labelLarge.c(), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
    )
}
