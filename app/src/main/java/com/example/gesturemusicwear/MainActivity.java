package com.example.gesturemusicwear;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String TAG = "GestureMusicWear";
    private static final String PREFS_NAME = "GestureWearPrefs";

    // Sensors
    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor mAccelerometer;
    private boolean mSensorsActive = true;

    // Gesture detection thresholds
    private boolean mIsLeftHand = true;
    private float mAngleThreshold = 45f;      // degrees
    private float mPinchThreshold = 2.2f;     // g
    private float mClenchThreshold = 3.0f;    // g
    private boolean mHapticsEnabled = true;

    // False trigger protection via Fist Activation (Взведение / Активация жестом кулака)
    private boolean mIsArmed = false;
    private long mArmedUntilTime = 0L;
    private static final long ARM_GUARD_WINDOW_MS = 12000L; // 12 seconds active window
    private boolean mFistGuardEnabled = true; // Protects from false positives
    private Button mSettingsFistGuardBtn;

    // Sensor processing state
    private float mLastGx = 0f, mLastGy = 0f, mLastGz = 0f;
    private float mLastAx = 0f, mLastAy = 0f, mLastAz = 0f;
    private long mLastGestureTriggerTime = 0L;
    private static final long GESTURE_COOLDOWN_MS = 650L;

    // Audio manager for real media control
    private AudioManager mAudioManager;
    private boolean mIsPlaying = false;

    // UI state: 4 screens
    // 0: Tile, 1: Player, 2: Settings, 3: Sensors
    private int mCurrentScreen = 0;
    private static final int NUM_SCREENS = 4;

    // Root views
    private FrameLayout mRootLayout;
    private FrameLayout mScreenContainer;
    private final View[] mScreens = new View[NUM_SCREENS];
    private final View[] mDotViews = new View[NUM_SCREENS];
    private TextView mTimeHeaderView;

    // Screen 0: Tile views
    private Button mTilePowerButton;
    private TextView mTileStatusSubtext;
    private Button mTileHandButton;
    private TextView mTileLastGestureText;

    // Screen 1: Player views
    private TextView mPlayerTrackTitle;
    private TextView mPlayerTrackArtist;
    private Button mPlayerPlayPauseBtn;
    private ProgressBar mPlayerVolumeBar;

    // Screen 2: Settings views
    private Button mSettingsHandBtn;
    private TextView mSettingsAngleText;
    private TextView mSettingsPinchText;
    private TextView mSettingsClenchText;
    private Button mSettingsHapticBtn;
    private ScrollView mSettingsScrollView;

    // Screen 3: Gesture Training & Calibration views
    private static final int TRAINING_NONE = 0;
    private static final int TRAINING_OUTWARD = 1;
    private static final int TRAINING_INWARD = 2;
    private static final int TRAINING_PINCH = 3;
    private static final int TRAINING_FIST = 4;

    private int mActiveTrainingGesture = TRAINING_NONE;
    private int mTrainingRepsCompleted = 0;
    private static final int TRAINING_TARGET_REPS = 5;
    private float mTrainingAccumulatedSum = 0f;
    private boolean mTrainingFinished = false;

    private ScrollView mTrainingScrollView;
    private LinearLayout mTrainingMenuLayout;
    private LinearLayout mTrainingActiveLayout;
    private TextView mTrainingGestureTitle;
    private TextView mTrainingInstructionText;
    private TextView mTrainingRepsCounter;
    private ProgressBar mTrainingProgressBar;
    private TextView mTrainingLiveFeedbackText;
    private TextView mTrainingSuccessBanner;
    private Button mTrainingRestartBtn;
    private Button mTrainingBackBtn;

    private TextView mSensorsGyroText;
    private TextView mSensorsAccelText;
    private ProgressBar mSensorsIntensityBar;
    private TextView mSensorsTriggerAlert;

    // Rotary Bezel accumulation
    private float mRotaryAccumulator = 0f;

    // Handlers, timer and gestures
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private GestureDetector mGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Keep AMOLED screen awake while app is open
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            loadPreferences();

            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null) {
                mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }

            buildNativeWearUI();

            setupGestureDetector();

            startClockUpdater();

            if (mSensorsActive) {
                registerSensors();
            }

        } catch (Throwable t) {
            Log.e(TAG, "Fatal error in onCreate", t);
            showErrorDiagnostics(t);
        }
    }

    private void loadPreferences() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            mSensorsActive = prefs.getBoolean("active", true);
            mIsLeftHand = prefs.getBoolean("left_hand", true);
            mAngleThreshold = prefs.getFloat("angle_thresh", 45f);
            mPinchThreshold = prefs.getFloat("pinch_thresh", 2.2f);
            mClenchThreshold = prefs.getFloat("clench_thresh", 3.0f);
            mHapticsEnabled = prefs.getBoolean("haptics", true);
        } catch (Throwable ignored) {}
    }

    private void savePreferences() {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean("active", mSensorsActive);
            editor.putBoolean("left_hand", mIsLeftHand);
            editor.putFloat("angle_thresh", mAngleThreshold);
            editor.putFloat("pinch_thresh", mPinchThreshold);
            editor.putFloat("clench_thresh", mClenchThreshold);
            editor.putBoolean("haptics", mHapticsEnabled);
            editor.apply();
        } catch (Throwable ignored) {}
    }

    // ==========================================
    // PURE NATIVE WEAR OS CIRCULAR UI
    // ==========================================
    private void buildNativeWearUI() {
        mRootLayout = new FrameLayout(this);
        mRootLayout.setBackgroundColor(0xFF000000); // Pure AMOLED Black
        mRootLayout.setFocusable(true);
        mRootLayout.setFocusableInTouchMode(true);

        // Screen container (fills 100% of round screen)
        mScreenContainer = new FrameLayout(this);
        mRootLayout.addView(mScreenContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Create 4 distinct native screens
        mScreens[0] = createTileScreen();
        mScreens[1] = createPlayerScreen();
        mScreens[2] = createSettingsScreen();
        mScreens[3] = createTrainingScreen();

        for (int i = 0; i < NUM_SCREENS; i++) {
            mScreenContainer.addView(mScreens[i], new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mScreens[i].setVisibility(i == mCurrentScreen ? View.VISIBLE : View.GONE);
        }

        // Top Header: Digital Clock + EXACTLY 4 VISUAL ROUND DOTS
        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.VERTICAL);
        topHeader.setGravity(Gravity.CENTER_HORIZONTAL);
        topHeader.setPadding(0, dp(12), 0, 0);

        mTimeHeaderView = new TextView(this);
        mTimeHeaderView.setTextColor(0xFF94A3B8); // Slate 400
        mTimeHeaderView.setTextSize(11);
        mTimeHeaderView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTimeHeaderView.setText(mTimeFormat.format(new Date()));
        topHeader.addView(mTimeHeaderView);

        // 4 Physical circular Dot Views (not Unicode characters)
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, dp(3), 0, 0);

        for (int i = 0; i < NUM_SCREENS; i++) {
            final int screenIdx = i;
            mDotViews[i] = new View(this);
            LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dp(6), dp(6));
            dotP.setMargins(dp(3), 0, dp(3), 0);
            mDotViews[i].setLayoutParams(dotP);
            mDotViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchToScreen(screenIdx);
                    vibrateFeedback(20);
                }
            });
            dotsRow.addView(mDotViews[i]);
        }
        topHeader.addView(dotsRow);

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mRootLayout.addView(topHeader, headerParams);

        updateDotsAppearance();

        setContentView(mRootLayout);
    }

    private void updateDotsAppearance() {
        for (int i = 0; i < NUM_SCREENS; i++) {
            if (mDotViews[i] == null) continue;
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) mDotViews[i].getLayoutParams();
            if (i == mCurrentScreen) {
                // Active dot: elongated cyan pill
                p.width = dp(14);
                p.height = dp(6);
                mDotViews[i].setLayoutParams(p);
                mDotViews[i].setBackground(createPillDrawable(0xFF06B6D4, 0xFF22D3EE, dp(3)));
            } else {
                // Inactive dot: small muted circular dot
                p.width = dp(6);
                p.height = dp(6);
                mDotViews[i].setLayoutParams(p);
                mDotViews[i].setBackground(createCircleDrawable(0xFF334155, 0xFF475569));
            }
        }
    }

    // --------------------------------------------------
    // SCREEN 0: QUICK TILE (Главный экран / Плитка)
    // --------------------------------------------------
    private View createTileScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        // Vertical padding leaves room for top clock/dots (y < 42dp) and bottom curve
        layout.setPadding(dp(20), dp(44), dp(20), dp(12));

        // Big Toggle Button: compact 124dp x 44dp to fit perfectly in circle
        mTilePowerButton = new Button(this);
        mTilePowerButton.setTextSize(13);
        mTilePowerButton.setTypeface(Typeface.DEFAULT_BOLD);
        updateTileButtonAppearance();

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dp(126), dp(44));
        btnParams.setMargins(0, dp(4), 0, dp(4));
        mTilePowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorsActive = !mSensorsActive;
                savePreferences();
                updateTileButtonAppearance();
                vibrateFeedback(45);
                if (mSensorsActive) {
                    registerSensors();
                    mTileLastGestureText.setText("Жесты активны");
                } else {
                    unregisterSensors();
                    mTileLastGestureText.setText("Жесты отключены");
                }
            }
        });
        layout.addView(mTilePowerButton, btnParams);

        mTileStatusSubtext = new TextView(this);
        mTileStatusSubtext.setTextColor(0xFF94A3B8);
        mTileStatusSubtext.setTextSize(10);
        mTileStatusSubtext.setText("Нажмите для вкл/выкл");
        mTileStatusSubtext.setGravity(Gravity.CENTER);
        layout.addView(mTileStatusSubtext);

        // Hand Switch Button: compact 124dp x 30dp
        mTileHandButton = new Button(this);
        updateHandButtonText(mTileHandButton);
        mTileHandButton.setTextSize(11);
        mTileHandButton.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(15)));
        mTileHandButton.setTextColor(0xFFE2E8F0);
        LinearLayout.LayoutParams handParams = new LinearLayout.LayoutParams(dp(126), dp(30));
        handParams.setMargins(0, dp(6), 0, dp(4));
        mTileHandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsLeftHand = !mIsLeftHand;
                savePreferences();
                updateHandButtonText(mTileHandButton);
                if (mSettingsHandBtn != null) updateHandButtonText(mSettingsHandBtn);
                vibrateFeedback(30);
            }
        });
        layout.addView(mTileHandButton, handParams);

        // Last Detected Gesture status badge
        mTileLastGestureText = new TextView(this);
        mTileLastGestureText.setTextColor(0xFF22D3EE); // Cyan 400
        mTileLastGestureText.setTextSize(10);
        mTileLastGestureText.setGravity(Gravity.CENTER);
        mTileLastGestureText.setText(mSensorsActive ? "Ожидание жеста..." : "Жесты отключены");
        layout.addView(mTileLastGestureText);

        // Direct Quick Button to Training & Calibration
        Button tileTrainingBtn = new Button(this);
        tileTrainingBtn.setText("🎓 Обучение жестов");
        tileTrainingBtn.setTextSize(10);
        tileTrainingBtn.setBackground(createPillDrawable(0xFF083344, 0xFF0E7490, dp(12)));
        tileTrainingBtn.setTextColor(0xFF22D3EE);
        LinearLayout.LayoutParams trainParams = new LinearLayout.LayoutParams(dp(130), dp(28));
        trainParams.setMargins(0, dp(3), 0, dp(3));
        tileTrainingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToScreen(3);
                vibrateFeedback(30);
            }
        });
        layout.addView(tileTrainingBtn, trainParams);

        // Bottom quick navigation bar: 3 compact circular icon buttons (32dp x 32dp)
        // Perfectly fits within the 180dp bottom width arc of circular screen!
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(0, dp(4), 0, 0);

        Button toPlayerBtn = createCircleNavButton("🎵", new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchToScreen(1); }
        });
        Button toTrainingBtn = createCircleNavButton("🎓", new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchToScreen(3); }
        });
        Button toSettingsBtn = createCircleNavButton("⚙️", new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchToScreen(2); }
        });

        navBar.addView(toPlayerBtn);
        navBar.addView(toTrainingBtn);
        navBar.addView(toSettingsBtn);
        layout.addView(navBar);

        return layout;
    }

    private void updateTileButtonAppearance() {
        if (mTilePowerButton == null) return;
        if (mSensorsActive) {
            mTilePowerButton.setText("⚡ ЖЕСТЫ: ВКЛ");
            mTilePowerButton.setTextColor(0xFF083344);
            mTilePowerButton.setBackground(createPillDrawable(0xFF06B6D4, 0xFF22D3EE, dp(22)));
        } else {
            mTilePowerButton.setText("⏸ ЖЕСТЫ: ВЫКЛ");
            mTilePowerButton.setTextColor(0xFF94A3B8);
            mTilePowerButton.setBackground(createPillDrawable(0xFF1E293B, 0xFF475569, dp(22)));
        }
    }

    private void updateHandButtonText(Button btn) {
        if (btn == null) return;
        btn.setText(mIsLeftHand ? "✋ Левая рука" : "🤚 Правая рука");
    }

    private Button createCircleNavButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(0xFFE2E8F0);
        btn.setBackground(createCircleDrawable(0xFF1E293B, 0xFF334155));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(32), dp(32));
        p.setMargins(dp(5), 0, dp(5), 0);
        btn.setLayoutParams(p);
        btn.setOnClickListener(listener);
        return btn;
    }

    // --------------------------------------------------
    // SCREEN 1: MUSIC PLAYER (Управление плеером)
    // --------------------------------------------------
    private View createPlayerScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(20), dp(44), dp(20), dp(16));

        mPlayerTrackTitle = new TextView(this);
        mPlayerTrackTitle.setText("Neon Groove");
        mPlayerTrackTitle.setTextColor(0xFFFFFFFF);
        mPlayerTrackTitle.setTextSize(13);
        mPlayerTrackTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mPlayerTrackTitle.setGravity(Gravity.CENTER);
        layout.addView(mPlayerTrackTitle);

        mPlayerTrackArtist = new TextView(this);
        mPlayerTrackArtist.setText("Wear OS Controller");
        mPlayerTrackArtist.setTextColor(0xFF94A3B8);
        mPlayerTrackArtist.setTextSize(10);
        mPlayerTrackArtist.setGravity(Gravity.CENTER);
        layout.addView(mPlayerTrackArtist);

        // Playback Buttons: Prev | Play/Pause | Next
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, dp(6));

        Button prevBtn = new Button(this);
        prevBtn.setText("⏮");
        prevBtn.setTextSize(14);
        prevBtn.setTextColor(0xFFFFFFFF);
        prevBtn.setBackground(createCircleDrawable(0xFF1E293B, 0xFF334155));
        LinearLayout.LayoutParams pPrev = new LinearLayout.LayoutParams(dp(40), dp(40));
        pPrev.setMargins(0, 0, dp(6), 0);
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchMediaAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Предыдущий трек");
            }
        });
        controls.addView(prevBtn, pPrev);

        mPlayerPlayPauseBtn = new Button(this);
        mPlayerPlayPauseBtn.setText(mIsPlaying ? "⏸" : "▶");
        mPlayerPlayPauseBtn.setTextSize(17);
        mPlayerPlayPauseBtn.setTextColor(0xFF000000);
        mPlayerPlayPauseBtn.setBackground(createCircleDrawable(0xFF06B6D4, 0xFF22D3EE));
        LinearLayout.LayoutParams pPlay = new LinearLayout.LayoutParams(dp(48), dp(48));
        mPlayerPlayPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsPlaying = !mIsPlaying;
                mPlayerPlayPauseBtn.setText(mIsPlaying ? "⏸" : "▶");
                dispatchMediaAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, mIsPlaying ? "Воспроизведение" : "Пауза");
            }
        });
        controls.addView(mPlayerPlayPauseBtn, pPlay);

        Button nextBtn = new Button(this);
        nextBtn.setText("⏭");
        nextBtn.setTextSize(14);
        nextBtn.setTextColor(0xFFFFFFFF);
        nextBtn.setBackground(createCircleDrawable(0xFF1E293B, 0xFF334155));
        LinearLayout.LayoutParams pNext = new LinearLayout.LayoutParams(dp(40), dp(40));
        pNext.setMargins(dp(6), 0, 0, 0);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchMediaAction(KeyEvent.KEYCODE_MEDIA_NEXT, "Следующий трек");
            }
        });
        controls.addView(nextBtn, pNext);

        layout.addView(controls);

        // Volume Controls Row
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER);

        Button volDownBtn = new Button(this);
        volDownBtn.setText("🔉 -");
        volDownBtn.setTextSize(10);
        volDownBtn.setTextColor(0xFFE2E8F0);
        volDownBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(12)));
        volDownBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjustVolume(-1); }
        });
        volRow.addView(volDownBtn, new LinearLayout.LayoutParams(dp(46), dp(26)));

        mPlayerVolumeBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mPlayerVolumeBar.setMax(100);
        updateVolumeIndicator();
        LinearLayout.LayoutParams pVolBar = new LinearLayout.LayoutParams(dp(56), dp(8));
        pVolBar.setMargins(dp(5), 0, dp(5), 0);
        volRow.addView(mPlayerVolumeBar, pVolBar);

        Button volUpBtn = new Button(this);
        volUpBtn.setText("🔊 +");
        volUpBtn.setTextSize(10);
        volUpBtn.setTextColor(0xFFE2E8F0);
        volUpBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(12)));
        volUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjustVolume(1); }
        });
        volRow.addView(volUpBtn, new LinearLayout.LayoutParams(dp(46), dp(26)));

        layout.addView(volRow);

        // Hint
        TextView hint = new TextView(this);
        hint.setTextColor(0xFF64748B);
        hint.setTextSize(9);
        hint.setText("Вращение: след/пред | Щипок: пауза");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(4), 0, 0);
        layout.addView(hint);

        return layout;
    }

    private void updateVolumeIndicator() {
        if (mPlayerVolumeBar != null && mAudioManager != null) {
            int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int percent = (max > 0) ? (current * 100 / max) : 50;
            mPlayerVolumeBar.setProgress(percent);
        }
    }

    private void adjustVolume(int direction) {
        if (mAudioManager != null) {
            int adjust = direction > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI);
            updateVolumeIndicator();
            vibrateFeedback(25);
        }
    }

    private void dispatchMediaAction(int keycode, String actionName) {
        vibrateFeedback(45);
        if (mAudioManager != null) {
            try {
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
            } catch (Throwable t) {
                Log.w(TAG, "Media dispatch error: ", t);
            }
        }
        if (mTileLastGestureText != null) {
            mTileLastGestureText.setText("Жест: " + actionName);
        }
    }

    // --------------------------------------------------
    // SCREEN 2: SETTINGS (Настройки чувствительности)
    // --------------------------------------------------
    private View createSettingsScreen() {
        mSettingsScrollView = new ScrollView(this);
        mSettingsScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mSettingsScrollView.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(22), dp(44), dp(22), dp(36));

        TextView title = new TextView(this);
        title.setText("Настройки жестов");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        layout.addView(title);

        // Hand switch
        mSettingsHandBtn = new Button(this);
        updateHandButtonText(mSettingsHandBtn);
        mSettingsHandBtn.setTextSize(11);
        mSettingsHandBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(15)));
        mSettingsHandBtn.setTextColor(0xFFE2E8F0);
        mSettingsHandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsLeftHand = !mIsLeftHand;
                savePreferences();
                updateHandButtonText(mSettingsHandBtn);
                if (mTileHandButton != null) updateHandButtonText(mTileHandButton);
                vibrateFeedback(30);
            }
        });
        layout.addView(mSettingsHandBtn, new LinearLayout.LayoutParams(dp(140), dp(32)));

        // Angle Threshold Row
        layout.addView(createSettingRow("Порог вращения:", mAngleThreshold + "°",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAngleThreshold = Math.max(20f, mAngleThreshold - 5f);
                    savePreferences();
                    mSettingsAngleText.setText(((int)mAngleThreshold) + "°");
                    vibrateFeedback(20);
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAngleThreshold = Math.min(80f, mAngleThreshold + 5f);
                    savePreferences();
                    mSettingsAngleText.setText(((int)mAngleThreshold) + "°");
                    vibrateFeedback(20);
                }
            },
            view -> mSettingsAngleText = view
        ));

        // Pinch Threshold Row
        layout.addView(createSettingRow("Порог щипка:", String.format(Locale.US, "%.1f g", mPinchThreshold),
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPinchThreshold = Math.max(1.2f, mPinchThreshold - 0.2f);
                    savePreferences();
                    mSettingsPinchText.setText(String.format(Locale.US, "%.1f g", mPinchThreshold));
                    vibrateFeedback(20);
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPinchThreshold = Math.min(4.0f, mPinchThreshold + 0.2f);
                    savePreferences();
                    mSettingsPinchText.setText(String.format(Locale.US, "%.1f g", mPinchThreshold));
                    vibrateFeedback(20);
                }
            },
            view -> mSettingsPinchText = view
        ));

        // Clench Threshold Row
        layout.addView(createSettingRow("Порог кулака:", String.format(Locale.US, "%.1f g", mClenchThreshold),
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mClenchThreshold = Math.max(1.5f, mClenchThreshold - 0.3f);
                    savePreferences();
                    mSettingsClenchText.setText(String.format(Locale.US, "%.1f g", mClenchThreshold));
                    vibrateFeedback(20);
                }
            },
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mClenchThreshold = Math.min(5.0f, mClenchThreshold + 0.3f);
                    savePreferences();
                    mSettingsClenchText.setText(String.format(Locale.US, "%.1f g", mClenchThreshold));
                    vibrateFeedback(20);
                }
            },
            view -> mSettingsClenchText = view
        ));

        // Haptic feedback toggle
        mSettingsHapticBtn = new Button(this);
        updateHapticButtonText();
        mSettingsHapticBtn.setTextSize(11);
        mSettingsHapticBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(15)));
        mSettingsHapticBtn.setTextColor(0xFFE2E8F0);
        mSettingsHapticBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHapticsEnabled = !mHapticsEnabled;
                savePreferences();
                updateHapticButtonText();
                vibrateFeedback(50);
            }
        });
        LinearLayout.LayoutParams hapticP = new LinearLayout.LayoutParams(dp(140), dp(32));
        hapticP.setMargins(0, dp(6), 0, dp(6));
        layout.addView(mSettingsHapticBtn, hapticP);

        // Open Gesture Training & Calibration Button
        Button openTrainingSettingsBtn = new Button(this);
        openTrainingSettingsBtn.setText("🎓 Обучение и калибровка");
        openTrainingSettingsBtn.setTextSize(10);
        openTrainingSettingsBtn.setTextColor(0xFF22D3EE);
        openTrainingSettingsBtn.setBackground(createPillDrawable(0xFF083344, 0xFF0E7490, dp(14)));
        openTrainingSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToScreen(3);
                vibrateFeedback(30);
            }
        });
        LinearLayout.LayoutParams trainSetP = new LinearLayout.LayoutParams(dp(156), dp(30));
        trainSetP.setMargins(0, dp(4), 0, dp(6));
        layout.addView(openTrainingSettingsBtn, trainSetP);

        // Test Vibration Button
        Button testVibBtn = new Button(this);
        testVibBtn.setText("📳 Проверить вибро");
        testVibBtn.setTextSize(10);
        testVibBtn.setTextColor(0xFF22D3EE);
        testVibBtn.setBackground(createPillDrawable(0xFF083344, 0xFF0E7490, dp(14)));
        testVibBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrateFeedback(80);
            }
        });
        layout.addView(testVibBtn, new LinearLayout.LayoutParams(dp(140), dp(30)));

        mSettingsScrollView.addView(layout);
        return mSettingsScrollView;
    }

    private interface TextViewBinder {
        void bind(TextView tv);
    }

    private View createSettingRow(String label, String initialVal,
                                  View.OnClickListener onMinus,
                                  View.OnClickListener onPlus,
                                  TextViewBinder binder) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(0xFF94A3B8);
        lbl.setTextSize(10);
        row.addView(lbl);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Button minusBtn = new Button(this);
        minusBtn.setText("-");
        minusBtn.setTextSize(12);
        minusBtn.setTextColor(0xFFFFFFFF);
        minusBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(10)));
        minusBtn.setOnClickListener(onMinus);
        controls.addView(minusBtn, new LinearLayout.LayoutParams(dp(34), dp(26)));

        TextView valTv = new TextView(this);
        valTv.setText(initialVal);
        valTv.setTextColor(0xFFFFFFFF);
        valTv.setTextSize(11);
        valTv.setGravity(Gravity.CENTER);
        binder.bind(valTv);
        LinearLayout.LayoutParams valP = new LinearLayout.LayoutParams(dp(65), dp(26));
        controls.addView(valTv, valP);

        Button plusBtn = new Button(this);
        plusBtn.setText("+");
        plusBtn.setTextSize(12);
        plusBtn.setTextColor(0xFFFFFFFF);
        plusBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(10)));
        plusBtn.setOnClickListener(onPlus);
        controls.addView(plusBtn, new LinearLayout.LayoutParams(dp(34), dp(26)));

        row.addView(controls);
        return row;
    }

    private void updateHapticButtonText() {
        if (mSettingsHapticBtn == null) return;
        mSettingsHapticBtn.setText(mHapticsEnabled ? "Виброотклик: ВКЛ" : "Виброотклик: ВЫКЛ");
    }

    // --------------------------------------------------
    // SCREEN 3: GESTURE TRAINING & CALIBRATION (Обучение)
    // --------------------------------------------------
    private View createTrainingScreen() {
        mTrainingScrollView = new ScrollView(this);
        mTrainingScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mTrainingScrollView.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(20), dp(44), dp(20), dp(36));

        TextView title = new TextView(this);
        title.setText("🎓 Обучение жестов");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(4));
        layout.addView(title);

        // --- SECTION A: SELECTION MENU ---
        mTrainingMenuLayout = new LinearLayout(this);
        mTrainingMenuLayout.setOrientation(LinearLayout.VERTICAL);
        mTrainingMenuLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView menuSub = new TextView(this);
        menuSub.setText("Выберите жест для калибровки:");
        menuSub.setTextColor(0xFF94A3B8);
        menuSub.setTextSize(10);
        menuSub.setGravity(Gravity.CENTER);
        menuSub.setPadding(0, 0, 0, dp(6));
        mTrainingMenuLayout.addView(menuSub);

        mTrainingMenuLayout.addView(createTrainingChoiceButton("🔄 След. трек (Вращение наружу)", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_OUTWARD); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("🔄 Пред. трек (Вращение внутрь)", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_INWARD); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("✌️ Play/Pause (Щипок)", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_PINCH); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("✊ Громкость+ (Кулак)", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_FIST); }
        }));

        layout.addView(mTrainingMenuLayout);

        // --- SECTION B: ACTIVE TRAINING MODE ---
        mTrainingActiveLayout = new LinearLayout(this);
        mTrainingActiveLayout.setOrientation(LinearLayout.VERTICAL);
        mTrainingActiveLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mTrainingActiveLayout.setVisibility(View.GONE);

        mTrainingGestureTitle = new TextView(this);
        mTrainingGestureTitle.setTextColor(0xFF22D3EE);
        mTrainingGestureTitle.setTextSize(12);
        mTrainingGestureTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTrainingGestureTitle.setGravity(Gravity.CENTER);
        mTrainingActiveLayout.addView(mTrainingGestureTitle);

        mTrainingInstructionText = new TextView(this);
        mTrainingInstructionText.setTextColor(0xFFCBD5E1);
        mTrainingInstructionText.setTextSize(10);
        mTrainingInstructionText.setGravity(Gravity.CENTER);
        mTrainingInstructionText.setPadding(0, dp(2), 0, dp(4));
        mTrainingActiveLayout.addView(mTrainingInstructionText);

        mTrainingRepsCounter = new TextView(this);
        mTrainingRepsCounter.setTextColor(0xFFFFFFFF);
        mTrainingRepsCounter.setTextSize(16);
        mTrainingRepsCounter.setTypeface(Typeface.DEFAULT_BOLD);
        mTrainingRepsCounter.setGravity(Gravity.CENTER);
        mTrainingRepsCounter.setText("0 / 5");
        mTrainingActiveLayout.addView(mTrainingRepsCounter);

        mTrainingProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mTrainingProgressBar.setMax(5);
        mTrainingProgressBar.setProgress(0);
        LinearLayout.LayoutParams progP = new LinearLayout.LayoutParams(dp(140), dp(8));
        progP.setMargins(0, dp(3), 0, dp(5));
        mTrainingActiveLayout.addView(mTrainingProgressBar, progP);

        mTrainingLiveFeedbackText = new TextView(this);
        mTrainingLiveFeedbackText.setTextColor(0xFF94A3B8);
        mTrainingLiveFeedbackText.setTextSize(10);
        mTrainingLiveFeedbackText.setGravity(Gravity.CENTER);
        mTrainingLiveFeedbackText.setText("Ожидание движения...");
        mTrainingActiveLayout.addView(mTrainingLiveFeedbackText);

        mTrainingSuccessBanner = new TextView(this);
        mTrainingSuccessBanner.setTextColor(0xFF22C55E);
        mTrainingSuccessBanner.setTextSize(11);
        mTrainingSuccessBanner.setTypeface(Typeface.DEFAULT_BOLD);
        mTrainingSuccessBanner.setGravity(Gravity.CENTER);
        mTrainingSuccessBanner.setPadding(0, dp(4), 0, dp(4));
        mTrainingSuccessBanner.setVisibility(View.GONE);
        mTrainingActiveLayout.addView(mTrainingSuccessBanner);

        mTrainingRestartBtn = new Button(this);
        mTrainingRestartBtn.setText("↺ Повторить");
        mTrainingRestartBtn.setTextSize(10);
        mTrainingRestartBtn.setTextColor(0xFF22D3EE);
        mTrainingRestartBtn.setBackground(createPillDrawable(0xFF083344, 0xFF0E7490, dp(12)));
        mTrainingRestartBtn.setVisibility(View.GONE);
        mTrainingRestartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGestureTraining(mActiveTrainingGesture);
            }
        });
        LinearLayout.LayoutParams restartP = new LinearLayout.LayoutParams(dp(150), dp(28));
        restartP.setMargins(0, dp(3), 0, dp(3));
        mTrainingActiveLayout.addView(mTrainingRestartBtn, restartP);

        mTrainingBackBtn = new Button(this);
        mTrainingBackBtn.setText("⬅ Другой жест");
        mTrainingBackBtn.setTextSize(10);
        mTrainingBackBtn.setTextColor(0xFFE2E8F0);
        mTrainingBackBtn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(12)));
        mTrainingBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGestureTraining();
            }
        });
        LinearLayout.LayoutParams backP = new LinearLayout.LayoutParams(dp(150), dp(28));
        backP.setMargins(0, dp(2), 0, dp(4));
        mTrainingActiveLayout.addView(mTrainingBackBtn, backP);

        layout.addView(mTrainingActiveLayout);

        // --- SECTION C: LIVE SENSORS PREVIEW ---
        LinearLayout sensorsBox = new LinearLayout(this);
        sensorsBox.setOrientation(LinearLayout.VERTICAL);
        sensorsBox.setGravity(Gravity.CENTER_HORIZONTAL);
        sensorsBox.setPadding(0, dp(8), 0, 0);

        mSensorsGyroText = new TextView(this);
        mSensorsGyroText.setText("Гиро: Gx:0 Gy:0 Gz:0");
        mSensorsGyroText.setTextColor(0xFF38BDF8);
        mSensorsGyroText.setTextSize(9);
        sensorsBox.addView(mSensorsGyroText);

        mSensorsAccelText = new TextView(this);
        mSensorsAccelText.setText("Аксель: Ax:0 Ay:0 Az:9.8");
        mSensorsAccelText.setTextColor(0xFFA78BFA);
        mSensorsAccelText.setTextSize(9);
        sensorsBox.addView(mSensorsAccelText);

        mSensorsIntensityBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mSensorsIntensityBar.setMax(100);
        mSensorsIntensityBar.setProgress(0);
        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(dp(130), dp(6));
        barP.setMargins(0, dp(2), 0, dp(4));
        sensorsBox.addView(mSensorsIntensityBar, barP);

        mSensorsTriggerAlert = new TextView(this);
        mSensorsTriggerAlert.setText("Датчики активны");
        mSensorsTriggerAlert.setTextColor(0xFF22C55E);
        mSensorsTriggerAlert.setTextSize(9);
        mSensorsTriggerAlert.setTypeface(Typeface.DEFAULT_BOLD);
        mSensorsTriggerAlert.setGravity(Gravity.CENTER);
        sensorsBox.addView(mSensorsTriggerAlert);

        layout.addView(sensorsBox);

        mTrainingScrollView.addView(layout);
        return mTrainingScrollView;
    }

    private Button createTrainingChoiceButton(String text, final Runnable action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(10);
        btn.setTextColor(0xFFE2E8F0);
        btn.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, dp(14)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(160), dp(32));
        p.setMargins(0, dp(3), 0, dp(3));
        btn.setLayoutParams(p);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrateFeedback(30);
                action.run();
            }
        });
        return btn;
    }

    private void startGestureTraining(int gesture) {
        mActiveTrainingGesture = gesture;
        mTrainingRepsCompleted = 0;
        mTrainingAccumulatedSum = 0f;
        mTrainingFinished = false;

        mTrainingMenuLayout.setVisibility(View.GONE);
        mTrainingActiveLayout.setVisibility(View.VISIBLE);
        mTrainingSuccessBanner.setVisibility(View.GONE);
        mTrainingRestartBtn.setVisibility(View.GONE);

        mTrainingProgressBar.setProgress(0);
        mTrainingRepsCounter.setText("0 / " + TRAINING_TARGET_REPS);
        mTrainingLiveFeedbackText.setText("Ожидание движения...");

        switch (gesture) {
            case TRAINING_OUTWARD:
                mTrainingGestureTitle.setText("🔄 Вращение наружу");
                mTrainingInstructionText.setText(mIsLeftHand
                    ? "Поверните кисть влево от себя"
                    : "Поверните кисть вправо от себя");
                break;
            case TRAINING_INWARD:
                mTrainingGestureTitle.setText("🔄 Вращение внутрь");
                mTrainingInstructionText.setText(mIsLeftHand
                    ? "Поверните кисть вправо к телу"
                    : "Поверните кисть влево к телу");
                break;
            case TRAINING_PINCH:
                mTrainingGestureTitle.setText("✌️ Щипок пальцами");
                mTrainingInstructionText.setText("Сделайте четкий щипок пальцами");
                break;
            case TRAINING_FIST:
                mTrainingGestureTitle.setText("✊ Сжатие в кулак");
                mTrainingInstructionText.setText("Энергично сожмите кисть в кулак");
                break;
        }
        vibrateFeedback(40);
    }

    private void stopGestureTraining() {
        mActiveTrainingGesture = TRAINING_NONE;
        mTrainingFinished = false;
        mTrainingMenuLayout.setVisibility(View.VISIBLE);
        mTrainingActiveLayout.setVisibility(View.GONE);
        vibrateFeedback(25);
    }

    private void recordTrainingRepetition(float val, final String name) {
        mLastGestureTriggerTime = System.currentTimeMillis();
        mTrainingRepsCompleted++;
        mTrainingAccumulatedSum += val;
        vibrateFeedback(60);

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTrainingRepsCounter != null) {
                    mTrainingRepsCounter.setText(mTrainingRepsCompleted + " / " + TRAINING_TARGET_REPS);
                }
                if (mTrainingProgressBar != null) {
                    mTrainingProgressBar.setProgress(mTrainingRepsCompleted);
                }
                if (mTrainingLiveFeedbackText != null) {
                    mTrainingLiveFeedbackText.setText("Отлично! Движение #" + mTrainingRepsCompleted + " записано");
                }

                if (mTrainingRepsCompleted >= TRAINING_TARGET_REPS) {
                    completeTraining();
                }
            }
        });
    }

    private void completeTraining() {
        mTrainingFinished = true;
        vibrateFeedback(140);

        float avg = mTrainingAccumulatedSum / (float) TRAINING_TARGET_REPS;
        String resultMsg = "";

        if (mActiveTrainingGesture == TRAINING_OUTWARD || mActiveTrainingGesture == TRAINING_INWARD) {
            mAngleThreshold = Math.max(25f, Math.min(75f, (avg / 4.0f) * 0.85f));
            resultMsg = "Порог вращения: " + ((int) mAngleThreshold) + "°";
            if (mSettingsAngleText != null) mSettingsAngleText.setText(((int) mAngleThreshold) + "°");
        } else if (mActiveTrainingGesture == TRAINING_PINCH) {
            mPinchThreshold = Math.max(1.4f, Math.min(3.5f, avg * 0.85f));
            resultMsg = String.format(Locale.US, "Порог щипка: %.1f g", mPinchThreshold);
            if (mSettingsPinchText != null) mSettingsPinchText.setText(String.format(Locale.US, "%.1f g", mPinchThreshold));
        } else if (mActiveTrainingGesture == TRAINING_FIST) {
            mClenchThreshold = Math.max(2.0f, Math.min(4.8f, avg * 0.85f));
            resultMsg = String.format(Locale.US, "Порог кулака: %.1f g", mClenchThreshold);
            if (mSettingsClenchText != null) mSettingsClenchText.setText(String.format(Locale.US, "%.1f g", mClenchThreshold));
        }

        savePreferences();

        final String bannerText = "✅ Жест откалиброван!\n" + resultMsg;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTrainingSuccessBanner != null) {
                    mTrainingSuccessBanner.setText(bannerText);
                    mTrainingSuccessBanner.setVisibility(View.VISIBLE);
                }
                if (mTrainingRestartBtn != null) {
                    mTrainingRestartBtn.setVisibility(View.VISIBLE);
                }
                if (mTrainingLiveFeedbackText != null) {
                    mTrainingLiveFeedbackText.setText("Калибровка сохранена в памяти часов!");
                }
            }
        });
    }

    // ==========================================
    // TOUCH GESTURES (SWIPE NAVIGATION)
    // ==========================================
    private void setupGestureDetector() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 40;
            private static final int SWIPE_THRESHOLD_VELOCITY = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        if (diffX > 0) {
                            // User swiped Left-to-Right -> Go to PREVIOUS screen
                            if (mCurrentScreen > 0) {
                                switchToScreen(mCurrentScreen - 1);
                                vibrateFeedback(25);
                                return true;
                            }
                        } else {
                            // User swiped Right-to-Left -> Go to NEXT screen
                            if (mCurrentScreen < NUM_SCREENS - 1) {
                                switchToScreen(mCurrentScreen + 1);
                                vibrateFeedback(25);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    // Intercept touch events without blocking button clicks
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ==========================================
    // ROTARY BEZEL (TOUCH BEZEL ON SM-R860)
    // ==========================================
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        try {
            if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) != 0) {
                float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
                handleRotaryScroll(delta);
                return true;
            }
        } catch (Throwable ignored) {}
        return super.onGenericMotionEvent(event);
    }

    private void handleRotaryScroll(float delta) {
        vibrateFeedback(18);

        if (mCurrentScreen == 1) {
            // Screen 1: Player -> Adjust volume up/down
            adjustVolume(delta > 0 ? 1 : -1);
        } else if (mCurrentScreen == 2 && mSettingsScrollView != null) {
            // Screen 2: Settings -> Scroll content
            mSettingsScrollView.smoothScrollBy(0, (int) (delta * 80));
        } else if (mCurrentScreen == 3 && mTrainingScrollView != null) {
            // Screen 3: Gesture Training -> Scroll content
            mTrainingScrollView.smoothScrollBy(0, (int) (delta * 80));
        } else {
            // Screen 0: Tile -> Bezel rotates between screens
            mRotaryAccumulator += delta;
            if (mRotaryAccumulator > 0.7f) {
                switchToScreen(Math.min(NUM_SCREENS - 1, mCurrentScreen + 1));
                mRotaryAccumulator = 0f;
            } else if (mRotaryAccumulator < -0.7f) {
                switchToScreen(Math.max(0, mCurrentScreen - 1));
                mRotaryAccumulator = 0f;
            }
        }
    }

    // Hardware back button navigation
    @Override
    public void onBackPressed() {
        if (mCurrentScreen > 0) {
            switchToScreen(mCurrentScreen - 1);
            vibrateFeedback(20);
        } else {
            super.onBackPressed();
        }
    }

    private void switchToScreen(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= NUM_SCREENS) return;
        mCurrentScreen = screenIndex;
        mRotaryAccumulator = 0f;

        for (int i = 0; i < NUM_SCREENS; i++) {
            if (mScreens[i] != null) {
                mScreens[i].setVisibility(i == mCurrentScreen ? View.VISIBLE : View.GONE);
            }
        }
        updateDotsAppearance();
        updateVolumeIndicator();
    }

    // ==========================================
    // HARDWARE SENSOR PROCESSING & GESTURE ENGINE
    // ==========================================
    private synchronized void registerSensors() {
        if (mSensorManager == null) return;
        try {
            if (mGyroscope != null) {
                mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            if (mAccelerometer != null) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Sensor registration failed: ", t);
        }
    }

    private synchronized void unregisterSensors() {
        if (mSensorManager == null) return;
        try {
            mSensorManager.unregisterListener(this);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mSensorsActive) return;

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mLastGx = event.values[0];
            mLastGy = event.values[1];
            mLastGz = event.values[2];
            processRotationalGesture(mLastGx, mLastGy, mLastGz);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mLastAx = event.values[0];
            mLastAy = event.values[1];
            mLastAz = event.values[2];
            processAccelerationGesture(mLastAx, mLastAy, mLastAz);
        }

        updateLiveSensorsUI();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // Wrist twist detection
    private void processRotationalGesture(float gx, float gy, float gz) {
        long now = System.currentTimeMillis();
        if (now - mLastGestureTriggerTime < GESTURE_COOLDOWN_MS) return;

        float rotationSpeed = (float) Math.toDegrees(Math.abs(gy));
        float threshold = mAngleThreshold * 4.0f; // Angular velocity threshold (deg/s)
        boolean isOutward = mIsLeftHand ? (gy < 0) : (gy > 0);

        if (mActiveTrainingGesture != TRAINING_NONE && !mTrainingFinished) {
            if (mActiveTrainingGesture == TRAINING_OUTWARD && isOutward && rotationSpeed > mAngleThreshold * 2.2f) {
                recordTrainingRepetition(rotationSpeed, "Вращение наружу");
                return;
            } else if (mActiveTrainingGesture == TRAINING_INWARD && !isOutward && rotationSpeed > mAngleThreshold * 2.2f) {
                recordTrainingRepetition(rotationSpeed, "Вращение внутрь");
                return;
            }
        }

        if (rotationSpeed > threshold) {
            mLastGestureTriggerTime = now;

            if (isOutward) {
                // Twist outward -> Next track
                triggerGestureAction("Вращение наружу", "Следующий трек", KeyEvent.KEYCODE_MEDIA_NEXT);
            } else {
                // Twist inward -> Previous track
                triggerGestureAction("Вращение внутрь", "Предыдущий трек", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        }
    }

    // Double pinch & fist clench detection
    private void processAccelerationGesture(float ax, float ay, float az) {
        long now = System.currentTimeMillis();
        if (now - mLastGestureTriggerTime < GESTURE_COOLDOWN_MS) return;

        float totalG = (float) Math.sqrt(ax * ax + ay * ay + az * az) / 9.80665f;

        if (mActiveTrainingGesture != TRAINING_NONE && !mTrainingFinished) {
            if (mActiveTrainingGesture == TRAINING_PINCH && totalG > 1.5f && totalG < 3.2f) {
                recordTrainingRepetition(totalG, "Щипок пальцами");
                return;
            } else if (mActiveTrainingGesture == TRAINING_FIST && totalG >= 2.4f) {
                recordTrainingRepetition(totalG, "Сжатие кулака");
                return;
            }
        }

        if (totalG > mPinchThreshold && totalG < mClenchThreshold) {
            mLastGestureTriggerTime = now;
            // Quick pinch snap -> Play/Pause
            mIsPlaying = !mIsPlaying;
            if (mPlayerPlayPauseBtn != null) mPlayerPlayPauseBtn.setText(mIsPlaying ? "⏸" : "▶");
            triggerGestureAction("Щипок пальцами", mIsPlaying ? "Воспроизведение" : "Пауза", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else if (totalG >= mClenchThreshold) {
            mLastGestureTriggerTime = now;
            // Clench fist -> raise volume
            adjustVolume(1);
            triggerGestureAction("Сжатие кулака", "Громкость +", -1);
        }
    }

    private void triggerGestureAction(final String gestureName, final String actionName, final int keycode) {
        vibrateFeedback(60);
        if (keycode > 0 && mAudioManager != null) {
            try {
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
            } catch (Throwable ignored) {}
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTileLastGestureText != null) {
                    mTileLastGestureText.setText("⚡ " + gestureName + " -> " + actionName);
                }
                if (mSensorsTriggerAlert != null) {
                    mSensorsTriggerAlert.setText("⚡ СРАБОТАЛ: " + gestureName.toUpperCase());
                    mMainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mSensorsTriggerAlert != null) {
                                mSensorsTriggerAlert.setText("Ожидание жеста...");
                            }
                        }
                    }, 1200);
                }
            }
        });
    }

    private void updateLiveSensorsUI() {
        if (mCurrentScreen != 3) return;

        if (mSensorsGyroText != null) {
            mSensorsGyroText.setText(String.format(Locale.US,
                "Гиро: X:%+.1f Y:%+.1f Z:%+.1f rad/s", mLastGx, mLastGy, mLastGz));
        }
        if (mSensorsAccelText != null) {
            mSensorsAccelText.setText(String.format(Locale.US,
                "Аксель: X:%+.1f Y:%+.1f Z:%+.1f m/s²", mLastAx, mLastAy, mLastAz));
        }
        if (mSensorsIntensityBar != null) {
            float speed = (float) Math.sqrt(mLastGx * mLastGx + mLastGy * mLastGy + mLastGz * mLastGz);
            int percent = Math.min(100, (int) (speed * 20));
            mSensorsIntensityBar.setProgress(percent);
        }
    }

    // ==========================================
    // HAPTIC FEEDBACK & CLOCK
    // ==========================================
    private void vibrateFeedback(long milliseconds) {
        if (!mHapticsEnabled) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                    return;
                }
            }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(milliseconds);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void startClockUpdater() {
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mTimeHeaderView != null) {
                    mTimeHeaderView.setText(mTimeFormat.format(new Date()));
                }
                mMainHandler.postDelayed(this, 10000);
            }
        }, 10000);
    }

    // ==========================================
    // GRAPHIC HELPERS (ROUND WEAR DRAWABLES)
    // ==========================================
    private GradientDrawable createPillDrawable(int fillColor, int strokeColor, int cornerRadius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setStroke(dp(1), strokeColor);
        gd.setCornerRadius(cornerRadius);
        return gd;
    }

    private GradientDrawable createCircleDrawable(int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fillColor);
        gd.setStroke(dp(2), strokeColor);
        return gd;
    }

    private int dp(int value) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return (int) (value * dm.density + 0.5f);
    }

    private void showErrorDiagnostics(Throwable t) {
        TextView errorView = new TextView(this);
        errorView.setTextColor(Color.WHITE);
        errorView.setBackgroundColor(Color.BLACK);
        errorView.setPadding(dp(20), dp(20), dp(20), dp(20));
        errorView.setText("Gesture Wear:\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        setContentView(errorView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRootLayout != null) {
            mRootLayout.requestFocus();
        }
        if (mSensorsActive) registerSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensors();
    }
}
