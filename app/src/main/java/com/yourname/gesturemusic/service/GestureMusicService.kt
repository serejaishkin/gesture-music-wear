package com.yourname.gesturemusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.gesturemusic.MainActivity
import com.yourname.gesturemusic.R
import com.yourname.gesturemusic.gesture.DoublePinchDetector
import com.yourname.gesturemusic.gesture.GestureArmingManager
import com.yourname.gesturemusic.gesture.GestureTrainer
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.gesture.WristRotationDetector
import com.yourname.gesturemusic.media.MediaControllerManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GestureMusicService : Service(), SensorEventListener {
    companion object {
        private const val TAG = "GestureMusicService"
        private const val CHANNEL_ID = "gesture_music_channel"
        private const val NOTIFICATION_ID = 1
        private const val IDLE_TIMEOUT_MS = 30000L
        private const val GESTURE_COOLDOWN_MS = 1000L
        private const val SCREEN_OFF_BLOCK_MS = 800L

        const val ACTION_START = "com.yourname.gesturemusic.ACTION_START"
        const val ACTION_STOP = "com.yourname.gesturemusic.ACTION_STOP"
        const val ACTION_UPDATE_SENSITIVITY = "com.yourname.gesturemusic.ACTION_UPDATE_SENSITIVITY"
        const val ACTION_START_TRAINING = "com.yourname.gesturemusic.ACTION_START_TRAINING"
        const val ACTION_STOP_TRAINING = "com.yourname.gesturemusic.ACTION_STOP_TRAINING"
        const val ACTION_CLEAR_TRAINING = "com.yourname.gesturemusic.ACTION_CLEAR_TRAINING"
        const val EXTRA_ANGLE_THRESHOLD = "angle_threshold"
        const val EXTRA_PINCH_THRESHOLD = "pinch_threshold"
        const val EXTRA_COOLDOWN = "gesture_cooldown"
        const val EXTRA_LEFT_HAND = "left_hand"
        const val EXTRA_TRAINING_GESTURE = "training_gesture"
    }

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var linearAccel: Sensor? = null
    private lateinit var mediaControllerManager: MediaControllerManager
    private lateinit var wristDetector: WristRotationDetector
    private lateinit var pinchDetector: DoublePinchDetector
    private lateinit var gestureTrainer: GestureTrainer
    private lateinit var armingManager: GestureArmingManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var vibrator: Vibrator
    private lateinit var notificationManager: NotificationManager

    private var isRunning = false
    private var isMusicPlaying = false
    private var lastSensorTimestamp = 0L
    private var lastGestureTime = 0L
    private var screenOffTime = -1L
    private var isTrainingMode = false
    private var trainingGestureType: GestureType? = null
    private var idleExecutor: ScheduledExecutorService? = null
    private var idleTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    private var lastLinAccX = 0f
    private var lastLinAccY = 0f
    private var lastLinAccZ = 0f

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffTime = System.currentTimeMillis()
                    Log.d(TAG, "SCREEN_OFF — блокируем жесты на ${SCREEN_OFF_BLOCK_MS}мс")
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOffTime = -1L
                    Log.d(TAG, "SCREEN_ON — блок снят")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mediaControllerManager = MediaControllerManager(this)
        wristDetector = WristRotationDetector()
        pinchDetector = DoublePinchDetector()
        gestureTrainer = GestureTrainer(this)
        armingManager = GestureArmingManager()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GestureMusic::WakeLock")
        wakeLock.setReferenceCounted(false)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        notificationManager = getSystemService(NotificationManager::class.java)

        mediaControllerManager.setStateListener { playing ->
            isMusicPlaying = playing
            if (playing) cancelIdleTimer() else startIdleTimer()
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, screenFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, screenFilter)
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGestureDetection()
            ACTION_STOP -> stopGestureDetection()
            ACTION_UPDATE_SENSITIVITY -> updateSensitivity(intent)
            ACTION_START_TRAINING -> startTraining(intent)
            ACTION_STOP_TRAINING -> stopTraining()
            ACTION_CLEAR_TRAINING -> clearTraining()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopGestureDetection()
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        try { mediaControllerManager.disconnect() } catch (_: Exception) {}
        idleExecutor?.shutdown()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        startService(Intent(applicationContext, GestureMusicService::class.java).apply { action = ACTION_START })
        super.onTaskRemoved(rootIntent)
    }

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
                if (isTrainingMode) {
                    gestureTrainer.addSample(lastGyroX, lastGyroY, lastGyroZ, lastLinAccX, lastLinAccY, lastLinAccZ)
                    val progress = gestureTrainer.getRecordingProgress()
                    if (progress % 25 == 0 && progress > 0) vibrateShort()
                } else {
                    processGestures(timestamp)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processGestures(timestamp: Long) {
        if (screenOffTime > 0 && timestamp - screenOffTime < SCREEN_OFF_BLOCK_MS) return
        if (timestamp - lastGestureTime < GESTURE_COOLDOWN_MS) return
        armingManager.update(timestamp)

        val trainedGesture = gestureTrainer.recognize(
            lastGyroX, lastGyroY, lastGyroZ,
            lastLinAccX, lastLinAccY, lastLinAccZ
        )

        // A learned ACTIVATE gesture is deliberately checked before all media commands.
        if (trainedGesture == GestureType.ACTIVATE) {
            armingManager.activate(timestamp)
            lastGestureTime = timestamp
            vibrateLong()
            sendBroadcast(Intent("com.yourname.gesturemusic.GESTURE_DETECTED").apply { putExtra("gesture", GestureType.ACTIVATE.name) })
            return
        }

        // If an activation template exists, normal commands stay locked until activation.
        if (gestureTrainer.hasTrainedGesture(GestureType.ACTIVATE) && !armingManager.isArmed) return

        val gesture = trainedGesture ?: run {
            val wristGesture = wristDetector.process(
                timestamp, lastGyroX, lastGyroY, lastGyroZ,
                lastLinAccX, lastLinAccY, lastLinAccZ
            )
            val pinchGesture = pinchDetector.process(
                timestamp, lastGyroX, lastGyroY, lastGyroZ,
                lastLinAccX, lastLinAccY, lastLinAccZ
            )
            wristGesture ?: pinchGesture
        }
        gesture?.let {
            armingManager.touch(timestamp)
            executeGesture(it, timestamp)
        }
    }

    private fun executeGesture(gesture: GestureType, timestamp: Long = System.currentTimeMillis()) {
        if (gesture == GestureType.ACTIVATE) {
            armingManager.activate(timestamp)
            lastGestureTime = timestamp
            vibrateLong()
            return
        }
        lastGestureTime = timestamp
        vibrate()
        when (gesture) {
            GestureType.NEXT_TRACK -> mediaControllerManager.nextTrack()
            GestureType.PREVIOUS_TRACK -> mediaControllerManager.previousTrack()
            GestureType.PLAY_PAUSE -> mediaControllerManager.playPause()
            GestureType.ACTIVATE -> Unit
        }
        sendBroadcast(Intent("com.yourname.gesturemusic.GESTURE_DETECTED").apply { putExtra("gesture", gesture.name) })
    }

    private fun startTraining(intent: Intent) {
        val gestureName = intent.getStringExtra(EXTRA_TRAINING_GESTURE) ?: return
        trainingGestureType = try { GestureType.valueOf(gestureName) } catch (_: IllegalArgumentException) { return }
        isTrainingMode = true
        gestureTrainer.startRecording()
        vibrate()
    }

    private fun stopTraining() {
        if (!isTrainingMode) return
        isTrainingMode = false
        val type = trainingGestureType ?: return
        val success = gestureTrainer.stopRecording(type)
        trainingGestureType = null
        if (success) vibrateLong()
    }

    private fun clearTraining() {
        gestureTrainer.clearAll()
        armingManager.deactivate()
    }

    private fun startGestureDetection() {
        if (isRunning) return
        isRunning = true
        lastGestureTime = 0L
        screenOffTime = -1L
        try { mediaControllerManager.connect() } catch (e: Exception) { Log.e(TAG, "MediaController connect failed", e) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun stopGestureDetection() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        if (wakeLock.isHeld) wakeLock.release()
        wristDetector.reset()
        pinchDetector.reset()
        armingManager.deactivate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateSensitivity(intent: Intent) {
        val angleThreshold = intent.getFloatExtra(EXTRA_ANGLE_THRESHOLD, 28f)
        val pinchThreshold = intent.getFloatExtra(EXTRA_PINCH_THRESHOLD, 3.0f)
        val cooldown = intent.getLongExtra(EXTRA_COOLDOWN, 1200L)
        val leftHand = intent.getBooleanExtra(EXTRA_LEFT_HAND, false)

        wristDetector = WristRotationDetector(
            angleThresholdDegrees = angleThreshold,
            cooldownMs = cooldown,
            leftHand = leftHand
        )
        pinchDetector = DoublePinchDetector(thresholdUp = pinchThreshold)
        Log.d(TAG, "Sensitivity updated: angle=$angleThreshold, pinch=$pinchThreshold, cooldown=$cooldown, leftHand=$leftHand")
    }

    private fun startIdleTimer() {
        cancelIdleTimer()
        idleExecutor = idleExecutor ?: Executors.newSingleThreadScheduledExecutor()
        idleTask = idleExecutor?.schedule({
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
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Gesture Music Control", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Фоновое управление музыкой жестами"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String = "Слушаю жесты запястья"): Notification {
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, GestureMusicService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Music")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "Стоп", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun vibrate() {
        try { vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
    }

    private fun vibrateShort() {
        try { vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
    }

    private fun vibrateLong() {
        try { vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
    }

    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
}
