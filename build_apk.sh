#!/bin/bash
set -e

BUILD_DIR="/tmp/apk_build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{src/com/example/gesturemusicwear,res/values,res/mipmap-mdpi,res/drawable,assets,bin,gen,dex}

echo "1. Preparing Android Manifest and Resources..."
cat << 'EOF' > "$BUILD_DIR/AndroidManifest.xml"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.gesturemusicwear"
    android:versionCode="1"
    android:versionName="1.0.0">

    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33" />
    <uses-feature android:name="android.hardware.type.watch" />
    
    <!-- Sensor and background permissions for Wear OS -->
    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">

        <!-- Main Wear OS Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Background Gesture Detection Service -->
        <service
            android:name=".GestureService"
            android:exported="false"
            android:label="Gesture Detection Service" />

        <!-- Wear OS Quick Tile Service -->
        <service
            android:name=".GestureTileService"
            android:exported="true"
            android:label="@string/tile_name"
            android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
            <intent-filter>
                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
            </intent-filter>
        </service>
    </application>
</manifest>
EOF

cat << 'EOF' > "$BUILD_DIR/res/values/strings.xml"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Gesture Music Wear</string>
    <string name="tile_name">Плитка жестов</string>
</resources>
EOF

# Copy production web assets
echo "2. Copying built web assets into assets/..."
cp -r dist "$BUILD_DIR/assets/"

# Generate Java sources
echo "3. Writing Android Java Sources..."
cat << 'EOF' > "$BUILD_DIR/src/com/example/gesturemusicwear/MainActivity.java"
package com.example.gesturemusicwear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.content.Context;

public class MainActivity extends Activity {
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mWebView = new WebView(this);
        setContentView(mWebView);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        // Load bundled web app
        mWebView.loadUrl("file:///android_asset/dist/index.html");

        // Start background gesture sensor service
        Intent serviceIntent = new Intent(this, GestureService.class);
        startService(serviceIntent);
    }

    public static class WebAppInterface {
        private Context mContext;

        WebAppInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public void triggerHaptic(int durationMs) {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(durationMs);
                }
            }
        }
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
EOF

cat << 'EOF' > "$BUILD_DIR/src/com/example/gesturemusicwear/GestureService.java"
package com.example.gesturemusicwear;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

public class GestureService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor gyroscope;
    private Sensor accelerometer;
    private AudioManager audioManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // High frequency sensor reading for Wear OS wrist gesture recognition
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
EOF

cat << 'EOF' > "$BUILD_DIR/src/com/example/gesturemusicwear/GestureTileService.java"
package com.example.gesturemusicwear;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class GestureTileService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
EOF

echo "4. Running aapt to generate R.java and compiled resources..."
aapt package -m -J "$BUILD_DIR/gen" -M "$BUILD_DIR/AndroidManifest.xml" -S "$BUILD_DIR/res" -I /tmp/android.jar

echo "5. Compiling Java sources with javac..."
javac -cp /tmp/android.jar -d "$BUILD_DIR/bin" \
    "$BUILD_DIR/gen/com/example/gesturemusicwear/R.java" \
    "$BUILD_DIR/src/com/example/gesturemusicwear/MainActivity.java" \
    "$BUILD_DIR/src/com/example/gesturemusicwear/GestureService.java" \
    "$BUILD_DIR/src/com/example/gesturemusicwear/GestureTileService.java"

echo "6. Dexing classes into classes.dex with D8..."
java -cp /tmp/r8.jar com.android.tools.r8.D8 \
    --lib /tmp/android.jar \
    --output "$BUILD_DIR/dex" \
    --min-api 26 \
    $(find "$BUILD_DIR/bin" -name "*.class")

echo "7. Packaging unaligned APK with aapt..."
aapt package -f \
    -M "$BUILD_DIR/AndroidManifest.xml" \
    -S "$BUILD_DIR/res" \
    -A "$BUILD_DIR/assets" \
    -I /tmp/android.jar \
    -F "$BUILD_DIR/app-unaligned.apk"

# Add classes.dex
cd "$BUILD_DIR/dex"
aapt add "$BUILD_DIR/app-unaligned.apk" classes.dex
cd -

echo "8. Aligning APK with zipalign..."
zipalign -v -p 4 "$BUILD_DIR/app-unaligned.apk" "$BUILD_DIR/app-aligned.apk"

echo "9. Generating keystore and signing APK..."
KEYSTORE="/tmp/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -storepass android \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "/app/applet/gesture-music-wear.apk" \
    "$BUILD_DIR/app-aligned.apk"

# Also copy to app-debug.apk in root for standard gradle path compatibility
cp "/app/applet/gesture-music-wear.apk" "/app/applet/app-debug.apk"

echo "10. Verifying signed APK..."
apksigner verify "/app/applet/gesture-music-wear.apk"

echo "SUCCESS: APK created at /app/applet/gesture-music-wear.apk and /app/applet/app-debug.apk"
ls -lh /app/applet/*.apk
