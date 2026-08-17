package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Детектор двойного щипка (double pinch) для play/pause.
 *
 * Упрощённая версия: отслеживаем резкий пик linAccZ.
 * Щипок = быстрое движение пальцев к экрану и обратно.
 */
class DoublePinchDetector(
    private val thresholdUp: Float = 3.0f,
    private val thresholdDown: Float = -2.0f,
    private val windowMs: Long = 800L,
    private val cooldownMs: Long = 1000L
) : GestureDetector {

    companion object {
        private const val TAG = "DoublePinchDetector"
    }

    private var lastGestureTime = 0L
    private val pinches = mutableListOf<Long>()

    private enum class PinchState { IDLE, UP_DETECTED }
    private var state = PinchState.IDLE
    private var upTime = 0L

    override fun process(
        timestamp: Long,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        linAccX: Float, linAccY: Float, linAccZ: Float
    ): GestureType? {
        // Cooldown
        if (timestamp - lastGestureTime < cooldownMs) return null

        // Удаляем старые щипки
        pinches.removeAll { timestamp - it > windowMs }

        when (state) {
            PinchState.IDLE -> {
                if (linAccZ > thresholdUp) {
                    Log.d(TAG, "Pinch UP detected: z=${"%.1f".format(linAccZ)}")
                    state = PinchState.UP_DETECTED
                    upTime = timestamp
                }
            }
            PinchState.UP_DETECTED -> {
                if (linAccZ < thresholdDown) {
                    // Один щипок завершён
                    pinches.add(timestamp)
                    state = PinchState.IDLE
                    Log.d(TAG, "Pinch DOWN detected: z=${"%.1f".format(linAccZ)}, count=${pinches.size}")

                    // Проверяем double pinch
                    if (pinches.size >= 2) {
                        val first = pinches[pinches.size - 2]
                        val second = pinches[pinches.size - 1]
                        if (second - first <= windowMs) {
                            lastGestureTime = timestamp
                            pinches.clear()
                            Log.d(TAG, "Double pinch detected!")
                            return GestureType.PLAY_PAUSE
                        }
                    }
                } else if (timestamp - upTime > 300L) {
                    // Таймаут, сброс
                    Log.d(TAG, "Pinch timeout, resetting")
                    state = PinchState.IDLE
                }
            }
        }

        return null
    }

    override fun reset() {
        pinches.clear()
        state = PinchState.IDLE
        upTime = 0L
        lastGestureTime = 0L
    }
}
