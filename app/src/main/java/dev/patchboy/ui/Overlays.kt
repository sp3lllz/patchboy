package dev.patchboy.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import dev.patchboy.R
import dev.patchboy.app.AppState
import dev.patchboy.app.CartRow
import dev.patchboy.data.Cart
import kotlin.math.min
import dev.patchboy.app.GameController
import dev.patchboy.app.GameOverlay
import dev.patchboy.app.Toast
import dev.patchboy.app.Overlay
import dev.patchboy.app.QuickRow
import dev.patchboy.data.SaveState
import dev.patchboy.input.Button
import dev.patchboy.input.KeyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun ShelfOverlays(app: AppState) {
    when (app.overlay) {
        Overlay.QUICK_MENU -> QuickMenu(app)
        Overlay.CART -> {
            CartSheet(app)
            if (app.edit != null && LocalTouch.current) SoftEdit(app)
        }
        Overlay.CONTROLS -> Controls(app)
        Overlay.ABOUT -> About(app)
        Overlay.SETUP -> SetupScreen(app)
        null -> Unit
    }
}

@Composable
fun GameOverlays(game: GameController) {
    when (game.overlay) {
        GameOverlay.PAUSE -> PauseMenu(game)
        GameOverlay.SWITCHER -> Switcher(game)
        null -> Unit
    }
}

@Composable
internal fun Sheet(alpha: Float = 1f, content: @Composable () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.opening.copy(alpha = alpha))
            // Taps on the sheet's background must not fall through to the shelf.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            // Keep the bottom rows off the gesture bar.
            .padding(bottom = with(LocalDensity.current) { ui.safeBottom.toDp() }),
    ) { content() }
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = ui.topDp(48f), bottom = ui.dp(18f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(title.uppercase(), style = label(17f, Ink.dim, FontWeight.Bold, 0.2f))
        if (subtitle != null) {
            BasicText(
                subtitle,
                modifier = Modifier.padding(top = ui.dp(6f), start = ui.dp(32f), end = ui.dp(32f)),
                style = label(15f, Ink.faint).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- shelf -------------------------------------------------------------------------------

@Composable
private fun QuickMenu(app: AppState) {
    val ui = LocalUi.current
    val keys = app.keyMap
    val list = rememberLazyListState()
    KeepInView(list, app.quickRow.ordinal)
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Settings")
            LazyColumn(Modifier.weight(1f), state = list) {
                items(QuickRow.entries) { row ->
                    MenuRow(
                        text = row.label,
                        value = app.quickValue(row),
                        selected = row == app.quickRow,
                        pitch = 50f,
                        onClick = { app.activateQuick(row) },
                        carets = row.cycles,
                    )
                }
            }
            Legend(
                "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Change",
                keys.hint(Button.A) to "Select",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)),
            )
            TouchBar("Close" to app::onBack, modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)))
        }
    }
}

/** Scrolls [list] so the selected row [at] stays on screen as the keys move it. */
@Composable
private fun KeepInView(list: androidx.compose.foundation.lazy.LazyListState, at: Int) {
    LaunchedEffect(at) {
        val first = list.firstVisibleItemIndex
        val visible = list.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        if (at < first || at >= first + visible - 1) {
            list.animateScrollToItem((at - visible / 2).coerceAtLeast(0))
        }
    }
}

@Composable
private fun CartSheet(app: AppState) {
    val ui = LocalUi.current
    val cart = app.shelf?.current ?: return
    val keys = app.keyMap
    val rows = app.cartRows(cart)
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Cart", cart.platform.title)
            CartPreview(cart, Modifier.fillMaxWidth().height(ui.dp(PREVIEW_H)))
            Spacer(Modifier.height(ui.dp(10f)))
            rows.forEach { row ->
                MenuRow(
                    text = row.label,
                    value = app.cartValue(cart, row),
                    selected = row == app.cartRow,
                    pitch = 40f,
                    onClick = { app.activateCart(row) },
                    carets = row.cycles,
                    textSize = 20f,
                    valueWidth = if (row == CartRow.NAME) 400f else 210f,
                )
            }
            Spacer(Modifier.weight(1f))
            TouchBar("Close" to app::onBack, modifier = Modifier.padding(bottom = ui.dp(30f)))
            if (app.edit != null) {
                Legend(
                    "keyboard" to "Type",
                    "ENTER" to "Save",
                    "DEL" to "Erase",
                    "back" to "Cancel",
                    modifier = Modifier.padding(bottom = ui.dp(30f)),
                )
            } else {
                Legend(
                    "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Change",
                    keys.hint(Button.A) to "Select",
                    keys.hint(Button.B) to "Back",
                    modifier = Modifier.padding(bottom = ui.dp(30f)),
                )
            }
        }
    }
}

private const val PREVIEW_H = 170f

/**
 * Typing a cart's name or colour without a keyboard: a field over the sheet that brings up
 * the on-screen keyboard. The sheet's row shows the text as it's typed.
 */
@Composable
private fun SoftEdit(app: AppState) {
    val ui = LocalUi.current
    val e = app.edit ?: return
    val focus = remember { FocusRequester() }
    // The old text starts selected, so typing replaces it.
    var value by remember(e.row) { mutableStateOf(TextFieldValue(e.text, TextRange(0, e.text.length))) }
    DisposableEffect(Unit) {
        app.softEdit = true
        onDispose { app.softEdit = false }
    }
    LaunchedEffect(e.row) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.opening.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = app::cancelEdit),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .background(Ink.housing)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = ui.dp(28f), vertical = ui.dp(20f)),
            verticalArrangement = Arrangement.spacedBy(ui.dp(16f)),
        ) {
            BasicText(
                (if (e.row == CartRow.COLOUR) "Colour, like #2F5CC0" else "Name").uppercase(),
                style = label(14f, Ink.dim, FontWeight.Bold, 0.16f),
            )
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    app.typeEdit(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .background(Ink.recess, RoundedCornerShape(ui.dp(6f)))
                    .padding(horizontal = ui.dp(14f), vertical = ui.dp(10f)),
                textStyle = label(22f, Ink.menu),
                cursorBrush = SolidColor(Ink.menu),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (e.row == CartRow.NAME) KeyboardCapitalization.Words else KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { app.commitEdit() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(16f), Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TouchPill("Cancel", app::cancelEdit)
                TouchPill("Save", app::commitEdit)
            }
        }
    }
}

/** The cart as it will sit on the shelf, redrawn as you change it. */
@Composable
private fun CartPreview(cart: Cart, modifier: Modifier) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.open_sans) ?: Typeface.DEFAULT }
    val spec = CartArt.spec(cart.shape)
    val maxH = ui.px(PREVIEW_H)
    val w = min(cartWidth(cart) * ui.u * 0.8f, maxH / spec.aspect)
    val face by produceState<ImageBitmap?>(null, cart) {
        value = withContext(Dispatchers.Default) {
            runCatching { CartArt.render(cart, w.toInt(), typeface).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        face?.let {
            Image(
                it,
                contentDescription = null,
                modifier = with(LocalDensity.current) { Modifier.size(w.toDp(), (w * spec.aspect).toDp()) },
            )
        }
    }
}

@Composable
private fun Controls(app: AppState) {
    val ui = LocalUi.current
    val keys = app.keyMap
    val list = rememberLazyListState()
    KeepInView(list, app.controlsRow)
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Keyboard & Gamepad", if (LocalTouch.current) "Connect a keyboard to rebind its keys" else "Choose a button, then press its key")
            LazyColumn(Modifier.weight(1f), state = list) {
                itemsIndexed(Button.entries) { i, button ->
                    val bound = keys.bindings[button].orEmpty()
                    val value = when {
                        app.listening == button -> "Press a key"
                        bound.isEmpty() -> "-"
                        else -> bound.joinToString(" / ") { KeyMap.keyName(it) }
                    }
                    MenuRow(
                        text = button.label,
                        value = value,
                        selected = i == app.controlsRow,
                        pitch = 44f,
                        onClick = { app.activateControl(i) },
                        carets = false,
                        textSize = 22f,
                        valueWidth = 220f,
                    )
                }
                item {
                    MenuRow(
                        text = "Reset to Default Keys",
                        value = null,
                        selected = app.controlsRow == Button.entries.size,
                        pitch = 44f,
                        onClick = { app.activateControl(Button.entries.size) },
                        textSize = 22f,
                    )
                }
            }
            Legend(
                keys.hint(Button.A) to if (app.listening != null) "Waiting for a key" else "Rebind",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)),
            )
            TouchBar("Back" to app::onBack, modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)))
        }
    }
}

@Composable
private fun About(app: AppState) {
    val ui = LocalUi.current
    Sheet {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = ui.dp(44f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header("About")
            Spacer(Modifier.weight(1f))
            BasicText("patchboy.", style = label(48f, Ink.menu, FontWeight.Bold))
            BasicText(
                "A Game Boy cartridge shelf for your phone, with touch controls",
                modifier = Modifier.padding(top = ui.dp(8f), bottom = ui.dp(30f)),
                style = label(17f, Ink.dim).copy(textAlign = TextAlign.Center),
            )
            listOf(
                "Forked from Titan Slot, github.com/sp3lllz/titan-slot",
                "Interface after slot by Brandon Kowalski, GPL-3.0",
                "Cart labels: slot's art set, from ScreenScraper (CC BY-NC-SA 4.0)",
                "Box art: the libretro thumbnails",
                "Cores: Gambatte (GPL-2.0) and mGBA (MPL-2.0), from libretro",
                "Frontend library: LibretroDroid by Filippo Scognamiglio",
                "Game process design after Lemuroid",
                "Type: Open Sans, SIL Open Font License",
                "Cart sounds: slot's recording of a GBA",
                "Source code (GPL-3.0): github.com/sp3lllz/patchboy",
            ).forEach {
                BasicText(
                    it,
                    modifier = Modifier.padding(vertical = ui.dp(4f)),
                    style = label(16f, Ink.dim).copy(textAlign = TextAlign.Center),
                )
            }
            BasicText(
                "Games: ${app.paths.library.path}\nSaves: ${app.paths.data.path}",
                modifier = Modifier.padding(top = ui.dp(26f)),
                style = label(15f, Ink.faint).copy(textAlign = TextAlign.Center),
            )
            BasicText(
                "Game Boy, Game Boy Color and Game Boy Advance are trademarks of Nintendo. " +
                    "This app is not affiliated with Nintendo.",
                modifier = Modifier.padding(top = ui.dp(10f)),
                style = label(13f, Ink.faint).copy(textAlign = TextAlign.Center),
            )
            Spacer(Modifier.weight(1f))
            Legend(app.keyMap.hint(Button.B) to "Back", modifier = Modifier.padding(bottom = ui.dp(30f)))
            TouchBar("Back" to app::onBack, modifier = Modifier.padding(bottom = ui.dp(30f)))
        }
    }
}

// ---- game --------------------------------------------------------------------------------

@Composable
private fun PauseMenu(game: GameController) {
    val ui = LocalUi.current
    val session = game.session
    val keys = game.keyMap
    Sheet(alpha = 0.88f) {
        Column(Modifier.fillMaxSize()) {
            Header("Paused", session.cart.title)
            Spacer(Modifier.weight(1f))
            game.pauseRows().forEach { row ->
                MenuRow(
                    text = row.label,
                    value = null,
                    selected = row == game.pauseRow,
                    pitch = 58f,
                    onClick = { game.activatePause(row) },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().padding(bottom = ui.dp(30f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ui.dp(12f)),
            ) {
                Legend(
                    keys.hint(Button.QUICK_SAVE) to "Quick save",
                    "hold ${keys.hint(Button.QUICK_SAVE)}" to "Quick load",
                    "hold ${keys.hint(Button.MENU)}" to "Eject",
                )
                Legend(
                    "hold ${keys.hint(Button.FAST_FORWARD)}" to "Fast forward",
                    "hold ${keys.hint(Button.REWIND)}" to "Rewind",
                    keys.hint(Button.B) to "Resume",
                )
                if (LocalTouch.current) {
                    BasicText(
                        "Tap SAVE to quick save, hold it to load · hold MENU to eject\n" +
                            "Hold ▶▶ to fast forward, double-tap to lock it · hold ◀◀ to rewind",
                        modifier = Modifier.padding(horizontal = ui.dp(30f)),
                        style = label(14f, Ink.faint).copy(textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun Switcher(game: GameController) {
    val ui = LocalUi.current
    val states = game.switcher
    val keys = game.keyMap
    val slide by animateFloatAsState(game.switcherIndex.toFloat(), spring(stiffness = 420f), label = "polaroid")
    Sheet(alpha = 0.9f) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Header("Save States", "${game.switcherIndex + 1} of ${states.size}")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                states.forEachIndexed { i, st ->
                    val off = i - slide
                    if (abs(off) > 2.5f) return@forEachIndexed
                    val lit = 1f - abs(off).coerceAtMost(1f)
                    Polaroid(
                        st,
                        modifier = Modifier
                            .offset(x = ui.dp(off * 330f))
                            .graphicsLayer {
                                val s = 0.78f + 0.22f * lit
                                scaleX = s
                                scaleY = s
                                rotationZ = off * 3f
                                alpha = 0.45f + 0.55f * lit
                            }
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                game.selectSwitcher(i)
                            },
                    )
                }
            }
            Legend(
                "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Flick",
                keys.hint(Button.A) to "Load",
                keys.hint(Button.Y) to "Delete",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(bottom = ui.dp(30f)),
            )
            TouchBar(
                "Delete" to { game.onKey(Button.Y, true) },
                "Load" to { game.onKey(Button.A, true) },
                "Back" to game::onBack,
                modifier = Modifier.padding(bottom = ui.dp(30f)),
            )
        }
    }
}

@Composable
private fun Polaroid(state: SaveState, modifier: Modifier) {
    val ui = LocalUi.current
    val thumb by produceState<ImageBitmap?>(null, state.thumb) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(state.thumb.path)?.asImageBitmap() }.getOrNull()
        }
    }
    val caption = remember(state) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.time))
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.time))
        if (state.auto) "Resume · $time" else "$day · $time"
    }
    Column(
        modifier
            .width(ui.dp(400f))
            .background(Ink.menu, RoundedCornerShape(ui.dp(3f)))
            .padding(start = ui.dp(16f), end = ui.dp(16f), top = ui.dp(16f), bottom = ui.dp(10f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val image = thumb
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(image?.let { it.width.toFloat() / it.height } ?: 1.2f)
                .background(Color(0xFF15151A)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                BasicText("no picture", style = label(15f, Ink.faint))
            }
        }
        BasicText(
            caption,
            modifier = Modifier.padding(top = ui.dp(12f)),
            style = label(19f, Color(0xFF2A2A30)),
        )
    }
}

// ---- toast ----------------------------------------------------------------------------------

@Composable
fun ToastHost(toast: Toast?, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        shown = true
        delay(1600)
        shown = false
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 300), label = "toast")
    if (a <= 0f || toast == null) return
    Box(modifier.fillMaxWidth().padding(top = ui.topDp(20f)), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .alpha(a)
                .background(Ink.housing.copy(alpha = 0.92f), RoundedCornerShape(ui.dp(8f)))
                .padding(horizontal = ui.dp(18f), vertical = ui.dp(6f)),
        ) {
            BasicText(toast.text.uppercase(), style = label(17f, Ink.menu, FontWeight.Bold, 0.14f))
        }
    }
}
