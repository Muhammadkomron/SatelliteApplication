#!/bin/bash

echo "=== Building with jpackage and JavaFX ==="

# Clean and compile (not package, to avoid shaded JAR issues)
mvn clean compile

# Create directories
mkdir -p target/app
mkdir -p target/installer

# Copy dependencies
mvn dependency:copy-dependencies -DoutputDirectory=target/app/libs

# Copy classes
cp -r target/classes/* target/app/

# Find JavaFX
JAVAFX_PATH="/opt/homebrew/opt/openjfx/libexec/lib"
if [ ! -d "$JAVAFX_PATH" ]; then
    JAVAFX_PATH="/usr/local/opt/openjfx/libexec/lib"
fi

echo "Using JavaFX from: $JAVAFX_PATH"

# Create a simple JAR (not shaded)
# shellcheck disable=SC2164
cd target/classes
jar cf ../app/satellite-gcs.jar com
cd ../..

# Use jpackage with proper module configuration
# NOTE: Removed javafx.fxml since it's not used in the application
jpackage \
  --type dmg \
  --name "NazarX GCS" \
  --icon src/main/resources/app.icns \
  --app-version "1.0" \
  --vendor "NazarX" \
  --description "NazarX Ground Control Station" \
  --input target/app \
  --dest target/installer \
  --main-jar satellite-gcs.jar \
  --main-class com.example.satelliteapplication.SatelliteApplication \
  --module-path "$JAVAFX_PATH:target/app/libs" \
  --add-modules javafx.controls \
  --mac-package-identifier "com.example.satelliteapplication" \
  --java-options "-Dprism.order=sw"

echo "Done! Check target/installer/"