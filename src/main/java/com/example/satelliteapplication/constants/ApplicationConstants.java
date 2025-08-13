package com.example.satelliteapplication.constants;

import java.time.format.DateTimeFormatter;

/**
 * Centralized constants for the Satellite GCS application.
 * This eliminates magic numbers and makes configuration changes easier.
 */
public final class ApplicationConstants {
    
    // Prevent instantiation
    private ApplicationConstants() {}
    
    // Application Configuration
    public static final String APPLICATION_TITLE = "NazarX Ground Control Station";
    public static final String TEAM_ID = "569287";
    public static final String APPLICATION_ICON_PATH = "/images/light-icon.png";
    
    // UI Configuration
    public static final int MAX_SERIAL_MESSAGES = 100;
    public static final int ERROR_CODE_LENGTH = 4;
    public static final double INTERPOLATION_SPEED = 0.15;
    
    // Timeouts and Delays
    public static final long DATA_TIMEOUT_MS = 10000; // 10 seconds
    public static final long UI_UPDATE_INTERVAL_MS = 100; // 100ms
    
    // 3D Viewer Configuration
    public static final double SATELLITE_BODY_WIDTH = 60.0;
    public static final double SATELLITE_BODY_HEIGHT = 20.0;
    public static final double SATELLITE_BODY_DEPTH = 40.0;
    public static final double CAMERA_DISTANCE = -200.0;
    
    // Date/Time Formats
    public static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Error Code Indices
    public static final int GROUND_STATION_COMMUNICATION = 0;
    public static final int PAYLOAD_POSITION_DATA = 1;
    public static final int PAYLOAD_PRESSURE_DATA = 2;
    public static final int RESERVED_ERROR_CODE = 3;
    
    // Color Constants
    public static final String PRIMARY_COLOR = "#3498db";
    public static final String SECONDARY_COLOR = "#2c3e50";
    public static final String ACCENT_COLOR = "#e74c3c";
    public static final String NEUTRAL_COLOR = "#7f8c8d";
}
