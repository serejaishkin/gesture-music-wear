package com.yourname.gesturemusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var angleThreshold: Float
        get() = prefs.getFloat(KEY_ANGLE, DEFAULT_ANGLE)
        set(value) = prefs.edit { putFloat(KEY_ANGLE, value) }

    var pinchThreshold: Float
        get() = prefs.getFloat(KEY_PINCH, DEFAULT_PINCH)
        set(value) = prefs.edit { putFloat(KEY_PINCH, value) }

    // !!! Новые параметры
    var minDuration: Long
        get() = prefs.getLong(KEY_MIN_DURATION, DEFAULT_MIN_DURATION)
        set(value) = prefs.edit { putLong(KEY_MIN_DURATION, value) }

    var maxDuration: Long
        get() = prefs.getLong(KEY_MAX_DURATION, DEFAULT_MAX_DURATION)
        set(value) = prefs.edit { putLong(KEY_MAX_DURATION, value) }

    companion object {
        private const val PREFS_NAME = "gesture_music_settings"
        private const val KEY_ANGLE = "angle_threshold"
        private const val KEY_PINCH = "pinch_threshold"
        private const val KEY_MIN_DURATION = "min_duration"
        private const val KEY_MAX_DURATION = "max_duration"
        private const val DEFAULT_ANGLE = 22f
        private const val DEFAULT_PINCH = 3.0f
        private const val DEFAULT_MIN_DURATION = 150L
        private const val DEFAULT_MAX_DURATION = 600L
    }
}
