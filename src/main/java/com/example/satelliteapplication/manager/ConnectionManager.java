package com.example.satelliteapplication.manager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

import com.fazecast.jSerialComm.SerialPort;
import com.example.satelliteapplication.service.VideoCapture;
import com.example.satelliteapplication.service.VideoCapture.VideoSource;
import com.example.satelliteapplication.service.VideoCapture.VideoSourceType;

import java.util.Arrays;
import java.util.List;

/**
 * Manages all connection-related operations including telemetry and video connections.
 * Separates connection logic from UI logic for better maintainability.
 */
public class ConnectionManager {
    
    private final ConnectionComponents connectionComponents;
    private final TelemetryManager telemetryManager;
    private final VideoCapture videoCapture;
    
    private boolean isTelemetryConnected = false;
    private boolean isVideoConnected = false;
    
    public ConnectionManager(ConnectionComponents connectionComponents, 
                           TelemetryManager telemetryManager, 
                           VideoCapture videoCapture) {
        this.connectionComponents = connectionComponents;
        this.telemetryManager = telemetryManager;
        this.videoCapture = videoCapture;
    }
    
    /**
     * Initialize connection components
     */
    public void initializeConnections() {
        setupTelemetryComboBox();
        setupVideoComboBox();
        updateConnectionStatus();
    }
    
    /**
     * Setup telemetry port combo box
     */
    private void setupTelemetryComboBox() {
        if (connectionComponents.telemetryComboBox != null) {
            connectionComponents.telemetryComboBox.setItems(FXCollections.observableArrayList());
            refreshTelemetryPorts();
        }
    }
    
    /**
     * Setup video source combo box
     */
    private void setupVideoComboBox() {
        if (connectionComponents.videoComboBox != null) {
            // Create default video sources
            List<VideoSource> videoSources = Arrays.asList(
                new VideoSource("USB Camera", 0, VideoSourceType.USB_CAMERA),
                new VideoSource("Network Stream", "rtsp://example.com/stream", VideoSourceType.NETWORK_STREAM)
            );
            connectionComponents.videoComboBox.setItems(FXCollections.observableArrayList(videoSources));
            connectionComponents.videoComboBox.getSelectionModel().selectFirst();
        }
    }
    
    /**
     * Refresh available telemetry ports
     */
    public void refreshTelemetryPorts() {
        if (connectionComponents.telemetryComboBox != null) {
            SerialPort[] ports = SerialPort.getCommPorts();
            ObservableList<SerialPortInfo> portList = FXCollections.observableArrayList();
            
            for (SerialPort port : ports) {
                portList.add(new SerialPortInfo(port));
            }
            
            connectionComponents.telemetryComboBox.setItems(portList);
            
            if (!portList.isEmpty()) {
                connectionComponents.telemetryComboBox.getSelectionModel().selectFirst();
            }
        }
    }
    
    /**
     * Connect to telemetry
     */
    public void connectTelemetry() {
        if (isTelemetryConnected) {
            disconnectTelemetry();
            return;
        }
        
        SerialPortInfo selectedPort = connectionComponents.telemetryComboBox.getValue();
        if (selectedPort == null) {
            updateTelemetryStatus("No port selected", false);
            return;
        }
        
        try {
            boolean success = telemetryManager.connect(selectedPort.getPort());
            if (success) {
                isTelemetryConnected = true;
                updateTelemetryStatus("Connected", true);
                updateConnectionButtonText(true);
            } else {
                updateTelemetryStatus("Connection failed", false);
            }
        } catch (Exception e) {
            updateTelemetryStatus("Error: " + e.getMessage(), false);
        }
    }
    
    /**
     * Disconnect telemetry
     */
    public void disconnectTelemetry() {
        if (telemetryManager != null) {
            telemetryManager.disconnect();
        }
        isTelemetryConnected = false;
        updateTelemetryStatus("Disconnected", false);
        updateConnectionButtonText(false);
    }
    
    /**
     * Connect to video source
     */
    public void connectVideo() {
        if (isVideoConnected) {
            disconnectVideo();
            return;
        }
        
        VideoSource selectedSource = connectionComponents.videoComboBox.getValue();
        if (selectedSource == null) {
            updateVideoStatus("No source selected", false);
            return;
        }
        
        try {
            videoCapture.startCapture(selectedSource, null); // ImageView will be set later
            isVideoConnected = true;
            updateVideoStatus("Connected", true);
            updateVideoButtonText(true);
        } catch (Exception e) {
            updateVideoStatus("Error: " + e.getMessage(), false);
        }
    }
    
    /**
     * Disconnect video
     */
    public void disconnectVideo() {
        if (videoCapture != null) {
            videoCapture.stopCapture();
        }
        isVideoConnected = false;
        updateVideoStatus("Disconnected", false);
        updateVideoButtonText(false);
    }
    
    /**
     * Update telemetry status display
     */
    private void updateTelemetryStatus(String status, boolean isConnected) {
        if (connectionComponents.telemetryStatus != null) {
            Platform.runLater(() -> {
                connectionComponents.telemetryStatus.setText(status);
                String style = isConnected ? "-fx-text-fill: green;" : "-fx-text-fill: red;";
                connectionComponents.telemetryStatus.setStyle(style);
            });
        }
    }
    
    /**
     * Update video status display
     */
    private void updateVideoStatus(String status, boolean isConnected) {
        if (connectionComponents.videoStatus != null) {
            Platform.runLater(() -> {
                connectionComponents.videoStatus.setText(status);
                String style = isConnected ? "-fx-text-fill: green;" : "-fx-text-fill: red;";
                connectionComponents.videoStatus.setStyle(style);
            });
        }
    }
    
    /**
     * Update telemetry connection button text
     */
    private void updateConnectionButtonText(boolean isConnected) {
        if (connectionComponents.telemetryConnectBtn != null) {
            Platform.runLater(() -> {
                String text = isConnected ? "Disconnect" : "Connect";
                connectionComponents.telemetryConnectBtn.setText(text);
            });
        }
    }
    
    /**
     * Update video connection button text
     */
    private void updateVideoButtonText(boolean isConnected) {
        if (connectionComponents.videoConnectBtn != null) {
            Platform.runLater(() -> {
                String text = isConnected ? "Disconnect" : "Connect";
                connectionComponents.videoConnectBtn.setText(text);
            });
        }
    }
    
    /**
     * Update overall connection status
     */
    private void updateConnectionStatus() {
        // This method can be used to update any overall connection status indicators
    }
    
    /**
     * Check if telemetry is connected
     */
    public boolean isTelemetryConnected() {
        return isTelemetryConnected;
    }
    
    /**
     * Check if video is connected
     */
    public boolean isVideoConnected() {
        return isVideoConnected;
    }
    
    /**
     * Shutdown all connections
     */
    public void shutdown() {
        disconnectTelemetry();
        disconnectVideo();
    }
    
    /**
     * Inner class to hold connection component references
     */
    public static class ConnectionComponents {
        // Telemetry connection
        public ComboBox<SerialPortInfo> telemetryComboBox;
        public Button telemetryRefreshBtn;
        public Button telemetryConnectBtn;
        public Label telemetryStatus;
        
        // Video connection
        public ComboBox<VideoSource> videoComboBox;
        public Button videoRefreshBtn;
        public Button videoConnectBtn;
        public Label videoStatus;
    }
    
    /**
     * Helper class for serial port display
     */
    public static class SerialPortInfo {
        private final SerialPort port;
        private final String displayName;

        public SerialPortInfo(SerialPort port) {
            this.port = port;
            this.displayName = port.getSystemPortName() + " - " + port.getDescriptivePortName();
        }

        public SerialPort getPort() {
            return port;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
