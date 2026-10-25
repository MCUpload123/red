package com.smith.fireassist

import org.opencv.core.Mat
import org.opencv.core.Rect

// detects "fire button pressed" by brightness delta in a small region.
// Fire button dims/highlights when held in Free Fire's default HUD.
class FireDetector {

    private var prevLuma = -1.0
    private var armed = false

    fun sample(rgba: Mat, cx: Int, cy: Int, radius: Int): Boolean {
        if (cx <= 0 || cy <= 0) return false
        val x = (cx - radius).coerceAtLeast(0)
        val y = (cy - radius).coerceAtLeast(0)
        val w = (radius * 2).coerceAtMost(rgba.cols() - x)
        val h = (radius * 2).coerceAtMost(rgba.rows() - y)
        if (w <= 4 || h <= 4) return false

        val roi = Rect(x, y, w, h)
        val sub = rgba.submat(roi)
        val mean = org.opencv.core.Core.mean(sub)
        val luma = (mean.`val`[0] * 0.299 + mean.`val`[1] * 0.587 + mean.`val`[2] * 0.114) / 3.0
        sub.release()

        // pressed = sudden brightening when finger covers button (releases an inner glow)
        val pressed = prevLuma > 0 && (luma - prevLuma) > 6.0
        prevLuma = luma
        return pressed
    }
}