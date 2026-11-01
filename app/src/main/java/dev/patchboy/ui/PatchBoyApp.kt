package dev.patchboy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.patchboy.app.AppState
import dev.patchboy.app.GameController
import dev.patchboy.app.Mode
import dev.patchboy.input.rememberHasControls

/**
 * Full screen, cutout included: the layouts keep clear of the camera hole and gesture bar
 * with [Ui.safeTop] and [Ui.safeBottom] rather than giving up a strip across the whole top.
 * [LocalTouch] says whether to show touch hints, which is whenever no keyboard or gamepad is
 * connected.
 */
@Composable
fun UiRoot(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val top = WindowInsets.displayCutout.getTop(density).toFloat()
        val bottom = WindowInsets.systemGestures.getBottom(density).toFloat()
        val ui = remember(w, h, density, top, bottom) { Ui.of(w, h, density, top, bottom) }
        val touch = !rememberHasControls()
        CompositionLocalProvider(LocalUi provides ui, LocalTouch provides touch) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun PatchBoyApp(app: AppState) = UiRoot {
    when (app.mode) {
        Mode.Setup -> SetupScreen(app)
        else -> {
            ShelfScreen(app)
            ShelfOverlays(app)
        }
    }
    ToastHost(app.toast)
}

@Composable
fun GameApp(game: GameController) = UiRoot {
    GameScreen(game)
    GameOverlays(game)
    ToastHost(game.toast)
}
