package dev.patchboy.input

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Controller d-pads that report as a hat (two axes) rather than as keys, as Xbox-style pads
 * do, turned into d-pad presses. The thumbsticks are ignored on purpose: every controller
 * motion event is used up here, so Android doesn't turn the left stick into d-pad keys either.
 */
class HatDpad {
    private var held = emptySet<Button>()

    /** Returns true when [event] came from a controller, and so was used up. */
    fun route(event: MotionEvent, onKey: (Button, Boolean) -> Unit): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            update(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), onKey)
        }
        return true
    }

    fun update(x: Float, y: Float, onKey: (Button, Boolean) -> Unit) {
        val next = directions(x, y)
        (held - next).forEach { onKey(it, false) }
        (next - held).forEach { onKey(it, true) }
        held = next
    }

    /** Lets go of whatever the hat holds, when the screen goes away. */
    fun release(onKey: (Button, Boolean) -> Unit) = update(0f, 0f, onKey)

    companion object {
        /** The hat's axes are -1, 0 or 1; a diagonal holds two directions. */
        fun directions(x: Float, y: Float): Set<Button> = buildSet {
            if (x < -0.5f) add(Button.LEFT)
            if (x > 0.5f) add(Button.RIGHT)
            if (y < -0.5f) add(Button.UP)
            if (y > 0.5f) add(Button.DOWN)
        }
    }
}
