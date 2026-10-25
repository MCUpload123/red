package com.smith.fireassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

class AimbotService : AccessibilityService() {

    companion object {
        @Volatile var instance: AimbotService? = null
        private const val SNAP_MS = 55L
        private const val MIN_DIST = 55f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapAt = 0L

    override fun onServiceConnected() {
        instance = this
        handler.post(snapLoop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { instance = null }

    private val snapLoop = object : Runnable {
        override fun run() {
            if (State.aimbotEnabled && State.firePressed) trySnap()
            handler.postDelayed(this, 16)
        }
    }

    private fun trySnap() {
        val now = System.currentTimeMillis()
        if (now - lastSnapAt < 40) return
        val heads = State.heads
        if (heads.isEmpty()) return

        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f

        // pick nearest head to crosshair
        var best = -1
        var bestD2 = Float.MAX_VALUE
        var i = 0
        while (i + 1 < heads.size) {
            val dx = heads[i] - cx
            val dy = heads[i + 1] - cy
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
            i += 2
        }
        if (best < 0) return
        if (bestD2 < MIN_DIST * MIN_DIST) return

        val hx = heads[best]
        val hy = heads[best + 1]

        // build smooth path via native
        val pathPts = NativeBridge.smoothPath(cx, cy, hx, hy, SNAP_MS, 3f)
        if (pathPts.isEmpty()) return

        val path = Path()
        path.moveTo(pathPts[0], pathPts[1])
        var k = 2
        while (k + 1 < pathPts.size) {
            path.lineTo(pathPts[k], pathPts[k + 1])
            k += 2
        }

        val gd = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SNAP_MS))
            .build()
        dispatchGesture(gd, null, null)
        lastSnapAt = now
    }
}