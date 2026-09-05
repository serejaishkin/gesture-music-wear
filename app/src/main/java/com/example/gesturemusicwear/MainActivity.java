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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final long GESTURE_COOLDOWN_MS = 1100L;

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

    // Rotary Bezel accumulation
    private float mRotaryAccumulator = 0f;

    // ==========================================
    // DTW GESTURE RECOGNITION ENGINE
    // ==========================================
    private static final int WINDOW_SIZE = 90;          // Max samples in sliding window
    private static final int MIN_SAMPLES = 22;          // Need substantial gesture
    private static final float DTW_THRESHOLD = 1.05f;   // Strict DTW distance threshold
    private static final float MOTION_START_THRESHOLD = 0.15f; // Above noise floor (~0.05-0.08)
    private static final float MOTION_EVAL_THRESHOLD = 0.38f; // Real motion to evaluate DTW
    private static final float START_MOTION_THRESHOLD = 0.35f;
    private static final float END_MOTION_THRESHOLD = 0.12f;
    private static final int QUIET_SAMPLES_TO_END = 6;
    private static final int RECOGNITION_EVAL_INTERVAL = 14; // Evaluate every N samples
    private static final long DTW_RECOGNITION_COOLDOWN_MS = 1500L;
    private static final int IDLE_CLEAR_THRESHOLD = 3;  // Clear window after N quiet eval cycles
    private static final long MIN_GESTURE_DURATION_MS = 320L; // Minimum duration for real gesture

    // Sliding window buffer: [WINDOW_SIZE][6] where 6 = {gx, gy, gz, ax, ay, az}
    private final float[][] mSensorWindow = new float[WINDOW_SIZE][6];
    private int mWindowIndex = 0;
    private int mWindowFill = 0;

    // Training recording buffer (raw samples before normalization)
    private final List<float[]> mTrainingRecording = new ArrayList<>();
    private boolean mTrainingMotionActive = false;
    private int mTrainingQuietCount = 0;
    private float[] mTrainingPrevSample = null;
    private long mTrainingRecordingStartTime = 0L;
    private int mTrainingRepIndex = 0; // Which repetition we're on within a session

    // Stored gesture templates: gestureType -> list of normalized sample sequences
    private final Map<Integer, List<float[][]>> mGestureTemplates = new HashMap<>();

    // DTW recognition state
    private long mLastDtwRecognitionTime = 0L;
    private int mDtwEvalCounter = 0;
    private int mIdleCycleCount = 0; // Counts consecutive quiet evaluation cycles
    private long mWindowFillStartTime = 0L; // When window started receiving motion

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
            loadGestureTemplates();

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
        int trainedCount = mGestureTemplates.size();
        String tileHint = mSensorsActive ? "Ожидание жеста..." : "Жесты отключены";
        if (trainedCount > 0) tileHint += " (DTW: " + trainedCount + " жестов)";
        mTileLastGestureText.setText(tileHint);
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
        mTrainingScrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(24), dp(52), dp(24), dp(48));

        TextView title = new TextView(this);
        title.setText("🎓 Обучение");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        layout.addView(title);

        // --- SECTION A: SELECTION MENU ---
        mTrainingMenuLayout = new LinearLayout(this);
        mTrainingMenuLayout.setOrientation(LinearLayout.VERTICAL);
        mTrainingMenuLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView menuSub = new TextView(this);
        menuSub.setText("Выберите жест:");
        menuSub.setTextColor(0xFF94A3B8);
        menuSub.setTextSize(10);
        menuSub.setGravity(Gravity.CENTER);
        menuSub.setPadding(0, 0, 0, dp(6));
        mTrainingMenuLayout.addView(menuSub);

        mTrainingMenuLayout.addView(createTrainingChoiceButton("🔄 Вращение наружу", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_OUTWARD); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("🔄 Вращение внутрь", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_INWARD); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("✌️ Щипок", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_PINCH); }
        }));
        mTrainingMenuLayout.addView(createTrainingChoiceButton("✊ Кулак", new Runnable() {
            @Override
            public void run() { startGestureTraining(TRAINING_FIST); }
        }));

        // DTW status
        TextView dtwStatus = new TextView(this);
        dtwStatus.setTextColor(0xFF64748B);
        dtwStatus.setTextSize(8);
        dtwStatus.setGravity(Gravity.CENTER);
        dtwStatus.setPadding(0, dp(4), 0, dp(2));
        int totalTemplates = 0;
        for (List<float[][]> t : mGestureTemplates.values()) totalTemplates += t.size();
        dtwStatus.setText("DTW: " + totalTemplates + " шаблонов");
        mTrainingMenuLayout.addView(dtwStatus);

        Button clearTemplatesBtn = new Button(this);
        clearTemplatesBtn.setText("🗑 Очистить шаблоны");
        clearTemplatesBtn.setTextSize(9);
        clearTemplatesBtn.setTextColor(0xFFEF4444);
        clearTemplatesBtn.setBackground(createPillDrawable(0xFF450A0A, 0xFF7F1D1D, dp(10)));
        LinearLayout.LayoutParams clearP = new LinearLayout.LayoutParams(dp(140), dp(26));
        clearP.setMargins(0, dp(2), 0, dp(2));
        clearTemplatesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGestureTemplates.clear();
                saveGestureTemplates();
                vibrateFeedback(40);
                // Refresh DTW status text
                dtwStatus.setText("DTW шаблонов: 0 (макс. 3/жест)");
            }
        });
        mTrainingMenuLayout.addView(clearTemplatesBtn, clearP);

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

        // Reset DTW training recording state
        mTrainingRecording.clear();
        mTrainingMotionActive = false;
        mTrainingQuietCount = 0;
        mTrainingPrevSample = null;
        mTrainingRecordingStartTime = 0L;
        mTrainingRepIndex = 0;

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
        mTrainingRecording.clear();
        mTrainingMotionActive = false;
        mTrainingPrevSample = null;
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

        // Feed DTW engine (always, even during training)
        feedDtwWindow(gx, gy, gz, mLastAx, mLastAy, mLastAz);

        // Feed training recording if in training mode
        if (mActiveTrainingGesture != TRAINING_NONE && !mTrainingFinished) {
            if (feedTrainingRecording(gx, gy, gz, mLastAx, mLastAy, mLastAz)) return;

            // Legacy threshold-based training (fallback)
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

        // Feed training recording if in training mode (for pinch/fist gestures)
        if (mActiveTrainingGesture != TRAINING_NONE && !mTrainingFinished) {
            if (feedTrainingRecording(mLastGx, mLastGy, mLastGz, ax, ay, az)) return;

            // Legacy threshold-based training (fallback)
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
            }
        });
    }

    private void updateLiveSensorsUI() {
    }

    // ==========================================
    // DTW GESTURE TRAINING & RECOGNITION
    // ==========================================

    /** Add a sensor sample to the sliding window for DTW recognition. */
    private void feedDtwWindow(float gx, float gy, float gz, float ax, float ay, float az) {
        boolean wasEmpty = (mWindowFill == 0);

        mSensorWindow[mWindowIndex][0] = gx;
        mSensorWindow[mWindowIndex][1] = gy;
        mSensorWindow[mWindowIndex][2] = gz;
        mSensorWindow[mWindowIndex][3] = ax;
        mSensorWindow[mWindowIndex][4] = ay;
        mSensorWindow[mWindowIndex][5] = az;
        mWindowIndex = (mWindowIndex + 1) % WINDOW_SIZE;
        if (mWindowFill < WINDOW_SIZE) mWindowFill++;

        if (wasEmpty) {
            mWindowFillStartTime = System.currentTimeMillis();
        }

        // Frame-to-frame delta (real motion, not gravity)
        if (mWindowFill >= 2) {
            int prevIdx = (mWindowIndex - 2 + WINDOW_SIZE) % WINDOW_SIZE;
            float dgx = gx - mSensorWindow[prevIdx][0];
            float dgy = gy - mSensorWindow[prevIdx][1];
            float dgz = gz - mSensorWindow[prevIdx][2];
            float dax = ax - mSensorWindow[prevIdx][3];
            float day = ay - mSensorWindow[prevIdx][4];
            float daz = az - mSensorWindow[prevIdx][5];
            float delta = (float) Math.sqrt(dgx * dgx + dgy * dgy + dgz * dgz + dax * dax + day * day + daz * daz);
            if (delta > 0.8f) {
                mIdleCycleCount = 0;
            }
        }

        mDtwEvalCounter++;
        if (mDtwEvalCounter >= RECOGNITION_EVAL_INTERVAL) {
            mDtwEvalCounter = 0;
            tryDtwRecognition();
        }
    }

    /** Attempt to recognize a trained gesture from the current sliding window. */
    private void tryDtwRecognition() {
        long now = System.currentTimeMillis();
        if (now - mLastDtwRecognitionTime < DTW_RECOGNITION_COOLDOWN_MS) return;
        if (mActiveTrainingGesture != TRAINING_NONE) return;

        int count = mWindowFill;
        if (count < MIN_SAMPLES) return;

        float[][] window = getWindowSamples(count);
        float energy = motionEnergy(window, count);

        // Below start threshold = pure idle, clear window
        if (energy < MOTION_START_THRESHOLD) {
            mIdleCycleCount++;
            if (mIdleCycleCount >= IDLE_CLEAR_THRESHOLD) {
                mWindowFill = 0;
                mWindowIndex = 0;
            }
            return;
        }

        mIdleCycleCount = 0;

        // Between start and eval threshold = accumulating but not yet evaluating
        if (energy < MOTION_EVAL_THRESHOLD) return;

        // Need enough samples AND enough elapsed time for a real gesture
        if (count < MIN_SAMPLES) return;
        long elapsed = System.currentTimeMillis() - mWindowFillStartTime;
        if (elapsed < MIN_GESTURE_DURATION_MS) return;

        float[][] normalized = normalizeSamples(window, count);

        int bestType = -1;
        float bestDist = Float.MAX_VALUE;

        for (Map.Entry<Integer, List<float[][]>> entry : mGestureTemplates.entrySet()) {
            int gestureType = entry.getKey();
            List<float[][]> templates = entry.getValue();
            if (templates == null || templates.isEmpty()) continue;

            for (float[][] template : templates) {
                float dist = dtwDistance(normalized, count, template, template.length);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestType = gestureType;
                }
            }
        }

        if (bestType >= 0 && bestDist <= DTW_THRESHOLD) {
            mLastDtwRecognitionTime = now;
            mWindowFill = 0;
            mWindowIndex = 0;
            mIdleCycleCount = 0;
            executeDtwGesture(bestType, bestDist);
        }
    }

    /** Execute the action for a DTW-recognized gesture. */
    private void executeDtwGesture(int gestureType, float distance) {
        switch (gestureType) {
            case TRAINING_OUTWARD:
                triggerGestureAction("DTW: Вращение наружу", "Следующий трек (DTW)", KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case TRAINING_INWARD:
                triggerGestureAction("DTW: Вращение внутрь", "Предыдущий трек (DTW)", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case TRAINING_PINCH:
                mIsPlaying = !mIsPlaying;
                if (mPlayerPlayPauseBtn != null) mPlayerPlayPauseBtn.setText(mIsPlaying ? "⏸" : "▶");
                triggerGestureAction("DTW: Щипок", mIsPlaying ? "Воспроизведение (DTW)" : "Пауза (DTW)", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case TRAINING_FIST:
                adjustVolume(1);
                triggerGestureAction("DTW: Кулак", "Громкость+ (DTW)", -1);
                break;
        }
    }

    // --- Training sample recording for DTW templates ---

    /** Feed sensor data into the training recording pipeline. Returns true if training handled it. */
    private boolean feedTrainingRecording(float gx, float gy, float gz, float ax, float ay, float az) {
        if (mActiveTrainingGesture == TRAINING_NONE || mTrainingFinished) return false;

        float[] sample = new float[]{gx, gy, gz, ax, ay, az};
        float energy = mTrainingPrevSample != null ? sampleDistance(mTrainingPrevSample, sample) : 0f;
        mTrainingPrevSample = sample;

        if (!mTrainingMotionActive) {
            if (energy >= START_MOTION_THRESHOLD) {
                mTrainingMotionActive = true;
                mTrainingRecordingStartTime = System.currentTimeMillis();
                mTrainingRecording.clear();
                mTrainingRecording.add(sample);
                mTrainingQuietCount = 0;
                updateTrainingFeedback("Движение обнаружено... Запись #" + (mTrainingRepIndex + 1));
            }
            return true;
        }

        // Recording active
        if (mTrainingRecording.size() < WINDOW_SIZE) {
            mTrainingRecording.add(sample);
        }

        if (energy < END_MOTION_THRESHOLD) {
            mTrainingQuietCount++;
        } else {
            mTrainingQuietCount = 0;
        }

        // Auto-stop: quiet for enough samples, or buffer full
        if (mTrainingQuietCount >= QUIET_SAMPLES_TO_END || mTrainingRecording.size() >= WINDOW_SIZE) {
            finishTrainingRepetition();
        }
        return true;
    }

    /** Called when a single training repetition recording is complete. */
    private void finishTrainingRepetition() {
        mTrainingMotionActive = false;
        mTrainingQuietCount = 0;
        mTrainingPrevSample = null;

        int count = mTrainingRecording.size();
        if (count < MIN_SAMPLES) {
            updateTrainingFeedback("Слишком коротко, попробуйте ещё раз...");
            return;
        }

        // Normalize the recorded samples
        float[][] raw = mTrainingRecording.toArray(new float[0][]);
        float[][] normalized = normalizeSamples(raw, count);

        // Store as template
        int gestureType = mActiveTrainingGesture;
        if (!mGestureTemplates.containsKey(gestureType)) {
            mGestureTemplates.put(gestureType, new ArrayList<>());
        }
        // Keep at most 3 templates per gesture (best variety)
        List<float[][]> templates = mGestureTemplates.get(gestureType);
        if (templates.size() >= 3) {
            templates.remove(0); // Remove oldest
        }
        templates.add(normalized);

        mTrainingRepIndex++;
        vibrateFeedback(60);

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTrainingRepsCounter != null) {
                    mTrainingRepsCounter.setText(mTrainingRepIndex + " / " + TRAINING_TARGET_REPS);
                }
                if (mTrainingProgressBar != null) {
                    mTrainingProgressBar.setProgress(mTrainingRepIndex);
                }

                if (mTrainingRepIndex >= TRAINING_TARGET_REPS) {
                    completeDtwTraining();
                } else {
                    updateTrainingFeedback("Шаблон #" + mTrainingRepIndex + " сохранён. Повторите gesture...");
                }
            }
        });
    }

    /** Called when all training repetitions are complete. */
    private void completeDtwTraining() {
        mTrainingFinished = true;
        vibrateFeedback(140);
        saveGestureTemplates();

        final String gestureName;
        switch (mActiveTrainingGesture) {
            case TRAINING_OUTWARD: gestureName = "Вращение наружу"; break;
            case TRAINING_INWARD: gestureName = "Вращение внутрь"; break;
            case TRAINING_PINCH: gestureName = "Щипок"; break;
            case TRAINING_FIST: gestureName = "Кулак"; break;
            default: gestureName = "Жест"; break;
        }

        final String bannerText = "✅ DTW шаблон сохранён!\n" + gestureName + " (" + TRAINING_TARGET_REPS + " повторов)";
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
                updateTrainingFeedback("Распознавание DTW активно для этого жеста!");
            }
        });
    }

    // --- DTW Algorithm ---

    /** Compute DTW distance between two normalized sample sequences. */
    private float dtwDistance(float[][] a, int lenA, float[][] b, int lenB) {
        float[][] dp = new float[lenA + 1][lenB + 1];
        for (int i = 0; i <= lenA; i++) java.util.Arrays.fill(dp[i], Float.MAX_VALUE);
        dp[0][0] = 0f;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                float cost = sampleDistance(a[i - 1], b[j - 1]);
                dp[i][j] = cost + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
            }
        }
        return dp[lenA][lenB] / (lenA + lenB);
    }

    /** Euclidean distance between two 6D samples. */
    private float sampleDistance(float[] a, float[] b) {
        float sum = 0f;
        for (int i = 0; i < 6; i++) {
            float d = a[i] - b[i];
            sum += d * d;
        }
        return (float) Math.sqrt(sum);
    }

    /** Average frame-to-frame distance (motion energy). */
    private float motionEnergy(float[][] samples, int count) {
        if (count < 2) return 0f;
        float total = 0f;
        for (int i = 1; i < count; i++) {
            total += sampleDistance(samples[i - 1], samples[i]);
        }
        return total / (count - 1);
    }

    /** Extract window samples in chronological order from circular buffer. */
    private float[][] getWindowSamples(int count) {
        float[][] result = new float[count][6];
        int start = (mWindowIndex - count + WINDOW_SIZE) % WINDOW_SIZE;
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % WINDOW_SIZE;
            System.arraycopy(mSensorWindow[idx], 0, result[i], 0, 6);
        }
        return result;
    }

    /** Normalize samples to zero-mean, unit-variance per channel. */
    private float[][] normalizeSamples(float[][] raw, int count) {
        if (count == 0) return raw;

        // Compute mean per channel
        float[] mean = new float[6];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < 6; c++) {
                mean[c] += raw[i][c];
            }
        }
        for (int c = 0; c < 6; c++) mean[c] /= count;

        // Compute RMS per channel
        float[] rms = new float[6];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < 6; c++) {
                float d = raw[i][c] - mean[c];
                rms[c] += d * d;
            }
        }
        for (int c = 0; c < 6; c++) {
            rms[c] = (float) Math.sqrt(Math.max(0.001, rms[c] / count));
        }

        // Normalize
        float[][] result = new float[count][6];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < 6; c++) {
                result[i][c] = (raw[i][c] - mean[c]) / rms[c];
            }
        }
        return result;
    }

    // --- Template Persistence (SharedPreferences) ---

    private void saveGestureTemplates() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<Integer, List<float[][]>> entry : mGestureTemplates.entrySet()) {
                int gestureType = entry.getKey();
                List<float[][]> templates = entry.getValue();
                if (templates == null || templates.isEmpty()) continue;

                for (int t = 0; t < templates.size(); t++) {
                    float[][] template = templates.get(t);
                    sb.append(gestureType).append(":").append(t).append("=");
                    sb.append(template.length);
                    for (float[] sample : template) {
                        for (int c = 0; c < 6; c++) {
                            sb.append(",").append(sample[c]);
                        }
                    }
                    sb.append(";");
                }
            }
            editor.putString("dtw_templates", sb.toString());
            editor.apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save DTW templates: " + t.getMessage());
        }
    }

    private void loadGestureTemplates() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String data = prefs.getString("dtw_templates", "");
            if (data.isEmpty()) return;

            mGestureTemplates.clear();
            String[] entries = data.split(";");
            for (String entry : entries) {
                if (entry.isEmpty()) continue;
                int eqIdx = entry.indexOf('=');
                if (eqIdx < 0) continue;

                String key = entry.substring(0, eqIdx); // "gestureType:templateIdx"
                String values = entry.substring(eqIdx + 1);

                int colonIdx = key.indexOf(':');
                if (colonIdx < 0) continue;
                int gestureType = Integer.parseInt(key.substring(0, colonIdx));

                String[] nums = values.split(",");
                int sampleCount = Integer.parseInt(nums[0]);
                float[][] template = new float[sampleCount][6];
                for (int i = 0; i < sampleCount; i++) {
                    for (int c = 0; c < 6; c++) {
                        template[i][c] = Float.parseFloat(nums[1 + i * 6 + c]);
                    }
                }

                if (!mGestureTemplates.containsKey(gestureType)) {
                    mGestureTemplates.put(gestureType, new ArrayList<>());
                }
                mGestureTemplates.get(gestureType).add(template);
            }
            Log.d(TAG, "Loaded DTW templates: " + mGestureTemplates.size() + " gesture types");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load DTW templates: " + t.getMessage());
        }
    }

    private void updateTrainingFeedback(String msg) {
        if (mTrainingLiveFeedbackText != null) {
            mTrainingLiveFeedbackText.setText(msg);
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
