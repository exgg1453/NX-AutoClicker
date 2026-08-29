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
import org.json.JSONArray
import org.json.JSONObject

class OverlayService : Service() {

    private class TargetItem(val view: TargetView, val params: WindowManager.LayoutParams)

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val targets = mutableListOf<TargetItem>()
    private var columnView: LinearLayout? = null
    private var columnParams: WindowManager.LayoutParams? = null
    private var playButton: CircleButtonView? = null
    private var settingsView: View? = null
    private var targetMenuView: View? = null

    private var dragOriginX = 0
    private var dragOriginY = 0

    private var running = false
    private var intervalMs = 100L
    private var pressMs = 40L
    private var clickLimit = 0
    private var clickCount = 0
    private var targetSizeDp = DEFAULT_TARGET_DP
    private var sequenceIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        intervalMs = prefs.getLong(KEY_INTERVAL, 100L)
        pressMs = prefs.getLong(KEY_PRESS, 40L)
        clickLimit = prefs.getInt(KEY_LIMIT, 0)
        targetSizeDp = prefs.getInt(KEY_TARGET_SIZE, DEFAULT_TARGET_DP)
        startForegroundInternal()
        restoreTargets()
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

    private fun restoreTargets() {
        val raw = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGETS, null)
        val positions = mutableListOf<Pair<Int, Int>>()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    positions.add(Pair(item.getInt("x"), item.getInt("y")))
                }
            }
        }
        if (positions.isEmpty()) {
            val metrics = resources.displayMetrics
            positions.add(Pair(metrics.widthPixels / 2, metrics.heightPixels / 2))
        }
        positions.forEach { addTarget(it.first, it.second, persist = false) }
        saveTargets()
    }

    private fun saveTargets() {
        val array = JSONArray()
        targets.forEach { item ->
            val obj = JSONObject()
            obj.put("x", item.view.centerX)
            obj.put("y", item.view.centerY)
            array.put(obj)
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGETS, array.toString())
            .apply()
    }

    private fun addTarget(x: Int, y: Int, persist: Boolean = true) {
        if (targets.size >= MAX_TARGETS) {
            Toast.makeText(this, "En fazla $MAX_TARGETS nokta eklenebilir", Toast.LENGTH_SHORT).show()
            return
        }
        val size = dp(targetSizeDp)
        lateinit var item: TargetItem
        val view = TargetView(
            this,
            onMoved = { newX, newY -> moveTarget(item, newX, newY) },
            onTapped = { openTargetMenu(item) }
        )
        view.centerX = x
        view.centerY = y
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x - size / 2
        params.y = y - size / 2
        item = TargetItem(view, params)
        targets.add(item)
        runCatching { windowManager.addView(view, params) }
        relabelTargets()
        if (persist) saveTargets()
    }

    private fun addTargetNearCenter() {
        if (running) stopClicking()
        val metrics = resources.displayMetrics
        val offset = dp(28) * targets.size
        val x = (metrics.widthPixels / 2 + offset).coerceIn(dp(40), metrics.widthPixels - dp(40))
        val y = (metrics.heightPixels / 2 + offset).coerceIn(dp(40), metrics.heightPixels - dp(40))
        addTarget(x, y)
    }

    private fun relabelTargets() {
        targets.forEachIndexed { index, item -> item.view.label = index + 1 }
    }

    private fun moveTarget(item: TargetItem, x: Int, y: Int) {
        item.view.centerX = x
        item.view.centerY = y
        item.params.x = x - item.view.width / 2
        item.params.y = y - item.view.height / 2
        runCatching { windowManager.updateViewLayout(item.view, item.params) }
        saveTargets()
    }

    private fun removeTarget(item: TargetItem) {
        if (targets.size <= 1) {
            Toast.makeText(this, "En az bir nokta kalmalı", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { windowManager.removeView(item.view) }
        targets.remove(item)
        relabelTargets()
        saveTargets()
    }

    private fun applyTargetSize() {
        val size = dp(targetSizeDp)
        targets.forEach { item ->
            item.params.width = size
            item.params.height = size
            item.params.x = item.view.centerX - size / 2
            item.params.y = item.view.centerY - size / 2
            runCatching { windowManager.updateViewLayout(item.view, item.params) }
            item.view.invalidate()
        }
    }

    private fun applyTargetTouchable() {
        targets.forEach { item ->
            item.params.flags = if (running) {
                baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                baseFlags()
            }
            item.view.running = running
            if (!running) item.view.active = false
            runCatching { windowManager.updateViewLayout(item.view, item.params) }
        }
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
        column.addView(makeCircleButton(IconType.ADD) { addTargetNearCenter() })
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
        params.y = prefs.getInt(KEY_COLUMN_Y, metrics.heightPixels / 5)

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
            if (targets.isEmpty()) return
            closeSettings()
            closeTargetMenu()
            clickCount = 0
            sequenceIndex = 0
            running = true
            playButton?.icon = IconType.STOP
            applyTargetTouchable()
            handler.post { dispatchTap() }
        }
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacksAndMessages(null)
        playButton?.icon = IconType.PLAY
        applyTargetTouchable()
    }

    private fun dispatchTap() {
        if (!running) return
        val service = ClickerAccessibilityService.instance
        if (service == null) {
            stopClicking()
            return
        }
        if (targets.isEmpty()) {
            stopClicking()
            return
        }
        if (sequenceIndex >= targets.size) sequenceIndex = 0
        val item = targets[sequenceIndex]

        targets.forEach { it.view.active = false }
        item.view.active = true

        val x = item.view.centerX.toFloat()
        val y = item.view.centerY.toFloat()

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
        sequenceIndex = if (targets.isEmpty()) 0 else (sequenceIndex + 1) % targets.size
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
            .putInt(KEY_TARGET_SIZE, targetSizeDp)
            .apply()
    }

    private fun closeTargetMenu() {
        val view = targetMenuView ?: return
        runCatching { windowManager.removeView(view) }
        targetMenuView = null
    }

    private fun openTargetMenu(item: TargetItem) {
        if (running) return
        closeTargetMenu()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = roundedBackground(Color.argb(244, 15, 17, 21), 14)
        root.setPadding(dp(16), dp(12), dp(16), dp(12))

        root.addView(makeText("Nokta ${item.view.label}", 16f, Color.WHITE))
        root.addView(
            makeText(
                "Sıra: ${item.view.label}. tıklama. Noktayı sürükleyerek taşıyabilirsin.",
                12f,
                Color.rgb(154, 164, 178)
            )
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(makeButton("Sil") {
            removeTarget(item)
            closeTargetMenu()
        })
        row.addView(makeButton("Kapat") { closeTargetMenu() })
        root.addView(row)

        val params = WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        targetMenuView = root
        runCatching { windowManager.addView(root, params) }
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
        closeTargetMenu()

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
        title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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

        root.addView(
            makeText(
                "Nokta sayısı: ${targets.size}. Tıklamalar 1'den başlayıp sırayla ilerler, son noktadan sonra başa döner.",
                12f,
                Color.rgb(123, 228, 149)
            )
        )

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

        val sizeLabel = makeText("Hedef boyutu: $targetSizeDp dp", 13f, Color.argb(220, 255, 255, 255))
        root.addView(sizeLabel)

        val sizeBar = SeekBar(this)
        sizeBar.max = 140
        sizeBar.progress = (targetSizeDp - 28).coerceIn(0, 140)
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                targetSizeDp = progress + 28
                sizeLabel.text = "Hedef boyutu: $targetSizeDp dp"
                applyTargetSize()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePrefs()
            }
        })
        root.addView(sizeBar)

        root.addView(
            makeText(
                "Yeni nokta için artı tuşuna bas. Bir noktaya kısa dokunursan silme menüsü açılır.",
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
            targetSizeDp = DEFAULT_TARGET_DP
            applyTargetSize()
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
        closeTargetMenu()
        targets.forEach { runCatching { windowManager.removeView(it.view) } }
        targets.clear()
        columnView?.let { runCatching { windowManager.removeView(it) } }
        columnView = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nxteam.nxautoclicker.STOP"
        private const val CHANNEL_ID = "nx_autoclicker"
        private const val NOTIFICATION_ID = 4411
        private const val DEFAULT_TARGET_DP = 72
        private const val BUTTON_DP = 48
        private const val MAX_TARGETS = 20
        private const val PREFS = "nx_autoclicker"
        private const val KEY_TARGETS = "targets"
        private const val KEY_COLUMN_X = "column_x"
        private const val KEY_COLUMN_Y = "column_y"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_PRESS = "press"
        private const val KEY_LIMIT = "limit"
        private const val KEY_TARGET_SIZE = "target_size"
    }
}
