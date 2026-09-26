package com.example.gesturemusicwear;

/**
 * Fist clench detector (activation/arming gesture).
 *
 * Uses raw accelerometer (TYPE_ACCELEROMETER, with gravity ~9.81 m/s²).
 * Discriminates from a single pinch spike via multi-axis spread: a clench
 * produces a shockwave across several axes, while a pinch excites one axis.
 * Also detects rapid jerk onset as an alternative trigger.
 *
 * Improved thresholds for better detection on real devices:
 * - Lower clench threshold (2.2g instead of 3.0g) for easier activation
 * - Reduced spread threshold (1.5 m/s² instead of 2.0 m/s²) for better sensitivity
 * - Lower jerk threshold (25.0 m/s³ instead of 42.0 m/s³) for faster detection
 * - Improved settling logic for better user experience
 */
public class FistClenchDetector {
    public static final int RESULT_NONE = 0;
    public static final int RESULT_ACTIVATE = 1;

    private float clenchThreshold; // g-force (e.g. 2.2)
    private long cooldownMs;
    private float maxGyroMagnitude; // rad/s

    private long lastGestureTime = 0;
    private float prevAccMag = 0;
    private long prevTimestamp = 0;
    private boolean isSettling = false;
    private long settleStartTime = 0;

    private static final float SPREAD_THRESHOLD = 1.5f; // m/s² per axis (reduced from 2.0f)
    private static final float JERK_THRESHOLD = 25.0f;  // m/s³ (reduced from 42.0f)

    public FistClenchDetector(float clenchThreshold, long cooldownMs, float maxGyroMagnitude) {
        this.clenchThreshold = clenchThreshold;
        this.cooldownMs = cooldownMs;
        this.maxGyroMagnitude = maxGyroMagnitude;
    }

    public void updateSettings(float clenchThreshold, long cooldownMs) {
        this.clenchThreshold = clenchThreshold;
        this.cooldownMs = cooldownMs;
    }

    public int process(long timestamp, float gx, float gy, float gz,
                       float accX, float accY, float accZ) {
        if (timestamp - lastGestureTime < cooldownMs) {
            prevTimestamp = timestamp;
            return RESULT_NONE;
        }

        float gyroMag = Math.max(Math.abs(gx), Math.max(Math.abs(gy), Math.abs(gz)));
        if (gyroMag > maxGyroMagnitude) {
            isSettling = false;
            prevTimestamp = timestamp;
            return RESULT_NONE;
        }

        float accMag = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);
        float totalG = accMag / 9.80665f;

        float dt = prevTimestamp > 0 ? Math.min(0.1f, (timestamp - prevTimestamp) / 1000f) : 0.02f;
        float jerk = dt > 0.001f ? Math.abs(accMag - prevAccMag) / dt : 0;

        prevAccMag = accMag;
        prevTimestamp = timestamp;

        if (isSettling) {
            // Improved settling logic with longer timeout for better user experience
            if (totalG < clenchThreshold * 0.4f || timestamp - settleStartTime > 500) {
                isSettling = false;
            }
            return RESULT_NONE;
        }

        int significantAxes = 0;
        if (Math.abs(accX) > SPREAD_THRESHOLD) significantAxes++;
        if (Math.abs(accY) > SPREAD_THRESHOLD) significantAxes++;
        if (Math.abs(accZ) > SPREAD_THRESHOLD) significantAxes++;
        boolean isBroadSpread = significantAxes >= 2;

        // Improved trigger conditions with lower thresholds
        boolean isHighImpulse = totalG >= clenchThreshold && isBroadSpread;
        boolean isSharpJerk = jerk >= JERK_THRESHOLD && totalG >= clenchThreshold * 0.5f && isBroadSpread;

        // Additional condition: single-axis strong impulse (for less forceful clenches)
        boolean isStrongSingleAxis = totalG >= clenchThreshold * 1.2f && significantAxes >= 1;

        if (isHighImpulse || isSharpJerk || isStrongSingleAxis) {
            lastGestureTime = timestamp;
            isSettling = true;
            settleStartTime = timestamp;
            return RESULT_ACTIVATE;
        }

        return RESULT_NONE;
    }

    public void reset() {
        lastGestureTime = 0;
        prevAccMag = 0;
        prevTimestamp = 0;
        isSettling = false;
        settleStartTime = 0;
    }
}