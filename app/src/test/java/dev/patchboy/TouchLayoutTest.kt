package dev.patchboy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.patchboy.core.Platform
import dev.patchboy.data.Scaling
import dev.patchboy.input.Button
import dev.patchboy.input.TouchLayout
import dev.patchboy.input.TouchShape
import dev.patchboy.ui.gameViewSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutTest {
    // A typical phone: 1080 x 2400 at 420 dpi, with a punch hole at the top.
    private val dp = 2.625f
    private val cutout = Rect(0f, 110f, 0f, 60f)

    private fun portrait(platform: Platform) = TouchLayout.of(1080f, 2400f, dp, platform, insets = cutout)
    private fun landscape(platform: Platform) = TouchLayout.of(2400f, 1080f, dp, platform, insets = Rect(110f, 0f, 0f, 60f))

    private fun TouchLayout.at(b: Button) = controls.first { it.button == b }.centre

    @Test
    fun `every control is on screen and none overlap`() {
        for (layout in Platform.entries.flatMap { listOf(portrait(it) to 1080f, landscape(it) to 2400f) }) {
            val (l, w) = layout
            val h = if (w > 2000f) 1080f else 2400f
            l.controls.forEach { c ->
                assertTrue("$c off screen", c.bounds.left >= 0f && c.bounds.top >= 0f && c.bounds.right <= w && c.bounds.bottom <= h)
            }
            l.controls.forEachIndexed { i, a ->
                l.controls.drop(i + 1).forEach { b -> assertFalse("$a overlaps $b", a.bounds.overlaps(b.bounds)) }
            }
        }
    }

    @Test
    fun `portrait keeps the picture above the controls at full integer scale`() {
        val gb = portrait(Platform.GB)
        assertTrue(gb.controls.all { it.bounds.top >= gb.picture.bottom })
        assertEquals(cutout.top, gb.picture.top)
        assertEquals(960 to 864, gameViewSize(gb.picture.width, gb.picture.height, Platform.GB, Scaling.INTEGER))
        val gba = portrait(Platform.GBA)
        assertEquals(960 to 640, gameViewSize(gba.picture.width, gba.picture.height, Platform.GBA, Scaling.INTEGER))
    }

    @Test
    fun `only the GBA gets shoulder buttons`() {
        assertTrue(portrait(Platform.GB).controls.none { it.shape == TouchShape.SHOULDER })
        assertEquals(setOf(Button.L, Button.R), landscape(Platform.GBA).controls.filter { it.shape == TouchShape.SHOULDER }.mapNotNull { it.button }.toSet())
    }

    @Test
    fun `landscape puts the picture between the controls`() {
        val l = landscape(Platform.GB)
        val dpad = l.dpad!!.bounds
        val a = l.controls.first { it.button == Button.A }.bounds
        assertTrue(l.picture.left >= dpad.right)
        assertTrue(l.picture.right <= a.left)
        // 7x of 144 is 1008 px: the full height is still used.
        assertEquals(1120 to 1008, gameViewSize(l.picture.width, l.picture.height, Platform.GB, Scaling.INTEGER))
    }

    @Test
    fun `the d-pad reads eight ways and keeps steering past its edge`() {
        val l = portrait(Platform.GB)
        val c = l.dpad!!.centre
        val r = l.dpad!!.bounds.width / 2f
        assertEquals(emptySet<Button>(), l.dpadAt(c))
        assertEquals(setOf(Button.RIGHT), l.dpadAt(c + Offset(r * 0.6f, 0f)))
        assertEquals(setOf(Button.UP), l.dpadAt(c + Offset(r * 0.1f, -r * 0.6f)))
        assertEquals(setOf(Button.DOWN, Button.LEFT), l.dpadAt(c + Offset(-r * 0.5f, r * 0.5f)))
        assertEquals(setOf(Button.RIGHT), l.dpadAt(c + Offset(r * 3f, 0f)))
        assertTrue(l.onDpad(c + Offset(r * 1.1f, 0f)))
        assertFalse(l.onDpad(c + Offset(r * 1.5f, 0f)))
    }

    @Test
    fun `a thumb between A and B presses both`() {
        val l = portrait(Platform.GB)
        val a = l.at(Button.A)
        val b = l.at(Button.B)
        assertEquals(setOf(Button.A), l.buttonsAt(a))
        assertEquals(setOf(Button.B), l.buttonsAt(b))
        assertEquals(setOf(Button.A, Button.B), l.buttonsAt((a + b) / 2f))
    }

    @Test
    fun `neighbouring extras never press together`() {
        for (l in Platform.entries.flatMap { listOf(portrait(it), landscape(it)) }) {
            val extras = l.controls.filter { it.button in TouchLayout.DEFAULT_EXTRAS }
            extras.forEach { e -> assertEquals(setOf(e.button), l.buttonsAt(e.centre)) }
            extras.zipWithNext().forEach { (x, y) -> assertTrue(l.buttonsAt((x.centre + y.centre) / 2f).size <= 1) }
        }
    }

    @Test
    fun `without rewind there are three extras`() {
        val l = TouchLayout.of(1080f, 2400f, dp, Platform.GB, extras = TouchLayout.DEFAULT_EXTRAS - Button.REWIND)
        assertTrue(l.controls.none { it.button == Button.REWIND })
        assertEquals(3, l.controls.count { it.button in TouchLayout.DEFAULT_EXTRAS })
    }
}
