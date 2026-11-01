package dev.patchboy

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.patchboy.app.AppState
import dev.patchboy.app.Host
import dev.patchboy.app.Sfx
import dev.patchboy.app.SystemStatus
import dev.patchboy.core.Core
import dev.patchboy.core.Delivery
import dev.patchboy.data.Cart
import dev.patchboy.data.Importer
import dev.patchboy.data.Paths
import dev.patchboy.data.Settings
import dev.patchboy.data.Storage
import dev.patchboy.input.HatDpad
import dev.patchboy.input.KeyMap
import dev.patchboy.input.KeyRouter
import dev.patchboy.ui.PatchBoyApp
import java.io.InputStream

/** The shelf. Games run in [GameActivity], launched once a cart is all the way in. */
class MainActivity : ComponentActivity(), Host {
    private lateinit var app: AppState
    private lateinit var sfx: Sfx
    private val hat = HatDpad()

    private val game = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        app.onGameFinished()
    }

    private var onPicked: ((Uri) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked?.invoke(it) }
        onPicked = null
    }

    private var onFolder: ((Uri) -> Unit)? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onFolder?.invoke(it) }
        onFolder = null
    }

    private var onFiles: ((List<Uri>) -> Unit)? = null
    private val filesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFiles?.invoke(uris)
        onFiles = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Some launchers start a second shelf on top of a running game instead of bringing
        // the task forward. Step aside so the game underneath comes back.
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sfx = Sfx(this)
        app = AppState(
            host = this,
            settings = Settings(this),
            keyMap = KeyMap(this),
            status = SystemStatus(this),
            sfx = sfx,
            scope = lifecycleScope,
            cacheDir = cacheDir,
            storage = Storage(this),
            importer = Importer(contentResolver),
            cores = Delivery.installer(this),
        )
        onBackPressedDispatcher.addCallback(this) { app.onBack() }
        setContent { PatchBoyApp(app) }
    }

    override fun onResume() {
        super.onResume()
        if (!::app.isInitialized) return
        hideSystemBars()
        app.status.start()
        app.onResume()
    }

    override fun onPause() {
        if (::app.isInitialized) {
            hat.release(app::onKey)
            app.onPause()
            app.status.stop()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::sfx.isInitialized) sfx.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Lint flags any override of ComponentActivity.dispatchKeyEvent as a restricted API; it is
    // the framework Activity method and overriding it is supported.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::app.isInitialized) return super.dispatchKeyEvent(event)
        if (app.softEdit) return super.dispatchKeyEvent(event)
        if (app.editKey(event)) return true
        return KeyRouter.route(event, app.keyMap, app::captureKey, app::onKey) || super.dispatchKeyEvent(event)
    }

    /** Controller hats steer the shelf and menus; the thumbsticks are swallowed so they do nothing. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::app.isInitialized && hat.route(event, app::onKey)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---- Host ----------------------------------------------------------------------------

    override fun launchGame(cart: Cart, core: Core, fresh: Boolean, paths: Paths) {
        game.launch(
            GameActivity.intent(this, cart, core, fresh, paths),
            ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, 0),
        )
    }

    override fun moveToBack() {
        moveTaskToBack(true)
    }

    override fun pickImage(onPicked: (Uri) -> Unit) {
        this.onPicked = onPicked
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun pickFolder(onPicked: (Uri) -> Unit): Boolean {
        onFolder = onPicked
        return try {
            folderPicker.launch(null)
            true
        } catch (e: ActivityNotFoundException) {
            onFolder = null
            false
        }
    }

    override fun pickFiles(onPicked: (List<Uri>) -> Unit): Boolean {
        onFiles = onPicked
        return try {
            // ROMs and saves have no registered type, so offer everything.
            filesPicker.launch(arrayOf("*/*"))
            true
        } catch (e: ActivityNotFoundException) {
            onFiles = null
            false
        }
    }

    override fun openStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)
}
