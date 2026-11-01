package dev.patchboy.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.patchboy.core.Platform
import kotlin.math.max
import kotlin.math.min

enum class TouchShape { DPAD, ROUND, PILL, SHOULDER }

/**
 * One on-screen control: where it is drawn, and which button a touch on it presses. A round
 * control answers touches out to [reach] times its radius.
 */
data class TouchControl(
    val shape: TouchShape,
    val button: Button?,
    val bounds: Rect,
    val label: String,
    val reach: Float = 1.2f,
) {
    val centre: Offset get() = bounds.center
}

/**
 * The on-screen gamepad for one screen size, and the part of the screen left for the picture.
 * Everything is in px. Portrait is a Game Boy: the picture on top, the controls under it.
 * Landscape puts the picture in the middle with a thumb's worth of controls either side.
 */
data class TouchLayout(val picture: Rect, val controls: List<TouchControl>) {
    val dpad: TouchControl? get() = controls.firstOrNull { it.shape == TouchShape.DPAD }

    /**
     * What a finger at [p] presses, the d-pad aside (see [dpadAt]). A and B reach far enough
     * to overlap a little, so a thumb laid between them presses both.
     */
    fun buttonsAt(p: Offset): Set<Button> = controls.mapNotNullTo(mutableSetOf()) { c ->
        val b = c.button ?: return@mapNotNullTo null
        val hit = when (c.shape) {
            TouchShape.DPAD -> false
            TouchShape.ROUND -> (p - c.centre).getDistance() <= c.bounds.width / 2f * c.reach
            TouchShape.PILL, TouchShape.SHOULDER -> c.bounds.inflate(c.bounds.height * RECT_SLOP).contains(p)
        }
        if (hit) b else null
    }

    /** True if [p] lands on the d-pad (with some slop), where a touch starts steering it. */
    fun onDpad(p: Offset): Boolean {
        val d = dpad ?: return false
        return (p - d.centre).getDistance() <= d.bounds.width / 2f * DPAD_HIT
    }

    /**
     * The directions a finger at [p] holds. Once a touch has started on the d-pad it keeps
     * steering it wherever it slides, so a thumb that drifts off the edge doesn't let go.
     * Each direction covers 120°, so the diagonals are the 30° where two overlap.
     */
    fun dpadAt(p: Offset): Set<Button> {
        val d = dpad ?: return emptySet()
        val v = p - d.centre
        val dist = v.getDistance()
        if (dist < d.bounds.width / 2f * DPAD_DEAD) return emptySet()
        val reach = dist * DIAGONAL
        return buildSet {
            if (v.x > reach) add(Button.RIGHT)
            if (v.x < -reach) add(Button.LEFT)
            if (v.y > reach) add(Button.DOWN)
            if (v.y < -reach) add(Button.UP)
        }
    }

    companion object {
        private const val RECT_SLOP = 0.3f
        /** A and B are 1.565 widths apart, so this leaves a thin band that presses both. */
        private const val FACE_REACH = 1.64f
        private const val DPAD_HIT = 1.2f
        private const val DPAD_DEAD = 0.16f
        /** cos 60°: a direction is held within 60° either side of it. */
        private const val DIAGONAL = 0.5f

        /**
         * Lays the gamepad out on a [width] x [height] px screen with [dp] px per dp. [size]
         * scales every control. [insets] are the cutout and gesture areas to keep clear of.
         * [extras] are the frontend buttons (menu, quick save, fast-forward, rewind) to show.
         */
        fun of(
            width: Float,
            height: Float,
            dp: Float,
            platform: Platform,
            size: Float = 1f,
            insets: Rect = Rect.Zero,
            extras: List<Button> = DEFAULT_EXTRAS,
        ): TouchLayout {
            val m = Metrics(dp, size)
            return if (height >= width) portrait(width, height, dp, m, platform, insets, extras)
            else landscape(width, height, dp, m, platform, insets, extras)
        }

        val DEFAULT_EXTRAS = listOf(Button.MENU, Button.QUICK_SAVE, Button.REWIND, Button.FAST_FORWARD)

        private class Metrics(dp: Float, size: Float) {
            val s = dp * size
            val dpad = 156f * s
            val face = 68f * s
            val pillW = 70f * s
            val pillH = 30f * s
            val shoulderW = 100f * s
            val shoulderH = 40f * s
            val extra = 44f * s
            val gap = 14f * s
        }

        private fun portrait(w: Float, h: Float, dp: Float, m: Metrics, platform: Platform, insets: Rect, extras: List<Button>): TouchLayout {
            val left = insets.left + 20f * dp
            val right = w - insets.right - 20f * dp
            val bottom = h - max(insets.bottom, 16f * dp)
            val cx = w / 2f

            val pillY = bottom - m.pillH / 2f
            val padY = pillY - m.pillH / 2f - 2f * m.gap - m.dpad / 2f
            val controls = mutableListOf<TouchControl>()
            controls += dpad(left + m.dpad / 2f, padY, m)
            controls += face(right, padY, m)
            controls += startSelect(cx - m.pillW / 2f - m.gap, cx + m.pillW / 2f + m.gap, pillY, m)

            // The extras sit in the gap between the shoulders on a Game Boy, or above them on
            // a GBA, where four of them would run into L and R.
            var rowY = padY - m.dpad / 2f - m.gap - max(m.shoulderH, m.extra) / 2f
            if (platform == Platform.GBA) {
                controls += shoulders(left, right, rowY, m)
                rowY -= max(m.shoulderH, m.extra) / 2f + m.gap + m.extra / 2f
            }
            controls += extrasRow(extras, cx, rowY, m)

            val top = controls.minOf { it.bounds.top } - m.gap
            // A short, wide phone can't fit both: the picture gets at least 40% of the
            // height and the controls sit over its bottom edge.
            val pictureBottom = max(top, h * 0.4f)
            return TouchLayout(Rect(insets.left, insets.top, w - insets.right, pictureBottom), controls)
        }

        private fun landscape(w: Float, h: Float, dp: Float, m: Metrics, platform: Platform, insets: Rect, extras: List<Button>): TouchLayout {
            val left = insets.left + 24f * dp
            val right = w - insets.right - 24f * dp
            val top = insets.top + 14f * dp
            val bottom = h - max(insets.bottom, 12f * dp)

            val pillY = bottom - m.pillH / 2f
            val padX = left + m.dpad / 2f
            val padY = min(h * 0.58f, pillY - m.pillH / 2f - m.gap - m.dpad / 2f)
            val controls = mutableListOf<TouchControl>()
            controls += dpad(padX, padY, m)
            val faces = face(right, padY, m)
            controls += faces
            val faceMid = (faces[0].centre.x + faces[1].centre.x) / 2f
            controls += startSelect(padX, faceMid, pillY, m)

            var rowY = top + m.extra / 2f
            if (platform == Platform.GBA) {
                controls += shoulders(left, right, top + m.shoulderH / 2f, m)
                rowY = top + m.shoulderH + m.gap + m.extra / 2f
            }
            // Half the extras under each shoulder: menu and quick save left, the speeds right.
            val half = (extras.size + 1) / 2
            controls += extrasRow(extras.take(half), left + rowWidth(half, m) / 2f, rowY, m)
            controls += extrasRow(extras.drop(half), right - rowWidth(extras.size - half, m) / 2f, rowY, m)

            // Keep the picture between the two sides when that costs it little; a narrow
            // phone lets the controls sit over its edges instead.
            val side = left + m.dpad + 8f * dp
            val between = Rect(side, insets.top, w - side, h - insets.bottom)
            val full = Rect(insets.left, insets.top, w - insets.right, h - insets.bottom)
            val fit = { r: Rect -> min(r.width / platform.nativeW, r.height / platform.nativeH) }
            val picture = if (between.width > 0f && fit(between) >= fit(full) * 0.85f) between else full
            return TouchLayout(picture, controls)
        }

        private fun dpad(x: Float, y: Float, m: Metrics) = TouchControl(
            TouchShape.DPAD, null, Rect(Offset(x, y), m.dpad / 2f), "",
        )

        /** A on the right, B down and to its left, like the consoles. */
        private fun face(right: Float, y: Float, m: Metrics): List<TouchControl> {
            val r = m.face / 2f
            val a = Offset(right - r, y - m.face * 0.42f)
            val b = Offset(a.x - m.face * 1.32f, y + m.face * 0.42f)
            return listOf(
                TouchControl(TouchShape.ROUND, Button.A, Rect(a, r), "A", reach = FACE_REACH),
                TouchControl(TouchShape.ROUND, Button.B, Rect(b, r), "B", reach = FACE_REACH),
            )
        }

        private fun startSelect(selectX: Float, startX: Float, y: Float, m: Metrics) = listOf(
            TouchControl(TouchShape.PILL, Button.SELECT, pill(selectX, y, m.pillW, m.pillH), "SELECT"),
            TouchControl(TouchShape.PILL, Button.START, pill(startX, y, m.pillW, m.pillH), "START"),
        )

        private fun shoulders(left: Float, right: Float, y: Float, m: Metrics) = listOf(
            TouchControl(TouchShape.SHOULDER, Button.L, pill(left + m.shoulderW / 2f, y, m.shoulderW, m.shoulderH), "L"),
            TouchControl(TouchShape.SHOULDER, Button.R, pill(right - m.shoulderW / 2f, y, m.shoulderW, m.shoulderH), "R"),
        )

        private fun rowWidth(n: Int, m: Metrics) = n * m.extra + (n - 1).coerceAtLeast(0) * m.gap

        private fun extrasRow(extras: List<Button>, cx: Float, y: Float, m: Metrics): List<TouchControl> {
            var x = cx - rowWidth(extras.size, m) / 2f + m.extra / 2f
            return extras.map { b ->
                TouchControl(TouchShape.ROUND, b, Rect(Offset(x, y), m.extra / 2f), extraLabel(b)).also { x += m.extra + m.gap }
            }
        }

        private fun extraLabel(b: Button) = when (b) {
            Button.MENU -> "MENU"
            Button.QUICK_SAVE -> "SAVE"
            Button.FAST_FORWARD -> "▶▶"
            Button.REWIND -> "◀◀"
            else -> b.label
        }

        private fun pill(cx: Float, cy: Float, w: Float, h: Float) = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}
