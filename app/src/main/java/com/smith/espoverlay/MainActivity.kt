package com.smith.espoverlay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var pkgGroup: RadioGroup
    private var selectedPkg = ""

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val vpnPrepare = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) startVpnService()
        else toast("VPN permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            setBackgroundColor(0xFF0F0F1A.toInt())
        }

        root.addView(TextView(this).apply {
            text = "ESP Overlay v2"
            setTextColor(0xFFE94560.toInt())
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "เลือกเกม"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            setPadding(0, 32, 0, 8)
        })

        pkgGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val packages = detectGames()
        if (packages.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "ไม่พบ Free Fire / Free Fire MAX\nติดตั้งเกมก่อน"
                setTextColor(0xFFFF8888.toInt())
            })
        } else {
            packages.forEachIndexed { idx, p ->
                val rb = RadioButton(this).apply {
                    text = p.second
                    setTextColor(0xFFEEEEEE.toInt())
                    id = idx + 1
                    tag = p.first
                    isChecked = (Prefs.selectedPackage == p.first) ||
                            (Prefs.selectedPackage.isEmpty() && idx == 0)
                }
                pkgGroup.addView(rb)
                if (rb.isChecked) selectedPkg = p.first
            }
            pkgGroup.setOnCheckedChangeListener { group, checkedId ->
                val rb = group.findViewById<RadioButton>(checkedId)
                selectedPkg = rb?.tag as? String ?: ""
                Prefs.selectedPackage = selectedPkg
            }
        }
        root.addView(pkgGroup)

        status = TextView(this).apply {
            text = statusText()
            setTextColor(0xFF88FFAA.toInt())
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        root.addView(status)

        fun btn(label: String, color: Int, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setBackgroundColor(color)
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 })
        }

        btn("1. ขอสิทธิ์ Overlay", 0xFF1A1A2E.toInt()) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else toast("Overlay พร้อมแล้ว")
        }

        btn("2. เปิด VPN + เข้าเกม", 0xFFE94560.toInt()) {
            if (!Settings.canDrawOverlays(this)) {
                toast("ขอ Overlay ก่อน")
                return@btn
            }
            if (selectedPkg.isEmpty() && packages.isNotEmpty()) {
                selectedPkg = packages[0].first
                Prefs.selectedPackage = selectedPkg
            }
            if (!OverlayService.running) {
                ContextCompat.startForegroundService(
                    this, Intent(this, OverlayService::class.java)
                )
            }
            val prep = VpnService.prepare(this)
            if (prep != null) vpnPrepare.launch(prep)
            else startVpnService()

            if (selectedPkg.isNotEmpty()) {
                try {
                    packageManager.getLaunchIntentForPackage(selectedPkg)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                } catch (t: Throwable) {
                    toast("เปิดเกมไม่สำเร็จ: ${t.message}")
                }
            }
        }

        btn("หยุดทั้งหมด", 0xFF3A1A1A.toInt()) {
            stopService(Intent(this, OverlayService::class.java).apply { action = "STOP" })
            stopService(Intent(this, EspVpnService::class.java).apply { action = "STOP" })
            status.text = statusText()
            toast("หยุดแล้ว")
        }

        root.addView(TextView(this).apply {
            text = "\nESP เปิดเป็นค่าเริ่มต้น\nกด Reset Match หลังจบเกมทุกครั้ง\nเมนูลอย: ย่อ / ปิด"
            setTextColor(0xFF666688.toInt())
            textSize = 12f
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) status.text = statusText()
    }

    private fun startVpnService() {
        ContextCompat.startForegroundService(this, Intent(this, EspVpnService::class.java))
        status.text = statusText()
        toast("VPN เริ่มทำงาน")
    }

    private fun detectGames(): List<Pair<String, String>> {
        val candidates = listOf(
            "com.dts.freefireth" to "Free Fire",
            "com.dts.freefiremax" to "Free Fire MAX",
            "com.garena.game.kgvn" to "Free Fire (VN)"
        )
        val pm = packageManager
        return candidates.filter {
            try {
                pm.getPackageInfo(it.first, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun statusText(): String {
        val vpn = if (EspVpnService.running) "VPN: ON" else "VPN: OFF"
        val ov = if (OverlayService.running) "Overlay: ON" else "Overlay: OFF"
        val pkg = Prefs.selectedPackage.ifEmpty { "—" }
        return "$vpn  |  $ov\npkg: $pkg"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
