package dev.patchboy.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.swordfish.libretrodroid.ShaderConfig
import dev.patchboy.core.Core

enum class Scaling(val label: String) { INTEGER("Integer"), FIT("Fit") }

enum class Filter(val label: String, val shader: ShaderConfig) {
    SHARP("Sharp", ShaderConfig.Sharp),
    SMOOTH("Smooth", ShaderConfig.Default),
    LCD("LCD", ShaderConfig.LCD),
    CRT("CRT", ShaderConfig.CRT),
}

/** Palettes for original Game Boy games, named for each core's option values. */
enum class GbPalette(val label: String, val gambatte: String, val mgba: String) {
    DMG("DMG", "GB - DMG", "DMG Green"),
    POCKET("Pocket", "GB - Pocket", "GB Pocket"),
    LIGHT("Light", "GB - Light", "GB Light"),
    GREY("Grey", "", "Grayscale"),
}

/**
 * When the on-screen gamepad shows. Auto hides it while a keyboard or gamepad is in use and
 * brings it back at the next touch.
 */
enum class TouchMode(val label: String) { AUTO("Auto"), ON("On"), OFF("Off") }

enum class TouchSize(val label: String, val scale: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1f),
    LARGE("Large", 1.15f),
}

/**
 * How Settings, the cart sheet and setup look. Touch has big rows, switches and arrows to tap;
 * Classic is slot's compact list, made for a controller or keyboard.
 */
enum class MenuStyle(val label: String) { TOUCH("Touch"), CLASSIC("Classic") }

/** Everything the quick menu changes, kept in SharedPreferences and observable from Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ffSpeed by intPref("ff_speed", 4)
    var ffSound by boolPref("ff_sound", false)
    var rewind by boolPref("rewind", true)
    var colourCorrection by boolPref("colour_correction", true)
    var scaling by enumPref("scaling", Scaling.INTEGER)
    var filter by enumPref("filter", Filter.SHARP)
    var gbPalette by enumPref("gb_palette", GbPalette.DMG)
    var touchMode by enumPref("touch_mode", TouchMode.AUTO)
    var touchSize by enumPref("touch_size", TouchSize.MEDIUM)
    /** How opaque the on-screen controls are, in percent: one of [TOUCH_OPACITIES]. */
    var touchOpacity by intPref("touch_opacity", 60)
    var touchHaptics by boolPref("touch_haptics", true)
    var menuStyle by enumPref("menu_style", MenuStyle.TOUCH)

    var lastShelf: String?
        get() = prefs.getString("last_shelf", null)
        set(v) = prefs.edit { putString("last_shelf", v) }

    var lastCart: String?
        get() = prefs.getString("last_cart", null)
        set(v) = prefs.edit { putString("last_cart", v) }

    fun coreFor(cart: Cart): Core {
        val chosen = Core.byId(prefs.getString("core:${cart.key}", null))
        return chosen?.takeIf { it in cart.platform.cores } ?: cart.platform.defaultCore
    }

    fun setCore(cart: Cart, core: Core) = prefs.edit { putString("core:${cart.key}", core.id) }

    // Written only by the game process. SharedPreferences don't merge across processes, so it
    // gets a file of its own that the shelf's process never rewrites.
    private val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE)

    /** How many more games start with the controls hint. */
    var hintsLeft: Int
        get() = hints.getInt("left", 3)
        set(v) = hints.edit { putInt("left", v) }

    /**
     * After a battery save is imported, the next start skips the resume state: a state holds
     * the game's save memory too, and loading an old one would put the old save back.
     */
    fun startFreshNextTime(cart: Cart) = prefs.edit { putBoolean("fresh:${cart.key}", true) }

    /** True once if [cart] was marked by [startFreshNextTime]. */
    fun takeFreshStart(cart: Cart): Boolean {
        val key = "fresh:${cart.key}"
        if (!prefs.getBoolean(key, false)) return false
        prefs.edit { remove(key) }
        return true
    }

    private fun intPref(key: String, default: Int) = Pref(
        mutableIntStateOf(prefs.getInt(key, default)),
    ) { prefs.edit { putInt(key, it) } }

    private fun boolPref(key: String, default: Boolean) = Pref(
        mutableStateOf(prefs.getBoolean(key, default)),
    ) { prefs.edit { putBoolean(key, it) } }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): Pref<E> {
        val stored = prefs.getString(key, null)
        val value = enumValues<E>().firstOrNull { it.name == stored } ?: default
        return Pref(mutableStateOf(value)) { prefs.edit { putString(key, it.name) } }
    }

    class Pref<T>(
        private val state: androidx.compose.runtime.MutableState<T>,
        private val write: (T) -> Unit,
    ) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            state.value = value
            write(value)
        }
    }

    companion object {
        val FF_SPEEDS = listOf(2, 3, 4, 6)
        val TOUCH_OPACITIES = listOf(30, 45, 60, 80, 100)
    }
}
