#!/bin/bash

# create-dmg.sh - Create DMG installer for MAVLink GCS

echo "=== Creating DMG with create-dmg tool ==="

# Install create-dmg if not present
if ! command -v create-dmg &> /dev/null; then
    echo "Installing create-dmg..."
    brew install create-dmg
fi

# Build app first if needed
if [ ! -d "target/installer/MAVLink GCS.app" ]; then
    echo "Building application first..."
    ./jpackage-with-javafx.sh

    # Extract app from DMG if needed
    if [ -f "target/installer/*.dmg" ]; then
        hdiutil attach target/installer/*.dmg -nobrowse -quiet
        cp -R "/Volumes/MAVLink GCS/MAVLink GCS.app" "target/installer/"
        hdiutil detach "/Volumes/MAVLink GCS" -quiet
        rm target/installer/*.dmg
    fi
fi

# Create DMG with create-dmg
echo "Creating custom DMG..."

# Set variables
APP_NAME="MAVLink GCS"
VERSION="1.0"
DMG_NAME="MAVLink-GCS-${VERSION}"

# Remove old DMG if exists
rm -f "target/installer/${DMG_NAME}.dmg"

# Clean up any old temporary files
rm -f target/installer/rw.*.dmg
rm -f target/installer/*.dmg.bak

# Create DMG with all the nice features
create-dmg \
  --volname "${APP_NAME} ${VERSION}" \
  --window-pos 200 120 \
  --window-size 600 400 \
  --icon-size 100 \
  --icon "${APP_NAME}.app" 150 200 \
  --hide-extension "${APP_NAME}.app" \
  --app-drop-link 450 200 \
  --eula "src/main/resources/license.txt" \
  --text-size 14 \
  --volicon "src/main/resources/app.icns" \
  --background "src/main/resources/dmg-background.png" \
  --no-internet-enable \
  "target/installer/${DMG_NAME}.dmg" \
  "target/installer/${APP_NAME}.app"

# If no background exists, create without it
if [ ! -f "src/main/resources/dmg-background.png" ]; then
    echo "No background image found. Creating DMG without background..."

    create-dmg \
      --volname "${APP_NAME} ${VERSION}" \
      --window-pos 200 120 \
      --window-size 600 400 \
      --icon-size 100 \
      --icon "${APP_NAME}.app" 150 200 \
      --hide-extension "${APP_NAME}.app" \
      --app-drop-link 450 200 \
      --text-size 14 \
      --volicon "src/main/resources/app.icns" \
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

echo ""
echo "To add a custom background:"
echo "1. Create a 600x400 PNG image"
echo "2. Save as: src/main/resources/dmg-background.png"
echo "3. Run this script again"