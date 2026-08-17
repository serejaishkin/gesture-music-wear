package com.yourname.gesturemusic.gesture

import android.util.Log

/**
 * Double pinch detector for play/pause.
 *
 * Uses an explicit armed/disarmed state so the same physical pinch
 * cannot generate multiple PLAY_PAUSE events from sensor jitter.
 */
class DoublePinchDetector(
    private val thresholdUp: Float = 3.0f,
    private val thresholdDown: Float = -2.0f,
    private val windowMs: Long = 800L,
    private val cooldownMs: Long = 1200L
) : GestureDetector {

    companion object {
        private const val TAG = "DoublePinchDetector"
        private const val REARM_ACCEL = 1.0f
        private const val REARM_STABLE_MS = 120L
        private const val PINCH_TIMEOUT_MS = 300L
    }

    private var lastGestureTime = 0L
    private val pinches = mutableListOf<Long>()

    private enum class PinchState { IDLE, UP_DETECTED }
    private var state = PinchState.IDLE
    private var upTime = 0L

    private var armed = true
    private var stableStartTime = 0L

    override fun process(
        timestamp: Long,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        linAccX: Float, linAccY: Float, linAccZ: Float
    ): GestureType? {

        // After a gesture, require the sensor to return to a stable position.
        // This prevents the release/rebound of the same pinch from retriggering.
        if (!armed) {
            if (kotlin.math.abs(linAccZ) <= REARM_ACCEL) {
                if (stableStartTime == 0L) stableStartTime = timestamp
                if (timestamp - stableStartTime >= REARM_STABLE_MS &&
                    timestamp - lastGestureTime >= cooldownMs
                ) {
                    armed = true
                    stableStartTime = 0L
                    Log.d(TAG, "Detector re-armed")
                }
            } else {
                stableStartTime = 0L
            }
            return null
        }

        if (timestamp - lastGestureTime < cooldownMs) return null

        pinches.removeAll { timestamp - it > windowMs }

        when (state) {
            PinchState.IDLE -> {
                if (linAccZ > thresholdUp) {
                    state = PinchState.UP_DETECTED
                    upTime = timestamp
                    Log.d(TAG, "Pinch UP detected: z=${"%.1f".format(linAccZ)}")
                }
            }

            PinchState.UP_DETECTED -> {
                if (linAccZ < thresholdDown) {
                    pinches.add(timestamp)
                    state = PinchState.IDLE

                    Log.d(
                        TAG,
                        "Pinch DOWN detected: z=${"%.1f".format(linAccZ)}, count=${pinches.size}"
                    )

                    if (pinches.size >= 2) {
                        val first = pinches[pinches.size - 2]
                        val second = pinches[pinches.size - 1]

                        if (second - first <= windowMs) {
                            lastGestureTime = timestamp
                            pinches.clear()
                            armed = false
                            stableStartTime = 0L

                            Log.d(TAG, "Double pinch detected — disarming until stable")
                            return GestureType.PLAY_PAUSE
                        }
                    }
                } else if (timestamp - upTime > PINCH_TIMEOUT_MS) {
                    state = PinchState.IDLE
                    Log.d(TAG, "Pinch timeout, resetting")
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
        armed = true
        stableStartTime = 0L
    }
}
