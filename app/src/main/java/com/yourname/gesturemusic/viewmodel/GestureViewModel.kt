package com.yourname.gesturemusic.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.gesturemusic.service.GestureMusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GestureViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)

    private val _isRunning = mutableStateOf(false)
    val isRunning: State<Boolean> = _isRunning

    private val _lastGesture = mutableStateOf("")
    val lastGesture: State<String> = _lastGesture

    private val _angleThreshold = mutableStateOf(prefs.getFloat("angle_threshold", 28f))
    val angleThreshold: State<Float> = _angleThreshold

    private val _pinchThreshold = mutableStateOf(prefs.getFloat("pinch_threshold", 3.5f))
    val pinchThreshold: State<Float> = _pinchThreshold

    private val _minDuration = mutableStateOf(prefs.getLong("min_duration", 160L))
    val minDuration: State<Long> = _minDuration

    private val _maxDuration = mutableStateOf(prefs.getLong("max_duration", 600L))
    val maxDuration: State<Long> = _maxDuration

    private val _gestureCooldown = mutableStateOf(prefs.getLong("gesture_cooldown", 1200L))
    val gestureCooldown: State<Long> = _gestureCooldown

    private val _leftHand = mutableStateOf(prefs.getBoolean("left_hand", false))
    val leftHand: State<Boolean> = _leftHand

    private val _saveMessage = mutableStateOf("")
    val saveMessage: State<String> = _saveMessage

    private val gestureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.yourname.gesturemusic.GESTURE_DETECTED" -> {
                    val gesture = intent.getStringExtra("gesture")
                    gesture?.let {
                        _lastGesture.value = when (it) {
                            "NEXT_TRACK" -> "➡️ Следующий трек"
                            "PREVIOUS_TRACK" -> "⬅️ Предыдущий трек"
                            "PLAY_PAUSE" -> "⏯️ Play / Pause"
                            "ACTIVATE" -> "🔓 Активация"
                            else -> it
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction("com.yourname.gesturemusic.GESTURE_DETECTED")
        }
        context.registerReceiver(gestureReceiver, filter, Context.RECEIVER_EXPORTED)
        sendSensitivityUpdate()
    }

    fun startService() {
        val intent = Intent(context, GestureMusicService::class.java).apply {
            action = GestureMusicService.ACTION_START
        }
        context.startForegroundService(intent)
        _isRunning.value = true
    }

    fun stopService() {
        val intent = Intent(context, GestureMusicService::class.java).apply {
            action = GestureMusicService.ACTION_STOP
        }
        context.startService(intent)
        _isRunning.value = false
    }

    fun updateAngleThreshold(value: Float) {
        _angleThreshold.value = value
        sendSensitivityUpdate()
    }

    fun updatePinchThreshold(value: Float) {
        _pinchThreshold.value = value
        sendSensitivityUpdate()
    }

    fun updateMinDuration(value: Long) {
        _minDuration.value = value
        sendSensitivityUpdate()
    }

    fun updateMaxDuration(value: Long) {
        _maxDuration.value = value
        sendSensitivityUpdate()
    }

    fun updateGestureCooldown(value: Long) {
        _gestureCooldown.value = value
        sendSensitivityUpdate()
    }

    fun updateLeftHand(value: Boolean) {
        _leftHand.value = value
        sendSensitivityUpdate()
    }

    fun saveSettings() {
        prefs.edit().apply {
            putFloat("angle_threshold", _angleThreshold.value)
            putFloat("pinch_threshold", _pinchThreshold.value)
            putLong("min_duration", _minDuration.value)
            putLong("max_duration", _maxDuration.value)
            putLong("gesture_cooldown", _gestureCooldown.value)
            putBoolean("left_hand", _leftHand.value)
            apply()
        }
        _saveMessage.value = "Сохранено"
        viewModelScope.launch {
            delay(1500)
            _saveMessage.value = ""
        }
    }

    fun restoreDefaults() {
        _angleThreshold.value = 28f
        _pinchThreshold.value = 3.5f
        _minDuration.value = 160L
        _maxDuration.value = 600L
        _gestureCooldown.value = 1200L
        _leftHand.value = false
        saveSettings()
        sendSensitivityUpdate()
    }

    private fun sendSensitivityUpdate() {
        val intent = Intent(context, GestureMusicService::class.java).apply {
            action = GestureMusicService.ACTION_UPDATE_SENSITIVITY
            putExtra(GestureMusicService.EXTRA_ANGLE_THRESHOLD, _angleThreshold.value)
            putExtra(GestureMusicService.EXTRA_PINCH_THRESHOLD, _pinchThreshold.value)
            putExtra(GestureMusicService.EXTRA_COOLDOWN, _gestureCooldown.value)
            putExtra(GestureMusicService.EXTRA_LEFT_HAND, _leftHand.value)
            putExtra(GestureMusicService.EXTRA_MIN_DURATION, _minDuration.value)
            putExtra(GestureMusicService.EXTRA_MAX_DURATION, _maxDuration.value)
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(gestureReceiver)
    }
}
