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
    private val onMoved: (Int, Int) -> Unit,
    private val onTapped: () -> Unit
) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private val density = resources.displayMetrics.density

    var centerX = 0
    var centerY = 0

    var label: Int = 1
        set(value) {
            field = value
            invalidate()
        }

    var running = false
        set(value) {
            field = value
            invalidate()
        }

    var active = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        ringPaint.style = Paint.Style.STROKE
        crossPaint.style = Paint.Style.STROKE
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val strokeWidth = (minOf(width, height) / 24f).coerceIn(2f * density, 4f * density)
        ringPaint.strokeWidth = strokeWidth
        crossPaint.strokeWidth = strokeWidth * 0.7f
        val radius = minOf(width, height) / 2f - strokeWidth

        val accent = when {
            active -> Color.rgb(123, 228, 149)
            running -> Color.rgb(255, 82, 82)
            else -> Color.rgb(77, 208, 225)
        }

        fillPaint.color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
        ringPaint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
        crossPaint.color = Color.argb(200, 255, 255, 255)

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.18f, ringPaint)
        canvas.drawLine(cx - radius * 0.7f, cy, cx - radius * 0.34f, cy, crossPaint)
        canvas.drawLine(cx + radius * 0.34f, cy, cx + radius * 0.7f, cy, crossPaint)
        canvas.drawLine(cx, cy - radius * 0.7f, cx, cy - radius * 0.34f, crossPaint)
        canvas.drawLine(cx, cy + radius * 0.34f, cx, cy + radius * 0.7f, crossPaint)

        val badgeRadius = (radius * 0.34f).coerceAtLeast(9f * density)
        val badgeX = cx
        val badgeY = cy - radius + badgeRadius * 0.2f
        labelBackPaint.color = Color.argb(235, 15, 17, 21)
        canvas.drawCircle(badgeX, badgeY, badgeRadius, labelBackPaint)
        ringPaint.strokeWidth = strokeWidth * 0.6f
        canvas.drawCircle(badgeX, badgeY, badgeRadius, ringPaint)
        labelPaint.color = Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent))
        labelPaint.textSize = badgeRadius * 1.25f
        canvas.drawText(label.toString(), badgeX, badgeY + labelPaint.textSize / 3f, labelPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = centerX
                startY = centerY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val threshold = 8f * density
                if (!dragging && (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)) {
                    dragging = true
                }
                if (dragging) onMoved((startX + dx).toInt(), (startY + dy).toInt())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) onTapped()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
