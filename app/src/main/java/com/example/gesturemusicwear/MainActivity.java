package com.example.gesturemusicwear;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String TAG = "GestureMusicWear";
    private WebView mWebView;
    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor mAccelerometer;
    private boolean mSensorsActive = false;

    private float lastGx = 0f, lastGy = 0f, lastGz = 0f;
    private float lastAx = 0f, lastAy = 0f, lastAz = 0f;
    private boolean hasGyro = false;
    private boolean hasAcc = false;
    private long lastDispatchTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Catch any uncaught runtime errors to prevent application crash on Galaxy Watch
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.e(TAG, "Uncaught exception safely handled: ", e);
            }
        });

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mWebView = new WebView(this);
            mWebView.setBackgroundColor(Color.BLACK);
            setContentView(mWebView);

            WebSettings settings = mWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            mWebView.setWebViewClient(new WebViewClient());
            mWebView.setWebChromeClient(new WebChromeClient());
            mWebView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

            // Touch Bezel (digital rotary ring on Samsung Galaxy Watch 4 SM-R860)
            mWebView.setOnGenericMotionListener(new View.OnGenericMotionListener() {
                @Override
                public boolean onGenericMotion(View v, MotionEvent event) {
                    try {
                        if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                            (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) != 0) {
                            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
                            mWebView.scrollBy(0, (int) (delta * 65));
                            return true;
                        }
                    } catch (Throwable ignored) {}
                    return false;
                }
            });

            initSensors();

            // Load bundled web app
            mWebView.loadUrl("file:///android_asset/dist/index.html");

        } catch (Throwable t) {
            Log.e(TAG, "Fatal startup error: ", t);
            TextView errorView = new TextView(this);
            errorView.setTextColor(Color.WHITE);
            errorView.setBackgroundColor(Color.BLACK);
            errorView.setPadding(24, 24, 24, 24);
            errorView.setText("Gesture Wear:\n" + t.getMessage());
            setContentView(errorView);
        }
    }

    private void initSensors() {
        try {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null) {
                mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Sensor init failed: " + t.getMessage());
        }
    }

    private synchronized void registerSensors() {
        if (mSensorsActive || mSensorManager == null) return;
        try {
            if (mGyroscope != null) {
                mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            if (mAccelerometer != null) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            mSensorsActive = true;
            Log.d(TAG, "Sensors successfully registered");
        } catch (Throwable t) {
            Log.w(TAG, "Could not register sensors: " + t.getMessage());
        }
    }

    private synchronized void unregisterSensors() {
        if (!mSensorsActive || mSensorManager == null) return;
        try {
            mSensorManager.unregisterListener(this);
            mSensorsActive = false;
            Log.d(TAG, "Sensors unregistered");
        } catch (Throwable t) {
            Log.w(TAG, "Could not unregister sensors: " + t.getMessage());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mSensorsActive || mWebView == null) return;
        try {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_GYROSCOPE) {
                lastGx = event.values[0];
                lastGy = event.values[1];
                lastGz = event.values[2];
                hasGyro = true;
            } else if (type == Sensor.TYPE_ACCELEROMETER) {
                lastAx = event.values[0];
                lastAy = event.values[1];
                lastAz = event.values[2];
                hasAcc = true;
            }

            long now = System.currentTimeMillis();
            if (hasGyro && hasAcc && (now - lastDispatchTime >= 20)) {
                lastDispatchTime = now;
                final String js = String.format(Locale.US,
                    "if(window.onAndroidSensorData){window.onAndroidSensorData(%.4f,%.4f,%.4f,%.4f,%.4f,%.4f);}",
                    lastGx, lastGy, lastGz, lastAx, lastAy, lastAz);
                mWebView.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mWebView.evaluateJavascript(js, null);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "Sensor dispatch warning: " + t.getMessage());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public class WebAppInterface {
        private final Context mContext;

        WebAppInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public void triggerHaptic(int durationMs) {
            try {
                Vibrator v = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vm = (VibratorManager) mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    if (vm != null) v = vm.getDefaultVibrator();
                }
                if (v == null) {
                    v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                }

                if (v != null && v.hasVibrator()) {
                    int clamped = Math.max(15, Math.min(durationMs, 400));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(clamped, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(clamped);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Haptic error: " + t.getMessage());
            }
        }

        @JavascriptInterface
        public void startSensors() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    registerSensors();
                }
            });
        }

        @JavascriptInterface
        public void stopSensors() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    unregisterSensors();
                }
            });
        }

        @JavascriptInterface
        public String getWatchInfo() {
            return "Samsung Galaxy Watch 4 (SM-R860)";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorsActive) {
            registerSensors();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensors();
    }

    @Override
    protected void onDestroy() {
        unregisterSensors();
        if (mWebView != null) {
            try {
                mWebView.destroy();
            } catch (Throwable ignored) {}
            mWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

