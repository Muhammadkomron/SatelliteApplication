#!/bin/bash

echo "=== Building with jpackage and JavaFX ==="

# Build the app and dependencies
mvn clean package -DskipTests
mvn dependency:copy-dependencies -DoutputDirectory=target/app/libs

# Setup folders
mkdir -p target/app/libs
mkdir -p target/installer

# Copy compiled classes
cp -r target/classes/* target/app/

# Create the main JAR from classes
cd target/app
jar cf satellite-gcs.jar com
cd ../../

# Locate JavaFX lib
JAVAFX_PATH="/opt/homebrew/opt/openjfx/libexec/lib"
if [ ! -d "$JAVAFX_PATH" ]; then
  JAVAFX_PATH="/usr/local/opt/openjfx/libexec/lib"
fi
echo "Using JavaFX from: $JAVAFX_PATH"

# Package the app using jpackage without jlink
jpackage \
  --type dmg \
  --name "NazarX GCS" \
  --icon src/main/resources/images/app.icns \
  --app-version "1.0" \
  --vendor "NazarX" \
  --description "NazarX Ground Control Station" \
  --input target/app \
  --dest target/installer \
  --main-jar satellite-gcs.jar \
  --main-class com.example.satelliteapplication.Main \
  --mac-package-identifier "com.example.satelliteapplication" \
  --java-options "--module-path $JAVAFX_PATH --add-modules javafx.controls -Dprism.order=sw"

echo "✅ Done! Check target/installer/"
