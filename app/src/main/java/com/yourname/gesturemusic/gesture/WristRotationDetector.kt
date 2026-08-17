package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Детектор поворота запястья для переключения треков.
 *
 * Исправления:
 * - Реверс направлений (было: +X = NEXT, стало: +X = PREVIOUS)
 * - Минимальная и максимальная длительность жеста (настраиваемая)
 *   чтобы избежать фантомных срабатываний от возврата руки
 * - Cooldown 1000 мс между жестами
 */
class WristRotationDetector(
    private val angleThresholdDegrees: Float = 22f,
    private val minAngularSpeed: Float = 1.5f,
    private val minDurationMs: Long = 150L,      // минимальная длительность жеста
    private val maxDurationMs: Long = 600L,      // !!! максимальная длительность — отсекает возврат руки
    private val cooldownMs: Long = 1000L,        // !!! 1 секунда между жестами
    private val windowMs: Long = 400L,
    private val idleThreshold: Float = 0.3f,
    private val idleTimeoutMs: Long = 200L,
    private val antiNoiseAccY: Float = 15f
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

        // Cooldown между жестами
        if (timestamp - lastGestureTime < cooldownMs) return null

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

        val firstSample = samples.first()
        val lastSample = samples.last()
        val duration = lastSample.timestamp - firstSample.timestamp

        // !!! Минимальная длительность
        if (duration < minDurationMs) return null

        // !!! Максимальная длительность — отсекает возврат руки (медленное движение)
        if (duration > maxDurationMs) {
            Log.d(TAG, "Rejected: duration ${duration}ms > max ${maxDurationMs}ms (return motion)")
            resetWindow()
            return null
        }

        return when {
            angleDegrees > angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                // !!! РЕВЕРС: +X теперь PREVIOUS (было NEXT)
                Log.d(TAG, "PREVIOUS on X ${"%.0f".format(angleDegrees)}° (duration=${duration}ms)")
                GestureType.PREVIOUS_TRACK
            }
            angleDegrees < -angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                // !!! РЕВЕРС: -X теперь NEXT (было PREVIOUS)
                Log.d(TAG, "NEXT on X ${"%.0f".format(abs(angleDegrees))}° (duration=${duration}ms)")
                GestureType.NEXT_TRACK
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

    companion object {
        private const val TAG = "WristRotationDetector"
    }
}
