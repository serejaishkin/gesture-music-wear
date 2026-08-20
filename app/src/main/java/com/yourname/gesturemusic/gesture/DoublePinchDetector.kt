package com.yourname.gesturemusic.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Double pinch detector for play/pause.
 *
 * Uses an explicit armed/disarmed state so the same physical pinch
 * cannot generate multiple PLAY_PAUSE events from sensor jitter.
 *
 * FIX (phantom triggers): the previous version reacted to *any* sharp
 * Z-axis acceleration spike (threshold 3.0) with no check on the gyroscope.
 * A wrist twist, raising the arm to look at the watch, or an arm swing while
 * walking produces the exact same up/down Z-acceleration pattern, so it was
 * misread as a double pinch. The repo already had an (unused) SmartPinchDetector
 * with a gyroscope-exclusion idea — this merges that idea into the more robust
 * double-pinch state machine below: while the wrist is rotating (gyro magnitude
 * above maxGyroMagnitude) we no longer accumulate pinch state at all.
 */
class DoublePinchDetector(
    private val thresholdUp: Float = 3.5f,
    private val thresholdDown: Float = -2.5f,
    private val windowMs: Long = 800L,
    private val cooldownMs: Long = 1200L,
    private val maxGyroMagnitude: Float = 2.0f
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
            if (abs(linAccZ) <= REARM_ACCEL) {
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

        // The wrist is rotating (checking the watch, turning the arm, walking swing) —
        // this is not a pinch. Reject outright and don't let a coincidental Z spike
        // during rotation seed a fake "pinch up" state.
        val gyroMagnitude = maxOf(abs(gyroX), abs(gyroY), abs(gyroZ))
        if (gyroMagnitude > maxGyroMagnitude) {
            if (state == PinchState.UP_DETECTED && timestamp - upTime > PINCH_TIMEOUT_MS) {
                state = PinchState.IDLE
            }
            return null
        }

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
