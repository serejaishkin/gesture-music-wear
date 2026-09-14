package com.example.gesturemusicwear;

/**
 * Fist clench detector (activation/arming gesture).
 *
 * Uses raw accelerometer (TYPE_ACCELEROMETER, with gravity ~9.81 m/s²).
 * Discriminates from a single pinch spike via multi-axis spread: a clench
 * produces a shockwave across several axes, while a pinch excites one axis.
 * Also detects rapid jerk onset as an alternative trigger.
 */
public class FistClenchDetector {
    public static final int RESULT_NONE = 0;
    public static final int RESULT_ACTIVATE = 1;

    private float clenchThreshold; // g-force (e.g. 3.0)
    private long cooldownMs;
    private float maxGyroMagnitude; // rad/s

    private long lastGestureTime = 0;
    private float prevAccMag = 0;
    private long prevTimestamp = 0;
    private boolean isSettling = false;
    private long settleStartTime = 0;

    private static final float SPREAD_THRESHOLD = 2.0f; // m/s² per axis
    private static final float JERK_THRESHOLD = 42.0f;  // m/s³

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
            if (totalG < clenchThreshold * 0.5f || timestamp - settleStartTime > 350) {
                isSettling = false;
            }
            return RESULT_NONE;
        }

        int significantAxes = 0;
        if (Math.abs(accX) > SPREAD_THRESHOLD) significantAxes++;
        if (Math.abs(accY) > SPREAD_THRESHOLD) significantAxes++;
        if (Math.abs(accZ) > SPREAD_THRESHOLD) significantAxes++;
        boolean isBroadSpread = significantAxes >= 2;

        boolean isHighImpulse = totalG >= clenchThreshold && isBroadSpread;
        boolean isSharpJerk = jerk >= JERK_THRESHOLD && totalG >= clenchThreshold * 0.6f && isBroadSpread;

        if (isHighImpulse || isSharpJerk) {
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