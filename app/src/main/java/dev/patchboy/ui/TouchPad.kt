package dev.patchboy.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import dev.patchboy.app.GameController
import dev.patchboy.input.Button
import dev.patchboy.input.TouchControl
import dev.patchboy.input.TouchLayout
import dev.patchboy.input.TouchShape

/**
 * The on-screen gamepad. Every finger is tracked on its own: it presses whatever it's over
 * and can slide from button to button, and one that lands on the d-pad keeps steering it.
 * Presses go to [GameController.onKey] just like keys, so the menu, quick save and speed
 * buttons tap, hold and double-tap the same way their keys do.
 */
@Composable
fun TouchPad(game: GameController, layout: TouchLayout) {
    val view = LocalView.current
    val density = LocalDensity.current
    val settings = game.settings
    var pressed by remember { mutableStateOf(emptySet<Button>()) }
    val measurer = rememberTextMeasurer()
    val labels = remember(layout, density) {
        layout.controls.filter { it.label.isNotEmpty() }.associate { c ->
            val px = when (c.shape) {
                TouchShape.ROUND -> c.bounds.height * if (c.button == Button.A || c.button == Button.B) 0.36f else 0.24f
                TouchShape.SHOULDER -> c.bounds.height * 0.42f
                else -> c.bounds.height * 0.38f
            }
            val style = TextStyle(
                fontFamily = LabelFont,
                fontSize = with(density) { px.toSp() },
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
            c to measurer.measure(c.label, style)
        }
    }
    val exclusions = remember(layout) {
        val faces = layout.controls.filter { it.button == Button.A || it.button == Button.B }
        listOfNotNull(
            layout.dpad?.bounds,
            faces.map { it.bounds }.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) },
        )
    }

    Canvas(
        Modifier
            .fillMaxSize()
            // A thumb near the screen's edge mustn't turn into the Back gesture.
            .systemGestureExclusion { exclusions.getOrNull(0) ?: Rect.Zero }
            .systemGestureExclusion { exclusions.getOrNull(1) ?: Rect.Zero }
            .pointerInput(layout) {
                val onDpad = mutableMapOf<PointerId, Boolean>()
                var held = emptySet<Button>()
                fun send(next: Set<Button>) {
                    (held - next).forEach { game.onKey(it, false) }
                    val added = next - held
                    added.forEach { game.onKey(it, true) }
                    if (added.isNotEmpty() && settings.touchHaptics) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    held = next
                    pressed = next
                }
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            game.onTouched()
                            val next = mutableSetOf<Button>()
                            event.changes.forEach { c ->
                                if (c.changedToDown()) onDpad[c.id] = layout.onDpad(c.position)
                                if (c.pressed) {
                                    next += if (onDpad[c.id] == true) layout.dpadAt(c.position) else layout.buttonsAt(c.position)
                                } else {
                                    onDpad.remove(c.id)
                                }
                                c.consume()
                            }
                            send(next)
                        }
                    }
                } finally {
                    // A new layout (the phone turned, a setting changed) lets go of everything.
                    send(emptySet())
                }
            },
    ) {
        val alpha = settings.touchOpacity / 100f
        layout.controls.forEach { c ->
            when (c.shape) {
                TouchShape.DPAD -> drawDpad(c, pressed, alpha)
                else -> drawButton(c, c.button in pressed, labels[c], alpha)
            }
        }
    }
}

private val Fill = Color.White.copy(alpha = 0.10f)
private val FillLit = Color.White.copy(alpha = 0.42f)
private val Line = Ink.menu.copy(alpha = 0.55f)

private fun DrawScope.drawButton(c: TouchControl, lit: Boolean, text: TextLayoutResult?, alpha: Float) {
    val b = c.bounds
    val stroke = Stroke(width = (b.height * 0.04f).coerceAtLeast(2f))
    val fill = if (lit) FillLit else Fill
    when (c.shape) {
        TouchShape.ROUND -> {
            drawCircle(fill, b.width / 2f, b.center, alpha)
            drawCircle(Line, b.width / 2f, b.center, alpha, style = stroke)
        }
        else -> {
            val r = CornerRadius(if (c.shape == TouchShape.PILL) b.height / 2f else b.height * 0.36f)
            drawRoundRect(fill, b.topLeft, b.size, r, alpha = alpha)
            drawRoundRect(Line, b.topLeft, b.size, r, alpha = alpha, style = stroke)
        }
    }
    if (text != null) {
        drawText(
            text,
            color = Ink.menu,
            topLeft = Offset(b.center.x - text.size.width / 2f, b.center.y - text.size.height / 2f),
            alpha = alpha.coerceAtLeast(0.35f),
        )
    }
}

/** A plus-shaped pad on a faint disc, each arm lighting up while it's held. */
private fun DrawScope.drawDpad(c: TouchControl, pressed: Set<Button>, alpha: Float) {
    val b = c.bounds
    val r = b.width / 2f
    val arm = r * 0.68f
    val half = arm * 0.5f
    val corner = CornerRadius(half * 0.35f)
    drawCircle(Fill, r, b.center, alpha * 0.6f)

    val upright = Path().apply {
        addRoundRect(RoundRect(Rect(b.center.x - half, b.center.y - r * 0.9f, b.center.x + half, b.center.y + r * 0.9f), corner))
    }
    val across = Path().apply {
        addRoundRect(RoundRect(Rect(b.center.x - r * 0.9f, b.center.y - half, b.center.x + r * 0.9f, b.center.y + half), corner))
    }
    val cross = Path.combine(PathOperation.Union, upright, across)
    drawPath(cross, Fill, alpha)

    // Arms in drawing order: right, down, left, up, each a rotation of the right arm.
    listOf(Button.RIGHT to 0f, Button.DOWN to 90f, Button.LEFT to 180f, Button.UP to 270f).forEach { (button, angle) ->
        rotate(angle, b.center) {
            val armRect = Rect(b.center.x + half * 0.2f, b.center.y - half, b.center.x + r * 0.9f, b.center.y + half)
            if (button in pressed) drawRoundRect(FillLit, armRect.topLeft, armRect.size, corner, alpha = alpha)
            val tip = Path().apply {
                val x = b.center.x + r * 0.62f
                moveTo(x + half * 0.42f, b.center.y)
                lineTo(x - half * 0.18f, b.center.y - half * 0.42f)
                lineTo(x - half * 0.18f, b.center.y + half * 0.42f)
                close()
            }
            drawPath(tip, Line, alpha)
        }
    }
    drawPath(cross, Line, alpha, style = Stroke(width = (r * 0.03f).coerceAtLeast(2f)))
}
