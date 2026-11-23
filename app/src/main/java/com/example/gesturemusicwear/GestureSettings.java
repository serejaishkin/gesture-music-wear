package com.example.gesturemusicwear;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for persisted user settings (keys + defaults + migrations),
 * shared by MainActivity and GestureForegroundService.
 */
public final class GestureSettings {
    public static final String PREFS_NAME = "GestureWearPrefs";

    public boolean active = true;
    public boolean leftHand = true;
    public float angleThreshold = 35f;
    public float pinchThreshold = 2.2f;
    public float clenchThreshold = 2.2f;
    public boolean haptics = true;
    /** Media gestures only work for 12 s after a fist clench. Off by default. */
    public boolean fistGuard = false;
    /** Background service ignores motion unless music is playing. */
    public boolean onlyWhenPlaying = false;
    public boolean advancedMode = false;
    public boolean debugMode = false;

    public static GestureSettings load(Context ctx) {
        GestureSettings s = new GestureSettings();
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            s.active = p.getBoolean("active", s.active);
            s.leftHand = p.getBoolean("left_hand", s.leftHand);
            s.angleThreshold = p.getFloat("angle_thresh", s.angleThreshold);
            s.pinchThreshold = p.getFloat("pinch_thresh", s.pinchThreshold);
            s.clenchThreshold = p.getFloat("clench_thresh", s.clenchThreshold);
            s.haptics = p.getBoolean("haptics", s.haptics);
            s.fistGuard = p.getBoolean("fist_guard", s.fistGuard);
            s.onlyWhenPlaying = p.getBoolean("only_when_playing", s.onlyWhenPlaying);
            s.advancedMode = p.getBoolean("advanced_mode", s.advancedMode);
            s.debugMode = p.getBoolean("debug_mode", s.debugMode);
            if (!p.getBoolean("guard_migrated_v2", false)) {
                // Media gestures must work out of the box; fist arming is opt-in.
                s.fistGuard = false;
                p.edit().putBoolean("fist_guard", false).putBoolean("guard_migrated_v2", true).apply();
            }
        } catch (Throwable ignored) {}
        return s;
    }

    public void save(Context ctx) {
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean("active", active)
                    .putBoolean("left_hand", leftHand)
                    .putFloat("angle_thresh", angleThreshold)
                    .putFloat("pinch_thresh", pinchThreshold)
                    .putFloat("clench_thresh", clenchThreshold)
                    .putBoolean("haptics", haptics)
                    .putBoolean("fist_guard", fistGuard)
                    .putBoolean("only_when_playing", onlyWhenPlaying)
                    .putBoolean("advanced_mode", advancedMode)
                    .putBoolean("debug_mode", debugMode)
                    .apply();
        } catch (Throwable ignored) {}
    }
}
