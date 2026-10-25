package com.smith.fireassist

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service(), SurfaceHolder.Callback {

    companion object {
        private const val CH_ID = "overlay"
        private const val NOTIF_ID = 2202
    }

    private lateinit var wm: WindowManager
    private var surfaceView: SurfaceView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var thread: Thread? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotif())
        wm = getSystemService(WindowManager::class.java)
        addOverlay()
        State.overlayActive = true
    }

    private fun addOverlay() {
        val sv = SurfaceView(this)
        sv.holder.addCallback(this)
        sv.setZOrderOnTop(true)
        sv.holder.setFormat(PixelFormat.TRANSLUCENT)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(sv, lp)
        surfaceView = sv
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread {
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
            val text = Paint().apply {
                color = Color.RED
                textSize = 26f
                isAntiAlias = true
            }
            while (running) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    val heads = State.heads
                    var i = 0
                    while (i + 1 < heads.size) {
                        val hx = heads[i]
                        val hy = heads[i + 1]
                        // draw a head marker + box hint
                        canvas.drawCircle(hx, hy, 12f, paint)
                        canvas.drawLine(hx - 20f, hy, hx + 20f, hy, paint)
                        canvas.drawLine(hx, hy - 20f, hx, hy + 20f, paint)
                        canvas.drawText("•", hx - 5, hy - 18, text)
                        i += 2
                    }
                    // draw fire button indicator if calibrated
                    if (State.fireX > 0) {
                        paint.color = if (State.firePressed) Color.YELLOW else Color.GREEN
                        canvas.drawCircle(State.fireX.toFloat(), State.fireY.toFloat(),
                            State.fireRadius.toFloat(), paint)
                        paint.color = Color.RED
                    }
                } finally { holder.unlockCanvasAndPost(canvas) }
                Thread.sleep(16)
            }
        }.also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { running = false }

    private fun buildNotif(): Notification {
        val ch = NotificationChannel(CH_ID, "overlay", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FireAssist overlay")
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        running = false
        State.overlayActive = false
        surfaceView?.let { wm.removeView(it) }
        super.onDestroy()
    }
}