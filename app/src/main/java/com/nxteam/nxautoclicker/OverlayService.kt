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
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var targetView: TargetView? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var columnView: LinearLayout? = null
    private var columnParams: WindowManager.LayoutParams? = null
    private var playButton: CircleButtonView? = null
    private var settingsView: View? = null

    private var dragOriginX = 0
    private var dragOriginY = 0

    private var running = false
    private var intervalMs = 100L
    private var pressMs = 40L
    private var clickLimit = 0
    private var clickCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        intervalMs = prefs.getLong(KEY_INTERVAL, 100L)
        pressMs = prefs.getLong(KEY_PRESS, 40L)
        clickLimit = prefs.getInt(KEY_LIMIT, 0)
        startForegroundInternal()
        createTarget()
        createColumn()
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("NX Auto Clicker")
            .setContentText("Panel açık")
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

    private fun makeCircleButton(icon: IconType, onTap: () -> Unit): CircleButtonView {
        val view = CircleButtonView(
            this,
            icon,
            onTap = onTap,
            onDragStart = { snapshotColumnPosition() },
            onDrag = { dx, dy -> dragColumn(dx, dy) }
        )
        val size = dp(BUTTON_DP)
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(0, dp(5), 0, dp(5))
        view.layoutParams = params
        return view
    }

    private fun createColumn() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL

        val play = makeCircleButton(IconType.PLAY) { toggleRun() }
        playButton = play
        column.addView(play)
        column.addView(makeCircleButton(IconType.GEAR) { toggleSettings() })
        column.addView(makeCircleButton(IconType.CLOSE) { stopSelf() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt(KEY_COLUMN_X, dp(12))
        params.y = prefs.getInt(KEY_COLUMN_Y, metrics.heightPixels / 4)

        columnView = column
        columnParams = params
        runCatching { windowManager.addView(column, params) }
    }

    private fun snapshotColumnPosition() {
        val params = columnParams ?: return
        dragOriginX = params.x
        dragOriginY = params.y
    }

    private fun dragColumn(dx: Float, dy: Float) {
        val view = columnView ?: return
        val params = columnParams ?: return
        params.x = dragOriginX + dx.toInt()
        params.y = dragOriginY + dy.toInt()
        runCatching { windowManager.updateViewLayout(view, params) }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COLUMN_X, params.x)
            .putInt(KEY_COLUMN_Y, params.y)
            .apply()
    }

    private fun toggleRun() {
        if (running) {
            stopClicking()
        } else {
            if (ClickerAccessibilityService.instance == null) {
                Toast.makeText(this, "Önce erişilebilirlik servisini açın", Toast.LENGTH_LONG).show()
                return
            }
            closeSettings()
            clickCount = 0
            running = true
            playButton?.icon = IconType.STOP
            targetView?.running = true
            applyTargetTouchable()
            handler.post { dispatchTap() }
        }
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacksAndMessages(null)
        playButton?.icon = IconType.PLAY
        targetView?.running = false
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
            stopClicking()
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
                afterTap()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                afterTap()
            }
        }

        val dispatched = runCatching {
            service.dispatchGesture(gesture, callback, handler)
        }.getOrDefault(false)

        if (!dispatched) {
            scheduleNext(200L)
        }
    }

    private fun afterTap() {
        if (!running) return
        clickCount++
        if (clickLimit > 0 && clickCount >= clickLimit) {
            stopClicking()
            Toast.makeText(this, "$clickLimit tıklama tamamlandı", Toast.LENGTH_SHORT).show()
            return
        }
        scheduleNext(intervalMs)
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
        val params = LinearLayout.LayoutParams(0, dp(44), 1f)
        params.setMargins(dp(3), dp(8), dp(3), dp(2))
        button.layoutParams = params
        return button
    }

    private fun makeText(text: String, size: Float, color: Int): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = size
        view.setTextColor(color)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(8), 0, 0)
        view.layoutParams = params
        return view
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERVAL, intervalMs)
            .putLong(KEY_PRESS, pressMs)
            .putInt(KEY_LIMIT, clickLimit)
            .apply()
    }

    private fun toggleSettings() {
        if (settingsView != null) closeSettings() else openSettings()
    }

    private fun closeSettings() {
        val view = settingsView ?: return
        runCatching { windowManager.removeView(view) }
        settingsView = null
    }

    private fun cpsText(): String {
        val perSecond = 1000.0 / (intervalMs + pressMs).coerceAtLeast(1L)
        return String.format("%.1f", perSecond)
    }

    private fun openSettings() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = roundedBackground(Color.argb(244, 15, 17, 21), 16)
        root.setPadding(dp(18), dp(14), dp(18), dp(14))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = "Ayarlar"
        title.setTextColor(Color.WHITE)
        title.textSize = 17f
        val titleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        title.layoutParams = titleParams
        header.addView(title)

        val closeIcon = CircleButtonView(
            this,
            IconType.CLOSE,
            onTap = { closeSettings() },
            onDragStart = { },
            onDrag = { _, _ -> }
        )
        closeIcon.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        header.addView(closeIcon)
        root.addView(header)

        val speedLabel = makeText("Hız: saniyede ${cpsText()} tıklama", 13f, Color.rgb(77, 208, 225))
        root.addView(speedLabel)

        val intervalLabel = makeText("Tıklama aralığı: $intervalMs ms", 13f, Color.argb(220, 255, 255, 255))
        root.addView(intervalLabel)

        val intervalBar = SeekBar(this)
        intervalBar.max = 2000
        intervalBar.progress = intervalMs.toInt().coerceIn(10, 2000)
        intervalBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalMs = progress.coerceAtLeast(10).toLong()
                intervalLabel.text = "Tıklama aralığı: $intervalMs ms"
                speedLabel.text = "Hız: saniyede ${cpsText()} tıklama"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePrefs()
            }
        })
        root.addView(intervalBar)

        val pressLabel = makeText("Basılı tutma süresi: $pressMs ms", 13f, Color.argb(220, 255, 255, 255))
        root.addView(pressLabel)

        val pressBar = SeekBar(this)
        pressBar.max = 500
        pressBar.progress = pressMs.toInt().coerceIn(1, 500)
        pressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                pressMs = progress.coerceAtLeast(1).toLong()
                pressLabel.text = "Basılı tutma süresi: $pressMs ms"
                speedLabel.text = "Hız: saniyede ${cpsText()} tıklama"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePrefs()
            }
        })
        root.addView(pressBar)

        val limitLabel = makeText(
            if (clickLimit > 0) "Tıklama sayısı: $clickLimit" else "Tıklama sayısı: sınırsız",
            13f,
            Color.argb(220, 255, 255, 255)
        )
        root.addView(limitLabel)

        val limitBar = SeekBar(this)
        limitBar.max = 1000
        limitBar.progress = clickLimit.coerceIn(0, 1000)
        limitBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                clickLimit = progress
                limitLabel.text = if (clickLimit > 0) {
                    "Tıklama sayısı: $clickLimit"
                } else {
                    "Tıklama sayısı: sınırsız"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePrefs()
            }
        })
        root.addView(limitBar)

        root.addView(
            makeText(
                "Hedef halkasını basılmasını istediğin yere sürükle. Çalışırken halka dokunmaları geçirmez.",
                11f,
                Color.rgb(154, 164, 178)
            )
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(makeButton("Sıfırla") {
            intervalMs = 100L
            pressMs = 40L
            clickLimit = 0
            savePrefs()
            closeSettings()
            openSettings()
        })
        row.addView(makeButton("Tamam") { closeSettings() })
        root.addView(row)

        val params = WindowManager.LayoutParams(
            dp(300),
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
        columnView?.let { runCatching { windowManager.removeView(it) } }
        targetView = null
        columnView = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nxteam.nxautoclicker.STOP"
        private const val CHANNEL_ID = "nx_autoclicker"
        private const val NOTIFICATION_ID = 4411
        private const val TARGET_DP = 72
        private const val BUTTON_DP = 48
        private const val PREFS = "nx_autoclicker"
        private const val KEY_TARGET_X = "target_x"
        private const val KEY_TARGET_Y = "target_y"
        private const val KEY_COLUMN_X = "column_x"
        private const val KEY_COLUMN_Y = "column_y"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_PRESS = "press"
        private const val KEY_LIMIT = "limit"
    }
}
