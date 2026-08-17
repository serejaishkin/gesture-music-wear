package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Wrist rotation detector for track switching.
 *
 * Direction is mirrored for the left wrist so the same physical
 * hand movement has the same meaning on either wrist.
 */
class WristRotationDetector(
    private val angleThresholdDegrees: Float = 22f,
    private val minAngularSpeed: Float = 1.5f,
    private val minDurationMs: Long = 150L,
    private val maxDurationMs: Long = 600L,
    private val cooldownMs: Long = 1200L,
    private val windowMs: Long = 400L,
    private val idleThreshold: Float = 0.3f,
    private val idleTimeoutMs: Long = 200L,
    private val antiNoiseAccY: Float = 15f,
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
        if (abs(linAccY) > antiNoiseAccY) {
            resetWindow()
            return null
        }

        filteredGyroX = alpha * filteredGyroX + (1 - alpha) * gyroX

        samples.removeAll { timestamp - it.timestamp > windowMs }
        samples.add(Sample(timestamp, filteredGyroX, linAccY))

        if (timestamp - lastGestureTime < cooldownMs) return null

        if (abs(filteredGyroX) < idleThreshold) {
            if (idleStartTime == 0L) idleStartTime = timestamp
            if (timestamp - idleStartTime > idleTimeoutMs) {
                resetWindow()
                return null
            }
        } else {
            idleStartTime = 0L
        }

        if (samples.size < 2) return null

        val maxAngularSpeed = samples.maxOf { abs(it.gyroX) }
        if (maxAngularSpeed < minAngularSpeed) return null

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
            Log.d(
                TAG,
                "Rejected: duration ${duration}ms > max ${maxDurationMs}ms"
            )
            resetWindow()
            return null
        }

        // Mirror the X direction for the left wrist.
        if (leftHand) {
            angleDegrees = -angleDegrees
        }

        return when {
            angleDegrees > angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                Log.d(
                    TAG,
                    "PREVIOUS on X ${"%.0f".format(angleDegrees)}° " +
                        "(duration=${duration}ms, leftHand=$leftHand)"
                )
                GestureType.PREVIOUS_TRACK
            }

            angleDegrees < -angleThresholdDegrees -> {
                lastGestureTime = timestamp
                resetWindow()
                Log.d(
                    TAG,
                    "NEXT on X ${"%.0f".format(abs(angleDegrees))}° " +
                        "(duration=${duration}ms, leftHand=$leftHand)"
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
