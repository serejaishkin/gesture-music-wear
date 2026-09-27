package com.example.gesturemusicwear;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Universal wrist rotation detector for track switching.
 *
 * Simplified and more sensitive version for better real-world detection.
 * Auto-detects the dominant rotation axis (Gyro X or Gyro Y), applies
 * low-pass filtering and trapezoidal angle integration over a sliding window.
 * Requires peak angular velocity AND cumulative angle threshold.
 * 3D acceleration magnitude gate rejects running/flailing.
 */
public class WristRotationDetector {
    public static final int RESULT_NONE = 0;
    public static final int RESULT_NEXT = 1;
    public static final int RESULT_PREVIOUS = -1;

    private static class Sample {
        long timestamp;
        float gx, gy, accMag;
        Sample(long ts, float gx, float gy, float am) {
            this.timestamp = ts;
            this.gx = gx;
            this.gy = gy;
            this.accMag = am;
        }
    }

    private float angleThresholdDegrees;
    private float minAngularSpeed;
    private int minDurationMs;
    private int maxDurationMs;
    private long cooldownMs;
    private int windowMs;
    private float idleThreshold;
    private int idleTimeoutMs;
    private float antiNoiseAccMag;
    private boolean leftHand;

    private static final float ALPHA = 0.8f; // Increased for smoother filtering
    private final ArrayList<Sample> samples = new ArrayList<>();
    private long lastGestureTime = 0;
    private long idleStartTime = 0;
    private float lastAngleDegrees = 0;
    private float liveGx = 0;
    private float liveGy = 0;

    public WristRotationDetector(float angleThresholdDegrees, float minAngularSpeed,
                                 int minDurationMs, int maxDurationMs, long cooldownMs,
                                 int windowMs, float idleThreshold, int idleTimeoutMs,
                                 float antiNoiseAccMag, boolean leftHand) {
        this.angleThresholdDegrees = angleThresholdDegrees;
        this.minAngularSpeed = minAngularSpeed;
        this.minDurationMs = minDurationMs;
        this.maxDurationMs = maxDurationMs;
        this.cooldownMs = cooldownMs;
        this.windowMs = windowMs;
        this.idleThreshold = idleThreshold;
        this.idleTimeoutMs = idleTimeoutMs;
        this.antiNoiseAccMag = antiNoiseAccMag;
        this.leftHand = leftHand;
    }

    public void updateSettings(float angleThresholdDegrees, long cooldownMs,
                               boolean leftHand, int minDurationMs, int maxDurationMs) {
        this.angleThresholdDegrees = angleThresholdDegrees;
        this.cooldownMs = cooldownMs;
        this.leftHand = leftHand;
        this.minDurationMs = minDurationMs;
        this.maxDurationMs = maxDurationMs;
    }

    public int process(long timestamp, float gx, float gy, float gz,
                       float accX, float accY, float accZ) {
        if (timestamp - lastGestureTime < cooldownMs) return RESULT_NONE;

        float accMag = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);
        if (accMag > antiNoiseAccMag) {
            resetWindow();
            return RESULT_NONE;
        }

        liveGx = ALPHA * liveGx + (1 - ALPHA) * gx;
        liveGy = ALPHA * liveGy + (1 - ALPHA) * gy;

        float maxAbsLive = Math.max(Math.abs(liveGx), Math.abs(liveGy));

        if (maxAbsLive < idleThreshold) {
            if (!samples.isEmpty()) {
                if (idleStartTime == 0) idleStartTime = timestamp;
                if (timestamp - idleStartTime > idleTimeoutMs) {
                    resetWindow();
                }
            }
            return RESULT_NONE;
        }
        idleStartTime = 0;

        Iterator<Sample> it = samples.iterator();
        while (it.hasNext()) {
            if (timestamp - it.next().timestamp > windowMs) it.remove();
        }
        samples.add(new Sample(timestamp, gx, gy, accMag));

        if (samples.size() < 2) return RESULT_NONE;

        // Simplified axis selection - use the axis with higher total movement
        float totalSpeedX = 0;
        float totalSpeedY = 0;
        for (Sample s : samples) {
            totalSpeedX += Math.abs(s.gx);
            totalSpeedY += Math.abs(s.gy);
        }

        boolean useAxisX = totalSpeedX >= totalSpeedY;

        // Check minimum speed requirement
        float maxSpeed = useAxisX ? totalSpeedX : totalSpeedY;
        if (maxSpeed < minAngularSpeed * samples.size()) return RESULT_NONE;

        // Simplified integration - just integrate all samples
        float integratedAngleRad = 0;
        for (int i = 1; i < samples.size(); i++) {
            float vCur = useAxisX ? samples.get(i).gx : samples.get(i).gy;
            float vPrev = useAxisX ? samples.get(i - 1).gx : samples.get(i - 1).gy;
            float dt = (samples.get(i).timestamp - samples.get(i - 1).timestamp) / 1000f;
            integratedAngleRad += ((vCur + vPrev) / 2f) * dt;
        }
        float angleDegrees = (float) Math.toDegrees(integratedAngleRad);

        long duration = samples.get(samples.size() - 1).timestamp - samples.get(0).timestamp;
        if (duration < minDurationMs) return RESULT_NONE;
        if (duration > maxDurationMs) {
            resetWindow();
            return RESULT_NONE;
        }

        float effectiveThreshold = getEffectiveThreshold();

        if (leftHand) angleDegrees = -angleDegrees;
        lastAngleDegrees = angleDegrees;

        if (angleDegrees > effectiveThreshold) {
            lastGestureTime = timestamp;
            resetWindow();
            return RESULT_NEXT;
        }
        if (angleDegrees < -effectiveThreshold) {
            lastGestureTime = timestamp;
            resetWindow();
            return RESULT_PREVIOUS;
        }

        return RESULT_NONE;
    }

    public float getLastAngleDegrees() { return lastAngleDegrees; }

    public float getEffectiveThreshold() {
        // Much more sensitive threshold for better real-world detection
        float t = Math.min(angleThresholdDegrees * 0.5f, 25f); // reduced from 0.7f to 0.5f, max 25f
        if (t < 8f) t = 8f; // reduced minimum from 12f to 8f
        return t;
    }

    /** Current accumulated angle of the live window (for on-device diagnostics). */
    public float getLiveAngleDegrees() {
        if (samples.size() < 2) return 0;

        // Simplified live angle calculation matching the main process logic
        float totalSpeedX = 0;
        float totalSpeedY = 0;
        for (Sample s : samples) {
            totalSpeedX += Math.abs(s.gx);
            totalSpeedY += Math.abs(s.gy);
        }

        boolean useAxisX = totalSpeedX >= totalSpeedY;

        float integ = 0;
        for (int i = 1; i < samples.size(); i++) {
            float vCur = useAxisX ? samples.get(i).gx : samples.get(i).gy;
            float vPrev = useAxisX ? samples.get(i - 1).gx : samples.get(i - 1).gy;
            float dt = (samples.get(i).timestamp - samples.get(i - 1).timestamp) / 1000f;
            integ += ((vCur + vPrev) / 2f) * dt;
        }
        float deg = (float) Math.toDegrees(integ);
        if (leftHand) deg = -deg;
        return deg;
    }

    public void reset() {
        resetWindow();
        liveGx = 0;
        liveGy = 0;
        lastGestureTime = 0;
        lastAngleDegrees = 0;
    }

    private void resetWindow() {
        samples.clear();
        idleStartTime = 0;
    }
}