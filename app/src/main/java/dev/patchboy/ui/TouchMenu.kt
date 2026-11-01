package dev.patchboy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * True when Settings > Menu Style is Touch: the menus below are drawn in place of slot's
 * compact rows. Unlike the rest of the UI, these are sized in dp and sp, as fingers are.
 */
val LocalTouchMenus = compositionLocalOf { false }

private val Danger = Color(0xFFE5736B)

fun touchText(size: Float, color: Color = Ink.menu, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    fontFamily = LabelFont,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = spacing.em,
)

/** A touch menu page: a bar with a back arrow and the title, then rows that scroll. */
@Composable
fun TouchPage(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Sheet {
        Column(Modifier.fillMaxSize()) {
            TouchTopBar(title, subtitle, onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) { content() }
            if (footer != null) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { footer() }
        }
    }
}

@Composable
fun TouchTopBar(title: String?, subtitle: String?, onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalUi.current
    val top = with(LocalDensity.current) { ui.safeTop.toDp() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = top + 8.dp, start = 8.dp, end = 16.dp, bottom = 4.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Chevron(left = true, color = Ink.menu, size = 22f) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            if (title != null) BasicText(title, style = touchText(24f, Ink.menu, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = touchText(14f, Ink.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

fun LazyListScope.touchSection(title: String) = item(key = "section:$title") {
    BasicText(
        title.uppercase(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp),
        style = touchText(13f, Ink.faint, FontWeight.Bold, 0.12f),
    )
}

/**
 * One row: a label (and an optional line under it) on the left, a control on the right.
 * [focused] is where a controller's d-pad has got to; it only shows while one is connected.
 */
@Composable
fun TouchRow(
    text: String,
    onClick: (() -> Unit)?,
    focused: Boolean = false,
    detail: String? = null,
    danger: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val lit = focused && !LocalTouch.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (lit) Ink.edge else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(text, style = touchText(18f, if (danger) Danger else Ink.menu))
            if (detail != null) {
                BasicText(
                    detail,
                    modifier = Modifier.padding(top = 2.dp),
                    style = touchText(14f, if (danger) Danger else Ink.dim),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** An on / off switch. The row it sits in does the toggling. */
@Composable
fun TouchSwitch(on: Boolean) {
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .background(if (on) Ink.menu else Ink.edge, RoundedCornerShape(16.dp))
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).background(if (on) Ink.opening else Ink.dim, CircleShape))
    }
}

/** A value with arrows either side to step back and forth through its choices. */
@Composable
fun TouchStepper(value: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(left = true, onPrev)
        BasicText(
            value,
            modifier = Modifier.widthIn(min = 84.dp).padding(horizontal = 4.dp),
            style = touchText(17f, Ink.menu, FontWeight.Bold).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StepButton(left = false, onNext)
    }
}

@Composable
private fun StepButton(left: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Ink.housing)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Chevron(left, Ink.menu, 14f) }
}

/** A row's value, if it has one, and an arrow saying the row opens something. */
@Composable
fun TouchChevron(value: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (value != null) BasicText(value, style = touchText(16f, Ink.dim), maxLines = 1)
        Chevron(left = false, color = Ink.faint, size = 14f)
    }
}

@Composable
fun Chevron(left: Boolean, color: Color, size: Float) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val x0 = if (left) w * 0.68f else w * 0.32f
        val x1 = if (left) w * 0.3f else w * 0.7f
        val stroke = w * 0.14f
        drawLine(color, Offset(x0, h * 0.12f), Offset(x1, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(x1, h * 0.5f), Offset(x0, h * 0.88f), stroke, StrokeCap.Round)
    }
}

/** A full-width button: light for the step forward, dark for anything else. */
@Composable
fun TouchButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (primary) Ink.menu else Ink.edge)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = touchText(18f, if (primary) Ink.opening else Ink.menu, FontWeight.Bold))
    }
}
