package com.smith.espoverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class OverlayService : Service() {

    companion object {
        private const val CH = "esp_overlay_ch"
        private const val NOTIF_ID = 1002
        private val latestSnap = AtomicReference<EspEntityStore.Snapshot?>(null)
        fun pushSnapshot(snap: EspEntityStore.Snapshot) { latestSnap.set(snap) }
        @Volatile var running = false
            private set
    }

    private lateinit var wm: WindowManager
    private var menuView: View? = null
    private var drawView: DrawView? = null
    private var minimized = false
    private var bubble: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var drawJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        running = true
        minimized = Prefs.menuMinimized
        addDrawLayer()
        addMenu()
        drawJob = scope.launch {
            while (isActive) {
                drawView?.invalidate()
                delay(16)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        drawJob?.cancel()
        scope.cancel()
        removeViews()
        super.onDestroy()
    }

    private fun removeViews() {
        try { menuView?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { drawView?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        menuView = null; drawView = null; bubble = null
    }

    private fun addDrawLayer() {
        drawView = DrawView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        wm.addView(drawView, params)
    }

    private fun addMenu() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE012121E.toInt())
            setPadding(24, 20, 24, 20)
            elevation = 12f
        }
        root.addView(TextView(ctx).apply {
            text = "ESP MENU"
            setTextColor(0xFFE94560.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        row.addView(TextView(ctx).apply {
            text = "ESP"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(ctx).apply {
            isChecked = Prefs.espEnabled
            setOnCheckedChangeListener { _, v -> Prefs.espEnabled = v }
        })
        root.addView(row)

        root.addView(Button(ctx).apply {
            text = "Reset Match"
            setBackgroundColor(0xFF2A2A3E.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                startService(Intent(ctx, EspVpnService::class.java).apply { action = "RESET_MATCH" })
                Toast.makeText(ctx, "Match reset", Toast.LENGTH_SHORT).show()
            }
        })

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        btnRow.addView(Button(ctx).apply {
            text = "ย่อ"
            setBackgroundColor(0xFF1A1A2E.toInt())
            setTextColor(0xFFAAAAAA.toInt())
            setOnClickListener { minimizeMenu() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        })
        btnRow.addView(Button(ctx).apply {
            text = "ปิด"
            setBackgroundColor(0xFF3A1A1A.toInt())
            setTextColor(0xFFFF6666.toInt())
            setOnClickListener {
                stopSelf()
                stopService(Intent(ctx, EspVpnService::class.java).apply { action = "STOP" })
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(btnRow)

        val params = WindowManager.LayoutParams(
            dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20; params.y = 120

        var lastX = 0f; var lastY = 0f
        root.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x -= (e.rawX - lastX).toInt()
                    params.y += (e.rawY - lastY).toInt()
                    lastX = e.rawX; lastY = e.rawY
                    wm.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }
        menuView = root
        if (!minimized) wm.addView(root, params) else showMiniBubble()
    }

    private fun minimizeMenu() {
        minimized = true
        Prefs.menuMinimized = true
        try { menuView?.let { wm.removeView(it) } } catch (_: Exception) {}
        showMiniBubble()
    }

    private fun showMiniBubble() {
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        val b = TextView(this).apply {
            text = "ESP"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(0xE0E94560.toInt())
            setPadding(28, 20, 28, 20)
            setOnClickListener {
                try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
                bubble = null
                minimized = false
                Prefs.menuMinimized = false
                addMenu()
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.END
        p.x = 20; p.y = 120
        bubble = b
        wm.addView(b, p)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class DrawView(ctx: Context) : View(ctx) {
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        private val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!Prefs.espEnabled) return
            val snap = latestSnap.get() ?: return
            val local = snap.localPlayer ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            val yaw = if (snap.yawRadians.isFinite()) snap.yawRadians else 0f
            val cos = kotlin.math.cos(-yaw.toDouble()).toFloat()
            val sin = kotlin.math.sin(-yaw.toDouble()).toFloat()
            for (e in snap.entities) {
                if (e.dead) continue
                val dx = e.x - local.x
                val dz = e.z - local.z
                val dist = kotlin.math.hypot(dx.toDouble(), dz.toDouble()).toFloat()
                if (dist < 0.5f || dist > 300f) continue
                val rx = dx * cos - dz * sin
                val rz = dx * sin + dz * cos
                if (rz < 0.5f) continue
                val sx = w / 2f + (rx / rz) * (w * 0.4f)
                val sy = h / 2f - ((e.y - local.y) / rz) * (h * 0.3f) - 80f / rz
                val boxH = (180f / rz).coerceIn(40f, 280f)
                val boxW = boxH * 0.45f
                val left = sx - boxW / 2
                val top = sy - boxH
                val right = sx + boxW / 2
                val bottom = sy
                val ratio = (e.health.coerceIn(0, 200)) / 200f
                boxPaint.color = when {
                    ratio > 0.6f -> 0xFF00FF88.toInt()
                    ratio > 0.3f -> 0xFFFFFF00.toInt()
                    else -> 0xFFFF4444.toInt()
                }
                canvas.drawRect(left, top, right, bottom, boxPaint)
                hpPaint.color = boxPaint.color
                canvas.drawRect(left, top - 10f, left + boxW * ratio, top - 4f, hpPaint)
                canvas.drawText("${dist.toInt()}m", right + 6f, top + 20f, textPaint)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "ESP Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("ESP Overlay")
            .setContentText("Drawing")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
