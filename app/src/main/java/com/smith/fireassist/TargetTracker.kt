package com.smith.fireassist

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

// finds enemy nameplates by HSV thresholding red.
// returns flat array of head positions: [x0,y0,x1,y1,...]
class TargetTracker {

    private val hsv = Mat()
    private val mask1 = Mat()
    private val mask2 = Mat()
    private val mask = Mat()
    private val contours = ArrayList<MatOfPoint>()
    private val hierarchy = Mat()

    // enemy nameplate color in Free Fire: red-ish above head
    private val lower1 = Scalar(0.0, 120.0, 80.0)
    private val upper1 = Scalar(10.0, 255.0, 255.0)
    private val lower2 = Scalar(170.0, 120.0, 80.0)
    private val upper2 = Scalar(180.0, 255.0, 255.0)

    fun detect(rgba: Mat): FloatArray {
        Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGBA2HSV)
        Core.inRange(hsv, lower1, upper1, mask1)
        Core.inRange(hsv, lower2, upper2, mask2)
        Core.bitwise_or(mask1, mask2, mask)
        // dilate to merge nameplate letters
        Imgproc.dilate(mask, mask,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 3.0)))

        contours.clear()
        Imgproc.findContours(mask, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val out = ArrayList<Float>(contours.size * 2)
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            if (r.width < 40 || r.height < 8) continue
            if (r.width > 400) continue
            // nameplate aspect ratio ~3:1 or wider
            if (r.width.toFloat() / r.height < 2.0f) continue
            // head is just under the nameplate
            val headX = r.x + r.width / 2f
            val headY = r.y + r.height + 18f
            out.add(headX); out.add(headY)
        }
        val arr = FloatArray(out.size)
        for (i in out.indices) arr[i] = out[i]
        return arr
    }
}