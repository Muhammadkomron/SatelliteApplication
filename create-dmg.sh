#!/bin/bash

# Enhanced DMG creator with proper signing for distribution
# Fixes "does not have permission to open (null)" error

set -e

APP_NAME="NazarX GCS"
APP_VERSION="1.0.0"
MAIN_CLASS="com.example.satelliteapplication.Main"
DMG_NAME="NazarX-GCS-${APP_VERSION}-macOS-Silicon.dmg"

echo "=== Building Properly Signed ${APP_NAME} DMG ==="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if create-dmg is installed
if ! command -v create-dmg &> /dev/null; then
    echo "Installing create-dmg..."
    brew install create-dmg || {
        echo -e "${RED}Error: Failed to install create-dmg. Please install Homebrew first.${NC}"
        exit 1
    }
fi

# Function to check for Developer ID
check_developer_id() {
    echo "Checking for Developer ID certificates..."

    # Check for Developer ID Application certificate
    if security find-identity -v -p codesigning | grep -q "Developer ID Application"; then
        DEVELOPER_ID=$(security find-identity -v -p codesigning | grep "Developer ID Application" | head -1 | awk '{print $2}')
        echo -e "${GREEN}✓ Found Developer ID: $DEVELOPER_ID${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ No Developer ID found. App will be ad-hoc signed.${NC}"
        return 1
    fi
}

# First, build the app if not already built
if [ ! -d "target/dist/${APP_NAME}.app" ]; then
    echo "App bundle not found. Building application first..."
    ./build-small-dmg.sh || {
        echo -e "${RED}Error: Failed to build application${NC}"
        exit 1
    }
fi

# Clean up any existing DMG
rm -f "$DMG_NAME"
rm -f "target/*.dmg"

# Create a temporary directory for DMG contents
DMG_TEMP="target/dmg-contents"
rm -rf "$DMG_TEMP"
mkdir -p "$DMG_TEMP"

# Copy the app bundle
echo "Copying app bundle..."
cp -R "target/dist/${APP_NAME}.app" "$DMG_TEMP/"

APP_BUNDLE="$DMG_TEMP/${APP_NAME}.app"

# Fix permissions
echo "Fixing permissions..."
chmod -R 755 "$APP_BUNDLE"
find "$APP_BUNDLE" -name "*.dylib" -exec chmod 755 {} \;
find "$APP_BUNDLE" -name "*.jar" -exec chmod 644 {} \;

# Update Info.plist with proper entitlements and permissions
echo "Updating Info.plist..."
PLIST="$APP_BUNDLE/Contents/Info.plist"

cat > "$PLIST" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.nazarx.gcs</string>
    <key>CFBundleName</key>
    <string>NazarX GCS</string>
    <key>CFBundleDisplayName</key>
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
    <key>LSMinimumSystemVersion</key>
    <string>11.0</string>
    <key>LSArchitecturePriority</key>
    <array>
        <string>arm64</string>
    </array>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
    <key>NSCameraUsageDescription</key>
    <string>NazarX GCS needs access to your camera to display video feed from satellites and ground equipment.</string>
    <key>NSMicrophoneUsageDescription</key>
    <string>NazarX GCS needs access to your microphone for video recording with audio.</string>
    <key>NSRequiresAquaSystemAppearance</key>
    <false/>
    <key>CFBundleDocumentTypes</key>
    <array/>
    <key>CFBundleURLTypes</key>
    <array/>
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <true/>
    </dict>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.utilities</string>
</dict>
</plist>
EOF

# Create entitlements file for proper signing
ENTITLEMENTS="target/entitlements.plist"
cat > "$ENTITLEMENTS" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.device.camera</key>
    <true/>
    <key>com.apple.security.device.microphone</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
    <key>com.apple.security.network.server</key>
    <true/>
    <key>com.apple.security.files.user-selected.read-write</key>
    <true/>
</dict>
</plist>
EOF

# Fix library paths to be relative
echo "Making libraries portable..."
APP_LIB="$APP_BUNDLE/Contents/MacOS/lib"
if [ -d "$APP_LIB" ]; then
    for dylib in "$APP_LIB"/*.dylib; do
        if [ -f "$dylib" ]; then
            # Update to @loader_path
            install_name_tool -id "@loader_path/$(basename "$dylib")" "$dylib" 2>/dev/null || true

            # Fix dependencies
            otool -L "$dylib" 2>/dev/null | grep -E "(homebrew|Users|opt)" | awk '{print $1}' | while read -r dep; do
                dep_name=$(basename "$dep")
                if [ -f "$APP_LIB/$dep_name" ]; then
                    install_name_tool -change "$dep" "@loader_path/$dep_name" "$dylib" 2>/dev/null || true
                fi
            done
        fi
    done
fi

# Remove quarantine attributes
echo "Removing quarantine attributes..."
xattr -cr "$APP_BUNDLE" 2>/dev/null || true

# Sign the application
echo "Signing application..."

# Check if we have a Developer ID
if check_developer_id; then
    # Sign with Developer ID
    echo "Signing with Developer ID..."

    # Sign all frameworks and libraries first
    find "$APP_BUNDLE" -name "*.dylib" -o -name "*.framework" | while read -r item; do
        codesign --force --sign "$DEVELOPER_ID" \
                 --options runtime \
                 --entitlements "$ENTITLEMENTS" \
                 --timestamp \
                 "$item" 2>/dev/null || true
    done

    # Sign the main app bundle
    codesign --force --deep --sign "$DEVELOPER_ID" \
             --options runtime \
             --entitlements "$ENTITLEMENTS" \
             --timestamp \
             "$APP_BUNDLE"

    echo -e "${GREEN}✓ App signed with Developer ID${NC}"

else
    # Ad-hoc signing (no Developer ID)
    echo "Ad-hoc signing (for local use)..."

    # Sign all frameworks and libraries first
    find "$APP_BUNDLE" -name "*.dylib" -o -name "*.framework" | while read -r item; do
        codesign --force --sign - \
                 --entitlements "$ENTITLEMENTS" \
                 "$item" 2>/dev/null || true
    done

    # Sign the main app bundle
    codesign --force --deep --sign - \
             --entitlements "$ENTITLEMENTS" \
             "$APP_BUNDLE"

    echo -e "${YELLOW}⚠ App is ad-hoc signed (users will need to approve in Security settings)${NC}"
fi

# Verify the signature
echo "Verifying signature..."
if codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE" 2>&1; then
    echo -e "${GREEN}✓ Signature verification passed${NC}"
else
    echo -e "${YELLOW}⚠ Signature verification warnings (app should still work)${NC}"
fi

# Check spctl (Gatekeeper)
echo "Checking Gatekeeper assessment..."
if spctl --assess --type execute "$APP_BUNDLE" 2>/dev/null; then
    echo -e "${GREEN}✓ Gatekeeper check passed${NC}"
else
    echo -e "${YELLOW}⚠ Gatekeeper check failed (users will need to approve in Security settings)${NC}"
fi

# Create DMG using create-dmg
echo "Creating DMG..."

create-dmg \
    --volname "${APP_NAME}" \
    --window-size 800 400 \
    --icon-size 100 \
    --icon "${APP_NAME}.app" 200 190 \
    --app-drop-link 600 185 \
    --no-internet-enable \
    "$DMG_NAME" \
    "$DMG_TEMP" 2>/dev/null || {
    echo -e "${RED}Error: DMG creation failed${NC}"
    exit 1
}

# Sign the DMG if we have Developer ID
if [ ! -z "$DEVELOPER_ID" ] 2>/dev/null; then
    echo "Signing DMG..."
    codesign --sign "$DEVELOPER_ID" "$DMG_NAME"
fi

# Clean up
rm -rf "$DMG_TEMP"
rm -f "$ENTITLEMENTS"

# Final instructions
if [ -f "$DMG_NAME" ]; then
    DMG_SIZE=$(du -h "$DMG_NAME" | cut -f1)
    echo ""
    echo "==========================================="
    echo -e "${GREEN}✅ DMG created successfully!${NC}"
    echo ""
    echo "📦 File: $DMG_NAME"
    echo "📏 Size: $DMG_SIZE"
    echo ""

    if [ ! -z "$DEVELOPER_ID" ] 2>/dev/null; then
        echo -e "${GREEN}✓ Signed with Developer ID${NC}"
        echo "  Users can open the app normally"
    else
        echo -e "${YELLOW}⚠ Ad-hoc signed (no Developer ID)${NC}"
        echo ""
        echo "📝 Installation Instructions for Users:"
        echo ""
        echo "  METHOD 1 - First Time Opening (Recommended):"
        echo "  1. Copy NazarX GCS to Applications folder"
        echo "  2. Right-click the app and select 'Open'"
        echo "  3. Click 'Open' in the security dialog"
        echo "  4. The app will remember this choice"
        echo ""
        echo "  METHOD 2 - Via Security Settings:"
        echo "  1. Try to open the app normally"
        echo "  2. When blocked, go to System Settings > Privacy & Security"
        echo "  3. Find 'NazarX GCS was blocked' message"
        echo "  4. Click 'Open Anyway'"
        echo ""
        echo "  METHOD 3 - Remove Quarantine (Terminal):"
        echo "  Run this command after installing:"
        echo "  xattr -cr '/Applications/NazarX GCS.app'"
    fi

    echo ""
    echo "⚠️  Important Notes:"
    echo "  • Works on macOS 11.0 (Big Sur) and later"
    echo "  • Optimized for Apple Silicon (M1/M2/M3)"
    echo "  • Camera permission will be requested on first use"
    echo "==========================================="
else
    echo -e "${RED}❌ Error: DMG creation failed${NC}"
    exit 1
fi

# Create a README for distribution
cat > "README-Installation.txt" << 'README_EOF'
NazarX GCS - Installation Instructions
======================================

For macOS Silicon (M1/M2/M3) Users

INSTALLATION:
------------
1. Double-click the DMG file to mount it
2. Drag "NazarX GCS" to your Applications folder
3. Eject the DMG (drag to trash or right-click and select Eject)

FIRST TIME OPENING:
------------------
Since this app is not from the Mac App Store, macOS will require your approval:

Option 1 (Easiest):
- Right-click on "NazarX GCS" in Applications
- Select "Open" from the menu
- Click "Open" in the dialog that appears
- The app will launch and be trusted for future use

Option 2 (If double-clicking doesn't work):
- Try to open the app normally
- Go to System Settings > Privacy & Security
- Look for "NazarX GCS was blocked"
- Click "Open Anyway"

Option 3 (Terminal - Advanced Users):
- Open Terminal
- Run: xattr -cr '/Applications/NazarX GCS.app'
- The app can now be opened normally

PERMISSIONS:
-----------
On first run, the app will request:
- Camera access (for video feeds)
- Microphone access (if needed)

Grant these permissions for full functionality.

TROUBLESHOOTING:
---------------
If you see "damaged and can't be opened":
1. Make sure you copied the app to Applications first
2. Use the right-click > Open method
3. Or remove quarantine via Terminal (Option 3 above)

SYSTEM REQUIREMENTS:
-------------------
- macOS 11.0 (Big Sur) or later
- Apple Silicon Mac (M1/M2/M3)

SUPPORT:
--------
For issues or questions, contact the NazarX team.
README_EOF

echo ""
echo "📄 Created README-Installation.txt for distribution"
echo ""
echo "🚀 Your DMG is ready for distribution!"