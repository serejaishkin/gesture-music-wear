package com.example.gesturemusicwear;

/**
 * Platform-free gesture pipeline shared by MainActivity and GestureForegroundService.
 * No Android imports: it can be unit-tested and replayed on a PC against recorded IMU CSV
 * (see tools/Replay.java).
 *
 * Logic is identical to the previous per-class processSensors(): cross-gesture debounce,
 * optional fist arming window, then wrist -> pinch.
 */
public class GestureEngine {
    public static final int NONE = 0;
    public static final int NEXT = 1;
    public static final int PREVIOUS = 2;
    public static final int PLAY_PAUSE = 3;
    public static final int FIST_ARM = 4;
    /** Fist guard is on and media gestures are blocked until a fist arms the engine. */
    public static final int ACTIVATION_NEEDED = 5;

    public static final long GESTURE_COOLDOWN_MS = 650L;
    public static final long CROSS_GESTURE_DEBOUNCE_MS = 300L;
    public static final long ARM_GUARD_WINDOW_MS = 12000L;

    private final WristRotationDetector wrist;
    private final DoublePinchDetector pinch;
    private final FistClenchDetector fist;

    private boolean fistGuard;
    private boolean armed;
    private long armedUntil;
    private long lastGestureTs;

    public GestureEngine(float angleThreshold, float pinchThreshold, float clenchThreshold,
                         boolean leftHand, boolean fistGuard) {
        this.fistGuard = fistGuard;
        wrist = new WristRotationDetector(angleThreshold, 0.8f, 100, 1000, GESTURE_COOLDOWN_MS,
                800, 0.25f, 200, 35.0f, leftHand);
        pinch = new DoublePinchDetector(pinchThreshold, -(pinchThreshold * 0.6f),
                4.0f, 900, GESTURE_COOLDOWN_MS, 2.5f);
        fist = new FistClenchDetector(clenchThreshold, 1200, 2.2f);
    }

    /** Detector accessors: callers may tune/reset them and read live angle for diagnostics. */
    public WristRotationDetector wrist() { return wrist; }
    public DoublePinchDetector pinch() { return pinch; }
    public FistClenchDetector fist() { return fist; }

    public void setFistGuard(boolean enabled) { this.fistGuard = enabled; }

    public boolean isArmed(long now) { return armed && now <= armedUntil; }

    public void reset() {
        wrist.reset();
        pinch.reset();
        fist.reset();
        armed = false;
        armedUntil = 0;
        lastGestureTs = 0;
    }

    /** Feed one sample (timestamp in ms, gyro rad/s, raw accelerometer m/s^2). */
    public int process(long now, float gx, float gy, float gz, float ax, float ay, float az) {
        if (now - lastGestureTs < CROSS_GESTURE_DEBOUNCE_MS) return NONE;

        if (fistGuard) {
            if (fist.process(now, gx, gy, gz, ax, ay, az) == FistClenchDetector.RESULT_ACTIVATE) {
                arm(now);
                return FIST_ARM;
            }
        }

        boolean canExecute = !fistGuard || isArmed(now);
        if (!canExecute) return ACTIVATION_NEEDED;

        int w = wrist.process(now, gx, gy, gz, ax, ay, az);
        if (w == WristRotationDetector.RESULT_NEXT) { arm(now); return NEXT; }
        if (w == WristRotationDetector.RESULT_PREVIOUS) { arm(now); return PREVIOUS; }

        if (pinch.process(now, gx, gy, gz, ax, ay, az) == DoublePinchDetector.RESULT_PLAY_PAUSE) {
            arm(now);
            return PLAY_PAUSE;
        }
        return NONE;
    }

    private void arm(long now) {
        lastGestureTs = now;
        armed = true;
        armedUntil = now + ARM_GUARD_WINDOW_MS;
    }
}
