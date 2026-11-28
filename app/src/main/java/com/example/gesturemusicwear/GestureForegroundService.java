package com.example.gesturemusicwear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import android.content.pm.ServiceInfo;
import android.util.Log;
import android.view.KeyEvent;

public class GestureForegroundService extends Service implements SensorEventListener {
    private static final String TAG = "GestureWearSrv";
    private static final String CHANNEL_ID = "gesture_wear_channel";
    private static final String PREFS_NAME = "GestureWearPrefs";

    private static final long GESTURE_COOLDOWN_MS = 650L;

    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor mAccelerometer;

    private GestureEngine mEngine;
    private boolean mOnlyWhenPlaying = false;
    private boolean mMusicActive = true;
    private long mMusicCheckedAt = 0L;
    private WristRotationDetector mWristDetector;
    private DoublePinchDetector mPinchDetector;
    private FistClenchDetector mFistDetector;

    private float mLastGx = 0f, mLastGy = 0f, mLastGz = 0f;
    private float mLastAx = 0f, mLastAy = 0f, mLastAz = 0f;

    private boolean mFistGuardEnabled = false;

    private boolean mSensorsActive = true;
    private boolean mHapticsEnabled = true;
    private boolean mIsLeftHand = true;
    private float mAngleThreshold = 35f; // reduced from 45f for better sensitivity
    private float mPinchThreshold = 2.2f;
    private float mClenchThreshold = 2.2f; // reduced from 3.0f for better detection

    private AudioManager mAudioManager;

    @Override
    public void onCreate() {
        super.onCreate();
        loadSettings();
        initDetectors();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(1, n);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to promote sensor service to foreground", t);
            writeDiag("Ошибка FGS: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        // Reload settings on every start: the service may already be running from a previous
        // session, and the user may have changed thresholds / fist-guard / only-when-playing
        // in the app. Without this the background service keeps stale settings (e.g. fist-guard
        // stuck on, which blocks all media gestures until a fist clench).
        loadSettings();
        initDetectors();
        if (mSensorsActive) registerSensors();
        return START_STICKY;
    }

    private synchronized void registerSensors() {
        if (mSensorManager == null) return;
        try {
            if (mGyroscope == null || mAccelerometer == null) {
                writeDiag("Сенсоры недоступны: gyro=" + (mGyroscope != null) + ", accel=" + (mAccelerometer != null));
                Log.e(TAG, "Required motion sensors are unavailable");
                return;
            }
            if (mGyroscope != null) {
                mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            if (mAccelerometer != null) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            Log.i(TAG, "Сенсоры зарегистрированы (фон)");
        } catch (Throwable t) {
            Log.w(TAG, "Sensor registration failed", t);
        }
    }

    private synchronized void unregisterSensors() {
        if (mSensorManager == null) return;
        try {
            mSensorManager.unregisterListener(this);
        } catch (Throwable ignored) {}
        if (mWristDetector != null) mWristDetector.reset();
        if (mPinchDetector != null) mPinchDetector.reset();
        if (mFistDetector != null) mFistDetector.reset();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mLastGx = event.values[0];
            mLastGy = event.values[1];
            mLastGz = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mLastAx = event.values[0];
            mLastAy = event.values[1];
            mLastAz = event.values[2];
        }
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        long eventMs = event.timestamp > 0L
                ? event.timestamp / 1_000_000L
                : System.currentTimeMillis();
        processSensors(eventMs, mLastGx, mLastGy, mLastGz,
                mLastAx, mLastAy, mLastAz);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void loadSettings() {
        GestureSettings g = GestureSettings.load(this);
        mSensorsActive = g.active;
        mIsLeftHand = g.leftHand;
        mAngleThreshold = g.angleThreshold;
        mPinchThreshold = g.pinchThreshold;
        mClenchThreshold = g.clenchThreshold;
        mHapticsEnabled = g.haptics;
        mFistGuardEnabled = g.fistGuard;
        mOnlyWhenPlaying = g.onlyWhenPlaying;
    }

    private void initDetectors() {
        mEngine = new GestureEngine(mAngleThreshold, mPinchThreshold, mClenchThreshold,
                mIsLeftHand, mFistGuardEnabled);
        mWristDetector = mEngine.wrist();
        mPinchDetector = mEngine.pinch();
        mFistDetector = mEngine.fist();
        mWristDetector.updateSettings(mAngleThreshold, GestureEngine.GESTURE_COOLDOWN_MS, mIsLeftHand, 100, 1000);
        mPinchDetector.updateSettings(mPinchThreshold, GestureEngine.GESTURE_COOLDOWN_MS);
        mFistDetector.updateSettings(mClenchThreshold, 1200);
    }

    private synchronized void processSensors(long now, float gx, float gy, float gz,
                                             float ax, float ay, float az) {
        if (mEngine == null) return;
        if (mOnlyWhenPlaying) {
            // isMusicActive() is a binder call: re-check at most once per second.
            if (now - mMusicCheckedAt > 1000) {
                mMusicCheckedAt = now;
                try { mMusicActive = mAudioManager == null || mAudioManager.isMusicActive(); }
                catch (Throwable t) { mMusicActive = true; }
            }
            if (!mMusicActive) return;
        }
        mEngine.setFistGuard(mFistGuardEnabled);
        switch (mEngine.process(now, gx, gy, gz, ax, ay, az)) {
            case GestureEngine.FIST_ARM:
                vibrateDoublePulse();
                writeDiag("Жест: сжатие кулака -> активация (12с)");
                Log.i(TAG, "Сжатие кулака -> активация (фон)");
                break;
            case GestureEngine.NEXT:
                triggerGestureAction("Вращение наружу", "Следующий трек",
                        KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case GestureEngine.PREVIOUS:
                triggerGestureAction("Вращение внутрь", "Предыдущий трек",
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case GestureEngine.PLAY_PAUSE:
                triggerGestureAction("Двойной щипок", "Пауза/Воспроизведение",
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            default:
                break;
        }
    }

    private void triggerGestureAction(String gestureName, String actionName, int keycode) {
        vibrateDoublePulse();
        sendMediaKey(keycode);
        writeDiag("Жест: " + gestureName + " -> " + actionName);
        Log.i(TAG, actionName);
    }

    private void sendMediaKey(int keycode) {
        String result = PlayerControl.send(this, keycode);
        Log.i(TAG, result);
        writeDiag(result);
    }


    private void writeDiag(String diag) {
        try {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("diag", diag).apply();
        } catch (Throwable ignored) {}
    }

    private void vibrateDoublePulse() {
        if (mHapticsEnabled) Haptics.doublePulse(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Gesture Wear фон", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {}
    }

    private Notification buildNotification() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("Gesture Wear активен")
                        .setContentText("Жесты работают в фоне")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .build();
            } else {
                // Fallback for older versions - but we minSdk is 26 (Android 8.0) so this shouldn't happen
                return new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Gesture Wear активен")
                        .setContentText("Жесты работают в фоне")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .build();
            }
        } catch (Throwable ignored) {}
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gesture Wear")
                .setContentText("")
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterSensors();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
