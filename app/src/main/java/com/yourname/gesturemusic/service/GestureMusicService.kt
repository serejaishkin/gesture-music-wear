package com.yourname.gesturemusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.gesturemusic.MainActivity
import com.yourname.gesturemusic.R
import com.yourname.gesturemusic.gesture.DoublePinchDetector
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.gesture.WristRotationDetector
import com.yourname.gesturemusic.media.MediaControllerManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * ForegroundService для распознавания жестов и управления музыкой.
 *
 * Работает с SensorManager (гироскоп + linear acceleration) и MediaController.
 * Автоотключает сенсоры через 30 секунд после паузы музыки.
 */
class GestureMusicService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "GestureMusicService"
        private const val CHANNEL_ID = "gesture_music_channel"
        private const val NOTIFICATION_ID = 1
        private const val IDLE_TIMEOUT_MS = 30000L  // 30 секунд

        const val ACTION_START = "com.yourname.gesturemusic.ACTION_START"
        const val ACTION_STOP = "com.yourname.gesturemusic.ACTION_STOP"
        const val ACTION_UPDATE_SENSITIVITY = "com.yourname.gesturemusic.ACTION_UPDATE_SENSITIVITY"
        const val EXTRA_ANGLE_THRESHOLD = "angle_threshold"
        const val EXTRA_PINCH_THRESHOLD = "pinch_threshold"
    }

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var linearAccel: Sensor? = null

    private lateinit var mediaControllerManager: MediaControllerManager
    private lateinit var wristDetector: WristRotationDetector
    private lateinit var pinchDetector: DoublePinchDetector
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var vibrator: Vibrator

    private var isRunning = false
    private var isMusicPlaying = false
    private var lastSensorTimestamp = 0L

    private var idleExecutor: ScheduledExecutorService? = null
    private var idleTask: java.util.concurrent.ScheduledFuture<*>? = null

    // Последние значения сенсоров
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    private var lastLinAccX = 0f
    private var lastLinAccY = 0f
    private var lastLinAccZ = 0f

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        mediaControllerManager = MediaControllerManager(this)
        wristDetector = WristRotationDetector()
        pinchDetector = DoublePinchDetector()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GestureMusic::WakeLock"
        )
        wakeLock.setReferenceCounted(false)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        mediaControllerManager.setStateListener { playing ->
            isMusicPlaying = playing
            if (playing) {
                cancelIdleTimer()
            } else {
                startIdleTimer()
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGestureDetection()
            ACTION_STOP -> stopGestureDetection()
            ACTION_UPDATE_SENSITIVITY -> updateSensitivity(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopGestureDetection()
        mediaControllerManager.disconnect()
        idleExecutor?.shutdown()
    }

    // --- SensorEventListener ---

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val timestamp = System.currentTimeMillis()
        lastSensorTimestamp = timestamp

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]
                lastGyroY = event.values[1]
                lastGyroZ = event.values[2]
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                lastLinAccX = event.values[0]
                lastLinAccY = event.values[1]
                lastLinAccZ = event.values[2]

                // Обрабатываем жесты при получении linear acceleration
                // (частота обычно совпадает с gyro или выше)
                processGestures(timestamp)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Gesture Processing ---

    private fun processGestures(timestamp: Long) {
        val wristGesture = wristDetector.process(
            timestamp,
            lastGyroX, lastGyroY, lastGyroZ,
            lastLinAccX, lastLinAccY, lastLinAccZ
        )
        val pinchGesture = pinchDetector.process(
            timestamp,
            lastGyroX, lastGyroY, lastGyroZ,
            lastLinAccX, lastLinAccY, lastLinAccZ
        )

        val gesture = wristGesture ?: pinchGesture
        gesture?.let { executeGesture(it) }
    }

    private fun executeGesture(gesture: GestureType) {
        Log.d(TAG, "Gesture detected: $gesture")
        vibrate()

        when (gesture) {
            GestureType.NEXT_TRACK -> mediaControllerManager.nextTrack()
            GestureType.PREVIOUS_TRACK -> mediaControllerManager.previousTrack()
            GestureType.PLAY_PAUSE -> mediaControllerManager.playPause()
        }

        // Отправляем broadcast для UI
        sendBroadcast(Intent("com.yourname.gesturemusic.GESTURE_DETECTED").apply {
            putExtra("gesture", gesture.name)
        })
    }

    // --- Service Control ---

    private fun startGestureDetection() {
        if (isRunning) return
        isRunning = true

        mediaControllerManager.connect()
        mediaControllerManager.refreshConnection()

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccel?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L) // 10 минут макс, обновляем при активности
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Gesture detection started")
    }

    private fun stopGestureDetection() {
        if (!isRunning) return
        isRunning = false

        sensorManager.unregisterListener(this)
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        wristDetector.reset()
        pinchDetector.reset()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Gesture detection stopped")
    }

    private fun updateSensitivity(intent: Intent) {
        val angleThreshold = intent.getFloatExtra(EXTRA_ANGLE_THRESHOLD, 22f)
        val pinchThreshold = intent.getFloatExtra(EXTRA_PINCH_THRESHOLD, 3.0f)

        wristDetector = WristRotationDetector(angleThresholdDegrees = angleThreshold)
        pinchDetector = DoublePinchDetector(thresholdUp = pinchThreshold)

        Log.d(TAG, "Sensitivity updated: angle=$angleThreshold, pinch=$pinchThreshold")
    }

    // --- Idle Timer ---

    private fun startIdleTimer() {
        cancelIdleTimer()
        idleExecutor = idleExecutor ?: Executors.newSingleThreadScheduledExecutor()
        idleTask = idleExecutor?.schedule({
            Log.d(TAG, "Idle timeout reached, stopping sensors")
            runOnUiThread {
                if (!isMusicPlaying) {
                    sensorManager.unregisterListener(this@GestureMusicService)
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
        }, IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun cancelIdleTimer() {
        idleTask?.cancel(false)
        idleTask = null
        if (!isRunning) return
        // Перерегистрируем сенсоры, если были отключены
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccel?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L)
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gesture Music Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фоновое управление музыкой жестами"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, GestureMusicService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Music")
            .setContentText("Слушаю жесты запястья")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "Стоп", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- Haptic Feedback ---

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }

    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
}
