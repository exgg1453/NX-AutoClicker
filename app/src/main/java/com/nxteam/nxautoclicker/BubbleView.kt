package com.nxteam.nxautoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

class BubbleView(
    context: Context,
    private val onMoved: (Int, Int) -> Unit,
    private val onToggle: () -> Unit,
    private val onLongPress: () -> Unit
) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var downTime = 0L
    private var longPressFired = false

    var posX = 0
    var posY = 0
    var running = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressFired = true
            onLongPress()
        }
    }

    init {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 2f * density
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth
        val accent = if (running) Color.rgb(255, 82, 82) else Color.rgb(77, 208, 225)
        fillPaint.color = Color.argb(230, 18, 22, 28)
        ringPaint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
        iconPaint.color = Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        val size = radius * 0.45f
        if (running) {
            canvas.drawRect(cx - size, cy - size, cx + size, cy + size, iconPaint)
        } else {
            val path = Path()
            path.moveTo(cx - size * 0.7f, cy - size)
            path.lineTo(cx + size, cy)
            path.lineTo(cx - size * 0.7f, cy + size)
            path.close()
            canvas.drawPath(path, iconPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = posX
                startY = posY
                dragging = false
                longPressFired = false
                downTime = System.currentTimeMillis()
                postDelayed(longPressRunnable, 600L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val threshold = 8f * density
                if (!dragging && (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) onMoved((startX + dx).toInt(), (startY + dy).toInt())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (!dragging && !longPressFired) onToggle()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
