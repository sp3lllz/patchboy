package dev.patchboy.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether a real keyboard or gamepad is plugged in or paired. Without one, the app shows
 * touch hints and buttons instead of key caps.
 */
object Hardware {
    fun hasControls(context: Context): Boolean {
        val input = context.getSystemService(InputManager::class.java) ?: return false
        return input.inputDeviceIds.any { id -> input.getInputDevice(id)?.let(::isControls) == true }
    }

    private fun isControls(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        val pad = device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK)
        return pad || device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }
}

/** [Hardware.hasControls], kept up to date as keyboards and gamepads come and go. */
@Composable
fun rememberHasControls(): Boolean {
    val context = LocalContext.current
    var has by remember { mutableStateOf(Hardware.hasControls(context)) }
    DisposableEffect(context) {
        val input = context.getSystemService(InputManager::class.java)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { has = Hardware.hasControls(context) }
            override fun onInputDeviceRemoved(deviceId: Int) { has = Hardware.hasControls(context) }
            override fun onInputDeviceChanged(deviceId: Int) { has = Hardware.hasControls(context) }
        }
        input?.registerInputDeviceListener(listener, null)
        onDispose { input?.unregisterInputDeviceListener(listener) }
    }
    return has
}
