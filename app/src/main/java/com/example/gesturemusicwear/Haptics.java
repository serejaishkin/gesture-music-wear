package com.example.gesturemusicwear;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Vibration helpers shared by the activity and the service. */
public final class Haptics {
    private Haptics() {}

    @SuppressWarnings("deprecation")
    public static Vibrator get(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) return vm.getDefaultVibrator();
            }
        } catch (Throwable ignored) {}
        return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @SuppressWarnings("deprecation")
    public static void pulse(Context ctx, long ms) {
        try {
            Vibrator v = get(ctx);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {}
    }

    public static void doublePulse(Context ctx) {
        try {
            Vibrator v = get(ctx);
            if (v != null && v.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 60, 80}, -1));
                return;
            }
        } catch (Throwable ignored) {}
        pulse(ctx, 90);
    }
}
