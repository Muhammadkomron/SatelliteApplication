#!/bin/bash

# check-app-icon.sh - Verify app icon setup and troubleshoot issues

echo "=== Checking App Icon Setup ==="

# Check if source icon exists
if [ -f "src/main/resources/icon.svg" ]; then
    echo "✓ Source SVG icon found: src/main/resources/icon.svg"

    # Get SVG dimensions
    if command -v identify >/dev/null 2>&1; then
        SVG_INFO=$(identify src/main/resources/icon.svg 2>/dev/null)
        echo "  SVG info: $SVG_INFO"
    fi
else
    echo "✗ Source SVG icon NOT found: src/main/resources/icon.svg"
    echo "  You need to place your icon file there first"
fi

# Check if ICNS file exists
if [ -f "src/main/resources/app.icns" ]; then
    echo "✓ ICNS file found: src/main/resources/app.icns"

    # Check ICNS file size and creation date
    ICNS_SIZE=$(ls -lh src/main/resources/app.icns | awk '{print $5}')
    ICNS_DATE=$(ls -l src/main/resources/app.icns | awk '{print $6, $7, $8}')
    echo "  Size: $ICNS_SIZE, Modified: $ICNS_DATE"

    # Check ICNS contents if iconutil is available
    if command -v iconutil >/dev/null 2>&1; then
        echo "  ICNS contents:"
        iconutil -l src/main/resources/app.icns 2>/dev/null || echo "  Could not read ICNS contents"
    fi
else
    echo "✗ ICNS file NOT found: src/main/resources/app.icns"
    echo "  Run ./convert-to-icns.sh src/main/resources/icon.svg to create it"
fi

echo ""
echo "=== Checking Built App Icon ==="

# Check if app bundle exists
APP_BUNDLE="target/installer/NazarX GCS.app"
if [ -d "$APP_BUNDLE" ]; then
    echo "✓ App bundle found: $APP_BUNDLE"

    # Check app icon in bundle
    APP_ICON="$APP_BUNDLE/Contents/Resources/NazarX GCS.icns"
    if [ -f "$APP_ICON" ]; then
        echo "✓ App icon found in bundle: $APP_ICON"

        # Compare sizes
        if [ -f "src/main/resources/app.icns" ]; then
            SRC_SIZE=$(stat -f%z src/main/resources/app.icns 2>/dev/null || stat -c%s src/main/resources/app.icns 2>/dev/null)
            BUNDLE_SIZE=$(stat -f%z "$APP_ICON" 2>/dev/null || stat -c%s "$APP_ICON" 2>/dev/null)

            if [ "$SRC_SIZE" = "$BUNDLE_SIZE" ]; then
                echo "  ✓ Icon sizes match ($SRC_SIZE bytes)"
            else
                echo "  ⚠ Icon sizes differ (src: $SRC_SIZE, bundle: $BUNDLE_SIZE)"
            fi
        fi
    else
        echo "✗ App icon NOT found in bundle"
        echo "  Expected at: $APP_ICON"
        echo "  Available resources:"
        ls -la "$APP_BUNDLE/Contents/Resources/" 2>/dev/null || echo "  Resources directory not found"
    fi

    # Check Info.plist for icon reference
    INFO_PLIST="$APP_BUNDLE/Contents/Info.plist"
    if [ -f "$INFO_PLIST" ]; then
        echo "  Checking Info.plist for icon reference..."
        if grep -q "CFBundleIconFile" "$INFO_PLIST"; then
            ICON_NAME=$(plutil -extract CFBundleIconFile raw "$INFO_PLIST" 2>/dev/null || echo "Could not extract")
            echo "  CFBundleIconFile: $ICON_NAME"
        else
            echo "  ⚠ CFBundleIconFile not found in Info.plist"
        fi
    fi
else
    echo "✗ App bundle NOT found: $APP_BUNDLE"
    echo "  Run ./jpackage-with-javafx.sh to build the app first"
fi

echo ""
echo "=== Checking DMG Icon ==="

# Check for DMG files
DMG_FILES=$(find target/installer -name "*.dmg" 2>/dev/null)
if [ -n "$DMG_FILES" ]; then
    echo "✓ DMG file(s) found:"
    echo "$DMG_FILES" | while read -r dmg; do
        echo "  $dmg"
        DMG_SIZE=$(ls -lh "$dmg" | awk '{print $5}')
        echo "    Size: $DMG_SIZE"
    done
else
    echo "✗ No DMG files found"
    echo "  Run ./create-dmg.sh to create the DMG installer"
fi

echo ""
echo "=== Quick Test Commands ==="
echo "To test your icon workflow:"
echo "1. Convert icon:     ./convert-to-icns.sh src/main/resources/icon.svg"
echo "2. Build app:        ./jpackage-with-javafx.sh"
echo "3. Create DMG:       ./create-dmg.sh"

echo ""
echo "=== Troubleshooting Tips ==="
echo "• If icon doesn't appear, ensure icon.svg is square (same width/height)"
echo "• ICNS file should be 50KB-500KB for good quality"
echo "• jpackage sometimes caches icons - try 'rm -rf target/installer' and rebuild"
echo "• Test by opening the .app file in Finder to see if icon shows"
echo "• For DMG, the icon appears when mounting the disk image"

echo ""
echo "=== System Icon Tools Check ==="
echo -n "iconutil: "
if command -v iconutil >/dev/null 2>&1; then
    echo "✓ Available"
else
    echo "✗ Not found (should be built into macOS)"
fi

echo -n "sips: "
if command -v sips >/dev/null 2>&1; then
    echo "✓ Available"
else
    echo "✗ Not found (should be built into macOS)"
fi

echo -n "SVG converter: "
if command -v rsvg-convert >/dev/null 2>&1; then
    echo "✓ rsvg-convert available"
elif command -v inkscape >/dev/null 2>&1; then
    echo "✓ inkscape available"
elif command -v convert >/dev/null 2>&1; then
    echo "✓ imagemagick available"
else
    echo "✗ No SVG converter found"
    echo "  Install with: brew install librsvg"
fi