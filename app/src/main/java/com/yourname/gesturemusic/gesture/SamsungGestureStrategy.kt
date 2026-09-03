package com.yourname.gesturemusic.gesture

import android.content.Context
import android.util.Log

/**
 * Samsung Galaxy Watch 4+ native gesture detection via Samsung SDK.
 *
 * Uses reflection to avoid hard dependency — the SDK is only present on
 * Samsung devices with One UI Watch. Falls back gracefully if unavailable.
 *
 * Supported gestures on Galaxy Watch 4+:
 * - Double pinch → PLAY_PAUSE
 * - Make fist → could map to NEXT_TRACK (configurable)
 *
 * @see <a href="https://developer.samsung.com/galaxy-watch">Samsung Wearable SDK</a>
 */
class SamsungGestureStrategy : GestureDetectionStrategy {

    companion object {
        private const val TAG = "SamsungGestureStrategy"
    }

    private var gestureManager: Any? = null
    private var listener: GestureDetectionStrategy.GestureListener? = null
    private var started = false

    // Samsung SDK classes loaded via reflection
    private var slatestureManagerClass: Class<*>? = null
    private var gestureCallback: Any? = null

    override val name = "Samsung SDK (Galaxy Watch 4+)"

    override fun isAvailable(context: Context): Boolean {
        return try {
            // Check if Samsung SDK is available on this device
            Class.forName("com.samsung.android.sdk.gesture.SlatestureManager")
            // Also verify we're on a Samsung device
            val manufacturer = android.os.Build.MANUFACTURER?.lowercase() ?: ""
            val brand = android.os.Build.BRAND?.lowercase() ?: ""
            val available = manufacturer.contains("samsung") || brand.contains("samsung")
            Log.d(TAG, "Samsung SDK available: $available (manufacturer=$manufacturer, brand=$brand)")
            available
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "Samsung SDK not available: ${e.message}")
            false
        }
    }

    override fun start(context: Context, listener: GestureDetectionStrategy.GestureListener) {
        if (started) return
        this.listener = listener

        try {
            slatestureManagerClass = Class.forName("com.samsung.android.sdk.gesture.SlatestureManager")

            // SlaveragestureManager constructor takes Context
            val constructor = slatestureManagerClass!!.getConstructor(Context::class.java)
            gestureManager = constructor.newInstance(context)

            // Create callback via reflection
            // Samsung API: setSlatestureListener(SlatestureManager.SlatestureListener)
            val listenerInterface = Class.forName("com.samsung.android.sdk.gesture.SlatestureManager\$SlatestureListener")

            // Create a dynamic proxy for the listener interface
            gestureCallback = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                when (method.name) {
                    "onSlatestureDetected" -> {
                        // args[0] = gesture type (int)
                        val gestureType = (args?.getOrNull(0) as? Int) ?: return@newProxyInstance null
                        handleSamsungGesture(gestureType)
                    }
                    "onSlatestureError" -> {
                        val errorCode = (args?.getOrNull(0) as? Int) ?: 0
                        Log.w(TAG, "Samsung gesture error: $errorCode")
                    }
                }
                null
            }

            // Register the listener
            val setListenerMethod = slatestureManagerClass!!.getMethod(
                "setSlatestureListener",
                listenerInterface
            )
            setListenerMethod.invoke(gestureManager, gestureCallback)

            started = true
            Log.d(TAG, "Samsung gesture detection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Samsung gesture detection", e)
            started = false
        }
    }

    override fun stop() {
        if (!started) return
        try {
            // Unregister listener
            val removeListenerMethod = slatestureManagerClass?.getMethod("removeSlatestureListener")
            removeListenerMethod?.invoke(gestureManager)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Samsung gestures: ${e.message}")
        }
        gestureManager = null
        gestureCallback = null
        listener = null
        started = false
        Log.d(TAG, "Samsung gesture detection stopped")
    }

    override fun updateSettings(
        angleThresholdDegrees: Float, pinchThreshold: Float,
        cooldownMs: Long, leftHand: Boolean, minDurationMs: Long, maxDurationMs: Long
    ) {
        // Samsung SDK doesn't expose sensitivity settings — it uses
        // its own internal thresholds tuned for Galaxy Watch hardware.
    }

    /**
     * Map Samsung gesture types to our GestureType.
     *
     * Samsung SDK gesture constants (from SlaveragestureManager):
     * - DOUBLE_PINCH = 0 → PLAY_PAUSE
     * - FIST = 1 → NEXT_TRACK (configurable)
     * - FINGER_HEART = 2 → not used
     * - PINCH = 3 → not used (single pinch)
     * - ALARM_WATCH = 4 → not used
     */
    private fun handleSamsungGesture(samsungGestureType: Int) {
        val gesture = when (samsungGestureType) {
            0 -> GestureType.PLAY_PAUSE          // DOUBLE_PINCH
            1 -> GestureType.NEXT_TRACK           // FIST (configurable)
            else -> {
                Log.d(TAG, "Unknown Samsung gesture type: $samsungGestureType")
                return
            }
        }
        Log.d(TAG, "Samsung gesture detected: type=$samsungGestureType → $gesture")
        listener?.onGestureDetected(gesture)
    }
}
