package com.smith.espoverlay

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "esp_overlay_prefs"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    var espEnabled: Boolean
        get() = sp.getBoolean("esp", true)
        set(v) = sp.edit().putBoolean("esp", v).apply()

    var selectedPackage: String
        get() = sp.getString("pkg", "") ?: ""
        set(v) = sp.edit().putString("pkg", v).apply()

    var menuMinimized: Boolean
        get() = sp.getBoolean("menu_min", false)
        set(v) = sp.edit().putBoolean("menu_min", v).apply()
}
