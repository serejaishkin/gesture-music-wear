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
import com.yourname.gesturemusic.service.GestureMusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана управления.
 * Управляет состоянием сервиса, настройками чувствительности и отображением жестов.
 */
class GestureViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _isRunning = mutableStateOf(false)
    val isRunning: State<Boolean> = _isRunning

    private val _lastGesture = mutableStateOf<String>("")
    val lastGesture: State<String> = _lastGesture

    private val _angleThreshold = mutableStateOf(22f)
    val angleThreshold: State<Float> = _angleThreshold

    private val _pinchThreshold = mutableStateOf(3.0f)
    val pinchThreshold: State<Float> = _pinchThreshold

    private val _isCalibrating = mutableStateOf(false)
    val isCalibrating: State<Boolean> = _isCalibrating

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

    private fun sendSensitivityUpdate() {
        val intent = Intent(context, GestureMusicService::class.java).apply {
            action = GestureMusicService.ACTION_UPDATE_SENSITIVITY
            putExtra(GestureMusicService.EXTRA_ANGLE_THRESHOLD, _angleThreshold.value)
            putExtra(GestureMusicService.EXTRA_PINCH_THRESHOLD, _pinchThreshold.value)
        }
        context.startService(intent)
    }

    fun calibrate() {
        viewModelScope.launch {
            _isCalibrating.value = true
            _lastGesture.value = "Калибровка..."
            // В реальном приложении здесь можно отправить команду сервису
            // на снятие offset гироскопа
            kotlinx.coroutines.delay(3000)
            _isCalibrating.value = false
            _lastGesture.value = "Калибровка завершена"
        }
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(gestureReceiver)
    }
}
