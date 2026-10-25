package com.smith.fireassist

object NativeBridge {
    init { System.loadLibrary("fireassist") }

    // returns [dx, dy] in screen px for a smooth human-like swipe from center to head
    external fun smoothPath(
        cx: Float, cy: Float, headX: Float, headY: Float,
        durationMs: Long, jitter: Float
    ): FloatArray

    // predict where target will be after `leadMs` given previous and current pos
    external fun predictHead(
        px: Float, py: Float, cx: Float, cy: Float, leadMs: Float
    ): FloatArray
}