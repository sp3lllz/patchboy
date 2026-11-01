package dev.patchboy.app

import android.os.SystemClock

data class Toast(val text: String, val at: Long = SystemClock.uptimeMillis())

/** The quick menu on the shelf (MENU), after slot's quick_menu.rs, plus PatchBoy extras. */
enum class QuickRow(val label: String) {
    FAST_FORWARD("Fast Forward"),
    FAST_FORWARD_SOUND("Fast Forward Sound"),
    REWIND("Rewind"),
    SCALING("Scaling"),
    FILTER("Screen Filter"),
    COLOUR_CORRECTION("Colour Correction"),
    GB_PALETTE("Game Boy Palette"),
    TOUCH_MODE("Touch Controls"),
    TOUCH_SIZE("Touch Size"),
    TOUCH_OPACITY("Touch Opacity"),
    TOUCH_HAPTICS("Vibrate on Touch"),
    CONTROLS("Keyboard & Gamepad"),
    ADD_GAMES("Add Games"),
    IMPORT_SAVES("Import Saves"),
    SCRAPE_MISSING("Scrape Cart Art"),
    STORAGE("Game Storage"),
    CORES("Emulator Cores"),
    RESCAN("Rescan Games"),
    SETUP("Setup Guide"),
    ABOUT("About");

    /** Rows whose value Left / Right cycles. */
    val cycles: Boolean get() = ordinal <= TOUCH_HAPTICS.ordinal
}

/** The in-game menu on a tap of MENU. */
enum class PauseRow(val label: String) {
    RESUME("Resume"),
    SAVE_STATE("Save State"),
    LOAD_STATE("Load Last State"),
    UNDO_LOAD("Undo Load"),
    STATES("Save States"),
    RESET("Reset Game"),
    EJECT("Eject"),
}

/** The cart sheet (START on a cart): slot's Cart Studio, on the phone. */
enum class CartRow(val label: String) {
    NAME("Name"),
    SCRAPE("Scrape Art"),
    PICK("Use Image From Phone"),
    FIT("Art Fit"),
    REMOVE_ART("Remove Art"),
    COLOUR("Colour"),
    FINISH("Finish"),
    SHAPE("Outline"),
    CHIP("Chip"),
    RESET("Reset Cart"),
    REMOVE("Remove Game");

    /** Rows whose value Left / Right cycles. */
    val cycles: Boolean get() = this in setOf(SCRAPE, FIT, COLOUR, FINISH, SHAPE, CHIP)
}
