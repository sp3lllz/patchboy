package dev.patchboy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * slot draws on a 720 x 480 panel. Everything here is laid out in those units ("u"), scaled
 * to fit a 720 x 800 box: a 1080 px wide phone gets 1.5 px per unit, and a tall phone just has
 * more room top to bottom. [safeTop] and [safeBottom] are the camera cutout and gesture bar,
 * in px, so headers and tabs can keep clear of them.
 */
@Immutable
class Ui(
    val u: Float,
    private val density: Density,
    val widthPx: Float,
    val heightPx: Float,
    val safeTop: Float = 0f,
    val safeBottom: Float = 0f,
) {
    fun dp(v: Float): Dp = with(density) { (v * u).toDp() }
    fun sp(v: Float): TextUnit = with(density) { (v * u).toSp() }
    fun px(v: Float): Float = v * u
    val widthU: Float get() = widthPx / u
    val heightU: Float get() = heightPx / u

    /** [v] u from the top, or just under the camera cutout if that is lower, in px. */
    fun top(v: Float): Float = maxOf(v * u, safeTop + 10f * u)
    fun topDp(v: Float): Dp = with(density) { top(v).toDp() }

    companion object {
        fun of(widthPx: Float, heightPx: Float, density: Density, safeTop: Float = 0f, safeBottom: Float = 0f): Ui =
            Ui(minOf(widthPx / 720f, heightPx / 800f), density, widthPx, heightPx, safeTop, safeBottom)
    }
}

val LocalUi = staticCompositionLocalOf<Ui> { error("Ui not provided") }

/** True when there is no keyboard or gamepad, so hints are for fingers rather than keys. */
val LocalTouch = compositionLocalOf { true }

@Composable
fun label(size: Float, color: Color = Ink.menu, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f): TextStyle {
    val ui = LocalUi.current
    return TextStyle(
        fontFamily = LabelFont,
        fontSize = ui.sp(size),
        fontWeight = weight,
        color = color,
        letterSpacing = spacing.em,
    )
}

/**
 * A row of "KEY action" hints along the bottom of a menu, like slot's legends. Key caps mean
 * nothing without keys, so with [LocalTouch] it shows nothing: menus pair it with a [TouchBar].
 */
@Composable
fun Legend(vararg items: Pair<String, String>, modifier: Modifier = Modifier) {
    if (LocalTouch.current) return
    val ui = LocalUi.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ui.dp(22f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (key, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(key)
                BasicText(
                    action,
                    modifier = Modifier.padding(start = ui.dp(7f)),
                    style = label(15f, Ink.dim),
                )
            }
        }
    }
}

/** The touch stand-in for a [Legend]: a row of buttons, shown only with [LocalTouch]. */
@Composable
fun TouchBar(vararg items: Pair<String, () -> Unit>, modifier: Modifier = Modifier) {
    if (!LocalTouch.current) return
    val ui = LocalUi.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ui.dp(16f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (text, onClick) -> TouchPill(text, onClick) }
    }
}

@Composable
fun TouchPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Box(
        modifier = modifier
            .background(Ink.edge, RoundedCornerShape(ui.dp(22f)))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = ui.dp(22f), vertical = ui.dp(9f)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text.uppercase(), style = label(16f, Ink.menu, FontWeight.Bold, 0.1f))
    }
}

@Composable
fun KeyCap(key: String) {
    val ui = LocalUi.current
    Box(
        modifier = Modifier
            .background(Ink.edge, RoundedCornerShape(ui.dp(5f)))
            .padding(horizontal = ui.dp(7f), vertical = ui.dp(1.5f)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(key, style = label(13f, Ink.menu, FontWeight.Bold, 0.04f))
    }
}

/** A full-width menu row with a lit bar when selected, label left and value right. */
@Composable
fun MenuRow(
    text: String,
    value: String?,
    selected: Boolean,
    pitch: Float,
    onClick: () -> Unit,
    valueLit: Boolean = selected,
    carets: Boolean = value != null,
    textSize: Float = 25f,
    valueWidth: Float = 150f,
) {
    val ui = LocalUi.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ui.dp(pitch))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ui.dp(pitch - 8f))
                    .background(Ink.edge),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ui.dp(32f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text,
                modifier = Modifier.weight(1f),
                style = label(textSize, if (selected) Ink.menu else Ink.dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                val ink = if (valueLit) Ink.menu else Ink.dim
                if (carets && selected) BasicText("‹", style = label(textSize, ink))
                BasicText(
                    value,
                    modifier = Modifier
                        .width(ui.dp(valueWidth))
                        .padding(horizontal = ui.dp(10f)),
                    style = label(textSize, ink).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (carets && selected) BasicText("›", style = label(textSize, ink))
            }
        }
    }
}
