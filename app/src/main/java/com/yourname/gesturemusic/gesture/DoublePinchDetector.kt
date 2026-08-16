package com.yourname.gesturemusic.gesture

import kotlin.math.abs

/**
 * Детектор двойного щипка (double pinch) для play/pause.
 *
 * Алгоритм:
 * 1. Следим за осью Z linear acceleration
 * 2. Пик: z > thresholdUp (пальцы к экрану), затем z < thresholdDown (от экрана) = один щипок
 * 3. Два щипка в окне 800 мс = double pinch
 * 4. Cooldown 1000 мс
 */
class DoublePinchDetector(
    private val thresholdUp: Float = 3.0f,      // m/s² — пальцы к экрану
    private val thresholdDown: Float = -2.0f,   // m/s² — пальцы от экрана
    private val windowMs: Long = 800L,
    private val cooldownMs: Long = 1000L
) : GestureDetector {

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
                    state = PinchState.UP_DETECTED
                    upTime = timestamp
                }
            }
            PinchState.UP_DETECTED -> {
                if (linAccZ < thresholdDown) {
                    // Один щипок завершён
                    pinches.add(timestamp)
                    state = PinchState.IDLE

                    // Проверяем double pinch
                    if (pinches.size >= 2) {
                        val first = pinches[pinches.size - 2]
                        val second = pinches[pinches.size - 1]
                        if (second - first <= windowMs) {
                            lastGestureTime = timestamp
                            pinches.clear()
                            return GestureType.PLAY_PAUSE
                        }
                    }
                } else if (timestamp - upTime > 300L) {
                    // Таймаут, сброс
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
