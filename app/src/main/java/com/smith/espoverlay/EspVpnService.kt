package com.smith.espoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class EspVpnService : VpnService() {

    companion object {
        private const val TAG = "EspVpn"
        private const val CH = "esp_vpn_ch"
        private const val NOTIF_ID = 1001
        @Volatile var running = false
            private set
        var instance: EspVpnService? = null
            private set
    }

    private var tun: ParcelFileDescriptor? = null
    private val active = AtomicBoolean(false)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val snapBuf = FloatArray(5 + 64 * 6)

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        NativeBridge.serverInit()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            "RESET_MATCH" -> {
                NativeBridge.serverReset()
                EspEntityStore.reset()
                Log.i(TAG, "match reset")
                return START_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotif("VPN running"))
        if (!active.get()) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (active.getAndSet(true)) return
        running = true
        try {
            val builder = Builder()
                .setSession("ESP Overlay")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setBlocking(true)

            val pkg = Prefs.selectedPackage
            if (pkg.isNotEmpty()) {
                try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
            } else {
                listOf(
                    "com.dts.freefireth",
                    "com.dts.freefiremax",
                    "com.garena.game.kgvn"
                ).forEach {
                    try { builder.addAllowedApplication(it) } catch (_: Exception) {}
                }
            }

            tun = builder.establish()
            if (tun == null) {
                Log.e(TAG, "tun establish failed")
                active.set(false)
                running = false
                return
            }
            job = scope.launch { pump(tun!!) }
            Log.i(TAG, "VPN up")
        } catch (t: Throwable) {
            Log.e(TAG, "startVpn: ${t.message}", t)
            active.set(false)
            running = false
        }
    }

    private suspend fun pump(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        while (active.get() && isActive) {
            try {
                val n = input.read(buf)
                if (n <= 0) {
                    delay(1)
                    continue
                }
                if (n >= 28 && (buf[0].toInt() and 0xf0) == 0x40) {
                    val ihl = (buf[0].toInt() and 0x0f) * 4
                    val proto = buf[9].toInt() and 0xff
                    if (proto == 17 && n >= ihl + 8) {
                        val udpStart = ihl
                        val udpLen = ((buf[udpStart + 4].toInt() and 0xff) shl 8) or
                                (buf[udpStart + 5].toInt() and 0xff)
                        val payloadOff = udpStart + 8
                        val payloadLen = minOf(udpLen - 8, n - payloadOff)
                        if (payloadLen > 0) {
                            val payload = buf.copyOfRange(payloadOff, payloadOff + payloadLen)
                            NativeBridge.serverProcessUdp(payload, payload.size, true, false)
                            if (Prefs.espEnabled) {
                                val count = NativeBridge.serverGetEspSnapshot(snapBuf, 64)
                                if (count > 0) {
                                    EspEntityStore.fromNativeSnapshot(snapBuf)?.let {
                                        OverlayService.pushSnapshot(it)
                                    }
                                }
                            }
                        }
                    }
                }
                output.write(buf, 0, n)
            } catch (t: Throwable) {
                if (active.get()) Log.w(TAG, "pump: ${t.message}")
                delay(5)
            }
        }
    }

    fun stopVpn() {
        active.set(false)
        running = false
        job?.cancel()
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpn()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "ESP VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CH)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("ESP Overlay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
