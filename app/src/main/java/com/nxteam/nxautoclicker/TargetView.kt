package com.nxteam.nxautoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class TargetView(
    context: Context,
    private val onMoved: (Int, Int) -> Unit
) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    var centerX = 0
    var centerY = 0
    var running = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    init {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 3f * density
        crossPaint.style = Paint.Style.STROKE
        crossPaint.strokeWidth = 2f * density
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth
        val accent = if (running) Color.rgb(255, 82, 82) else Color.rgb(77, 208, 225)
        fillPaint.color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
        ringPaint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
        crossPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.18f, ringPaint)
        canvas.drawLine(cx - radius * 0.7f, cy, cx - radius * 0.3f, cy, crossPaint)
        canvas.drawLine(cx + radius * 0.3f, cy, cx + radius * 0.7f, cy, crossPaint)
        canvas.drawLine(cx, cy - radius * 0.7f, cx, cy - radius * 0.3f, crossPaint)
        canvas.drawLine(cx, cy + radius * 0.3f, cx, cy + radius * 0.7f, crossPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = centerX
                startY = centerY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                onMoved((startX + dx).toInt(), (startY + dy).toInt())
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
