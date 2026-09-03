package com.yourname.gesturemusic.gesture

import android.content.Context

/**
 * Strategy pattern for gesture detection across different OEM implementations.
 *
 * Priority order:
 * 1. SamsungGestureStrategy — native Samsung SDK (Galaxy Watch 4+)
 * 2. WearOsGestureStrategy — Google Wear OS 7 API (Pixel Watch 3+)
 * 3. RawSensorStrategy — universal fallback using raw IMU sensors (all Wear OS)
 */
interface GestureDetectionStrategy {

    /** Human-readable name for logging/debugging. */
    val name: String

    /** Whether this strategy is available on the current device. */
    fun isAvailable(context: Context): Boolean

    /**
     * Called once when the strategy is selected.
     * Register listeners, acquire resources, etc.
     */
    fun start(context: Context, listener: GestureListener)

    /** Unregister listeners, release resources. */
    fun stop()

    /**
     * Update sensitivity settings at runtime.
     */
    fun updateSettings(
        angleThresholdDegrees: Float = 28f,
        pinchThreshold: Float = 3.5f,
        cooldownMs: Long = 1200L,
        leftHand: Boolean = false,
        minDurationMs: Long = 160L,
        maxDurationMs: Long = 600L
    )

    interface GestureListener {
        fun onGestureDetected(gesture: GestureType)
    }
}
