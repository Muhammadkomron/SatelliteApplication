#!/bin/bash

# create-dmg.sh - Create DMG installer for NazarX GCS

echo "=== Creating DMG with create-dmg tool ==="

# Install create-dmg if not present
if ! command -v create-dmg &> /dev/null; then
    echo "Installing create-dmg..."
    brew install create-dmg
fi

# Build app first if needed
if [ ! -d "target/installer/NazarX GCS.app" ]; then
    echo "Building application first..."
    ./jpackage-with-javafx.sh

    # Extract app from DMG if needed (jpackage creates a DMG first)
    # shellcheck disable=SC2144
    if [ -f target/installer/*.dmg ]; then
        echo "Extracting app from jpackage DMG..."
        # Find the DMG file
        DMG_FILE=$(find target/installer -name "*.dmg" | head -1)
        if [ -n "$DMG_FILE" ]; then
            hdiutil attach "$DMG_FILE" -nobrowse -quiet
            # The volume name should match the app name
            cp -R "/Volumes/NazarX GCS/NazarX GCS.app" "target/installer/"
            hdiutil detach "/Volumes/NazarX GCS" -quiet
            rm "$DMG_FILE"
        fi
    fi
fi

# Check if app exists
if [ ! -d "target/installer/NazarX GCS.app" ]; then
    echo "Error: NazarX GCS.app not found after build!"
    echo "Available files in target/installer:"
    ls -la target/installer/
    exit 1
fi

# Create DMG with create-dmg
echo "Creating custom DMG..."

# Set variables
APP_NAME="NazarX GCS"
VERSION="1.0"
DMG_NAME="NazarX-GCS-${VERSION}"

# Remove old DMG if exists
rm -f "target/installer/${DMG_NAME}.dmg"

# Clean up any old temporary files
rm -f target/installer/rw.*.dmg
rm -f target/installer/*.dmg.bak


# Create DMG with all the nice features if background exists
if [ -f "src/main/resources/images/dmg-background.png" ]; then
    echo "Creating DMG with custom background..."
    create-dmg \
      --volname "${APP_NAME} ${VERSION}" \
      --window-pos 200 120 \
      --window-size 600 400 \
      --icon-size 100 \
      --icon "${APP_NAME}.app" 150 200 \
      --hide-extension "${APP_NAME}.app" \
      --app-drop-link 450 200 \
      --text-size 15 \
      --background "src/main/resources/images/dmg-background.png" \
      --no-internet-enable \
      "target/installer/${DMG_NAME}.dmg" \
      "target/installer/${APP_NAME}.app"
else
    echo "Creating DMG without background..."
    create-dmg \
      --volname "${APP_NAME} ${VERSION}" \
      --window-pos 200 120 \
      --window-size 600 400 \
      --icon-size 100 \
      --icon "${APP_NAME}.app" 150 150 \
      --hide-extension "${APP_NAME}.app" \
      --app-drop-link 450 150 \
      --text-size 16 \
      --no-internet-enable \
      "target/installer/${DMG_NAME}.dmg" \
      "target/installer/${APP_NAME}.app"
fi

echo ""
echo "=== DMG Created Successfully ==="
echo "Location: target/installer/${DMG_NAME}.dmg"

# Clean up any temporary DMG files
echo "Cleaning up temporary files..."
rm -f target/installer/rw.*.dmg
rm -f target/installer/*.dmg.bak