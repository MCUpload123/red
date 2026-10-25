package com.smith.fireassist

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_RESULT_DATA = "rd"
        private const val CH_ID = "capture"
        private const val NOTIF_ID = 1101
        private const val FRAME_SKIP = 2   // detect every 3rd frame
    }

    private var projection: MediaProjection? = null
    private var vDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 1280
    private var height = 720
    private var density = 320
    private val handler = Handler(Looper.getMainLooper())
    private var frameCounter = 0

    private lateinit var fireDetector: FireDetector
    private lateinit var tracker: TargetTracker

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif())
        if (!OpenCVLoader.initLocal()) { stopSelf(); return START_NOT_STICKY }

        fireDetector = FireDetector()
        tracker = TargetTracker()

        if (intent?.action == ACTION_START) {
            val rc = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val rd = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (rd != null) startProjection(rc, rd)
        }
        return START_STICKY
    }

    private fun startProjection(rc: Int, rd: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(rc, rd)

        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        vDisplay = projection!!.createVirtualDisplay(
            "capture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            frameCounter++
            if (frameCounter % (FRAME_SKIP + 1) != 0) { img.close(); return@setOnImageAvailableListener }
            try {
                processFrame(img)
            } catch (_: Throwable) {
            } finally { img.close() }
        }, handler)

        State.detectionActive = true
    }

    private fun processFrame(img: android.media.Image) {
        val plane = img.planes[0]
        val buffer = plane.buffer
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        val mat = Mat()
        Utils.bitmapToMat(bmp, mat)

        // fire button sample
        State.firePressed = fireDetector.sample(mat, State.fireX, State.fireY, State.fireRadius)

        // detect enemies
        val heads = tracker.detect(mat)
        State.heads = heads
        State.targetCount = heads.size / 2

        mat.release()
        bmp.recycle()
    }

    private fun buildNotif(): Notification {
        val ch = NotificationChannel(CH_ID, "capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("FireAssist capture")
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        State.detectionActive = false
        reader?.close(); vDisplay?.release(); projection?.stop()
        super.onDestroy()
    }
}