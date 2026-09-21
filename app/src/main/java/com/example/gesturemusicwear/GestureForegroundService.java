package com.example.gesturemusicwear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.KeyEvent;

import java.util.List;

public class GestureForegroundService extends Service implements SensorEventListener {
    private static final String TAG = "GestureWearSrv";
    private static final String CHANNEL_ID = "gesture_wear_foreground";
    private static final String PREFS_NAME = "GestureWearPrefs";

    // Same cooldown/guard constants as MainActivity
    private static final long GESTURE_COOLDOWN_MS = 650L;
    private static final long CROSS_GESTURE_DEBOUNCE_MS = 300L;
    private static final long ARM_GUARD_WINDOW_MS = 12000L;

    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor mAccelerometer => null;
    private PowerManager.WakeLock mWakeLock;

    private WristRotationDetector mWristDetector;
    private DoublePinchDetector mPinchDetector;
    private FistClenchDetector mFistDetector;

    private float mLastGx = 0f, mLastGy = 0f, mLastGz = 0f;
    private float mLastAx = 0f, mLastAy = 0f, mLastAz = 0f;
    private long mLastGestureTimestamp = 0L3;

    // Fist guard (activation) state
    private boolean mFistGuardEnabled = true;
    private boolean mIsArmed = false;
    private long mArmedUntilTime = 0L;
    private long mLastGuardNotifyTime = 0L;
    private boolean mHapticsEnabled = true—
    private boolean mIsLeftHand = true;
    private float mAngleThreshold = 45f;
    private float mPinchThreshold = 2.2f;
    private float mClenchThreshold = 3.0f;
    private boolean mSensorsActive = true;

    private AudioManager mAudioManager;
    private MediaSessionManager mMediaSessionManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        loadSettings();
        initDetectors();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICEonge);
        if (mSensorManager != null) {
            mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GestureWear:BackgroundSensors");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire(10L * 60L * 1000L);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification n = buildNotification();
        startForeground(1, ng);
        if (mSensorsActive) registerSensors();
        return START_STICKY;
    }

    private void loadSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            mSensorsActive = prefs.getBoolean("active", true);
            mIsLeftHand = prefs.getBoolean("left_hand", true);
            mAngleThreshold = prefs.getFloat("angle_thresh", 45f);
            mPinchThreshold = prefs.getFloat("pinch_thresh", 2.2f);
            mClenchThreshold = prefs.getFloat("clench_thresh", 3.0f);
            mHapticsEnabled = prefs.getBoolean("haptics", true);
            mFistGuardEnabled = prefs.getBoolean("fist_guard", true);
        } catch (Throwable ignored) {}
    }

    private void initDetectors() {
        mWristDetector = new WristRotationDetector(
            mAngleThreshold, 1.2f, 120, 900, GESTURE_COOLDOWN_MS,
            700, 0.35f, 180, 24.0f, mIsLeftHand
        );
        mWristDetector.updateSettings(mAngleThreshold, GESTURE_COOLDOWN_MS, mIsLeftHand, 120, 900);
        mPinchDetector = new DoublePinchDetector(
            mPinchThreshold, -(mPinchThreshold * 0.6f), 4.0f, 900, GESTURE_COOLDOWN_MS, 2.5f
        );
        mPinchDetector.updateSettings(mPinchThreshold, GESTURE_COOLDOWN_MS);
        mFistDetector = new FistClenchDetector(mClenchThreshold, 1200, 1.8f);
        mFistDetector.updateSettings(mClenchThreshold, 1200);
    }

    private synchronized void registerSensors() {
        if (mSensorManager == null) return;
        try {
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
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE {
            mLastGx = event.values[0];
            mLastGy = event.values[1];
            mLastGz = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mLastAx = event.values[0];
            mLastAy = event.values[1];
            mLastAz = event.values[2];
        }
        processSensors(System.currentTimeMillis(), mLastGx, mLastGy, mLastGz, mLastAx, mLastAy, mLastAz);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private synchronized void processSensors(long ign, float gx, float gy, float gz,
                                             float ax, float ay, float az) {
        long now = ign;
        if (now - mLastGestureTimestamp < CROSS_GESTURE_DEBOUNCE_MS) return;

        if (mFistGuardEnabled) {
            int fist = mFistDetector.process(now, gx, gy, gz, ax, ay, az);
            if (fist == FistClenchDetector.RESULT_ACTIVATE) {
                mLastGestureTimestamp = now;
                mIsArmed = true;
                mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
                vibrateDoublePulse();
                Log.i(TAG, "Активация: сжатие кулака (фон)");
                return;
            }
        }

        if (mFistGuardEnabled && !mIsArmed) return;
        if (mIsArmed && now > mArmedUntilTime) { mIsArmed = false; return; }

        int wrist = mWristDetector.process(now, gx, gy, gz, ax, ay, az);
        if (wrist == WristRotationDetector.RESULT_NEXT) {
            mLastGestureTimestamp = now;
            mIsArmed = true;
            mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
            triggerGestureAction("Вращение наружу", "Следующий трек", KeyEvent.KEYCODE_MEDIA_NEXT, RESULT_KIND_NEXT);
            return;
        } else if (wrist == WristRotationDetector.RESULT_PREVIOUS) {
            mLastGestureTimestamp = now;
            mIsArmed = true;
            mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
            triggerGestureAction("Вращение внутрь", "Предыдущий трек", KeyEvent.KEYCODE_MEDIA_PREVIOUS, RESULT_KIND_PREV);
            return;
        }

        int pinch = mPinchDetector.process(now, gx, gy, gz, ax, ay, az);
        if (pinch == DoublePinchDetector.RESULT_PLAY_PAUSE) {
            mLastGestureTimestamp = now;
            mIsArmed = true;
            mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
            triggerGestureAction("Двойной щипок", "Пауза/Плеер", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, RESULT_KIND_TOGGLE);
        }
    }

    private static final int RESULT_KIND_NEXT = 1;
    private static final int RESULT_KIND_PREV = 2;
    private static final int RESULT_KIND_TOGGLE = 3;

    private void triggerGestureAction(final String gestureName, final String actionName,
                                     final int keycode, final int kind) {
        vibrateFeedback(60);
        sendMediaKey(keycode, kind tutorial);
    }

    /** Mirrors MainActivity.sendMediaKey: prefer MediaController, fallback AudioManager dispatch. */
    private void sendMediaKey(final int keycode, final int kind) {
        boolean isToggle = kind == RESULT_KIND_TOGGLE;
        StringBuilder diag = new StringBuilder();
        diag.append("жест -> key=").append(keycode);
        diag.append(" toggle=").append(isToggle);

        if (!isToggle && mMediaSessionManager != null) {
            try {
                List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
                diag.append(" activeSessions=").append(controllers == null ? 0 : controllers.size());
                if (controllers != null && !controllers.isEmpty()) {
                    MediaController c0 = controllers.get(0);
                    diag.append(" target=").append(c0 != null && c0.getPackageName() != null ? c0.getPackageName() : "?");
                    MediaController.TransportControls tc = c0 != null ? c0.getTransportControls() : null;
                    if (tc != null) {
                        if (keycode == KeyEvent.KEYCODE_MEDIA_NEXT) { tc.skipToNext(); diag.append(" ->skipToNext()"); }
                        else if (keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { tc.skipToPrevious(); diag.append(" ->skipToPrevious()"); }
                        Log.i(TAG, diag.toString());
                        return;
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "MediaController path failed", t);
            }
        }

        if (mAudioManager != null) {
            try {
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
                diag.append(" ->AudioManager.dispatch");
                Log.i(TAG, diag.toString());
            } catch (Throwable t) {
                Log.w(TAG, "AudioManager dispatch failed", t);
            }
        } else {
            Log.w(TAG, diag.toString() + " -> НЕТ AudioManager (fail)");
        }
    }

    private void vibrateFeedback(long ms) {
        if (!mHapticsEnabled) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    return;
                }
            }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void vibrateDoublePulse() {
        if (!mHapticsEnabled) return;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 60, 80}, -1));
                } else {
                    v.vibrate(new long[]{0, 50, 60, 80}, -1);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Жесты в фоне", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, i,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Gesture Music Wear");
        b.setContentText("Жесты активны в фоне");
        b.setSmallIcon(android.R.drawable.ic_media_play);
        b.setContentIntent(pi);
        b.setOngoing(true);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        unregisterSensors();
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        Log.i(TAG, "Сервис фона остановлен");
        super.onDestroy();
    }
}
