package com.nxteam.nxautoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var targetView: TargetView? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var bubbleView: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var settingsView: View? = null

    private var running = false
    private var intervalMs = 100L
    private var pressMs = 40L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        intervalMs = prefs.getLong(KEY_INTERVAL, 100L)
        pressMs = prefs.getLong(KEY_PRESS, 40L)
        startForegroundInternal()
        createTarget()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun startForegroundInternal() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NX Auto Clicker", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("NX Auto Clicker")
            .setContentText("Panel acik")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    "Kapat",
                    stopIntent
                ).build()
            )
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createTarget() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics
        val size = dp(TARGET_DP)
        val view = TargetView(this) { x, y -> moveTarget(x, y) }
        view.centerX = prefs.getInt(KEY_TARGET_X, metrics.widthPixels / 2)
        view.centerY = prefs.getInt(KEY_TARGET_Y, metrics.heightPixels / 2)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = view.centerX - size / 2
        params.y = view.centerY - size / 2
        targetView = view
        targetParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun moveTarget(x: Int, y: Int) {
        val view = targetView ?: return
        val params = targetParams ?: return
        view.centerX = x
        view.centerY = y
        params.x = x - view.width / 2
        params.y = y - view.height / 2
        runCatching { windowManager.updateViewLayout(view, params) }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TARGET_X, x)
            .putInt(KEY_TARGET_Y, y)
            .apply()
    }

    private fun createBubble() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics
        val size = dp(BUBBLE_DP)
        val view = BubbleView(
            this,
            onMoved = { x, y -> moveBubble(x, y) },
            onToggle = { toggleRun() },
            onLongPress = { openSettings() }
        )
        view.posX = prefs.getInt(KEY_BUBBLE_X, dp(16))
        view.posY = prefs.getInt(KEY_BUBBLE_Y, metrics.heightPixels / 3)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = view.posX
        params.y = view.posY
        bubbleView = view
        bubbleParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun moveBubble(x: Int, y: Int) {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        view.posX = x
        view.posY = y
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(view, params) }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .apply()
    }

    private fun toggleRun() {
        if (running) {
            running = false
            handler.removeCallbacksAndMessages(null)
        } else {
            if (ClickerAccessibilityService.instance == null) {
                android.widget.Toast
                    .makeText(this, "Once erisilebilirlik servisini ac", android.widget.Toast.LENGTH_LONG)
                    .show()
                return
            }
            closeSettings()
            running = true
            handler.post { dispatchTap() }
        }
        targetView?.running = running
        bubbleView?.running = running
        applyTargetTouchable()
    }

    private fun applyTargetTouchable() {
        val view = targetView ?: return
        val params = targetParams ?: return
        params.flags = if (running) {
            baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags()
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dispatchTap() {
        if (!running) return
        val service = ClickerAccessibilityService.instance
        if (service == null) {
            running = false
            targetView?.running = false
            bubbleView?.running = false
            applyTargetTouchable()
            return
        }
        val view = targetView ?: return
        val x = view.centerX.toFloat()
        val y = view.centerY.toFloat()

        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y)
        val stroke = GestureDescription.StrokeDescription(path, 0L, pressMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scheduleNext(intervalMs)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scheduleNext(intervalMs)
            }
        }

        val dispatched = runCatching {
            service.dispatchGesture(gesture, callback, handler)
        }.getOrDefault(false)

        if (!dispatched) {
            scheduleNext(200L)
        }
    }

    private fun scheduleNext(delay: Long) {
        if (!running) return
        handler.postDelayed({ dispatchTap() }, delay.coerceAtLeast(1L))
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(radius).toFloat()
        drawable.setStroke(dp(1), Color.argb(70, 255, 255, 255))
        return drawable
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.setTextColor(Color.WHITE)
        button.textSize = 13f
        button.background = roundedBackground(Color.argb(235, 32, 38, 48), 10)
        button.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(0, dp(42), 1f)
        params.setMargins(dp(3), dp(6), dp(3), dp(2))
        button.layoutParams = params
        return button
    }

    private fun closeSettings() {
        val view = settingsView ?: return
        runCatching { windowManager.removeView(view) }
        settingsView = null
    }

    private fun openSettings() {
        if (settingsView != null) {
            closeSettings()
            return
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = roundedBackground(Color.argb(242, 15, 17, 21), 16)
        root.setPadding(dp(16), dp(14), dp(16), dp(12))

        val title = TextView(this)
        title.text = "Ayarlar"
        title.setTextColor(Color.WHITE)
        title.textSize = 16f
        root.addView(title)

        val intervalLabel = TextView(this)
        intervalLabel.setTextColor(Color.argb(210, 255, 255, 255))
        intervalLabel.textSize = 13f
        intervalLabel.text = "Aralik: $intervalMs ms"
        root.addView(intervalLabel)

        val intervalBar = SeekBar(this)
        intervalBar.max = 2000
        intervalBar.progress = intervalMs.toInt().coerceIn(10, 2000)
        intervalBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalMs = progress.coerceAtLeast(10).toLong()
                intervalLabel.text = "Aralik: $intervalMs ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_INTERVAL, intervalMs)
                    .apply()
            }
        })
        root.addView(intervalBar)

        val pressLabel = TextView(this)
        pressLabel.setTextColor(Color.argb(210, 255, 255, 255))
        pressLabel.textSize = 13f
        pressLabel.text = "Basma suresi: $pressMs ms"
        root.addView(pressLabel)

        val pressBar = SeekBar(this)
        pressBar.max = 500
        pressBar.progress = pressMs.toInt().coerceIn(1, 500)
        pressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                pressMs = progress.coerceAtLeast(1).toLong()
                pressLabel.text = "Basma suresi: $pressMs ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_PRESS, pressMs)
                    .apply()
            }
        })
        root.addView(pressBar)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(makeButton("Kapat") { closeSettings() })
        row.addView(makeButton("Paneli Kapat") { stopSelf() })
        root.addView(row)

        val params = WindowManager.LayoutParams(
            dp(290),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        settingsView = root
        runCatching { windowManager.addView(root, params) }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        closeSettings()
        targetView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        targetView = null
        bubbleView = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nxteam.nxautoclicker.STOP"
        private const val CHANNEL_ID = "nx_autoclicker"
        private const val NOTIFICATION_ID = 4411
        private const val TARGET_DP = 72
        private const val BUBBLE_DP = 48
        private const val PREFS = "nx_autoclicker"
        private const val KEY_TARGET_X = "target_x"
        private const val KEY_TARGET_Y = "target_y"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_PRESS = "press"
    }
}
