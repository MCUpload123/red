package com.smith.fireassist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

// picks whichever Free Fire variant is installed and launches it
object GameLauncher {

    private val candidates = listOf(
        "com.dts.freefiremax",   // FF MAX
        "com.dts.freefireth",    // FF
    )

    fun findInstalled(ctx: Context): String? {
        val pm = ctx.packageManager
        return candidates.firstOrNull {
            try { pm.getPackageInfo(it, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        }
    }

    fun launch(ctx: Context): Boolean {
        val pkg = findInstalled(ctx) ?: return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }
}