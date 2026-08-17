package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Щипок: резкое движение пальцев к экрану (linAccZ > 8.0),
 * НО только если gyro < 2.0 (рука не вращается — это не поворот запястья).
 */
class SmartPinchDetector(
    private val accThreshold: Float = 8.0f,
    private val maxGyro: Float = 2.0f,
    private val cooldownMs: Long = 1000L
) : GestureDetector {

    private var lastGestureTime = 0L
    private val TAG = "SmartPinchDetector"

    override fun process(timestamp: Long, gyroX: Float, gyroY: Float, gyroZ: Float,
                         linAccX: Float, linAccY: Float, linAccZ: Float): GestureType? {
        if (timestamp - lastGestureTime < cooldownMs) return null

        // Если рука вращается — это поворот, не щипок
        val gyroMagnitude = maxOf(abs(gyroX), abs(gyroY), abs(gyroZ))
        if (gyroMagnitude > maxGyro) return null

        // Резкое движение по Z (пальцы к экрану)
        if (linAccZ > accThreshold) {
            Log.d(TAG, "Pinch! z=${"%.1f".format(linAccZ)} gyro=${"%.1f".format(gyroMagnitude)}")
            lastGestureTime = timestamp
            return GestureType.PLAY_PAUSE
        }
        return null
    }

    override fun reset() { lastGestureTime = 0L }
}
