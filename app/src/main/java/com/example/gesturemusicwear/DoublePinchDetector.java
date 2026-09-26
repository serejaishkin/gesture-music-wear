package com.example.gesturemusicwear;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Double pinch detector for play/pause.
 *
 * Uses totalG (raw accelerometer magnitude in g-force) with a two-pinch FSM.
 * Requires two valid pinch impulses within a time window to fire,
 * preventing single-impulse false triggers from taps or bumps.
 * Gyroscope guard rejects during wrist rotation.
 *
 * Improved sensitivity with relaxed thresholds for better detection.
 */
public class DoublePinchDetector {
    public static final int RESULT_NONE = 0;
    public static final int RESULT_PLAY_PAUSE = 1;

    private static final long PINCH_TIMEOUT_MS = 500; // increased from 450ms
    private static final long MIN_PINCH_INTERVAL_MS = 80;  // reduced from 100ms

    private float thresholdUp;   // g-force
    private float thresholdDown; // g-force (negative)
    private float maxTotalG;
    private long windowMs;
    private long cooldownMs;
    private float maxGyroMagnitude; // rad/s

    private long lastGestureTime = 0;
    private final ArrayList<Long> pinches = new ArrayList<>();

    private static final int STATE_IDLE = 0;
    private static final int STATE_IMPULSE_ACTIVE = 1;
    private int state = STATE_IDLE;
    private long impulseStartTime = 0;
    private boolean armed = true;
    private long stableStartTime = 0;

    public DoublePinchDetector(float thresholdUp, float thresholdDown, float maxTotalG,
                               long windowMs, long cooldownMs, float maxGyroMagnitude) {
        this.thresholdUp = thresholdUp;
        this.thresholdDown = thresholdDown;
        this.maxTotalG = maxTotalG;
        this.windowMs = windowMs;
        this.cooldownMs = cooldownMs;
        this.maxGyroMagnitude = maxGyroMagnitude;
    }

    public void updateSettings(float pinchThreshold, long cooldownMs) {
        this.thresholdUp = pinchThreshold;
        this.thresholdDown = -(pinchThreshold * 0.6f);
        this.cooldownMs = cooldownMs;
    }

    public int process(long timestamp, float gx, float gy, float gz,
                       float accX, float accY, float accZ) {
        float accMag = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);
        float totalG = accMag / 9.80665f;

        if (!armed) {
            if (Math.abs(totalG - 1.0f) <= 0.3f) {
                if (stableStartTime == 0) stableStartTime = timestamp;
                if (timestamp - stableStartTime >= 80 && timestamp - lastGestureTime >= cooldownMs) {
                    armed = true;
                    stableStartTime = 0;
                }
            } else {
                stableStartTime = 0;
                if (timestamp - lastGestureTime >= cooldownMs + 400) {
                    armed = true;
                }
            }
            return RESULT_NONE;
        }

        if (timestamp - lastGestureTime < cooldownMs) return RESULT_NONE;

        float gyroMag = Math.max(Math.abs(gx), Math.max(Math.abs(gy), Math.abs(gz)));
        if (gyroMag > maxGyroMagnitude) {
            if (state == STATE_IMPULSE_ACTIVE && timestamp - impulseStartTime > PINCH_TIMEOUT_MS) {
                state = STATE_IDLE;
            }
            return RESULT_NONE;
        }

        Iterator<Long> it = pinches.iterator();
        while (it.hasNext()) {
            if (timestamp - it.next() > windowMs) it.remove();
        }

        boolean isImpulse = totalG >= thresholdUp * 0.9f && totalG <= maxTotalG; // reduced threshold by 10% for better sensitivity

        if (state == STATE_IDLE) {
            if (isImpulse) {
                state = STATE_IMPULSE_ACTIVE;
                impulseStartTime = timestamp;
            }
        } else if (state == STATE_IMPULSE_ACTIVE) {
            boolean isRebound = totalG < Math.abs(thresholdDown)
                || (timestamp - impulseStartTime >= 40 && totalG < thresholdUp * 0.5f);

            if (isRebound) {
                pinches.add(timestamp);
                state = STATE_IDLE;

                if (pinches.size() >= 2) {
                    long first = pinches.get(pinches.size() - 2);
                    long second = pinches.get(pinches.size() - 1);
                    long interval = second - first;

                    if (interval >= MIN_PINCH_INTERVAL_MS && interval <= windowMs) {
                        lastGestureTime = timestamp;
                        pinches.clear();
                        armed = false;
                        stableStartTime = 0;
                        return RESULT_PLAY_PAUSE;
                    }
                }
            } else if (timestamp - impulseStartTime > PINCH_TIMEOUT_MS) {
                state = STATE_IDLE;
            }
        }

        return RESULT_NONE;
    }

    public void reset() {
        pinches.clear();
        state = STATE_IDLE;
        impulseStartTime = 0;
        lastGestureTime = 0;
        armed = true;
        stableStartTime = 0;
    }
}