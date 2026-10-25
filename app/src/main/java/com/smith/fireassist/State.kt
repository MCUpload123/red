package com.smith.fireassist

// shared cross-service state (same process)
object State {
    @Volatile var detectionActive = false
    @Volatile var overlayActive = false
    @Volatile var firePressed = false
    @Volatile var aimbotEnabled = true
    @Volatile var targetCount = 0

    // screen coords of enemy heads (world-projected by native side)
    @Volatile var heads: FloatArray = FloatArray(0)

    // fire button center in screen coords (calibrated)
    @Volatile var fireX = 0
    @Volatile var fireY = 0
    @Volatile var fireRadius = 90

    // sensitivity: pixels of swipe per 1 degree of screen rotation
    @Volatile var swipePerDegree = 12f
}