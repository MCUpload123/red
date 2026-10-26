package com.smith.espoverlay

object NativeBridge {
    init {
        try {
            System.loadLibrary("server")
        } catch (t: Throwable) {
            android.util.Log.e("NativeBridge", "loadLibrary failed: ${t.message}")
        }
    }

    external fun serverInit(): Int
    external fun serverReset()
    external fun serverSetFeature(featureId: Int, enabled: Boolean)
    external fun serverProcessUdp(data: ByteArray, len: Int, flagA: Boolean, flagB: Boolean): Int
    external fun serverGetEspSnapshot(out: FloatArray, maxCount: Int): Int
    external fun serverGetInjectedPackets(): ByteArray?
}
