package com.smith.espoverlay

data class EspBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val hp: Int = 100,
    val dist: Int = 0,
    val id: Int = 0,
    val name: String = ""
)
