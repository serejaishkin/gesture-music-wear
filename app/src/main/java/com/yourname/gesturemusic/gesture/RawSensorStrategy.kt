package com.yourname.gesturemusic.gesture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log

/**
 * Universal fallback: raw gyro + linear-accelerometer detection.
 * Works on ALL Wear OS devices.
 */
class RawSensorStrategy : GestureDetectionStrategy, SensorEventListener {

    companion object {
        private const val TAG = "RawSensorStrategy"
        private const val GESTURE_COOLDOWN_MS = 400L
    }

    private var sensorManager: SensorManager? = null
    private var listener: GestureDetectionStrategy.GestureListener? = null

    private lateinit var wristDetector: WristRotationDetector
    private lateinit var pinchDetector: DoublePinchDetector

    private var lastGyroX = 0f; private var lastGyroY = 0f; private var lastGyroZ = 0f
    private var lastLinAccX = 0f; private var lastLinAccY = 0f; private var lastLinAccZ = 0f
    private var lastGestureTime = 0L

    override val name = "RawSensor (universal)"

    override fun isAvailable(context: Context): Boolean = true // always available

    override fun start(context: Context, listener: GestureDetectionStrategy.GestureListener) {
        this.listener = listener
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        wristDetector = WristRotationDetector()
        pinchDetector = DoublePinchDetector()

        val gyro = sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val linAcc = sensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyro?.let { sensorManager!!.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linAcc?.let { sensorManager!!.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        Log.d(TAG, "Started: gyro=${gyro != null}, linAcc=${linAcc != null}")
    }

    override fun stop() {
        sensorManager?.unregisterListener(this)
        wristDetector.reset()
        pinchDetector.reset()
        listener = null
    }

    override fun updateSettings(
        angleThresholdDegrees: Float, pinchThreshold: Float,
        cooldownMs: Long, leftHand: Boolean, minDurationMs: Long, maxDurationMs: Long
    ) {
        wristDetector = WristRotationDetector(
            angleThresholdDegrees = angleThresholdDegrees,
            cooldownMs = cooldownMs,
            leftHand = leftHand,
            minDurationMs = minDurationMs,
            maxDurationMs = maxDurationMs
        )
        pinchDetector = DoublePinchDetector(
            thresholdUp = pinchThreshold,
            thresholdDown = -(pinchThreshold * 0.8f),
            cooldownMs = cooldownMs
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]; lastGyroY = event.values[1]; lastGyroZ = event.values[2]
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                lastLinAccX = event.values[0]; lastLinAccY = event.values[1]; lastLinAccZ = event.values[2]
                processGestures()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processGestures() {
        val timestamp = SystemClock.elapsedRealtime()
        if (timestamp - lastGestureTime < GESTURE_COOLDOWN_MS) return

        val wrist = wristDetector.process(timestamp, lastGyroX, lastGyroY, lastGyroZ, lastLinAccX, lastLinAccY, lastLinAccZ)
        val pinch = pinchDetector.process(timestamp, lastGyroX, lastGyroY, lastGyroZ, lastLinAccX, lastLinAccY, lastLinAccZ)
        val gesture = wrist ?: pinch ?: return

        lastGestureTime = timestamp
        listener?.onGestureDetected(gesture)
    }

    fun feedSample(gyroX: Float, gyroY: Float, gyroZ: Float, linAccX: Float, linAccY: Float, linAccZ: Float) {
        lastGyroX = gyroX; lastGyroY = gyroY; lastGyroZ = gyroZ
        lastLinAccX = linAccX; lastLinAccY = linAccY; lastLinAccZ = linAccZ
    }

    fun processCurrentSample(timestamp: Long): GestureType? {
        if (timestamp - lastGestureTime < GESTURE_COOLDOWN_MS) return null
        val wrist = wristDetector.process(timestamp, lastGyroX, lastGyroY, lastGyroZ, lastLinAccX, lastLinAccY, lastLinAccZ)
        val pinch = pinchDetector.process(timestamp, lastGyroX, lastGyroY, lastGyroZ, lastLinAccX, lastLinAccY, lastLinAccZ)
        val gesture = wrist ?: pinch ?: return null
        lastGestureTime = timestamp
        return gesture
    }
}
