package com.example.gesturemusicwear;

/** Pure mapping from average training peak to detector thresholds (unit-testable). */
public final class TrainingCalibrator {
    private TrainingCalibrator() {}

    /** avgDegPerSec: mean peak gyro speed (deg/s) over training reps -> angle threshold in degrees. */
    public static float angleThreshold(float avgDegPerSec) {
        return Math.max(20f, Math.min(70f, (avgDegPerSec / 3.5f) * 0.9f));
    }

    /** avgTotalG: mean peak total acceleration (g, ~1.0 at rest) -> pinch slider value (1.2..3.2). */
    public static float pinchThreshold(float avgTotalG) {
        return Math.max(1.2f, Math.min(3.2f, 1.0f + 2.0f * Math.max(0f, avgTotalG - 1.0f)));
    }

    /** avgTotalG -> fist slider value (1.4..4.0). */
    public static float clenchThreshold(float avgTotalG) {
        return Math.max(1.4f, Math.min(4.0f, 1.0f + 2.0f * Math.max(0f, avgTotalG - 1.0f)));
    }
}
