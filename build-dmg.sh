#!/bin/bash

# Complete build script for NazarX GCS on macOS ARM64
# This script builds a working DMG with proper JavaFX configuration

set -e  # Exit on error

APP_NAME="NazarX GCS"
APP_VERSION="1.0.0"
MAIN_CLASS="com.example.satelliteapplication.Main"
IDENTIFIER="com.example.satellitegcs"

echo "=== Building ${APP_NAME} for macOS ARM64 ==="

# Clean previous builds
echo "Cleaning previous builds..."
rm -rf target/dist target/installer target/runtime
mvn clean

# Build JAR with dependencies
echo "Building application JAR with dependencies..."
mvn package

# Find the shaded JAR (uber-jar)
SHADED_JAR=$(find target -name "*-shaded.jar" | head -1)
if [ -z "$SHADED_JAR" ]; then
    # Try assembly JAR
    SHADED_JAR=$(find target -name "*-jar-with-dependencies.jar" | head -1)
fi
if [ -z "$SHADED_JAR" ]; then
    # Fall back to regular JAR
    SHADED_JAR=$(find target -name "SatelliteGCS-*.jar" -not -name "*-sources.jar" | head -1)
fi

echo "Using JAR: $SHADED_JAR"

# Download JavaFX modules if needed
echo "Setting up JavaFX modules..."
JAVAFX_VERSION="17.0.8"
JAVAFX_PATH="$HOME/.m2/repository/org/openjfx"

# Create custom runtime with JavaFX
echo "Creating custom runtime..."
MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.sql,java.xml,jdk.unsupported,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.web,javafx.swing,javafx.media,java.prefs,jdk.crypto.ec,jdk.localedata"

# Build module path
MODULE_PATH="$JAVA_HOME/jmods"
for module in base controls fxml graphics web swing media; do
    MODULE_JAR="$JAVAFX_PATH/javafx-$module/$JAVAFX_VERSION/javafx-$module-$JAVAFX_VERSION-mac-aarch64.jar"
    if [ -f "$MODULE_JAR" ]; then
        MODULE_PATH="$MODULE_PATH:$MODULE_JAR"
    fi
done

jlink \
  --module-path "$MODULE_PATH" \
  --add-modules "$MODULES" \
  --output target/runtime \
  --strip-debug \
  --compress 2 \
  --no-header-files \
  --no-man-pages

# Create launcher script
echo "Creating launcher script..."
mkdir -p target/input
cp "$SHADED_JAR" target/input/

cat > target/input/launcher.sh << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
APP_JAR="$DIR/SatelliteGCS-1.0-SNAPSHOT.jar"

# Set library paths
export DYLD_LIBRARY_PATH="$DIR/../MacOS/lib:/opt/homebrew/opt/opencv/lib:$DYLD_LIBRARY_PATH"
export JAVA_LIBRARY_PATH="$DIR/../MacOS/lib:/opt/homebrew/opt/opencv/lib"

# Run the application
exec "$DIR/../runtime/bin/java" \
  -Xmx2G \
  -Djava.library.path="$JAVA_LIBRARY_PATH" \
  -Dprism.order=sw \
  -Djavafx.preloader=false \
  -Djavafx.animation.framerate=60 \
  --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
  --add-opens=javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
  --enable-native-access=ALL-UNNAMED \
  -jar "$APP_JAR"
EOF

chmod +x target/input/launcher.sh

# Create app bundle using jpackage
echo "Creating app bundle..."
jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --vendor "NazarX Team" \
  --description "Ground Control Station for satellite telemetry and video" \
  --runtime-image target/runtime \
  --input target/input \
  --main-jar "$(basename "$SHADED_JAR")" \
  --main-class "${MAIN_CLASS}" \
  --dest target/dist \
  --mac-package-identifier "${IDENTIFIER}" \
  --mac-package-name "${APP_NAME}" \
  --java-options "-Xmx2G" \
  --java-options "-Djava.library.path=\$APPDIR/../MacOS/lib:/opt/homebrew/opt/opencv/lib:$HOME/.javacpp/cache" \
  --java-options "-Dprism.order=sw" \
  --java-options "-Djavafx.preloader=false" \
  --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED" \
  --java-options "--add-opens=javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.nio=ALL-UNNAMED" \
  --java-options "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED" \
  --java-options "--add-exports=javafx.graphics/com.sun.glass.utils=ALL-UNNAMED" \
  --java-options "--add-exports=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED" \
  --java-options "--enable-native-access=ALL-UNNAMED"

# Copy native libraries
echo "Setting up native libraries..."
APP_CONTENTS="target/dist/${APP_NAME}.app/Contents"
mkdir -p "$APP_CONTENTS/MacOS/lib"

# Copy OpenCV libraries
if [ -d "/opt/homebrew/opt/opencv/lib" ]; then
    echo "Copying OpenCV libraries..."
    cp -L /opt/homebrew/opt/opencv/lib/libopencv*.dylib "$APP_CONTENTS/MacOS/lib/" 2>/dev/null || true
fi

# Copy JavaCPP cache libraries
JAVACPP_CACHE="$HOME/.javacpp/cache"
if [ -d "$JAVACPP_CACHE" ]; then
    echo "Copying JavaCV native libraries..."
    find "$JAVACPP_CACHE" -name "*.dylib" -type f | while read lib; do
        cp "$lib" "$APP_CONTENTS/MacOS/lib/" 2>/dev/null || true
    done
fi

# Update Info.plist
echo "Updating Info.plist..."
/usr/libexec/PlistBuddy -c "Add :LSMinimumSystemVersion string 11.0" "$APP_CONTENTS/Info.plist" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :NSCameraUsageDescription string 'NazarX GCS needs camera access for video feed'" "$APP_CONTENTS/Info.plist" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :NSHighResolutionCapable bool true" "$APP_CONTENTS/Info.plist" 2>/dev/null || true

# Fix permissions
chmod -R 755 "$APP_CONTENTS/MacOS"

# Create DMG with Applications folder
echo "Creating DMG installer..."
DMG_NAME="NazarX-GCS-${APP_VERSION}-macOS-arm64.dmg"
DMG_TEMP="target/dmg-temp"

# Clean and create temp directory
rm -rf "$DMG_TEMP"
mkdir -p "$DMG_TEMP"

# Copy app to temp directory
cp -R "target/dist/${APP_NAME}.app" "$DMG_TEMP/"

# Create Applications symlink
ln -s /Applications "$DMG_TEMP/Applications"

# Create README
cat > "$DMG_TEMP/README.txt" << EOF
NazarX Ground Control Station
Version ${APP_VERSION}

Installation:
1. Drag "NazarX GCS" to the Applications folder
2. Eject this disk image
3. Launch from Applications
4. If macOS blocks the app, right-click and select "Open"

Requirements:
- macOS 11.0 or later
- Apple Silicon (M1/M2) or Intel Mac

First Launch:
- Grant camera access when prompted
- Allow incoming network connections if asked

Troubleshooting:
- If the app won't open, check System Preferences > Security & Privacy
- For issues, check Console.app for error messages
EOF

# Create DMG
rm -f "$DMG_NAME"
hdiutil create -volname "${APP_NAME}" \
  -srcfolder "$DMG_TEMP" \
  -ov -format UDZO \
  "$DMG_NAME"

# Clean up
rm -rf "$DMG_TEMP"

# Create install helper script
cat > "install-helper.sh" << 'EOF'
#!/bin/bash
# Helper script to properly install NazarX GCS

APP_NAME="NazarX GCS"
DMG_FILE="NazarX-GCS-1.0.0-macOS-arm64.dmg"

if [ ! -f "$DMG_FILE" ]; then
    echo "Error: DMG file not found!"
    exit 1
fi

echo "Mounting DMG..."
hdiutil attach "$DMG_FILE" -quiet

echo "Installing ${APP_NAME}..."
cp -R "/Volumes/${APP_NAME}/${APP_NAME}.app" /Applications/

echo "Unmounting DMG..."
hdiutil detach "/Volumes/${APP_NAME}" -quiet

echo "Setting permissions..."
xattr -cr "/Applications/${APP_NAME}.app"
chmod -R 755 "/Applications/${APP_NAME}.app"

echo "Installation complete!"
echo "You can now launch ${APP_NAME} from Applications"
echo "First time: Right-click and select 'Open'"
EOF

chmod +x install-helper.sh

echo "==========================================="
echo "✅ Build complete!"
echo ""
echo "Created: $DMG_NAME"
echo "Size: $(du -h "$DMG_NAME" | cut -f1)"
echo ""
echo "Installation options:"
echo "1. Double-click the DMG and drag to Applications"
echo "2. Or run: ./install-helper.sh"
echo ""
echo "To test the app directly:"
echo "open 'target/dist/${APP_NAME}.app'"
echo "==========================================="