#!/bin/bash

# Fixed build script that creates a small DMG with proper camera permissions and icons

set -e

APP_NAME="NazarX GCS"
APP_VERSION="1.0.0"
MAIN_CLASS="com.example.satelliteapplication.Main"

echo "=== Building ${APP_NAME} (Optimized) ==="

# First, let's build a clean JAR without the shade plugin issues
echo "Building clean JAR..."
mvn clean compile

# Create a lib directory for dependencies
mkdir -p target/lib

# Copy only necessary dependencies
echo "Copying dependencies..."
mvn dependency:copy-dependencies -DoutputDirectory=target/lib \
    -DexcludeArtifactIds=javafx-base,javafx-controls,javafx-fxml,javafx-graphics,javafx-web,javafx-swing,javafx-media \
    -DexcludeGroupIds=org.openjfx

# Remove non-macOS native libraries from the lib directory
echo "Removing non-macOS libraries..."
find target/lib -name "*.jar" -exec jar tf {} \; | grep -E "(\.dll|\.so|windows|linux|android)" | sort -u > /tmp/exclude-files.txt
# shellcheck disable=SC2162
find target/lib -name "*.jar" | while read jar; do
    echo "Cleaning $jar..."
    # Create temp directory
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    jar xf "$OLDPWD/$jar"

    # Remove non-macOS files
    find . -name "*.dll" -delete 2>/dev/null || true
    find . -name "*.so" -delete 2>/dev/null || true
    find . -type d -name "*windows*" -exec rm -rf {} + 2>/dev/null || true
    find . -type d -name "*linux*" -exec rm -rf {} + 2>/dev/null || true
    find . -type d -name "*android*" -exec rm -rf {} + 2>/dev/null || true
    find . -type d -name "*x86_64*" -exec rm -rf {} + 2>/dev/null || true

    # Repack the JAR
    # shellcheck disable=SC2035
    jar cf "$OLDPWD/$jar.new" *
    cd - > /dev/null
    mv "$jar.new" "$jar"
    rm -rf "$TEMP_DIR"
done

# Build the application JAR (without dependencies)
echo "Building application JAR..."
mvn jar:jar

# Create a single JAR with cleaned dependencies
echo "Creating optimized JAR..."
mkdir -p target/uber-jar
cd target/uber-jar

# Extract all cleaned dependencies
for jar in ../lib/*.jar; do
    if [ -f "$jar" ]; then
        jar xf "$jar" 2>/dev/null || true
    fi
done

# Extract our application classes
jar xf ../SatelliteGCS-1.0-SNAPSHOT.jar

# Remove unnecessary files
find . -name "*.txt" -not -name "LICENSE*" -delete 2>/dev/null || true
find . -name "*.properties" -not -path "*/logging.properties" -not -path "*/version.properties" -delete 2>/dev/null || true
rm -rf META-INF/maven META-INF/*.SF META-INF/*.DSA META-INF/*.RSA

# Create the final JAR
# shellcheck disable=SC2035
jar cfm ../SatelliteGCS-optimized.jar META-INF/MANIFEST.MF *
cd ../..

FINAL_JAR="target/SatelliteGCS-optimized.jar"
echo "Optimized JAR size: $(du -h "$FINAL_JAR" | cut -f1)"

# Download JavaFX if needed
JAVAFX_VERSION="17.0.8"
JAVAFX_PATH="$HOME/.m2/repository/org/openjfx"

# Ensure we have JavaFX media module
if [ ! -f "$JAVAFX_PATH/javafx-media/$JAVAFX_VERSION/javafx-media-$JAVAFX_VERSION-mac-aarch64.jar" ]; then
    echo "Downloading JavaFX media module..."
    mvn dependency:get -Dartifact=org.openjfx:javafx-media:$JAVAFX_VERSION:jar:mac-aarch64
fi

# Create runtime with all JavaFX modules
echo "Creating runtime..."
MODULE_PATH="$JAVA_HOME/jmods"
for module in base controls fxml graphics web swing media; do
    MODULE_JAR="$JAVAFX_PATH/javafx-$module/$JAVAFX_VERSION/javafx-$module-$JAVAFX_VERSION-mac-aarch64.jar"
    if [ -f "$MODULE_JAR" ]; then
        MODULE_PATH="$MODULE_PATH:$MODULE_JAR"
    else
        echo "Warning: Missing JavaFX module $module"
    fi
done

# Create runtime
rm -rf target/runtime
jlink \
    --module-path "$MODULE_PATH" \
    --add-modules java.base,java.desktop,java.logging,java.xml,jdk.unsupported,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.web,javafx.swing,javafx.media,java.naming,java.sql,java.prefs \
    --output target/runtime \
    --strip-debug \
    --compress 2 \
    --no-header-files \
    --no-man-pages

echo "Runtime size: $(du -sh target/runtime | cut -f1)"

# Verify icon exists and create resources directory
echo "Setting up resources and icon..."
mkdir -p target/resources

# Check if icon exists and copy it
ICON_SOURCE="src/main/resources/images/app.icns"
if [ -f "$ICON_SOURCE" ]; then
    cp "$ICON_SOURCE" target/resources/app.icns
    echo "✅ Icon copied from $ICON_SOURCE"
else
    echo "⚠️  Warning: Icon not found at $ICON_SOURCE"
    echo "    Creating a default icon placeholder..."
    # Create a simple default icon if the original doesn't exist
    sips -s format icns -s formatOptions default /System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/GenericApplicationIcon.icns --out target/resources/app.icns 2>/dev/null || {
        echo "❌ Could not create default icon. App will use system default."
        touch target/resources/app.icns
    }
fi

# Create Info.plist with proper icon reference
echo "Creating Info.plist..."
cat > target/resources/Info.plist << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.nazarx.gcs</string>
    <key>CFBundleName</key>
    <string>NazarX GCS</string>
    <key>CFBundleVersion</key>
    <string>1.0.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleExecutable</key>
    <string>NazarX GCS</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleIconFile</key>
    <string>app</string>
    <key>CFBundleIconName</key>
    <string>app</string>
    <key>NSCameraUsageDescription</key>
    <string>NazarX GCS needs access to your camera to display video feed from satellites and ground equipment.</string>
    <key>NSMicrophoneUsageDescription</key>
    <string>NazarX GCS needs access to your microphone for video recording with audio.</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
</dict>
</plist>
EOF

# Create app bundle
echo "Creating app bundle..."
mkdir -p target/input
cp "$FINAL_JAR" target/input/

jpackage \
    --type app-image \
    --name "${APP_NAME}" \
    --app-version "${APP_VERSION}" \
    --vendor "NazarX Team" \
    --runtime-image target/runtime \
    --input target/input \
    --main-jar "$(basename "$FINAL_JAR")" \
    --main-class "${MAIN_CLASS}" \
    --dest target/dist \
    --resource-dir target/resources \
    --icon target/resources/app.icns \
    --java-options "-Xmx512m" \
    --java-options "-XX:+UseG1GC" \
    --java-options "-Djava.library.path=/opt/homebrew/opt/opencv/lib:$HOME/.javacpp/cache" \
    --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Dapple.awt.UIElement=false" \
    --java-options "-Dapple.laf.useScreenMenuBar=true"

# Verify the app was created and has the icon
if [ -d "target/dist/${APP_NAME}.app" ]; then
    echo "✅ App bundle created successfully"

    # Check if icon was properly embedded
    if [ -f "target/dist/${APP_NAME}.app/Contents/Resources/app.icns" ]; then
        echo "✅ Icon embedded in app bundle"
    else
        echo "⚠️  Warning: Icon not found in app bundle"
        # Try to copy the icon manually
        cp target/resources/app.icns "target/dist/${APP_NAME}.app/Contents/Resources/" 2>/dev/null || true
    fi
else
    echo "❌ Failed to create app bundle"
    exit 1
fi

# Copy only essential native libraries
APP_LIB="target/dist/${APP_NAME}.app/Contents/MacOS/lib"
mkdir -p "$APP_LIB"

# Copy only the OpenCV libraries we need
if [ -d "/opt/homebrew/opt/opencv/lib" ]; then
    for lib in core imgproc videoio imgcodecs highgui; do
        cp -L "/opt/homebrew/opt/opencv/lib/libopencv_${lib}.dylib" "$APP_LIB/" 2>/dev/null || true
    done
fi

# Copy only ARM64 JavaCPP libraries
if [ -d "$HOME/.javacpp/cache" ]; then
    find "$HOME/.javacpp/cache" -name "*macosx-arm64*.dylib" -exec cp {} "$APP_LIB/" \; 2>/dev/null || true
fi

# Fix library paths
echo "Fixing library paths..."
cd "$APP_LIB"
for dylib in *.dylib; do
    if [ -f "$dylib" ]; then
        # Update paths to be relative
        install_name_tool -id "@loader_path/$dylib" "$dylib" 2>/dev/null || true

        # Fix dependencies
        # shellcheck disable=SC2162
        otool -L "$dylib" | grep -E "(opencv|javacpp)" | awk '{print $1}' | while read dep; do
            depname=$(basename "$dep")
            if [ -f "$depname" ]; then
                install_name_tool -change "$dep" "@loader_path/$depname" "$dylib" 2>/dev/null || true
            fi
        done
    fi
done
cd - > /dev/null

echo "App bundle size: $(du -sh "target/dist/${APP_NAME}.app" | cut -f1)"

# Sign the app for camera permissions and to preserve icon
echo "Signing app..."
codesign --force --deep --sign - "target/dist/${APP_NAME}.app" 2>/dev/null || echo "Warning: Could not sign app. Camera permissions may not work."

# Force refresh of icon cache (helps with icon display issues)
echo "Refreshing icon cache..."
touch "target/dist/${APP_NAME}.app"
touch "target/dist/${APP_NAME}.app/Contents/Info.plist"

# Create DMG with proper icon handling
echo "Creating DMG..."
DMG_TEMP="target/dmg"
rm -rf "$DMG_TEMP"
mkdir -p "$DMG_TEMP"

# Copy app to DMG folder
cp -R "target/dist/${APP_NAME}.app" "$DMG_TEMP/"

# Create Applications symlink
ln -s /Applications "$DMG_TEMP/Applications"

# Create a DS_Store file for better DMG appearance (optional)
echo "Setting up DMG layout..."

# Set custom icon for the app in the DMG (this helps with icon display)
if [ -f target/resources/app.icns ]; then
    # Convert icns to a format we can use for the DMG
    sips -s format png target/resources/app.icns --out "$DMG_TEMP/.VolumeIcon.png" 2>/dev/null || true
fi

DMG_NAME="NazarX-GCS-${APP_VERSION}-macOS-arm64.dmg"
rm -f "$DMG_NAME"

# Create DMG with better compression and icon preservation
hdiutil create -volname "${APP_NAME}" \
    -srcfolder "$DMG_TEMP" \
    -ov -format UDZO \
    -imagekey zlib-level=9 \
    "$DMG_NAME"

# Set custom icon for the DMG file itself
if [ -f target/resources/app.icns ]; then
    echo "Setting DMG icon..."
    # Use sips or custom icon tools to set the DMG icon
    sips -i target/resources/app.icns 2>/dev/null || true
    DeRez -only icns target/resources/app.icns > /tmp/app.rsrc 2>/dev/null || true
    Rez -append /tmp/app.rsrc -o "$DMG_NAME" 2>/dev/null || true
    SetFile -a C "$DMG_NAME" 2>/dev/null || true
fi

rm -rf "$DMG_TEMP"

echo "==========================================="
echo "✅ Build complete!"
echo "DMG: $DMG_NAME ($(du -h "$DMG_NAME" | cut -f1))"
echo ""
echo "🎨 Icon Status:"
if [ -f "target/dist/${APP_NAME}.app/Contents/Resources/app.icns" ]; then
    echo "   ✅ App icon embedded"
else
    echo "   ❌ App icon missing"
fi
echo ""
echo "⚠️  Important: On first run, macOS will ask for camera permissions."
echo "   Grant the permission for video to work properly."
echo ""
echo "📝 If icons don't appear immediately:"
echo "   1. Try logging out and back in"
echo "   2. Or run: sudo find /private/var/folders -name com.apple.LaunchServices* -exec rm {} \\;"
echo "   3. Then run: /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -kill -r -domain local -domain system -domain user"
echo "==========================================="