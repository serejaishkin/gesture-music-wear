package com.yourname.gesturemusic.gesture

import kotlin.math.abs

/**
 * Детектор поворота запястья для переключения треков.
 *
 * Алгоритм:
 * 1. Применяем low-pass filter к гироскопу (alpha = 0.8)
 * 2. Интегрируем ωx за скользящее окно 400 мс
 * 3. Если накопленный угол > threshold → NEXT, < -threshold → PREVIOUS
 * 4. Требуем min angular speed и min duration
 * 5. Cooldown 1500 мс между жестами
 * 6. Антишум: игнорируем при высоком linear_acc_y (бег)
 */
class WristRotationDetector(
    private val angleThresholdDegrees: Float = 22f,
    private val minAngularSpeed: Float = 1.5f,       // rad/s
    private val minDurationMs: Long = 150L,
    private val cooldownMs: Long = 1500L,
    private val windowMs: Long = 400L,
    private val idleThreshold: Float = 0.3f,          // rad/s
    private val idleTimeoutMs: Long = 200L,
    private val antiNoiseAccY: Float = 15f            // m/s²
) : GestureDetector {

    private val alpha = 0.8f
    private var filteredGyroX = 0f

    private val samples = mutableListOf<Sample>()
    private var lastGestureTime = 0L
    private var gestureStartTime = 0L
    private var idleStartTime = 0L
    private var inGesture = false

    private data class Sample(
        val timestamp: Long,
        val gyroX: Float,
        val linAccY: Float
    )

    override fun process(
        timestamp: Long,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        linAccX: Float, linAccY: Float, linAccZ: Float
    ): GestureType? {
        // Антишум: игнорируем при беге / резком движении
        if (abs(linAccY) > antiNoiseAccY) {
            resetWindow()
            return null
        }

        // Low-pass filter
        filteredGyroX = alpha * filteredGyroX + (1 - alpha) * gyroX

        // Удаляем старые сэмплы
        samples.removeAll { timestamp - it.timestamp > windowMs }
        samples.add(Sample(timestamp, filteredGyroX, linAccY))

        // Idle detection — сброс окна
        if (abs(filteredGyroX) < idleThreshold) {
            if (idleStartTime == 0L) idleStartTime = timestamp
            if (timestamp - idleStartTime > idleTimeoutMs) {
                resetWindow()
                return null
            }
        } else {
            idleStartTime = 0L
        }

        // Cooldown
        if (timestamp - lastGestureTime < cooldownMs) return null

        // Нужно минимум 2 сэмпла
        if (samples.size < 2) return null

        val maxAngularSpeed = samples.maxOf { abs(it.gyroX) }
        if (maxAngularSpeed < minAngularSpeed) return null

        // Интегрируем угол методом трапеций
        var angle = 0f
        for (i in 1 until samples.size) {
            val dt = (samples[i].timestamp - samples[i - 1].timestamp) / 1000f
            angle += (samples[i].gyroX + samples[i - 1].gyroX) / 2f * dt
        }
        val angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()

        // Определяем жест
        val firstSample = samples.first()
        val lastSample = samples.last()
        val duration = lastSample.timestamp - firstSample.timestamp

        if (duration < minDurationMs) return null

        return when {
            angleDegrees > angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                GestureType.NEXT_TRACK
            }
            angleDegrees < -angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                GestureType.PREVIOUS_TRACK
            }
            else -> null
        }
    }

    override fun reset() {
        resetWindow()
        filteredGyroX = 0f
        lastGestureTime = 0L
    }

    private fun resetWindow() {
        samples.clear()
        gestureStartTime = 0L
        idleStartTime = 0L
        inGesture = false
    }
}
