package com.nxteam.nxautoclicker

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(radius).toFloat()
        return drawable
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.textSize = 15f
        button.setTextColor(Color.WHITE)
        button.background = rounded(Color.rgb(24, 88, 100), 12)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        )
        params.setMargins(0, dp(8), 0, 0)
        button.layoutParams = params
        button.setOnClickListener { action() }
        return button
    }

    private fun makeLabel(text: String, size: Float, color: Int): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = size
        view.setTextColor(color)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(6), 0, 0)
        view.layoutParams = params
        return view
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.rgb(15, 17, 21))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))
        root.gravity = Gravity.TOP

        root.addView(makeLabel("NX Auto Clicker", 24f, Color.rgb(77, 208, 225)))
        root.addView(makeLabel("Yuvarlak hedefi nereye basmasini istiyorsan oraya surukle, baloncuktaki oynat tusuna bas.", 13f, Color.rgb(154, 164, 178)))

        root.addView(makeLabel("1. Erisilebilirlik servisi", 15f, Color.WHITE))
        accessibilityStatus = makeLabel("", 13f, Color.WHITE)
        root.addView(accessibilityStatus)
        root.addView(makeButton("Erisilebilirlik Ayarlarini Ac") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(makeLabel("2. Diger uygulamalar uzerinde goster", 15f, Color.WHITE))
        overlayStatus = makeLabel("", 13f, Color.WHITE)
        root.addView(overlayStatus)
        root.addView(makeButton("Ustte Gosterme Iznini Ac") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(makeLabel("3. Paneli baslat", 15f, Color.WHITE))
        root.addView(makeButton("Paneli Baslat") { startPanel() })
        root.addView(makeButton("Paneli Kapat") {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        })

        root.addView(
            makeLabel(
                "Kullanim: Baloncugu surukleyerek tasi, kisa dokunus baslatir ve durdurur, uzun dokunus ayarlari acar. Calisirken hedef halkasi dokunmalari gecirmez, oyun onu gormez.",
                12f,
                Color.rgb(154, 164, 178)
            )
        )

        root.addView(
            makeLabel(
                "Not: Baloncugu hedef halkasindan uzaga koy, ustuste gelirse dokunma baloncuga gider.",
                12f,
                Color.rgb(255, 167, 38)
            )
        )

        scroll.addView(root)
        return scroll
    }

    private fun startPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startService(Intent(this, OverlayService::class.java))
        moveTaskToBack(true)
    }

    private fun refreshStatus() {
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlayOk) "Hazir" else "Kapali"
        overlayStatus.setTextColor(if (overlayOk) Color.rgb(123, 228, 149) else Color.rgb(255, 138, 128))

        val accessibilityOk = isAccessibilityEnabled()
        accessibilityStatus.text = if (accessibilityOk) "Hazir" else "Kapali"
        accessibilityStatus.setTextColor(if (accessibilityOk) Color.rgb(123, 228, 149) else Color.rgb(255, 138, 128))
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (ClickerAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${ClickerAccessibilityService::class.java.name}")
    }
}
