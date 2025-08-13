package com.example.satelliteapplication;

import javafx.application.Application;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

import com.example.satelliteapplication.constants.ApplicationConstants;

public class Main {
    static {
        // Initialize JavaCV/OpenCV with better error handling
        try {
            // This will load the appropriate native libraries from embedded resources
            Loader.load(opencv_java.class);
            System.out.println("OpenCV loaded successfully via JavaCV");
        } catch (Exception e) {
            System.err.println("Warning: Failed to load OpenCV through JavaCV: " + e.getMessage());
            System.err.println("Video capture may not be available");
            // Don't exit - let the app run without video capability
        }
    }

    public static void main(String[] args) {
        // Remove hardcoded paths - JavaCV will find libraries automatically
        // The Loader.load() call above handles library loading

        // Enable native access for JavaCV
        System.setProperty("java.awt.headless", "false");

        // Set property to indicate we're running from packaged app
        String appDir = System.getProperty("java.home");
        if (appDir != null && appDir.contains(".app")) {
            System.setProperty("app.packaged", "true");
        }

        // Launch JavaFX application
        Application.launch(SatelliteGCS.class, args);
    }
}