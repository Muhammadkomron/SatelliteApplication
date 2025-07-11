#!/bin/bash

# convert-to-icns.sh - Convert PNG to ICNS for macOS app

if [ $# -eq 0 ]; then
    echo "Usage: $0 <path-to-png-file>"
    echo "Example: $0 myicon.png"
    exit 1
fi

PNG_FILE="$1"

if [ ! -f "$PNG_FILE" ]; then
    echo "Error: File '$PNG_FILE' not found!"
    exit 1
fi

echo "Converting $PNG_FILE to ICNS format..."

# Create resources directory
mkdir -p src/main/resources

# Create temporary iconset
ICONSET="temp.iconset"
mkdir -p "$ICONSET"

# Generate all required sizes
echo "Generating icon sizes..."
sips -z 16 16     "$PNG_FILE" --out "$ICONSET/icon_16x16.png" >/dev/null 2>&1
sips -z 32 32     "$PNG_FILE" --out "$ICONSET/icon_16x16@2x.png" >/dev/null 2>&1
sips -z 32 32     "$PNG_FILE" --out "$ICONSET/icon_32x32.png" >/dev/null 2>&1
sips -z 64 64     "$PNG_FILE" --out "$ICONSET/icon_32x32@2x.png" >/dev/null 2>&1
sips -z 128 128   "$PNG_FILE" --out "$ICONSET/icon_128x128.png" >/dev/null 2>&1
sips -z 256 256   "$PNG_FILE" --out "$ICONSET/icon_128x128@2x.png" >/dev/null 2>&1
sips -z 256 256   "$PNG_FILE" --out "$ICONSET/icon_256x256.png" >/dev/null 2>&1
sips -z 512 512   "$PNG_FILE" --out "$ICONSET/icon_256x256@2x.png" >/dev/null 2>&1
sips -z 512 512   "$PNG_FILE" --out "$ICONSET/icon_512x512.png" >/dev/null 2>&1
sips -z 1024 1024 "$PNG_FILE" --out "$ICONSET/icon_512x512@2x.png" >/dev/null 2>&1

# Convert to ICNS
echo "Creating ICNS file..."
iconutil -c icns "$ICONSET" -o src/main/resources/app.icns

# Clean up
rm -rf "$ICONSET"

echo "✓ Icon created at: src/main/resources/app.icns"
echo ""
echo "Now rebuild your installer with: ./jpackage-with-javafx.sh"
