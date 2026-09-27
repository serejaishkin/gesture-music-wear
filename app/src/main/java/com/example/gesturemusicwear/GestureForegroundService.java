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
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import android.content.pm.ServiceInfo;
import android.util.Log;
import android.view.KeyEvent;

import java.util.List;

public class GestureForegroundService extends Service implements SensorEventListener {
    private static final String TAG = "GestureWearSrv";
    private static final String CHANNEL_ID = "gesture_wear_channel";
    private static final String PREFS_NAME = "GestureWearPrefs";

    private static final long GESTURE_COOLDOWN_MS = 650L;
    private static final long CROSS_GESTURE_DEBOUNCE_MS = 300L;
    private static final long ARM_GUARD_WINDOW_MS = 12000L;
    private static final long ARM_GUARD_NOTIFY_MS = 2000L;

    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor mAccelerometer;

    private WristRotationDetector mWristDetector;
    private DoublePinchDetector mPinchDetector;
    private FistClenchDetector mFistDetector;

    private float mLastGx = 0f, mLastGy = 0f, mLastGz = 0f;
    private float mLastAx = 0f, mLastAy = 0f, mLastAz = 0f;
    private long mLastGestureTimestamp = 0L;

    private boolean mFistGuardEnabled = true;
    private boolean mIsArmed = false;
    private long mArmedUntilTime = 0L;
    private long mLastGuardNotifyTime = 0L;

    private boolean mSensorsActive = true;
    private boolean mHapticsEnabled = true;
    private boolean mIsLeftHand = true;
    private float mAngleThreshold = 35f; // reduced from 45f for better sensitivity
    private float mPinchThreshold = 2.2f;
    private float mClenchThreshold = 2.2f; // reduced from 3.0f for better detection

    private AudioManager mAudioManager;
    private MediaSessionManager mMediaSessionManager;

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
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

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
        long eventMs = event.timestamp > 0L
                ? event.timestamp / 1_000_000L
                : System.currentTimeMillis();
        processSensors(eventMs, mLastGx, mLastGy, mLastGz,
                mLastAx, mLastAy, mLastAz);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void loadSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            mSensorsActive = prefs.getBoolean("active", true);
            mIsLeftHand = prefs.getBoolean("left_hand", true);
            mAngleThreshold = prefs.getFloat("angle_thresh", 35f); // Reduced default from 45f to 35f for better sensitivity
            mPinchThreshold = prefs.getFloat("pinch_thresh", 2.2f);
            mClenchThreshold = prefs.getFloat("clench_thresh", 2.2f); // reduced from 3.0f
            mHapticsEnabled = prefs.getBoolean("haptics", true);
            mFistGuardEnabled = prefs.getBoolean("fist_guard", true);
        } catch (Throwable ignored) {}
    }

    private void initDetectors() {
        mWristDetector = new WristRotationDetector(
            mAngleThreshold, 0.8f, 100, 1000, GESTURE_COOLDOWN_MS, // Reduced minAngularSpeed from 1.2f to 0.8f, adjusted duration
            800, 0.25f, 200, 24.0f, mIsLeftHand // Reduced idleThreshold from 0.35f to 0.25f, increased idleTimeout
        );
        mWristDetector.updateSettings(mAngleThreshold, GESTURE_COOLDOWN_MS, mIsLeftHand, 100, 1000); // Updated duration parameters
        mPinchDetector = new DoublePinchDetector(
            mPinchThreshold, -(mPinchThreshold * 0.6f), 4.0f, 900, GESTURE_COOLDOWN_MS, 2.5f
        );
        mPinchDetector.updateSettings(mPinchThreshold, GESTURE_COOLDOWN_MS);
        mFistDetector = new FistClenchDetector(mClenchThreshold, 1200, 2.2f); // increased maxGyroMagnitude from 1.8f to 2.2f
        mFistDetector.updateSettings(mClenchThreshold, 1200);
    }

    private synchronized void processSensors(long now, float gx, float gy, float gz,
                                             float ax, float ay, float az) {
        if (now - mLastGestureTimestamp < CROSS_GESTURE_DEBOUNCE_MS) return;

        if (mFistGuardEnabled) {
            int fist = mFistDetector.process(now, gx, gy, gz, ax, ay, az);
            if (fist == FistClenchDetector.RESULT_ACTIVATE) {
                mLastGestureTimestamp = now;
                mIsArmed = true;
                mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
                vibrateDoublePulse();
                writeDiag("Жест: сжатие кулака -> активация (12с)");
                Log.i(TAG, "Сжатие кулака -> активация (фон)");
                return;
            }
        }

        boolean canExecuteMedia = !mFistGuardEnabled || (mIsArmed && now <= mArmedUntilTime);
        if (canExecuteMedia) {
            int wrist = mWristDetector.process(now, gx, gy, gz, ax, ay, az);
            if (wrist == WristRotationDetector.RESULT_NEXT) {
                mLastGestureTimestamp = now;
                mIsArmed = true;
                mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
                triggerGestureAction("Вращение наружу", "Следующий трек",
                        KeyEvent.KEYCODE_MEDIA_NEXT, 1);
                return;
            } else if (wrist == WristRotationDetector.RESULT_PREVIOUS) {
                mLastGestureTimestamp = now;
                mIsArmed = true;
                mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
                triggerGestureAction("Вращение внутрь", "Предыдущий трек",
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, 2);
                return;
            }

            int pinch = mPinchDetector.process(now, gx, gy, gz, ax, ay, az);
            if (pinch == DoublePinchDetector.RESULT_PLAY_PAUSE) {
                mLastGestureTimestamp = now;
                mIsArmed = true;
                mArmedUntilTime = now + ARM_GUARD_WINDOW_MS;
                triggerGestureAction("Двойной щипок", "Пауза/Воспроизведение",
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 3);
                return;
            }
        }
    }

    private void triggerGestureAction(String gestureName, String actionName,
                                      int keycode, int kind) {
        vibrateDoublePulse();
        sendMediaKey(keycode, kind);
        writeDiag("Жест: " + gestureName + " -> " + actionName);
        Log.i(TAG, actionName);
    }

    /** Mirror MainActivity.sendMediaKey: prefer MediaController, fallback AudioManager dispatch. */
    private void sendMediaKey(int keycode, int kind) {
        boolean isToggle = kind == 3; // RESULT_PLAY_PAUSE is toggle
        StringBuilder diag = new StringBuilder();
        diag.append("Жест -> key=").append(keycode).append(" toggle=").append(isToggle);
        if (!isToggle && mMediaSessionManager != null) {
            try {
                List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
                diag.append(" activeSessions=").append(controllers == null ? 0 : controllers.size());
                if (controllers != null && !controllers.isEmpty()) {
                    MediaController c0 = controllers.get(0);
                    if (c0 != null) {
                        MediaController.TransportControls tc = c0.getTransportControls();
                        if (tc != null) {
                            diag.append(" target=")
                                    .append(c0.getPackageName() != null ? c0.getPackageName() : "?");

                            // Check if the action is supported by the media session
                            android.media.session.PlaybackState state = c0.getPlaybackState();
                            if (state != null) {
                                long actions = state.getActions();
                                if (keycode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                    if ((actions & android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                                        tc.skipToNext();
                                        diag.append(" ->skipToNext()");
                                        Log.i(TAG, diag.toString());
                                        writeDiag(diag.toString());
                                        return;
                                    } else {
                                        diag.append(" ->SKIP_TO_NEXT not supported");
                                        Log.w(TAG, diag.toString());
                                    }
                                } else if (keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                    if ((actions & android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
                                        tc.skipToPrevious();
                                        diag.append(" ->skipToPrevious()");
                                        Log.i(TAG, diag.toString());
                                        writeDiag(diag.toString());
                                        return;
                                    } else {
                                        diag.append(" ->SKIP_TO_PREVIOUS not supported");
                                        Log.w(TAG, diag.toString());
                                    }
                                }
                            } else {
                                // Fallback if state is null
                                if (keycode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                    tc.skipToNext();
                                    diag.append(" ->skipToNext() (no state check)");
                                    Log.i(TAG, diag.toString());
                                    writeDiag(diag.toString());
                                    return;
                                } else if (keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                    tc.skipToPrevious();
                                    diag.append(" ->skipToPrevious() (no state check)");
                                    Log.i(TAG, diag.toString());
                                    writeDiag(diag.toString());
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "MediaController error, falling back to AudioManager", t);
            }
        }

        dispatchFallback(keycode);
        diag.append(" ->AudioManager.dispatch");
        Log.i(TAG, diag.toString());
        writeDiag(diag.toString());
    }

    private void dispatchFallback(int keycode) {
        if (mAudioManager == null) return;
        try {
            mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
            mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
        } catch (Throwable ignored) {}
    }

    private void writeDiag(String diag) {
        try {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("diag", diag).apply();
        } catch (Throwable ignored) {}
    }

    private void vibrateDoublePulse() {
        if (!mHapticsEnabled) return;
        try {
            Vibrator v = getVibrator();
            if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    private Vibrator getVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) return vm.getDefaultVibrator();
            }
        } catch (Throwable ignored) {}
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
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
