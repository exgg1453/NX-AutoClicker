package com.nxteam.nxautoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

enum class IconType {
    PLAY,
    STOP,
    GEAR,
    CLOSE,
    ADD
}

class CircleButtonView(
    context: Context,
    iconType: IconType,
    private val onTap: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float, Float) -> Unit
) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density = resources.displayMetrics.density

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    var icon: IconType = iconType
        set(value) {
            field = value
            invalidate()
        }

    init {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 2f * density
        iconStrokePaint.style = Paint.Style.STROKE
        iconStrokePaint.strokeWidth = 2.6f * density
        iconStrokePaint.strokeCap = Paint.Cap.ROUND
    }

    private fun accentColor(): Int = when (icon) {
        IconType.STOP -> Color.rgb(255, 82, 82)
        IconType.PLAY -> Color.rgb(77, 208, 225)
        IconType.GEAR -> Color.rgb(178, 190, 204)
        IconType.CLOSE -> Color.rgb(255, 167, 38)
        IconType.ADD -> Color.rgb(123, 228, 149)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth
        val accent = accentColor()
        fillPaint.color = Color.argb(232, 18, 22, 28)
        ringPaint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
        iconPaint.color = Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent))
        iconStrokePaint.color = iconPaint.color
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        val size = radius * 0.44f
        when (icon) {
            IconType.PLAY -> {
                val path = Path()
                path.moveTo(cx - size * 0.65f, cy - size)
                path.lineTo(cx + size, cy)
                path.lineTo(cx - size * 0.65f, cy + size)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            IconType.STOP -> {
                canvas.drawRect(cx - size * 0.85f, cy - size * 0.85f, cx + size * 0.85f, cy + size * 0.85f, iconPaint)
            }
            IconType.CLOSE -> {
                canvas.drawLine(cx - size, cy - size, cx + size, cy + size, iconStrokePaint)
                canvas.drawLine(cx + size, cy - size, cx - size, cy + size, iconStrokePaint)
            }
            IconType.ADD -> {
                canvas.drawLine(cx - size, cy, cx + size, cy, iconStrokePaint)
                canvas.drawLine(cx, cy - size, cx, cy + size, iconStrokePaint)
            }
            IconType.GEAR -> {
                canvas.drawCircle(cx, cy, size * 0.62f, iconStrokePaint)
                val inner = size * 0.95f
                val outer = size * 1.45f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val sx = cx + (Math.cos(angle) * inner).toFloat()
                    val sy = cy + (Math.sin(angle) * inner).toFloat()
                    val ex = cx + (Math.cos(angle) * outer).toFloat()
                    val ey = cy + (Math.sin(angle) * outer).toFloat()
                    canvas.drawLine(sx, sy, ex, ey, iconStrokePaint)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                onDragStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val threshold = 8f * density
                if (!dragging && (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)) {
                    dragging = true
                }
                if (dragging) onDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) onTap()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
