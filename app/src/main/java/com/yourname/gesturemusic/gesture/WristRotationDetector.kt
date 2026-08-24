package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Wrist rotation detector for track switching.
 *
 * A light/slow turn must not be enough to trigger a track change.
 * The detector therefore requires both a real angular-speed peak and
 * enough integrated rotation. Direction is mirrored for the left wrist.
 */
class WristRotationDetector(
    private val angleThresholdDegrees: Float = 28f,
    private val minAngularSpeed: Float = 2.2f,
    private val minDurationMs: Long = 160L,
    private val maxDurationMs: Long = 600L,
    private val cooldownMs: Long = 1200L,
    private val windowMs: Long = 400L,
    private val idleThreshold: Float = 0.35f,
    private val idleTimeoutMs: Long = 180L,
    private val antiNoiseAccY: Float = 20f,
    private val leftHand: Boolean = false
) : GestureDetector {

    private val alpha = 0.8f
    private var filteredGyroX = 0f

    private val samples = mutableListOf<Sample>()
    private var lastGestureTime = 0L
    private var idleStartTime = 0L

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
        // FIX (маhание рукой = 0 реакции): раньше ЛЮБОЙ единичный сэмпл с
        // |linAccY| > antiNoiseAccY полностью сбрасывал накопленное окно
        // поворота. Быстрый, резкий (то есть настоящий, уверенный) поворот
        // запястья сам по себе двигает всю кисть и легко даёт такой всплеск —
        // и обнулял сам себя посреди жеста, до того как угол успевал набраться.
        // Теперь сброс происходит только если всплеск НЕ сопровождается
        // реальной угловой скоростью (то есть это действительно похоже на
        // удар/шум, а не на часть самого поворота) — иначе просто не
        // учитываем этот один сэмпл и не портим уже накопленные данные.
        if (abs(linAccY) > antiNoiseAccY) {
            if (abs(gyroX) < idleThreshold * 2f) {
                resetWindow()
            }
            return null
        }

        filteredGyroX = alpha * filteredGyroX + (1 - alpha) * gyroX

        if (timestamp - lastGestureTime < cooldownMs) return null

        // Do not even start a rotation window for tiny wrist movement.
        // This is the main guard against accidental track changes.
        if (abs(filteredGyroX) < idleThreshold) {
            if (samples.isNotEmpty()) {
                if (idleStartTime == 0L) idleStartTime = timestamp
                if (timestamp - idleStartTime > idleTimeoutMs) {
                    resetWindow()
                }
            }
            return null
        }

        idleStartTime = 0L
        samples.removeAll { timestamp - it.timestamp > windowMs }
        samples.add(Sample(timestamp, filteredGyroX, linAccY))

        if (samples.size < 2) return null

        val maxAngularSpeed = samples.maxOf { abs(it.gyroX) }

        // Require a clearly intentional rotation, not a slow hand adjustment.
        if (maxAngularSpeed < minAngularSpeed) return null

        // Reject direction changes/noisy oscillation. A real gesture should
        // have one dominant rotation direction.
        val positive = samples.count { it.gyroX > idleThreshold }
        val negative = samples.count { it.gyroX < -idleThreshold }
        val dominant = maxOf(positive, negative)
        if (dominant < samples.size * 0.70f) return null

        var angle = 0f
        for (i in 1 until samples.size) {
            val dt = (samples[i].timestamp - samples[i - 1].timestamp) / 1000f
            angle += (samples[i].gyroX + samples[i - 1].gyroX) / 2f * dt
        }

        var angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()

        val firstSample = samples.first()
        val lastSample = samples.last()
        val duration = lastSample.timestamp - firstSample.timestamp

        if (duration < minDurationMs) return null

        if (duration > maxDurationMs) {
            Log.d(TAG, "Rejected: duration ${duration}ms > max ${maxDurationMs}ms")
            resetWindow()
            return null
        }

        if (leftHand) angleDegrees = -angleDegrees

        return when {
            angleDegrees > angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                Log.d(
                    TAG,
                    "PREVIOUS on X ${"%.0f".format(angleDegrees)}° " +
                        "(speed=${"%.1f".format(maxAngularSpeed)}, duration=${duration}ms, leftHand=$leftHand)"
                )
                GestureType.PREVIOUS_TRACK
            }

            angleDegrees < -angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                Log.d(
                    TAG,
                    "NEXT on X ${"%.0f".format(abs(angleDegrees))}° " +
                        "(speed=${"%.1f".format(maxAngularSpeed)}, duration=${duration}ms, leftHand=$leftHand)"
                )
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
        idleStartTime = 0L
    }

    companion object {
        private const val TAG = "WristRotationDetector"
    }
}
