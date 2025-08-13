package com.example.satelliteapplication.manager;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import com.example.satelliteapplication.constants.ApplicationConstants;
import com.example.satelliteapplication.manager.TelemetryManager.TelemetryData;

/**
 * Manages the display and updating of telemetry data in the UI.
 * Separates telemetry display logic from business logic.
 */
public class TelemetryDisplayManager {
    
    private final TelemetryDisplayComponents displayComponents;
    
    public TelemetryDisplayManager(TelemetryDisplayComponents displayComponents) {
        this.displayComponents = displayComponents;
    }
    
    /**
     * Update all telemetry displays with new data
     */
    public void updateTelemetryDisplay(TelemetryData data) {
        if (data == null) {
            clearTelemetryDisplay();
            return;
        }
        
        Platform.runLater(() -> {
            updateBasicTelemetry(data);
            updateAttitudeData(data);
            updateEnvironmentalData(data);
            updateMissionData(data);
            updatePacketInfo(data);
            updateErrorCodes(data);
        });
    }
    
    /**
     * Update basic telemetry information
     */
    private void updateBasicTelemetry(TelemetryData data) {
        if (displayComponents.batteryVoltageLabel != null) {
            displayComponents.batteryVoltageLabel.setText(
                String.format("Battery: %.2fV", data.batteryVoltage)
            );
        }
        
        if (displayComponents.batteryStatusLabel != null) {
            String batteryStatus = data.batteryPercentage >= 0 ? 
                String.format("(%d%%)", data.batteryPercentage) : "Unknown";
            displayComponents.batteryStatusLabel.setText(batteryStatus);
        }
        
        if (displayComponents.gpsLatitudeLabel != null) {
            displayComponents.gpsLatitudeLabel.setText(
                String.format("Lat: %.6f°", data.latitude)
            );
        }
        
        if (displayComponents.gpsLongitudeLabel != null) {
            displayComponents.gpsLongitudeLabel.setText(
                String.format("Lon: %.6f°", data.longitude)
            );
        }
        
        if (displayComponents.altitudeLabel != null) {
            displayComponents.altitudeLabel.setText(
                String.format("Alt: %.1fm", data.altitude)
            );
        }
        
        if (displayComponents.speedLabel != null) {
            displayComponents.speedLabel.setText(
                String.format("Speed: %.1fm/s", data.groundSpeed)
            );
        }
        
        if (displayComponents.satellitesLabel != null) {
            displayComponents.satellitesLabel.setText(
                String.format("GPS: %d satellites", data.satellites)
            );
        }
        
        if (displayComponents.flightModeLabel != null) {
            displayComponents.flightModeLabel.setText(
                String.format("Mode: %s", data.flightMode)
            );
        }
        
        if (displayComponents.armStatusLabel != null) {
            String armStatus = data.isArmed ? "ARMED" : "DISARMED";
            displayComponents.armStatusLabel.setText(armStatus);
            displayComponents.armStatusLabel.setStyle(
                data.isArmed ? "-fx-text-fill: red;" : "-fx-text-fill: green;"
            );
        }
    }
    
    /**
     * Update attitude data display
     */
    private void updateAttitudeData(TelemetryData data) {
        if (displayComponents.pitchLabel != null) {
            displayComponents.pitchLabel.setText(
                String.format("Pitch: %.1f°", data.pitch)
            );
        }
        
        if (displayComponents.rollLabel != null) {
            displayComponents.rollLabel.setText(
                String.format("Roll: %.1f°", data.roll)
            );
        }
        
        if (displayComponents.yawLabel != null) {
            displayComponents.yawLabel.setText(
                String.format("Yaw: %.1f°", data.yaw)
            );
        }
    }
    
    /**
     * Update environmental data display
     */
    private void updateEnvironmentalData(TelemetryData data) {
        if (displayComponents.pressureLabel != null) {
            displayComponents.pressureLabel.setText(
                String.format("Pressure: %.1fhPa", data.pressure)
            );
        }
        
        if (displayComponents.internalTempLabel != null) {
            displayComponents.internalTempLabel.setText(
                String.format("Internal: %.1f°C", data.internalTemp)
            );
        }
        
        if (displayComponents.externalTempLabel != null) {
            displayComponents.externalTempLabel.setText(
                String.format("External: %.1f°C", data.externalTemp)
            );
        }
        
        if (displayComponents.gpsAltitudeLabel != null) {
            displayComponents.gpsAltitudeLabel.setText(
                String.format("GPS Alt: %.1fm", data.gpsAltitude)
            );
        }
        
        if (displayComponents.verticalVelocityLabel != null) {
            displayComponents.verticalVelocityLabel.setText(
                String.format("V. Speed: %.1fm/s", data.verticalVelocity)
            );
        }
    }
    
    /**
     * Update mission-related data display
     */
    private void updateMissionData(TelemetryData data) {
        if (displayComponents.distanceLabel != null) {
            displayComponents.distanceLabel.setText(
                String.format("Distance: %.1fm", data.distanceFromHome)
            );
        }
        
        if (displayComponents.missionTimeLabel != null) {
            // Mission time calculation would go here
            displayComponents.missionTimeLabel.setText("Mission: --:--:--");
        }
    }
    
    /**
     * Update packet information display
     */
    private void updatePacketInfo(TelemetryData data) {
        if (displayComponents.packetCountLabel != null) {
            displayComponents.packetCountLabel.setText(
                String.format("Packets: %d", data.totalPackets)
            );
        }
    }
    
    /**
     * Update error code display
     */
    private void updateErrorCodes(TelemetryData data) {
        if (displayComponents.errorCodeContainer != null) {
            // This would be implemented based on the actual error code system
            // For now, just show a basic status
            if (displayComponents.errorCodeLabel != null) {
                displayComponents.errorCodeLabel.setText("Status: OK");
                displayComponents.errorCodeLabel.setStyle("-fx-text-fill: green;");
            }
        }
    }
    
    /**
     * Clear all telemetry displays
     */
    private void clearTelemetryDisplay() {
        Platform.runLater(() -> {
            clearLabel(displayComponents.batteryVoltageLabel, "Battery: --");
            clearLabel(displayComponents.batteryStatusLabel, "(--)");
            clearLabel(displayComponents.gpsLatitudeLabel, "Lat: --");
            clearLabel(displayComponents.gpsLongitudeLabel, "Lon: --");
            clearLabel(displayComponents.altitudeLabel, "Alt: --");
            clearLabel(displayComponents.speedLabel, "Speed: --");
            clearLabel(displayComponents.satellitesLabel, "GPS: --");
            clearLabel(displayComponents.flightModeLabel, "Mode: --");
            clearLabel(displayComponents.armStatusLabel, "DISARMED");
            clearLabel(displayComponents.pitchLabel, "Pitch: --");
            clearLabel(displayComponents.rollLabel, "Roll: --");
            clearLabel(displayComponents.yawLabel, "Yaw: --");
            clearLabel(displayComponents.pressureLabel, "Pressure: --");
            clearLabel(displayComponents.internalTempLabel, "Internal: --");
            clearLabel(displayComponents.externalTempLabel, "External: --");
            clearLabel(displayComponents.gpsAltitudeLabel, "GPS Alt: --");
            clearLabel(displayComponents.verticalVelocityLabel, "V. Speed: --");
            clearLabel(displayComponents.distanceLabel, "Distance: --");
            clearLabel(displayComponents.missionTimeLabel, "Mission: --");
            clearLabel(displayComponents.packetCountLabel, "Packets: --");
        });
    }
    
    /**
     * Helper method to clear a label with default text
     */
    private void clearLabel(Label label, String defaultText) {
        if (label != null) {
            label.setText(defaultText);
            label.setStyle("-fx-text-fill: black;");
        }
    }
    
    /**
     * Update team ID display
     */
    public void updateTeamId() {
        if (displayComponents.teamIdLabel != null) {
            displayComponents.teamIdLabel.setText(
                String.format("Team ID: %s", ApplicationConstants.TEAM_ID)
            );
        }
    }
    
    /**
     * Update satellite status display
     */
    public void updateSatelliteStatus(boolean isConnected) {
        if (displayComponents.satelliteStatusLabel != null) {
            String status = isConnected ? "Connected" : "Disconnected";
            String style = isConnected ? "-fx-text-fill: green;" : "-fx-text-fill: red;";
            displayComponents.satelliteStatusLabel.setText(status);
            displayComponents.satelliteStatusLabel.setStyle(style);
        }
    }
    
    /**
     * Inner class to hold telemetry display component references
     */
    public static class TelemetryDisplayComponents {
        // Basic telemetry
        public Label batteryVoltageLabel;
        public Label batteryStatusLabel;
        public Label gpsLatitudeLabel;
        public Label gpsLongitudeLabel;
        public Label altitudeLabel;
        public Label speedLabel;
        public Label armStatusLabel;
        public Label flightModeLabel;
        public Label satellitesLabel;
        public Label missionTimeLabel;
        
        // Attitude
        public Label pitchLabel;
        public Label rollLabel;
        public Label yawLabel;
        
        // Environmental
        public Label pressureLabel;
        public Label internalTempLabel;
        public Label externalTempLabel;
        public Label gpsAltitudeLabel;
        public Label verticalVelocityLabel;
        
        // Mission
        public Label distanceLabel;
        
        // Status
        public Label teamIdLabel;
        public Label satelliteStatusLabel;
        public Label errorCodeLabel;
        public Label packetCountLabel;
        public HBox errorCodeContainer;
    }
}
