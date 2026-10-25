package com.smith.fireassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.smith.fireassist.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val i = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(i)
            toast("capture started")
        } else toast("capture cancelled")
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        b.btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.btnCapture.setOnClickListener {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mgr.createScreenCaptureIntent())
        }

        b.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                toast("overlay started")
            }
        }

        b.btnVpn.setOnClickListener {
            val prep = VpnGuardService.prepare(this)
            if (prep != null) {
                startActivityForResult(prep, 4242)
            } else {
                startService(Intent(this, VpnGuardService::class.java))
                toast("vpn starting")
            }
        }

        b.btnCalibrate.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                toast("grant overlay first, then tap again")
                return@setOnClickListener
            }
            startActivity(Intent(this, CalibrateActivity::class.java))
        }

        b.btnLaunch.setOnClickListener {
            val pkg = GameLauncher.findInstalled(this)
            if (pkg == null) { toast("no Free Fire installed"); return@setOnClickListener }
            GameLauncher.launch(this)
            // auto start overlay if granted
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }

        b.btnStop.setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            stopService(Intent(this, VpnGuardService::class.java))
            State.detectionActive = false
            toast("stopped")
        }

        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun refreshStatus() {
        val sb = StringBuilder()
        sb.append("accessibility: ")
        sb.append(if (AimbotService.instance != null) "on" else "off").append('\n')
        sb.append("overlay: ")
        sb.append(if (Settings.canDrawOverlays(this)) "granted" else "denied").append('\n')
        sb.append("capture: ")
        sb.append(if (State.detectionActive) "active" else "idle").append('\n')
        sb.append("fire btn: (${State.fireX},${State.fireY}) r=${State.fireRadius}").append('\n')
        sb.append("targets: ${State.targetCount}")
        b.tvStatus.text = sb.toString()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}