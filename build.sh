#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

export JAVA_HOME=/home/unni/jdk-17.0.2
export PATH="$JAVA_HOME/bin:$PATH"

SDK_ROOT=/home/unni/Android/Sdk
BUILD_TOOLS="$SDK_ROOT/build-tools/34.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-34/android.jar"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

rm -rf build
mkdir -p build/gen build/classes build/dex

echo "[1/6] Compiling resources with aapt2..."
$AAPT2 compile --dir src/main/res -o build/resources.zip

echo "[2/6] Linking resources & generating R.java..."
$AAPT2 link -I "$ANDROID_JAR" \
    --manifest src/main/AndroidManifest.xml \
    -o build/unaligned.apk \
    --java build/gen \
    --auto-add-overlay \
    build/resources.zip

echo "[3/6] Compiling Java sources..."
javac -d build/classes \
    -cp "$ANDROID_JAR" \
    -source 11 -target 11 \
    src/main/java/io/safetv/launcher/*.java \
    build/gen/io/safetv/launcher/R.java

echo "[4/6] Dexing bytecode with d8..."
$D8 --release --min-api 26 --output build/dex build/classes/io/safetv/launcher/*.class

echo "[5/6] Adding classes.dex to APK..."
cd build/dex
zip -u ../unaligned.apk classes.dex
cd "$DIR"

echo "[6/6] Zipaligning & signing APK..."
$ZIPALIGN -f -p 4 build/unaligned.apk build/SafeTV.apk

# Generate debug keystore if missing
if [ ! -f debug.keystore ]; then
    keytool -genkey -v -keystore debug.keystore -alias androiddebugkey \
        -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=SafeTV, OU=Antigravity, O=SafeTV, L=Local, S=Local, C=US"
fi

$APKSIGNER sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android build/SafeTV.apk

echo "=== BUILD SUCCESSFUL: build/SafeTV.apk ==="
ls -lh build/SafeTV.apk
