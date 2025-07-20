#!/bin/bash

# convert-to-icns.sh - Convert PNG or SVG to ICNS with border radius

echo "=== Icon Converter (PNG/SVG to ICNS with Border Radius) ==="

# Check if input file is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <input-icon.png|svg> [border-radius-percentage]"
    echo "Example: $0 icon.png 20"
    echo "Example: $0 icon.svg 15"
    echo "Default border radius: 18% (macOS standard)"
    exit 1
fi

INPUT_FILE="$1"
BORDER_RADIUS="${2:-18}"  # Default to 18% for macOS style

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' not found!"
    exit 1
fi

# Get file extension
EXTENSION="${INPUT_FILE##*.}"
EXTENSION=$(echo "$EXTENSION" | tr '[:upper:]' '[:lower:]')

# Create temporary directory
TEMP_DIR=$(mktemp -d)
echo "Using temporary directory: $TEMP_DIR"

# Function to apply border radius using ImageMagick
apply_border_radius() {
    local input="$1"
    local output="$2"
    local size="$3"
    local radius_percent="$4"

    # Calculate actual radius in pixels
    local radius=$((size * radius_percent / 100))

    # Create rounded rectangle mask
    convert -size ${size}x${size} xc:none -draw "roundrectangle 0,0,$((size-1)),$((size-1)),$radius,$radius" "$TEMP_DIR/mask.png"

    # Apply mask to image
    convert "$input" -resize ${size}x${size}! "$TEMP_DIR/mask.png" -compose DstIn -composite "$output"
}

# Convert to PNG first if SVG
if [ "$EXTENSION" = "svg" ]; then
    echo "Converting SVG to PNG..."

    # Try different SVG converters
    if command -v rsvg-convert >/dev/null 2>&1; then
        rsvg-convert -w 1024 -h 1024 "$INPUT_FILE" -o "$TEMP_DIR/icon-1024.png"
    elif command -v inkscape >/dev/null 2>&1; then
        inkscape -w 1024 -h 1024 "$INPUT_FILE" -o "$TEMP_DIR/icon-1024.png"
    elif command -v convert >/dev/null 2>&1; then
        convert -background none -resize 1024x1024 "$INPUT_FILE" "$TEMP_DIR/icon-1024.png"
    else
        echo "Error: No SVG converter found. Install one of: librsvg, inkscape, or imagemagick"
        echo "  brew install librsvg"
        exit 1
    fi

    BASE_IMAGE="$TEMP_DIR/icon-1024.png"
else
    # Use PNG directly
    BASE_IMAGE="$INPUT_FILE"
fi

# Check if ImageMagick is installed
if ! command -v convert >/dev/null 2>&1; then
    echo "Error: ImageMagick not found. Install with: brew install imagemagick"
    exit 1
fi

# Create iconset directory
ICONSET_DIR="$TEMP_DIR/app.iconset"
mkdir -p "$ICONSET_DIR"

echo "Creating icon sizes with ${BORDER_RADIUS}% border radius..."

# Define all required sizes for macOS
declare -a SIZES=(16 32 64 128 256 512 1024)
declare -a RETINA_SIZES=(32 64 128 256 512 1024)

# Generate standard resolution icons
for size in "${SIZES[@]}"; do
    echo "  Creating ${size}x${size} icon..."
    apply_border_radius "$BASE_IMAGE" "$ICONSET_DIR/icon_${size}x${size}.png" "$size" "$BORDER_RADIUS"
done

# Generate retina resolution icons
for size in "${RETINA_SIZES[@]}"; do
    retina_size=$((size * 2))
    if [ $retina_size -le 1024 ]; then
        echo "  Creating ${size}x${size}@2x icon..."
        apply_border_radius "$BASE_IMAGE" "$ICONSET_DIR/icon_${size}x${size}@2x.png" "$retina_size" "$BORDER_RADIUS"
    fi
done

# Convert iconset to ICNS
echo "Converting to ICNS format..."
iconutil -c icns "$ICONSET_DIR" -o "app.icns"

# Copy to resources directory
if [ -d "src/main/resources" ]; then
    echo "Copying to src/main/resources/app.icns..."
    cp app.icns src/main/resources/app.icns
else
    echo "src/main/resources directory not found. ICNS file saved as app.icns"
fi

# Clean up
rm -rf "$TEMP_DIR"

echo ""
echo "=== Icon Conversion Complete ==="
echo "Output: app.icns (with ${BORDER_RADIUS}% border radius)"
if [ -d "src/main/resources" ]; then
    echo "Copied to: src/main/resources/app.icns"
fi

# Verify the output
if command -v iconutil >/dev/null 2>&1; then
    echo ""
    echo "ICNS contents:"
    iconutil -l app.icns 2>/dev/null || echo "Could not read ICNS contents"
fi

echo ""
echo "Next steps:"
echo "1. Run ./jpackage-with-javafx.sh to build the app"
echo "2. Run ./create-dmg.sh to create the installer"