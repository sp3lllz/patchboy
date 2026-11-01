package dev.patchboy

import dev.patchboy.input.Button
import dev.patchboy.input.HatDpad
import org.junit.Assert.assertEquals
import org.junit.Test

class HatDpadTest {
    @Test
    fun `hat axes become d-pad directions`() {
        assertEquals(emptySet<Button>(), HatDpad.directions(0f, 0f))
        assertEquals(setOf(Button.LEFT), HatDpad.directions(-1f, 0f))
        assertEquals(setOf(Button.DOWN), HatDpad.directions(0f, 1f))
        assertEquals(setOf(Button.UP, Button.RIGHT), HatDpad.directions(1f, -1f))
    }

    @Test
    fun `moving the hat presses and releases only what changed`() {
        val hat = HatDpad()
        val sent = mutableListOf<Pair<Button, Boolean>>()
        val onKey = { b: Button, down: Boolean -> sent += b to down; Unit }
        hat.update(0f, -1f, onKey)
        hat.update(1f, -1f, onKey)
        hat.update(1f, -1f, onKey)
        hat.update(1f, 0f, onKey)
        hat.release(onKey)
        assertEquals(
            listOf(Button.UP to true, Button.RIGHT to true, Button.UP to false, Button.RIGHT to false),
            sent,
        )
    }
}
