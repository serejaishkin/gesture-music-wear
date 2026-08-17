package com.yourname.gesturemusic.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.gesturemusic.data.SettingsRepository
import com.yourname.gesturemusic.service.GestureMusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GestureViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settings = SettingsRepository(context)

    private val _isRunning = mutableStateOf(false)
    val isRunning: State<Boolean> = _isRunning

    private val _lastGesture = mutableStateOf("")
    val lastGesture: State<String> = _lastGesture

    private val _angleThreshold = mutableStateOf(settings.angleThreshold)
    val angleThreshold: State<Float> = _angleThreshold

    private val _pinchThreshold = mutableStateOf(settings.pinchThreshold)
    val pinchThreshold: State<Float> = _pinchThreshold

    private val _minDuration = mutableStateOf(settings.minDuration)
    val minDuration: State<Long> = _minDuration

    private val _maxDuration = mutableStateOf(settings.maxDuration)
    val maxDuration: State<Long> = _maxDuration

    private val _gestureCooldown = mutableStateOf(settings.gestureCooldown)
    val gestureCooldown: State<Long> = _gestureCooldown

    private val _leftHand = mutableStateOf(settings.leftHand)
    val leftHand: State<Boolean> = _leftHand

    private val _saveMessage = mutableStateOf("")
    val saveMessage: State<String> = _saveMessage

    private val gestureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val gesture = intent?.getStringExtra("gesture")
            gesture?.let {
                _lastGesture.value = when (it) {
                    "NEXT_TRACK" -> "➡️ Следующий трек"
                    "PREVIOUS_TRACK" -> "⬅️ Предыдущий трек"
                    "PLAY_PAUSE" -> "⏯️ Play / Pause"
                    else -> it
                }
            }
        }
    }

    init {
        context.registerReceiver(
            gestureReceiver,
            IntentFilter("com.yourname.gesturemusic.GESTURE_DETECTED"),
            Context.RECEIVER_NOT_EXPORTED
        )
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
    }

    fun updatePinchThreshold(value: Float) {
        _pinchThreshold.value = value
    }

    fun updateMinDuration(value: Long) {
        _minDuration.value = value
    }

    fun updateMaxDuration(value: Long) {
        _maxDuration.value = value
    }

    fun updateGestureCooldown(value: Long) {
        _gestureCooldown.value = value
    }

    fun updateLeftHand(value: Boolean) {
        _leftHand.value = value
    }

    fun saveSettings() {
        viewModelScope.launch {
            settings.angleThreshold = _angleThreshold.value
            settings.pinchThreshold = _pinchThreshold.value
            settings.minDuration = _minDuration.value
            settings.maxDuration = _maxDuration.value
            settings.gestureCooldown = _gestureCooldown.value
            settings.leftHand = _leftHand.value

            sendSensitivityUpdate()

            _saveMessage.value = "✓ Сохранено"
            delay(2000)
            _saveMessage.value = ""
        }
    }

    fun restoreDefaults() {
        _angleThreshold.value = 22f
        _pinchThreshold.value = 3.0f
        _minDuration.value = 150L
        _maxDuration.value = 600L
        _gestureCooldown.value = 1200L
        _leftHand.value = false
        saveSettings()
    }

    private fun sendSensitivityUpdate() {
        val intent = Intent(context, GestureMusicService::class.java).apply {
            action = GestureMusicService.ACTION_UPDATE_SENSITIVITY
            putExtra(GestureMusicService.EXTRA_ANGLE_THRESHOLD, _angleThreshold.value)
            putExtra(GestureMusicService.EXTRA_PINCH_THRESHOLD, _pinchThreshold.value)
            putExtra(GestureMusicService.EXTRA_COOLDOWN, _gestureCooldown.value)
            putExtra(GestureMusicService.EXTRA_LEFT_HAND, _leftHand.value)
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(gestureReceiver)
    }
}
