package dev.patchboy.ui

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * PatchBoy's logotypes, drawn as shapes rather than set in a font, after the Game Boy and
 * Game Boy Advance logos: [GB] is PATCHBOY in heavy, wide, slanted capitals; [ADVANCE] puts that
 * word, outlined, beside ADVANCE in thin upright geometric capitals on a band.
 *
 * Letters are designed 100 units tall. [shapes] lays a logotype out and scales it to fit.
 */
object Logotype {
    enum class Kind { GB, ADVANCE }

    /** A laid-out logotype: what to fill for the letters, and the outline behind them, if any. */
    class Shapes(val letters: Path, val backing: Path?)

    private const val SLANT = -0.22f
    private const val SPACING = 7f
    private const val OUTLINE = 17f
    private const val ADV_SCALE = 0.64f
    private const val ADV_STROKE = 22f

    /** [kind] fitted inside a box [w] x [h], centred on ([cx], [cy]). */
    fun shapes(kind: Kind, cx: Float, cy: Float, w: Float, h: Float): Shapes {
        val word = heavyWord("PATCHBOY")
        val (letters, backing) = when (kind) {
            Kind.GB -> word to null
            Kind.ADVANCE -> advance(word)
        }
        val box = RectF().also { (backing ?: letters).computeBounds(it, true) }
        val k = minOf(w / box.width(), h / box.height())
        val m = Matrix().apply {
            postTranslate(-box.centerX(), -box.centerY())
            postScale(k, k)
            postTranslate(cx, cy)
        }
        letters.transform(m)
        backing?.transform(m)
        return Shapes(letters, backing)
    }

    /** How wide [kind] is for each unit of height, to size it before drawing. */
    fun aspect(kind: Kind): Float {
        val s = shapes(kind, 0f, 0f, 10_000f, 100f)
        val box = RectF().also { (s.backing ?: s.letters).computeBounds(it, true) }
        return box.width() / box.height()
    }

    /** Draws [kind] flat: letters in [ink], on a backing in [backing] where there is one. */
    fun print(c: Canvas, kind: Kind, cx: Float, cy: Float, w: Float, h: Float, ink: Int, backing: Int) {
        val s = shapes(kind, cx, cy, w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        s.backing?.let {
            p.color = backing
            c.drawPath(it, p)
        }
        p.color = ink
        c.drawPath(s.letters, p)
    }

    // ---- the heavy capitals ----------------------------------------------------------------

    private fun heavyWord(text: String): Path {
        val out = Path()
        var x = 0f
        text.forEach { ch ->
            val (glyph, width) = heavy(ch)
            out.addPath(glyph, x, 0f)
            x += width + SPACING
        }
        // Slant: tops lean right, baselines stay put.
        out.transform(Matrix().apply { setSkew(SLANT, 0f, 0f, 100f) })
        return out
    }

    private fun heavy(ch: Char): Pair<Path, Float> = when (ch) {
        'P' -> {
            val bowl = Path().apply {
                addRoundRect(RectF(0f, 0f, 74f, 62f), floatArrayOf(0f, 0f, 31f, 31f, 31f, 31f, 0f, 0f), Path.Direction.CW)
                addRect(RectF(0f, 0f, 27f, 100f), Path.Direction.CW)
            }
            minus(union(bowl), Path().apply { addRoundRect(RectF(27f, 21f, 50f, 41f), 9f, 9f, Path.Direction.CW) }) to 74f
        }
        'A' -> {
            val body = poly(0f, 100f, 39f, 0f, 63f, 0f, 84f, 100f, 59f, 100f, 55f, 79f, 31f, 79f, 24f, 100f)
            minus(body, poly(37f, 58f, 47f, 30f, 52f, 58f)) to 84f
        }
        'T' -> union(Path().apply {
            addRect(RectF(0f, 0f, 78f, 24f), Path.Direction.CW)
            addRect(RectF(26f, 0f, 52f, 100f), Path.Direction.CW)
        }) to 78f
        'C' -> {
            val ring = minus(oval(0f, 0f, 84f, 100f), oval(25f, 23f, 72f, 77f))
            minus(ring, Path().apply { addRect(RectF(58f, 36f, 90f, 64f), Path.Direction.CW) }) to 80f
        }
        'H' -> union(Path().apply {
            addRect(RectF(0f, 0f, 27f, 100f), Path.Direction.CW)
            addRect(RectF(55f, 0f, 82f, 100f), Path.Direction.CW)
            addRect(RectF(0f, 38f, 82f, 62f), Path.Direction.CW)
        }) to 82f
        'B' -> {
            val body = union(Path().apply {
                addRect(RectF(0f, 0f, 27f, 100f), Path.Direction.CW)
                addRoundRect(RectF(0f, 0f, 72f, 52f), floatArrayOf(0f, 0f, 26f, 26f, 22f, 22f, 0f, 0f), Path.Direction.CW)
                addRoundRect(RectF(0f, 46f, 78f, 100f), floatArrayOf(0f, 0f, 24f, 24f, 28f, 28f, 0f, 0f), Path.Direction.CW)
            })
            val counters = Path().apply {
                addRoundRect(RectF(27f, 19f, 50f, 36f), 8f, 8f, Path.Direction.CW)
                addRoundRect(RectF(27f, 62f, 54f, 81f), 9f, 9f, Path.Direction.CW)
            }
            minus(body, counters) to 78f
        }
        'O' -> minus(oval(0f, 0f, 82f, 100f), oval(25f, 22f, 57f, 78f)) to 82f
        'Y' -> poly(0f, 0f, 28f, 0f, 43f, 37f, 60f, 0f, 88f, 0f, 56f, 57f, 56f, 100f, 30f, 100f, 30f, 57f) to 88f
        else -> Path() to 40f
    }

    // ---- ADVANCE ---------------------------------------------------------------------------

    /**
     * The outlined word beside a band holding ADVANCE, cut through it in thin capitals. Returns
     * the letters to fill (the word and ADVANCE) and the backing (outline and band together).
     */
    private fun advance(word: Path): Pair<Path, Path> {
        val wordBox = RectF().also { word.computeBounds(it, true) }
        val outline = stroke(word, OUTLINE * 2f, Paint.Join.ROUND, Paint.Cap.ROUND)

        val adv = Path()
        var x = 0f
        "ADVANCE".forEach { ch ->
            val (glyph, width) = thin(ch)
            adv.addPath(glyph, x, 0f)
            x += width + 12f
        }
        val advLetters = stroke(adv, ADV_STROKE, Paint.Join.MITER, Paint.Cap.SQUARE)
        val advBox = RectF().also { advLetters.computeBounds(it, true) }
        val left = wordBox.right + OUTLINE + 10f
        advLetters.transform(Matrix().apply {
            postTranslate(-advBox.left, -advBox.top)
            postScale(ADV_SCALE, ADV_SCALE)
            postTranslate(left + 12f, 50f - advBox.height() * ADV_SCALE / 2f + 4f)
        })
        val placed = RectF().also { advLetters.computeBounds(it, true) }
        val band = Path().apply {
            addRect(RectF(wordBox.right - 4f, placed.top - 9f, placed.right + 12f, placed.bottom + 9f), Path.Direction.CW)
        }
        val backing = union(Path().apply {
            addPath(outline)
            addPath(band)
        })
        val letters = Path().apply {
            addPath(word)
            addPath(advLetters)
        }
        return letters to backing
    }

    /** ADVANCE's thin capitals, as centre lines 100 units tall; [advance] strokes them. */
    private fun thin(ch: Char): Pair<Path, Float> = when (ch) {
        'A' -> Path().apply {
            moveTo(0f, 100f); lineTo(46f, 8f); lineTo(92f, 100f)
            moveTo(20f, 76f); lineTo(46f, 76f)
        } to 92f
        'D' -> Path().apply {
            addRoundRect(RectF(0f, 8f, 84f, 92f), floatArrayOf(0f, 0f, 30f, 30f, 30f, 30f, 0f, 0f), Path.Direction.CW)
        } to 84f
        'V' -> Path().apply { moveTo(0f, 0f); lineTo(46f, 92f); lineTo(92f, 0f) } to 92f
        'N' -> Path().apply { moveTo(0f, 100f); lineTo(0f, 8f); lineTo(76f, 92f); lineTo(76f, 0f) } to 76f
        'C' -> Path().apply { moveTo(86f, 8f); lineTo(0f, 8f); lineTo(0f, 92f); lineTo(86f, 92f) } to 86f
        'E' -> Path().apply {
            moveTo(86f, 8f); lineTo(0f, 8f); lineTo(0f, 92f); lineTo(86f, 92f)
            moveTo(0f, 50f); lineTo(70f, 50f)
        } to 86f
        else -> Path() to 40f
    }

    // ---- shape helpers -----------------------------------------------------------------------

    private fun poly(vararg xy: Float) = Path().apply {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        close()
    }

    private fun oval(l: Float, t: Float, r: Float, b: Float) = Path().apply { addOval(RectF(l, t, r, b), Path.Direction.CW) }

    private fun union(p: Path) = Path().apply { op(p, Path(), Path.Op.UNION) }

    private fun minus(a: Path, b: Path) = Path().apply { op(a, b, Path.Op.DIFFERENCE) }

    private fun stroke(p: Path, width: Float, join: Paint.Join, cap: Paint.Cap): Path {
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = join
            strokeCap = cap
            strokeMiter = 10f
        }
        return Path().also { paint.getFillPath(p, it) }
    }
}
