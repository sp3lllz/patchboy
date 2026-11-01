package dev.patchboy.ui

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.core.content.res.ResourcesCompat
import dev.patchboy.R
import dev.patchboy.app.AppState
import dev.patchboy.app.Mode
import dev.patchboy.app.ShelfModel
import dev.patchboy.data.Cart
import dev.patchboy.input.Button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

private const val SIDE_SCALE = 0.78f
private const val SIDE_ALPHA = 0.55f
private const val PART = 130f
private const val SLOTS = 3
private const val SEATED_W = 240f
private const val LETTER_MS = 700L
/** Where the shelf tabs sit, pushed down by [Ui.top] on phones with a camera hole up there. */
private const val TABS_Y = 42f

/** The touch menus' vertical shelf, in portrait: neighbours shrink to this and fade further out. */
private const val V_SIDE_SCALE = 0.64f

/** How much bigger the touch menus show carts than slot does, when there's room. */
private const val TOUCH_ZOOM = 1.45f
/** The tallest cart at slot's size (a Game Boy cart is 312 wide and 1.08 times as tall), in u. */
private const val TALLEST_CART = 337f
private const val V_GAP = 16f
private const val V_SLOTS = 4

/** The touch menus' Cart and Settings buttons, in dp: their height and the gap under them. */
private const val BUTTON_H = 56f
private const val BUTTON_GAP = 14f
private const val BUTTON_HINT = 30f

private const val CATCH_IN = 0.42f
private const val CATCH_OUT = 0.62f
private const val CREEP = 0.03f

/** Cart widths in u, at the size they sit on the shelf. */
fun cartWidth(cart: Cart): Float = when (cart.platform) {
    dev.patchboy.core.Platform.GBA -> 360f
    dev.patchboy.core.Platform.GB, dev.patchboy.core.Platform.GBC -> 312f
}

/**
 * Where the carousel lives on screen: carts rest centred on [centre], between [top] and
 * [bottom]. [vertical] stacks them top to bottom (the touch menus in portrait); otherwise they
 * run left to right, as slot has them. The caption goes under the middle cart, or, stacked,
 * at [bottom].
 */
private class Stage(val vertical: Boolean, val top: Float, val bottom: Float, val zoom: Float = 1f) {
    val centre: Float get() = (top + bottom) / 2f

    /** A cart's width and height on this stage, in px. */
    fun width(cart: Cart, ui: Ui): Float = cartWidth(cart) * ui.u * zoom
    fun height(cart: Cart, ui: Ui): Float = width(cart, ui) * CartArt.spec(cart.shape).aspect

    /** How far a cart [t] places from the middle sits from it, stacked, for carts [ch] tall. */
    fun distance(t: Float, ch: Float, u: Float): Float {
        val first = ch * (1f + V_SIDE_SCALE) / 2f + V_GAP * u
        return if (t <= 1f) t * first else first + (t - 1f) * (ch * V_SIDE_SCALE + V_GAP * u)
    }

    companion object {
        /** [buttons] is the room the touch buttons above the slot need, in px (0 with key hints). */
        fun of(ui: Ui, chrome: Chrome, vertical: Boolean, buttons: Float): Stage {
            val top = ui.top(TABS_Y) + ui.px(40f)
            val bottom = when {
                buttons <= 0f -> ui.heightPx - ui.px(58f + 92f)
                vertical -> chrome.bandTop - buttons - ui.px(56f)
                else -> chrome.bandTop - buttons - ui.px(60f)
            }
            // Touch menus show carts bigger, as far as the space allows: stacked, the middle
            // cart may take half the height; in a row, all of it but the caption under it.
            val room = if (vertical) (bottom - top) * 0.5f else bottom - top - ui.px(60f)
            // ...and never wider than most of the screen (a GBA cart is 360 u wide).
            val wide = ui.widthPx * 0.82f / ui.px(360f)
            val zoom = when {
                buttons <= 0f && !vertical -> 1f
                else -> minOf(TOUCH_ZOOM, room / ui.px(TALLEST_CART), wide).coerceAtLeast(1f)
            }
            return Stage(vertical, top, bottom, zoom)
        }
    }
}

/** The slot along the bottom of the panel, after slot's slot_chrome.rs. */
private class Chrome(ui: Ui) {
    val w = ui.widthPx
    val h = ui.heightPx
    val u = ui.u
    val cx = w / 2f
    val mouthH = 58f * u
    val bandTop = h - mouthH
    val lipH = 2f * u
    val mouthW = (SEATED_W + 14f) * u
    val mouthX = cx - mouthW / 2f
    val bayW = mouthW + 18f * u
    val bayX = cx - bayW / 2f
    val bayY = bandTop + lipH
    val slitY = bayY + 5f * u
    val slitH = 9f * u
    val recessH = 42f * u
    val scoopW = mouthW * 0.88f
    val scoopY = slitY + slitH
    val scoopD = recessH - (scoopY - bayY)
    val rimW = 2f * u
    val seatedY = bayY + 4f * u

    private fun depth(x: Float): Float = scoopD * (1f - (abs(x) / (scoopW / 2f)).pow(4f)).coerceAtLeast(0f)

    /** The dark scoop under the slit, as a closed shape from [top] down to its curved floor. */
    fun scoop(extra: Float = 0f): Path = Path().apply {
        val hw = scoopW / 2f
        moveTo(cx - hw, scoopY)
        var x = -hw
        while (x <= hw) {
            lineTo(cx + x, scoopY + depth(x) + extra)
            x += 2f
        }
        lineTo(cx + hw, scoopY)
        close()
    }

    val front: Path = Path().apply {
        val band = Path().apply {
            addRect(Rect(0f, bandTop, bayX, h))
            addRect(Rect(bayX + bayW, bandTop, w, h))
            addRect(Rect(bayX, scoopY, bayX + bayW, h))
        }
        op(band, scoop(), PathOperation.Difference)
    }

    val rim: Path = Path().apply {
        val hw = scoopW / 2f
        var x = -hw
        moveTo(cx - hw, scoopY + depth(-hw))
        while (x <= hw) {
            lineTo(cx + x, scoopY + depth(x) + rimW / 2f)
            x += 2f
        }
    }

    /** Where the hole's middle sits, for the platform name and jump letters. */
    val holeCentre: Float get() = slitY + (scoopY + scoopD - slitY) / 2f

    fun drawBack(d: DrawScope, alpha: Float) = with(d) {
        drawRect(Ink.housing, Offset(bayX, bandTop), Size(bayW, lipH), alpha)
        drawRect(Ink.edge, Offset(mouthX, bandTop), Size(mouthW, lipH), alpha)
        drawRect(Ink.recess, Offset(bayX, bayY), Size(bayW, recessH), alpha)
        drawRect(Ink.opening, Offset(mouthX, slitY), Size(mouthW, slitH), alpha)
        drawPath(scoop(), Ink.opening, alpha)
    }

    fun drawFront(d: DrawScope, alpha: Float) = with(d) {
        drawPath(front, Ink.housing, alpha)
        drawPath(rim, Ink.edge, alpha, style = Stroke(rimW))
    }
}

/** slot's insert travel: ease to the catch, creep through it, then push home. */
private fun travel(seat: Float, catch: Float): Float = when {
    seat < CATCH_IN -> catch * ease(seat / CATCH_IN)
    seat < CATCH_OUT -> catch + CREEP * (seat - CATCH_IN) / (CATCH_OUT - CATCH_IN)
    else -> {
        val caught = catch + CREEP
        caught + (1f - caught) * ease((seat - CATCH_OUT) / (1f - CATCH_OUT))
    }
}

private fun ease(u: Float): Float = u * u * u * (u * (u * 6f - 15f) + 10f)

/** Rendered cart faces for the carts near the centre of the shelf. */
class FaceCache(private val typeface: Typeface) {
    private val faces = mutableStateMapOf<String, ImageBitmap>()
    private val loading = mutableSetOf<String>()

    fun get(cart: Cart, widthPx: Int, scope: CoroutineScope): ImageBitmap? {
        // The cart's hash changes with its colour, shape, name or label, so edits redraw.
        val key = "${cart.key}@$widthPx#${cart.hashCode()}"
        faces[key]?.let { return it }
        if (loading.add(key)) {
            scope.launch(Dispatchers.Default) {
                val face = runCatching { CartArt.render(cart, widthPx, typeface) }.getOrNull()
                withContext(Dispatchers.Main) {
                    loading.remove(key)
                    if (face != null) faces[key] = face.asImageBitmap()
                }
            }
        }
        return null
    }

    /** Drops faces for carts that are no longer near the centre. */
    fun keep(carts: Set<String>) {
        if (faces.size <= 24) return
        faces.keys.filter { k -> carts.none { k.startsWith("$it@") } }.forEach { faces.remove(it) }
    }
}

@Composable
fun ShelfScreen(app: AppState) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typeface = remember { ResourcesCompat.getFont(context, R.font.open_sans) ?: Typeface.DEFAULT }
    val faces = remember { FaceCache(typeface) }
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val chrome = remember(ui) { Chrome(ui) }
    val touch = LocalTouch.current
    // The touch menus get big Cart and Settings buttons, and stack the shelf in portrait.
    val touchMenus = LocalTouchMenus.current && touch
    val vertical = LocalTouchMenus.current && ui.heightPx > ui.widthPx
    val density = LocalDensity.current
    // In landscape the buttons go up in the corners beside the tabs, leaving the height to the
    // carts; only the hint line stays down by the slot.
    val buttons = when {
        !touchMenus -> 0f
        vertical -> with(density) { (BUTTON_H + BUTTON_GAP + BUTTON_HINT).dp.toPx() }
        else -> with(density) { (BUTTON_GAP + BUTTON_HINT).dp.toPx() }
    }
    val stage = remember(ui, chrome, vertical, buttons) { Stage.of(ui, chrome, vertical, buttons) }

    val seat = remember { Animatable(0f) }
    val screen = remember { Animatable(0f) }
    // Spring the carousel, but only while a shelf is still moving.
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { app.shelves.any { !it.settled } }.first { it }
            var last = 0L
            while (app.shelves.any { !it.settled }) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                    last = now
                    app.shelves.forEach { it.update(dt) }
                }
            }
        }
    }

    val letter = app.letter
    LaunchedEffect(letter) {
        if (letter != null) {
            kotlinx.coroutines.delay(LETTER_MS)
            app.clearLetter()
        }
    }

    val mode = app.mode
    LaunchedEffect(mode) {
        when (mode) {
            is Mode.Inserting -> {
                seat.snapTo(0f)
                screen.snapTo(0f)
                seat.animateTo(1f, tween(760, easing = LinearEasing))
                screen.animateTo(1f, tween(240))
                app.onInserted()
            }
            is Mode.Playing -> {
                seat.snapTo(1f)
                screen.snapTo(1f)
            }
            is Mode.Ejecting -> {
                screen.snapTo(1f)
                seat.snapTo(1f)
                screen.animateTo(0f, tween(200))
                seat.animateTo(0f, tween(640, easing = LinearEasing))
                app.onEjected()
            }
            else -> {
                seat.snapTo(0f)
                screen.snapTo(0f)
            }
        }
    }

    val shelf = app.shelf
    LaunchedEffect(shelf, shelf?.index) {
        val s = shelf ?: return@LaunchedEffect
        faces.keep((-SLOTS..SLOTS).mapNotNull { s.cartAt(it) }.map { s.carts[it].key }.toSet())
    }
    val moving = when (mode) {
        is Mode.Inserting -> mode.cart
        is Mode.Playing -> mode.cart
        is Mode.Ejecting -> mode.cart
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(app, stage) {
                detectTapGestures(
                    onTap = { p ->
                        if (app.mode != Mode.Shelf || app.overlay != null) return@detectTapGestures
                        if (p.y < ui.top(TABS_Y) + ui.px(50f)) {
                            app.switchShelf(if (p.x < size.width / 2) -1 else 1)
                            return@detectTapGestures
                        }
                        if (stage.vertical) {
                            val by = stackSlotAt(app, ui, stage, p.y)
                            if (by == 0) app.insert(fresh = false) else app.stepShelf(by)
                            return@detectTapGestures
                        }
                        when {
                            p.x < size.width * 0.28f -> app.stepShelf(-1)
                            p.x > size.width * 0.72f -> app.stepShelf(1)
                            else -> app.insert(fresh = false)
                        }
                    },
                    onLongPress = { p ->
                        if (app.mode != Mode.Shelf || app.overlay != null) return@detectTapGestures
                        val middle = if (stage.vertical) stackSlotAt(app, ui, stage, p.y) == 0 else p.x in size.width * 0.28f..size.width * 0.72f
                        if (middle) app.insert(fresh = true)
                    },
                )
            }
            .pointerInput(app, stage) {
                // Along the shelf, a drag flicks through carts. Stacked, a sideways swipe
                // switches shelves instead.
                var acc = 0f
                var across = 0f
                val step = ui.px(if (stage.vertical) 150f else 110f)
                val onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, d: Float ->
                    if (app.mode == Mode.Shelf && app.overlay == null) {
                        change.consume()
                        acc += d
                        while (abs(acc) > step) {
                            app.stepShelf(if (acc > 0) -1 else 1)
                            acc -= step * sign(acc)
                        }
                    }
                }
                if (stage.vertical) {
                    // Undispatched, so both are listening before the touch that started this
                    // block goes past; otherwise the first swipe would read as a tap.
                    coroutineScope {
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            detectVerticalDragGestures(onDragStart = { acc = 0f }, onVerticalDrag = onDrag)
                        }
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            detectHorizontalDragGestures(
                                onDragStart = { across = 0f },
                                onDragEnd = {
                                    if (app.mode == Mode.Shelf && app.overlay == null && abs(across) > ui.px(120f)) {
                                        app.switchShelf(if (across > 0) -1 else 1)
                                    }
                                },
                            ) { change, dx ->
                                change.consume()
                                across += dx
                            }
                        }
                    }
                } else {
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }, onHorizontalDrag = onDrag)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = seat.value
            val recede = if (moving != null) min(1f, s * 2.2f) else 0f

            drawTabs(app, ui, measurer, alpha = 1f - recede, caps = !touch)

            if (shelf != null) {
                drawRow(shelf, ui, stage, faces, scope, hidden = moving?.key, recede = recede)
                if (moving == null) drawCaption(shelf, ui, stage, measurer)
            } else if (app.scanned) {
                drawEmpty(app, ui, measurer, touch)
            }

            chrome.drawBack(this, 1f)
            if (moving != null) drawMoving(moving, s, ui, stage, chrome, faces, scope)
            else drawSlotName(app, ui, chrome, measurer)
            chrome.drawFront(this, 1f)
            drawFooter(app, ui, chrome, measurer)
            if (moving == null && shelf != null && !touch) drawHints(app, shelf, ui, chrome, measurer)

            if (screen.value > 0f) drawRect(Color.Black, alpha = screen.value)
        }
        if (touch && moving == null && app.overlay == null) {
            if (touchMenus) TouchButtons(app, chrome, hasCart = shelf?.current != null, corners = !stage.vertical)
            else TouchHints(app, chrome, hasCart = shelf?.current != null)
        }
    }
}

/**
 * Which cart a tap at [y] lands on in the stacked shelf, as a step from the middle one (0).
 * Taps between carts go to the nearer.
 */
private fun stackSlotAt(app: AppState, ui: Ui, stage: Stage, y: Float): Int {
    val cart = app.shelf?.current ?: return 0
    val ch = stage.height(cart, ui)
    val dy = y - stage.centre
    if (abs(dy) <= ch / 2f) return 0
    val n = (1..V_SLOTS).minBy { abs(stage.distance(it.toFloat(), ch, ui.u) - abs(dy)) }
    return n * sign(dy).toInt()
}

/**
 * The touch menus' home buttons: full-size Cart and Settings above the slot, or, with
 * [corners], in the top corners either side of the shelf tabs.
 */
@Composable
private fun TouchButtons(app: AppState, chrome: Chrome, hasCart: Boolean, corners: Boolean) {
    val density = LocalDensity.current
    if (corners) {
        val ui = LocalUi.current
        val top = with(density) { ui.safeTop.toDp() } + 12.dp
        Row(Modifier.fillMaxWidth().padding(top = top, start = 20.dp, end = 20.dp)) {
            if (hasCart) TouchButton("Cart", app::openCart, Modifier.width(160.dp), primary = false)
            Spacer(Modifier.weight(1f))
            TouchButton("Settings", app::openSettings, Modifier.width(160.dp), primary = false)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { chrome.bandTop.toDp() })
                .padding(bottom = BUTTON_GAP.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (hasCart) {
                BasicText(
                    "Tap to play · hold for a new game",
                    modifier = Modifier.height(BUTTON_HINT.dp),
                    style = touchText(13f, Ink.faint),
                    maxLines = 1,
                )
            }
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { chrome.bandTop.toDp() })
            .padding(bottom = BUTTON_GAP.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                if (hasCart) "Tap to play · hold for a new game" else "",
                modifier = Modifier.height(BUTTON_HINT.dp),
                style = touchText(13f, Ink.faint).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasCart) TouchButton("Cart", app::openCart, Modifier.weight(1f), primary = false)
                TouchButton("Settings", app::openSettings, Modifier.weight(1f), primary = false)
            }
        }
    }
}

/** In place of the key hints, with no keyboard: buttons for the cart sheet and Settings. */
@Composable
private fun TouchHints(app: AppState, chrome: Chrome, hasCart: Boolean) {
    val ui = LocalUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (chrome.bandTop - ui.px(66f)).roundToInt()) }
            .padding(horizontal = ui.dp(36f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasCart) TouchPill("Cart", app::openCart)
        BasicText(
            if (hasCart) "Tap to play · hold for a new game" else "",
            modifier = Modifier.weight(1f).padding(horizontal = ui.dp(10f)),
            style = label(13f, Ink.faint).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
        TouchPill("Settings", app::openSettings)
    }
}

private fun DrawScope.drawRow(
    shelf: ShelfModel,
    ui: Ui,
    stage: Stage,
    faces: FaceCache,
    scope: CoroutineScope,
    hidden: String?,
    recede: Float,
) {
    if (stage.vertical) return drawStack(shelf, ui, stage, faces, scope, hidden, recede)
    val u = ui.u
    val cx = size.width / 2f
    for (slot in -SLOTS..SLOTS) {
        val i = shelf.cartAt(slot) ?: continue
        val cart = shelf.carts[i]
        if (cart.key == hidden) continue
        val offset = shelf.ride + slot - shelf.scroll
        val t = min(abs(offset), 1f)
        val scale = 1f + (SIDE_SCALE - 1f) * t
        val alpha = (1f + (SIDE_ALPHA - 1f) * t) * (1f - recede)
        if (alpha <= 0f) continue
        val cw = stage.width(cart, ui)
        val ch = stage.height(cart, ui)
        val w = cw * scale
        val h = ch * scale
        val away = sign(offset) * (1f + abs(offset))
        val x = cx + offset * cw - w / 2f + away * PART * u * recede
        if (x + w <= 0f || x >= size.width) continue
        val foot = stage.centre + ch / 2f
        drawCart(cart, faces, scope, x, foot - h, w, h, cw, alpha)
    }
}

/**
 * The shelf stacked top to bottom: the middle cart full size, the rest smaller and fainter
 * the further they are, so a tall phone shows several at once.
 */
private fun DrawScope.drawStack(
    shelf: ShelfModel,
    ui: Ui,
    stage: Stage,
    faces: FaceCache,
    scope: CoroutineScope,
    hidden: String?,
    recede: Float,
) {
    val u = ui.u
    val cx = size.width / 2f
    // Below the shelf tabs, above the caption.
    val clipTop = ui.top(TABS_Y) + ui.px(34f)
    clipRect(top = clipTop, bottom = stage.bottom + ui.px(8f)) {
        for (slot in -V_SLOTS..V_SLOTS) {
            val i = shelf.cartAt(slot) ?: continue
            val cart = shelf.carts[i]
            if (cart.key == hidden) continue
            val offset = shelf.ride + slot - shelf.scroll
            val t = abs(offset)
            val near = min(t, 1f)
            val scale = 1f + (V_SIDE_SCALE - 1f) * near
            val fade = if (t <= 1f) 1f + (SIDE_ALPHA - 1f) * t else SIDE_ALPHA * (1f - (t - 1f) * 0.28f)
            val alpha = fade.coerceAtLeast(0f) * (1f - recede)
            if (alpha <= 0f) continue
            val cw = stage.width(cart, ui)
            val ch = stage.height(cart, ui)
            val w = cw * scale
            val h = ch * scale
            val y = stage.centre + sign(offset) * (stage.distance(t, ch, u) + PART * u * recede)
            if (y + h / 2f < clipTop || y - h / 2f > stage.bottom + ui.px(8f)) continue
            drawCart(cart, faces, scope, cx - w / 2f, y - h / 2f, w, h, cw, alpha)
        }
    }
}

private fun DrawScope.drawCart(
    cart: Cart,
    faces: FaceCache,
    scope: CoroutineScope,
    x: Float, y: Float, w: Float, h: Float,
    fullW: Float,
    alpha: Float,
) {
    val face = faces.get(cart, fullW.roundToInt(), scope)
    if (face == null) {
        val c = Color(cart.shell.rgb or 0xFF000000.toInt())
        drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(w * 0.03f), alpha = alpha)
        return
    }
    withTransform({
        translate(x, y)
        scale(w / face.width, h / face.height, pivot = Offset.Zero)
    }) {
        drawImage(face, alpha = alpha)
    }
}

private fun DrawScope.drawMoving(
    cart: Cart,
    seat: Float,
    ui: Ui,
    stage: Stage,
    chrome: Chrome,
    faces: FaceCache,
    scope: CoroutineScope,
) {
    val aspect = CartArt.spec(cart.shape).aspect
    val w0 = stage.width(cart, ui)
    val h0 = w0 * aspect
    val sw = SEATED_W * ui.u
    val sh = sw * aspect
    val foot0 = stage.centre + h0 / 2f
    val seatedFoot = chrome.seatedY + sh
    val catch = (chrome.bandTop - foot0) / (seatedFoot - foot0)
    val tr = travel(seat.coerceIn(0f, 1f), catch)
    val k = min(tr / catch, 1f)
    val cw = w0 + (sw - w0) * k
    val ch = h0 + (sh - h0) * k
    val y = foot0 + (seatedFoot - foot0) * tr - ch
    drawCart(cart, faces, scope, chrome.cx - cw / 2f, y, cw, ch, w0, 1f)
}

private fun DrawScope.drawTabs(app: AppState, ui: Ui, measurer: TextMeasurer, alpha: Float, caps: Boolean) {
    if (alpha <= 0f || app.shelves.isEmpty()) return
    val tabs = app.shelves
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(17f), fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)
    val laid = tabs.map { measurer.measure(it.platform.tab, style) }
    val gap = ui.px(26f)
    val keys = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), fontWeight = FontWeight.Bold)
    val left = measurer.measure(app.keyMap.hint(Button.L), keys)
    val right = measurer.measure(app.keyMap.hint(Button.R), keys)
    val capW = { l: androidx.compose.ui.text.TextLayoutResult -> l.size.width + ui.px(14f) }
    val keyed = caps && tabs.size > 1
    val capsW = if (keyed) capW(left) + capW(right) + 2 * ui.px(30f) else 0f
    val total = laid.sumOf { it.size.width } + gap * (laid.size - 1) + capsW
    var x = (size.width - total) / 2f
    val y = ui.top(TABS_Y)
    if (keyed) {
        drawKeyCap(left, Offset(x, y + ui.px(2f)), ui, alpha)
        x += capW(left) + ui.px(30f)
    }
    laid.forEachIndexed { i, l ->
        val lit = i == app.shelfIndex
        drawText(l, color = if (lit) Ink.menu else Ink.faint, topLeft = Offset(x, y), alpha = alpha)
        if (lit) {
            drawRect(Ink.menu, Offset(x, y + l.size.height + ui.px(3f)), Size(l.size.width.toFloat(), ui.px(2f)), alpha)
        }
        x += l.size.width + gap
    }
    if (keyed) drawKeyCap(right, Offset(x - gap + ui.px(30f), y + ui.px(2f)), ui, alpha)
}

private fun DrawScope.drawKeyCap(l: androidx.compose.ui.text.TextLayoutResult, at: Offset, ui: Ui, alpha: Float) {
    val padX = ui.px(7f)
    val padY = ui.px(1.5f)
    drawRoundRect(
        Ink.edge, at, Size(l.size.width + padX * 2, l.size.height + padY * 2),
        CornerRadius(ui.px(5f)), alpha = alpha,
    )
    drawText(l, color = Ink.menu, topLeft = at + Offset(padX, padY), alpha = alpha)
}

private fun DrawScope.drawCaption(shelf: ShelfModel, ui: Ui, stage: Stage, measurer: TextMeasurer) {
    val cart = shelf.current ?: return
    val settle = 1f - min(abs(shelf.scroll - shelf.ride) * 3f, 1f)
    if (settle <= 0f) return
    val ch = stage.height(cart, ui)
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(21f), color = Ink.dim)
    val l = measurer.measure(
        cart.title, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (size.width - ui.px(80f)).toInt()),
    )
    val y = if (stage.vertical) stage.bottom + ui.px(18f) else stage.centre + ch / 2f + ui.px(26f)
    drawText(l, topLeft = Offset((size.width - l.size.width) / 2f, y), alpha = settle)
}

private fun DrawScope.drawSlotName(app: AppState, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val letter = app.letter
    if (letter == null && app.shelf == null) return
    val text = letter?.first?.toString() ?: CartArt.BRAND.uppercase()
    val style = TextStyle(
        fontFamily = LabelFont,
        fontSize = ui.sp(if (letter != null) 22f else 10f),
        fontWeight = FontWeight.Bold,
        letterSpacing = if (letter != null) 0.em else 0.2.em,
        color = if (letter != null) Ink.menu else Ink.faint,
    )
    val l = measurer.measure(text, style)
    drawText(l, topLeft = Offset(chrome.cx - l.size.width / 2f, chrome.holeCentre - l.size.height / 2f))
}

private fun DrawScope.drawFooter(app: AppState, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(16f), fontWeight = FontWeight.Bold, color = Ink.menu, letterSpacing = 0.06.em)
    val mid = chrome.bandTop + chrome.mouthH / 2f
    val margin = ui.px(44f)

    // Battery gauge.
    val gw = ui.px(22f)
    val gh = ui.px(11f)
    var x = margin
    val battery = app.status.battery
    if (app.status.charging) {
        val bolt = Path().apply {
            moveTo(x + ui.px(5f), mid - ui.px(7f))
            lineTo(x + ui.px(1f), mid + ui.px(1f))
            lineTo(x + ui.px(4.5f), mid + ui.px(1f))
            lineTo(x + ui.px(3f), mid + ui.px(7f))
            lineTo(x + ui.px(8f), mid - ui.px(1.5f))
            lineTo(x + ui.px(4.5f), mid - ui.px(1.5f))
            close()
        }
        drawPath(bolt, Ink.menu)
        x += ui.px(13f)
    }
    drawRoundRect(Ink.menu, Offset(x, mid - gh / 2f), Size(gw, gh), CornerRadius(ui.px(2f)), style = Stroke(ui.px(1.6f)))
    drawRect(Ink.menu, Offset(x + gw, mid - gh / 4f), Size(ui.px(2f), gh / 2f))
    if (battery >= 0) {
        val inner = (gw - ui.px(4.4f)) * battery / 100f
        drawRect(Ink.menu, Offset(x + ui.px(2.2f), mid - gh / 2f + ui.px(2.2f)), Size(inner, gh - ui.px(4.4f)))
        val pct = measurer.measure("$battery%", style)
        drawText(pct, topLeft = Offset(x + gw + ui.px(10f), mid - pct.size.height / 2f))
    }

    val clock = measurer.measure(app.status.clock, style)
    drawText(clock, topLeft = Offset(size.width - margin - clock.size.width, mid - clock.size.height / 2f))
}

private fun DrawScope.drawHints(app: AppState, shelf: ShelfModel, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val keys = app.keyMap
    val parts = buildList {
        add(keys.hint(Button.A) to "Play")
        add("hold ${keys.hint(Button.A)}" to "New game")
        add(keys.hint(Button.START) to "Cart")
        add(keys.hint(Button.MENU) to "Settings")
    }
    val keyStyle = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), fontWeight = FontWeight.Bold, color = Ink.dim)
    val textStyle = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), color = Ink.faint)
    val laid = parts.map { (k, t) -> measurer.measure(k, keyStyle) to measurer.measure(t, textStyle) }
    val inner = ui.px(6f)
    val gap = ui.px(22f)
    val total = laid.sumOf { (k, t) -> (k.size.width + inner + t.size.width).toDouble() }.toFloat() + gap * (laid.size - 1)
    var x = (size.width - total) / 2f
    val y = chrome.bandTop - ui.px(30f)
    laid.forEach { (k, t) ->
        drawText(k, topLeft = Offset(x, y))
        x += k.size.width + inner
        drawText(t, topLeft = Offset(x, y))
        x += t.size.width + gap
    }
}

private fun DrawScope.drawEmpty(app: AppState, ui: Ui, measurer: TextMeasurer, touch: Boolean) {
    val keys = app.keyMap
    val missing = !app.paths.libraryAvailable
    val title = measurer.measure(
        if (missing) "${app.storage.libraryName} isn't in" else "No carts on the shelf yet",
        TextStyle(fontFamily = LabelFont, fontSize = ui.sp(26f), fontWeight = FontWeight.Bold, color = Ink.menu),
    )
    val body = measurer.measure(
        if (missing) {
            val settings = if (touch) "Settings" else "Settings (${keys.hint(Button.MENU)})"
            "Your games are on it. Put it back in, or move the shelf in $settings › Game Storage."
        } else if (touch) {
            "Tap here to add Game Boy, Game Boy Color and Game Boy Advance games."
        } else {
            "Press ${keys.hint(Button.A)} or tap here to add Game Boy, Game Boy Color and Game Boy Advance games."
        },
        TextStyle(fontFamily = LabelFont, fontSize = ui.sp(17f), color = Ink.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        constraints = Constraints(maxWidth = (size.width - ui.px(110f)).toInt()),
    )
    val top = size.height * 0.36f
    drawText(title, topLeft = Offset((size.width - title.size.width) / 2f, top))
    drawText(body, topLeft = Offset((size.width - body.size.width) / 2f, top + title.size.height + ui.px(18f)))
}
