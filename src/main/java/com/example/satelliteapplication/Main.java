package com.example.satelliteapplication;

import javafx.application.Application;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

public class Main {
    static {
        // Initialize JavaCV/OpenCV
        try {
            // This will load the appropriate native libraries
            Loader.load(opencv_java.class);
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV through JavaCV: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Set OpenCV library path for macOS (Homebrew installation)
        String opencvPath = "/opt/homebrew/opt/opencv/lib";
        String currentLibPath = System.getProperty("java.library.path");
        if (currentLibPath != null && !currentLibPath.contains(opencvPath)) {
            System.setProperty("java.library.path", currentLibPath + ":" + opencvPath);
        } else if (currentLibPath == null) {
            System.setProperty("java.library.path", opencvPath);
        }

        // Set native library path for JavaCV
        System.setProperty("org.bytedeco.javacpp.platform.preloadpath", opencvPath);

        // Enable native access for JavaCV
        System.setProperty("java.awt.headless", "false");

        // Launch JavaFX application
        Application.launch(SatelliteGCS.class, args);
    }
}