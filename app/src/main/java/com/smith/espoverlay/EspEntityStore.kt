package com.smith.espoverlay

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object EspEntityStore {
    private const val ENTITY_TTL_MS = 2000L
    private const val LOCAL_TTL_MS = 2500L

    @Volatile private var localPlayer: Entity? = null
    private val entities = ConcurrentHashMap<Int, Entity>()

    @Volatile var yawRadians: Float = Float.NaN
        private set
    @Volatile var directionX: Float = Float.NaN
        private set
    @Volatile var directionY: Float = Float.NaN
        private set
    @Volatile var directionZ: Float = Float.NaN
        private set

    fun reset() {
        entities.clear()
        localPlayer = null
        yawRadians = Float.NaN
        directionX = Float.NaN
        directionY = Float.NaN
        directionZ = Float.NaN
    }

    fun updateLocal(id: Int, x: Float, y: Float, z: Float) {
        if (!validPosition(x, y, z)) return
        val now = SystemClock.uptimeMillis()
        val prev = localPlayer
        var vx = 0f; var vy = 0f; var vz = 0f
        if (prev != null) {
            val dt = now - prev.updatedAt
            val dist = horizontalDistance(prev.x, prev.z, x, z)
            if (dt in 1..600 && dist <= 8f) {
                val sec = dt / 1000f
                vx = (x - prev.x) / sec
                vy = (y - prev.y) / sec
                vz = (z - prev.z) / sec
                if (dist >= 0.08f) yawRadians = atan2(vx, vz)
            }
        }
        localPlayer = Entity(id, x, y, z, vx, vy, vz, now, "YOU", 200, 0, false)
        entities.entries.removeIf { horizontalDistance(it.value.x, it.value.z, x, z) < 0.35f }
    }

    fun updateEntity(id: Int, x: Float, y: Float, z: Float) {
        if (id <= 0 || id > 64 || !validPosition(x, y, z)) return
        val lp = localPlayer
        if (lp != null && horizontalDistance(lp.x, lp.z, x, z) < 0.35f) return
        val now = SystemClock.uptimeMillis()
        entities.compute(id) { _, old ->
            if (old == null || now - old.updatedAt > 1000) {
                Entity(id, x, y, z, 0f, 0f, 0f, now, "E$id", 200, 0, false)
            } else if (horizontalDistance(old.x, old.z, x, z) > 12f) {
                Entity(id, x, y, z, 0f, 0f, 0f, now, old.name, old.health, old.team, old.dead)
            } else {
                val dt = max(8L, now - old.updatedAt)
                val sec = dt / 1000f
                val blend = if (dt < 80) 0.78f else 1f
                val inv = 1f - blend
                var nvx = ((x - old.x) / sec) * blend + old.vx * inv
                val nvy = ((y - old.y) / sec) * blend + old.vy * inv
                var nvz = ((z - old.z) / sec) * blend + old.vz * inv
                if (hypot(nvx.toDouble(), nvz.toDouble()) > 20.0) {
                    nvx = 0f; nvz = 0f
                }
                Entity(id, x, y, z, nvx, nvy, nvz, now, old.name, old.health, old.team, old.dead)
            }
        }
    }

    fun fromNativeSnapshot(arr: FloatArray?): Snapshot? {
        if (arr == null || arr.size < 5) return null
        val now = SystemClock.uptimeMillis()
        val local = if (arr[0].isFinite() && arr[2].isFinite()) {
            Entity(0, arr[0], arr[1], arr[2], 0f, 0f, 0f, now, "YOU", 200, 0, false)
        } else null
        val count = max(0, min(64, arr[4].roundToInt()))
        val list = ArrayList<Entity>(count)
        var i = 0
        while (i < count) {
            val base = i * 6
            val idIdx = base + 5
            val lastIdx = base + 10
            if (lastIdx >= arr.size) break
            list.add(
                Entity(
                    arr[idIdx].roundToInt(),
                    arr[base + 6],
                    arr[base + 7],
                    arr[base + 8],
                    arr[base + 9],
                    0f,
                    arr[lastIdx],
                    now,
                    "E${arr[idIdx].roundToInt()}",
                    200, 0, false
                )
            )
            i++
        }
        return Snapshot(local, list, arr[3], Float.NaN, Float.NaN, Float.NaN)
    }

    fun snapshot(): Snapshot {
        val now = SystemClock.uptimeMillis()
        val list = ArrayList<Entity>()
        val it = entities.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.updatedAt > ENTITY_TTL_MS) {
                it.remove()
            } else {
                list.add(e.value.predict(now))
            }
        }
        var lp = localPlayer
        if (lp != null && now - lp.updatedAt > LOCAL_TTL_MS) lp = null
        return Snapshot(
            lp?.predict(now),
            list,
            yawRadians,
            directionX,
            directionY,
            directionZ
        )
    }

    private fun validPosition(x: Float, y: Float, z: Float): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
                && kotlin.math.abs(x) <= 8000f
                && kotlin.math.abs(z) <= 8000f
                && y in -50f..500f
                && hypot(x.toDouble(), z.toDouble()) >= 0.3
    }

    private fun horizontalDistance(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        return hypot((x2 - x1).toDouble(), (z2 - z1).toDouble()).toFloat()
    }

    data class Entity(
        val id: Int,
        val x: Float,
        val y: Float,
        val z: Float,
        val vx: Float,
        val vy: Float,
        val vz: Float,
        val updatedAt: Long,
        val name: String,
        val health: Int,
        val team: Int,
        val dead: Boolean
    ) {
        fun predict(now: Long): Entity {
            val t = max(0L, min(100L, now - updatedAt)) / 1000f
            return Entity(
                id,
                x + vx * t,
                y + vy * t,
                z + vz * t,
                vx, vy, vz,
                updatedAt, name, health, team, dead
            )
        }
    }

    data class Snapshot(
        val localPlayer: Entity?,
        val entities: List<Entity>,
        val yawRadians: Float,
        val directionX: Float,
        val directionY: Float,
        val directionZ: Float
    )
}
