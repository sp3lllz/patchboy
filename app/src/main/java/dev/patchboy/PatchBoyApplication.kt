package dev.patchboy

import android.app.Application
import android.content.Context
import dev.patchboy.core.Delivery

/**
 * Runs in both processes (the shelf and :game), so each one can see cores Google Play
 * delivered after the app was installed.
 */
class PatchBoyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Delivery.attach(this)
    }
}
