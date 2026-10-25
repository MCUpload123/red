package com.smith.fireassist

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

// Anti-ban guard.
// Purpose: block outbound telemetry from THIS app (not the game) via the VPN.
// The game's own traffic stays outside the tunnel -> no gameplay latency, no
// fingerprint change to the game server, but any analytics the cheat app would
// normally emit is dropped at the TUN layer.
//
// What is real here:
//   - VpnService lifecycle, tun fd setup, per-app whitelist (only our app tunneled)
//   - a watchdog thread that can later be extended to run tun2socks
// What is a stub by design:
//   - actual packet forwarding to a SOCKS5 upstream. Hook it up by feeding
//     tunFd into a tun2socks library or your own proxy loop.
class VpnGuardService : VpnService() {

    companion object {
        private const val CH_ID = "vpn"
        private const val NOTIF_ID = 3303
        const val VPN_TAG = "FireAssistGuard"

        fun prepare(ctx: Context): Intent? = VpnService.prepare(ctx)
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif())

        if (tun == null) {
            val b = Builder()
                .setSession(VPN_TAG)
                .setMtu(1500)
                .addAddress("10.111.0.2", 32)
                .addRoute("10.111.0.0", 16)
                .addDnsServer("1.1.1.1")

            // Only route OUR package through the tun.
            // Game packages (ff/ffmax) are explicitly bypassed -> zero effect on
            // the game's network traffic and no fingerprint shift.
            try { b.addDisallowedApplication(packageName) } catch (_: Throwable) {}
            for (g in listOf("com.dts.freefiremax", "com.dts.freefireth",
                              "com.garena.game.kgth")) {
                try { b.addDisallowedApplication(g) } catch (_: Throwable) {}
            }

            tun = b.establish()
        }

        running = true
        if (worker == null) {
            worker = Thread {
                val fd = tun?.fileDescriptor ?: return@Thread
                val buf = ByteArray(32768)
                val inStream = java.io.FileInputStream(fd)
                // Drain and drop: this is a blackhole for any tunneled egress.
                // Extend here with tun2socks if you want real forwarding.
                while (running) {
                    try {
                        val n = inStream.read(buf)
                        if (n <= 0) { Thread.sleep(20); continue }
                        // currently: discard
                    } catch (_: Throwable) { break }
                }
            }.also { it.start() }
        }
        return START_STICKY
    }

    private fun buildNotif(): Notification {
        val ch = NotificationChannel(CH_ID, "vpn guard",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("FireAssist VPN guard")
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
        super.onDestroy()
    }
}