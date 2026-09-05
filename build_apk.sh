#!/bin/bash
set -e

# Ensure SDK jars are present
if [ ! -f /tmp/android.jar ]; then
    echo "Downloading android.jar..."
    curl -fsSL "https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar" -o /tmp/android.jar
fi
if [ ! -f /tmp/r8.jar ]; then
    echo "Downloading r8.jar (D8 dexer)..."
    curl -fsSL "https://maven.google.com/com/android/tools/r8/8.2.42/r8-8.2.42.jar" -o /tmp/r8.jar
fi

BUILD_DIR="/tmp/apk_build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{src,res,assets,bin,gen,dex}

echo "1. Preparing Android Manifest and Resources from app/src/main..."
cp app/src/main/AndroidManifest.xml "$BUILD_DIR/AndroidManifest.xml"
cp -r app/src/main/res/* "$BUILD_DIR/res/"
cp -r app/src/main/java/* "$BUILD_DIR/src/"

# Copy production web assets
echo "2. Copying built web assets into assets/..."
cp -r dist "$BUILD_DIR/assets/"
rm -f "$BUILD_DIR/assets/dist/"*.apk

echo "3. Running aapt to generate R.java and compiled resources..."
aapt package -m -J "$BUILD_DIR/gen" -M "$BUILD_DIR/AndroidManifest.xml" -S "$BUILD_DIR/res" -I /tmp/android.jar

echo "4. Compiling Java sources with javac..."
javac -cp /tmp/android.jar -d "$BUILD_DIR/bin" \
    "$BUILD_DIR/gen/com/example/gesturemusicwear/R.java" \
    $(find "$BUILD_DIR/src" -name "*.java")

echo "5. Dexing classes into classes.dex with D8..."
java -cp /tmp/r8.jar com.android.tools.r8.D8 \
    --lib /tmp/android.jar \
    --output "$BUILD_DIR/dex" \
    --min-api 26 \
    $(find "$BUILD_DIR/bin" -name "*.class")

echo "6. Packaging unaligned APK with aapt..."
aapt package -f \
    -M "$BUILD_DIR/AndroidManifest.xml" \
    -S "$BUILD_DIR/res" \
    -A "$BUILD_DIR/assets" \
    -I /tmp/android.jar \
    -F "$BUILD_DIR/app-unaligned.apk"

# Add classes.dex
cd "$BUILD_DIR/dex"
aapt add "$BUILD_DIR/app-unaligned.apk" classes.dex
cd - > /dev/null

echo "7. Aligning APK with zipalign..."
zipalign -v -p 4 "$BUILD_DIR/app-unaligned.apk" "$BUILD_DIR/app-aligned.apk"

echo "8. Generating keystore and signing APK..."
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
    --out "gesture-music-wear.apk" \
    "$BUILD_DIR/app-aligned.apk"

# Also copy to app-debug.apk in root for standard gradle path compatibility
cp "gesture-music-wear.apk" "app-debug.apk"
mkdir -p app/build/outputs/apk/debug
cp "gesture-music-wear.apk" app/build/outputs/apk/debug/app-debug.apk

mkdir -p public dist
cp "gesture-music-wear.apk" "app-debug.apk" public/
cp "gesture-music-wear.apk" "app-debug.apk" dist/

echo "9. Verifying signed APK..."
apksigner verify "gesture-music-wear.apk"

echo "SUCCESS: APK created at ./gesture-music-wear.apk and ./app-debug.apk"
ls -lh gesture-music-wear.apk app-debug.apk
