package com.rob.aikeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCKED }

enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/**
 * Canvas-drawn key grid. Reports key presses, long-press alternates, space-bar swipes,
 * cursor moves and word deletion to a [Listener]; all text editing happens in the service.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onText(text: String)
        fun onFunction(type: KeyType)
        fun onShiftLock()
        fun onSpaceSwipe(direction: Int)
        fun onCursorMove(steps: Int)
        fun onDeleteWord()
        fun onVoice()
        /** Backspace is being held: offer "delete everything". */
        fun onDeleteHeld()
    }

    var layout: KeyboardLayout = Layouts.symbols
        set(value) { field = value; requestLayout(); computeGeometry(); invalidate() }
    var shiftState = ShiftState.OFF
        set(value) { field = value; invalidate() }
    var spaceLabel = ""
        set(value) { field = value; invalidate() }
    var showLanguageArrows = true
        set(value) { field = value; invalidate() }
    var enterAction = EnterAction.NEWLINE
        set(value) { field = value; invalidate() }
    var showHints = true
        set(value) { field = value; invalidate() }
    var heightScale = 1f
        set(value) { field = value; requestLayout() }
    var spaceSwipeMovesCursor = false

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private class KeyRect(val key: Key, val rect: RectF, val hit: RectF)

    private var keys: List<KeyRect> = emptyList()
    private val pressed = HashSet<Key>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.1f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.8f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val tmp = RectF()

    // ---- Touch tracking ----

    private enum class Mode { TAP, ALTERNATES, SPACE_SWIPE, CURSOR, DELETE_SWIPE, REPEAT, CONSUMED }

    private inner class Touch(val id: Int, val key: KeyRect, val downX: Float, val downY: Float) {
        var mode = Mode.TAP
        var lastX = downX
        var stepAnchorX = downX
        var selected = 0
        val longPress = Runnable { onLongPress(this) }
        var repeats = 0
        val repeat = object : Runnable {
            override fun run() {
                // After about a second of holding, delete whole words instead of single letters.
                if (repeats++ < WORD_REPEAT_AFTER) listener.onFunction(KeyType.DELETE) else listener.onDeleteWord()
                handler.postDelayed(this, if (repeats > WORD_REPEAT_AFTER) WORD_REPEAT_INTERVAL else REPEAT_INTERVAL)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val touches = HashMap<Int, Touch>()
    private var popup: Popup? = null

    private class Popup(val owner: Int, val options: List<String>, val cells: List<RectF>, val bounds: RectF)

    // ---- Measurement & geometry ----

    private val keyHeight: Float
        get() {
            val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val tablet = resources.configuration.smallestScreenWidthDp >= 600
            val base = KeyboardTheme.BASE_KEY_HEIGHT_DP * (if (tablet) 1.12f else 1f) * (if (landscape) 0.78f else 1f)
            return dp(base * heightScale)
        }

    private val rowPitch get() = keyHeight + dp(KeyboardTheme.KEY_GAP_V_DP)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rowPitch * layout.rows.size + dp(6f)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeGeometry()

    private fun computeGeometry() {
        if (width == 0) return
        val gapH = dp(KeyboardTheme.KEY_GAP_H_DP)
        val gapV = dp(KeyboardTheme.KEY_GAP_V_DP)
        val side = dp(3f)
        val usable = width - side * 2
        val unit = usable / layout.maxRowUnits
        val out = ArrayList<KeyRect>()
        layout.rows.forEachIndexed { r, row ->
            val units = row.sumOf { it.width.toDouble() }.toFloat()
            var x = side + (usable - units * unit) / 2
            val top = dp(3f) + r * rowPitch
            val rowStart = out.size
            row.forEach { key ->
                val w = key.width * unit
                val rect = RectF(x + gapH / 2, top + gapV / 2, x + w - gapH / 2, top + gapV / 2 + keyHeight)
                val hit = RectF(x, top, x + w, top + rowPitch)
                out += KeyRect(key, rect, hit)
                x += w
            }
            // Stretch the outermost hit boxes to the screen edges so edge taps still land.
            out[rowStart].hit.left = 0f
            out[out.lastIndex].hit.right = width.toFloat()
        }
        keys = out
    }

    private fun keyAt(x: Float, y: Float): KeyRect? =
        keys.firstOrNull { it.hit.contains(x, y) } ?: keys.minByOrNull { dist2(it.rect, x, y) }

    private fun dist2(r: RectF, x: Float, y: Float): Float {
        val dx = max(max(r.left - x, 0f), x - r.right)
        val dy = max(max(r.top - y, 0f), y - r.bottom)
        return dx * dx + dy * dy
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        // Not drawColor(): inside Compose the canvas is not clipped to this view and would paint over the toolbar.
        fill.color = KeyboardTheme.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val radius = dp(KeyboardTheme.KEY_RADIUS_DP)
        for (kr in keys) drawKey(canvas, kr, radius)
        popup?.let { drawPopup(canvas, it, radius) }
    }

    private fun isFunction(key: Key) = key.type in setOf(
        KeyType.SHIFT, KeyType.DELETE, KeyType.SYMBOLS, KeyType.SYMBOLS_MORE, KeyType.ALPHA, KeyType.ENTER, KeyType.EMOJI,
    )

    private fun drawKey(canvas: Canvas, kr: KeyRect, radius: Float) {
        val key = kr.key
        val r = kr.rect
        val down = key in pressed
        val function = isFunction(key)
        fill.color = when {
            function && down -> KeyboardTheme.functionKeyPressed
            function -> KeyboardTheme.functionKey
            down -> KeyboardTheme.keyPressed
            else -> KeyboardTheme.key
        }
        canvas.drawRoundRect(r, radius, radius, fill)
        if (!function) {
            stroke.color = if (down) KeyboardTheme.outline else KeyboardTheme.outlineDim
            canvas.drawRoundRect(r, radius, radius, stroke)
            // Brighter top edge, like the glow on the reference keys.
            stroke.color = KeyboardTheme.outline
            tmp.set(r.left, r.top, r.right, r.top + radius * 2)
            canvas.save()
            canvas.clipRect(r.left, r.top - dp(1f), r.right, r.top + radius * 0.9f)
            canvas.drawRoundRect(r, radius, radius, stroke)
            canvas.restore()
        }

        val cx = r.centerX()
        val cy = r.centerY()
        val h = r.height()
        val fg = if (function) KeyboardTheme.labelOnFunction else KeyboardTheme.label
        icon.color = fg
        when (key.type) {
            KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD -> {
                val label = displayText(key)
                text.color = fg
                text.typeface = Typeface.DEFAULT
                text.textSize = if (label.length > 1) h * 0.34f else h * 0.48f
                canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text)
                if (key.type == KeyType.COMMA) drawMic(canvas, cx, r.top + h * 0.2f, h * 0.16f, KeyboardTheme.hint)
                val hint = key.hint
                if (hint != null && (showHints || key.type == KeyType.PERIOD)) {
                    text.textSize = h * 0.22f
                    text.color = KeyboardTheme.hint
                    val hx = if (key.type == KeyType.PERIOD) cx else r.right - h * 0.17f
                    canvas.drawText(hint, hx, r.top + h * 0.27f, text)
                }
            }
            KeyType.SPACE -> {
                text.color = KeyboardTheme.label
                text.textSize = h * 0.32f
                canvas.drawText(spaceLabel, cx, cy - (text.descent() + text.ascent()) / 2, text)
                if (showLanguageArrows) {
                    fill.color = KeyboardTheme.outline
                    val a = h * 0.12f
                    triangle(canvas, r.left + h * 0.3f, cy, a, left = true)
                    triangle(canvas, r.right - h * 0.3f, cy, a, left = false)
                }
            }
            KeyType.SHIFT -> drawShift(canvas, cx, cy, h * 0.42f)
            KeyType.DELETE -> drawDelete(canvas, cx, cy, h * 0.42f)
            KeyType.ENTER -> drawEnter(canvas, cx, cy, h * 0.4f)
            KeyType.EMOJI -> drawSmiley(canvas, cx, cy, h * 0.22f)
            KeyType.SYMBOLS, KeyType.SYMBOLS_MORE, KeyType.ALPHA -> {
                text.color = fg
                text.textSize = h * 0.3f
                canvas.drawText(key.text, cx, cy - (text.descent() + text.ascent()) / 2, text)
            }
        }
    }

    private fun displayText(key: Key): String {
        val t = key.text
        return if (shiftState != ShiftState.OFF && key.type == KeyType.CHAR) applyShift(t) else t
    }

    fun applyShift(t: String): String = if (t == "ß") t else t.uppercase()

    private fun triangle(canvas: Canvas, x: Float, y: Float, a: Float, left: Boolean) {
        path.reset()
        val dir = if (left) -1 else 1
        path.moveTo(x + dir * a, y)
        path.lineTo(x - dir * a * 0.6f, y - a)
        path.lineTo(x - dir * a * 0.6f, y + a)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy - s * 0.55f)
        path.lineTo(cx + s * 0.5f, cy)
        path.lineTo(cx + s * 0.22f, cy)
        path.lineTo(cx + s * 0.22f, cy + s * 0.45f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.45f)
        path.lineTo(cx - s * 0.22f, cy)
        path.lineTo(cx - s * 0.5f, cy)
        path.close()
        if (shiftState != ShiftState.OFF) {
            fill.color = KeyboardTheme.shiftActive
            canvas.drawPath(path, fill)
            icon.color = KeyboardTheme.shiftActive
        }
        canvas.drawPath(path, icon)
        if (shiftState == ShiftState.LOCKED) {
            canvas.drawLine(cx - s * 0.3f, cy + s * 0.65f, cx + s * 0.3f, cy + s * 0.65f, icon)
        }
    }

    private fun drawDelete(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx - s * 0.65f, cy)
        path.lineTo(cx - s * 0.3f, cy - s * 0.4f)
        path.lineTo(cx + s * 0.6f, cy - s * 0.4f)
        path.lineTo(cx + s * 0.6f, cy + s * 0.4f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.4f)
        path.close()
        canvas.drawPath(path, icon)
        val x = cx + s * 0.15f
        val d = s * 0.17f
        canvas.drawLine(x - d, cy - d, x + d, cy + d, icon)
        canvas.drawLine(x - d, cy + d, x + d, cy - d, icon)
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        when (enterAction) {
            EnterAction.SEARCH -> {
                canvas.drawCircle(cx - s * 0.12f, cy - s * 0.12f, s * 0.3f, icon)
                canvas.drawLine(cx + s * 0.1f, cy + s * 0.1f, cx + s * 0.4f, cy + s * 0.4f, icon)
            }
            EnterAction.DONE -> {
                path.reset()
                path.moveTo(cx - s * 0.4f, cy)
                path.lineTo(cx - s * 0.1f, cy + s * 0.3f)
                path.lineTo(cx + s * 0.45f, cy - s * 0.3f)
                canvas.drawPath(path, icon)
            }
            EnterAction.SEND -> {
                path.reset()
                path.moveTo(cx - s * 0.45f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.5f, cy)
                path.lineTo(cx - s * 0.45f, cy + s * 0.4f)
                path.lineTo(cx - s * 0.25f, cy)
                path.close()
                canvas.drawPath(path, icon)
            }
            EnterAction.GO, EnterAction.NEXT, EnterAction.PREVIOUS -> {
                val dir = if (enterAction == EnterAction.PREVIOUS) -1 else 1
                canvas.drawLine(cx - dir * s * 0.45f, cy, cx + dir * s * 0.45f, cy, icon)
                canvas.drawLine(cx + dir * s * 0.45f, cy, cx + dir * s * 0.15f, cy - s * 0.3f, icon)
                canvas.drawLine(cx + dir * s * 0.45f, cy, cx + dir * s * 0.15f, cy + s * 0.3f, icon)
            }
            EnterAction.NEWLINE -> {
                path.reset()
                path.moveTo(cx + s * 0.45f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.45f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.1f)
                canvas.drawPath(path, icon)
                canvas.drawLine(cx - s * 0.45f, cy + s * 0.1f, cx - s * 0.2f, cy - s * 0.15f, icon)
                canvas.drawLine(cx - s * 0.45f, cy + s * 0.1f, cx - s * 0.2f, cy + s * 0.35f, icon)
            }
        }
    }

    private fun drawSmiley(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, icon)
        fill.color = icon.color
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.25f, r * 0.11f, fill)
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.11f, fill)
        tmp.set(cx - r * 0.5f, cy - r * 0.35f, cx + r * 0.5f, cy + r * 0.55f)
        canvas.drawArc(tmp, 20f, 140f, false, icon)
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val old = icon.strokeWidth
        icon.strokeWidth = dp(1.2f)
        icon.color = color
        tmp.set(cx - s * 0.3f, cy - s * 0.6f, cx + s * 0.3f, cy + s * 0.25f)
        canvas.drawRoundRect(tmp, s * 0.3f, s * 0.3f, icon)
        tmp.set(cx - s * 0.55f, cy - s * 0.3f, cx + s * 0.55f, cy + s * 0.5f)
        canvas.drawArc(tmp, 0f, 180f, false, icon)
        canvas.drawLine(cx, cy + s * 0.5f, cx, cy + s * 0.75f, icon)
        icon.strokeWidth = old
    }

    private fun drawPopup(canvas: Canvas, p: Popup, radius: Float) {
        fill.color = KeyboardTheme.popup
        canvas.drawRoundRect(p.bounds, radius, radius, fill)
        stroke.color = KeyboardTheme.outline
        canvas.drawRoundRect(p.bounds, radius, radius, stroke)
        val sel = touches[p.owner]?.selected ?: 0
        p.options.forEachIndexed { i, opt ->
            val c = p.cells[i]
            if (i == sel) {
                fill.color = KeyboardTheme.popupSelected
                canvas.drawRoundRect(c, radius, radius, fill)
            }
            text.color = if (i == sel) KeyboardTheme.key else KeyboardTheme.label
            text.textSize = c.height() * 0.45f
            canvas.drawText(opt, c.centerX(), c.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    // ---- Touch handling ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast two-thumb typing: a new finger commits any pending tap of the other finger.
                touches.values.filter { it.mode == Mode.TAP }.forEach { t ->
                    handler.removeCallbacks(t.longPress)
                    release(t)
                    t.mode = Mode.CONSUMED
                }
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val kr = keyAt(e.getX(i), e.getY(i)) ?: return true
                val t = Touch(id, kr, e.getX(i), e.getY(i))
                touches[id] = t
                pressed += kr.key
                listener.onKeyDown(kr.key)
                if (kr.key.type == KeyType.DELETE) listener.onFunction(KeyType.DELETE)
                handler.postDelayed(t.longPress, LONG_PRESS)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val t = touches[e.getPointerId(i)] ?: continue
                    onMove(t, e.getX(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                touches.remove(id)?.let { finish(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                touches.values.forEach { cancel(it) }
                touches.clear()
                popup = null
                pressed.clear()
                invalidate()
            }
        }
        return true
    }

    private fun onMove(t: Touch, x: Float) {
        val dx = x - t.downX
        val key = t.key.key
        when (t.mode) {
            Mode.TAP -> when {
                key.type == KeyType.SPACE && abs(dx) > dp(28f) -> {
                    handler.removeCallbacks(t.longPress)
                    t.mode = if (spaceSwipeMovesCursor) Mode.CURSOR else Mode.SPACE_SWIPE
                    t.stepAnchorX = x
                }
                key.type == KeyType.DELETE && dx < -dp(36f) -> {
                    handler.removeCallbacks(t.longPress)
                    t.mode = Mode.DELETE_SWIPE
                    t.stepAnchorX = t.downX
                }
            }
            Mode.CURSOR -> {
                val step = dp(11f)
                val steps = ((x - t.stepAnchorX) / step).toInt()
                if (steps != 0) {
                    listener.onCursorMove(steps)
                    t.stepAnchorX += steps * step
                }
            }
            Mode.DELETE_SWIPE -> {
                val step = dp(36f)
                while (t.stepAnchorX - x > step) {
                    listener.onDeleteWord()
                    t.stepAnchorX -= step
                }
            }
            Mode.ALTERNATES -> popup?.let { p ->
                val idx = p.cells.indexOfFirst { x >= it.left && x <= it.right }
                val sel = when {
                    idx >= 0 -> idx
                    x < p.cells.first().left -> 0
                    else -> p.cells.lastIndex
                }
                if (sel != t.selected) { t.selected = sel; invalidate() }
            }
            Mode.REPEAT -> if (key.type == KeyType.DELETE && dx < -dp(36f)) {
                handler.removeCallbacks(t.repeat)
                t.mode = Mode.DELETE_SWIPE
                t.stepAnchorX = t.downX
            }
            else -> {}
        }
        t.lastX = x
    }

    private fun onLongPress(t: Touch) {
        if (t.mode != Mode.TAP) return
        val key = t.key.key
        when {
            key.type == KeyType.DELETE -> { t.mode = Mode.REPEAT; listener.onDeleteHeld(); handler.post(t.repeat) }
            key.type == KeyType.SPACE -> { t.mode = Mode.CURSOR; t.stepAnchorX = t.lastX; performHapticFeedback(HAPTIC_LONG) }
            key.type == KeyType.SHIFT -> { t.mode = Mode.CONSUMED; listener.onShiftLock() }
            key.type == KeyType.COMMA -> { t.mode = Mode.CONSUMED; listener.onVoice() }
            key.alternates.isNotEmpty() -> showAlternates(t)
        }
    }

    private fun showAlternates(t: Touch) {
        val kr = t.key
        val opts = kr.key.alternates.map { if (shiftState != ShiftState.OFF) applyShift(it) else it }
        val cellW = max(kr.rect.width(), dp(34f))
        val cellH = keyHeight * 0.95f
        val total = cellW * opts.size
        var left = kr.rect.centerX() - cellW / 2
        left = min(left, width - total - dp(2f)).coerceAtLeast(dp(2f))
        val top = (kr.rect.top - cellH - dp(4f)).coerceAtLeast(0f)
        val cells = opts.indices.map { i -> RectF(left + i * cellW, top, left + (i + 1) * cellW, top + cellH) }
        popup = Popup(t.id, opts, cells, RectF(left, top, left + total, top + cellH))
        t.mode = Mode.ALTERNATES
        t.selected = 0
        performHapticFeedback(HAPTIC_LONG)
        invalidate()
    }

    private fun finish(t: Touch) {
        handler.removeCallbacks(t.longPress)
        handler.removeCallbacks(t.repeat)
        when (t.mode) {
            Mode.TAP -> release(t)
            Mode.ALTERNATES -> popup?.let { p -> listener.onText(p.options[t.selected.coerceIn(0, p.options.lastIndex)]) }
            Mode.SPACE_SWIPE -> listener.onSpaceSwipe(if (t.lastX > t.downX) 1 else -1)
            else -> {}
        }
        if (popup?.owner == t.id) popup = null
        pressed.remove(t.key.key)
        invalidate()
    }

    private fun cancel(t: Touch) {
        handler.removeCallbacks(t.longPress)
        handler.removeCallbacks(t.repeat)
    }

    /** Emits the key's normal action for a completed tap. */
    private fun release(t: Touch) {
        val key = t.key.key
        when (key.type) {
            KeyType.CHAR -> listener.onText(displayText(key))
            KeyType.COMMA, KeyType.PERIOD, KeyType.SPACE -> listener.onText(key.text)
            KeyType.DELETE -> {} // fired on touch down
            else -> listener.onFunction(key.type)
        }
        pressed.remove(key)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        touches.values.forEach { cancel(it) }
        touches.clear()
        popup = null
        pressed.clear()
    }

    companion object {
        private const val LONG_PRESS = 350L
        private const val REPEAT_INTERVAL = 55L
        private const val WORD_REPEAT_AFTER = 18
        private const val WORD_REPEAT_INTERVAL = 160L
        private const val HAPTIC_LONG = android.view.HapticFeedbackConstants.LONG_PRESS
    }
}
