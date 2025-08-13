package com.example.satelliteapplication.manager;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;

import com.example.satelliteapplication.component.Satellite3DViewer;
import com.example.satelliteapplication.constants.ApplicationConstants;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.util.Objects;

/**
 * Manages UI component initialization and configuration.
 * Separates UI setup logic from business logic in the main controller.
 */
public class UIComponentManager {
    
    // UI Components - organized by functionality
    private final UIComponents components;
    private final Stage primaryStage;
    private Satellite3DViewer satellite3DViewer;
    
    public UIComponentManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.components = new UIComponents();
    }
    
    /**
     * Initialize all UI components
     */
    public void initializeUI() throws IOException {
        setupMainWindow();
        setupVideoComponents();
        setupMapComponents();
        setup3DViewer();
        setupErrorCodeDisplay();
    }
    
    /**
     * Setup main window properties
     */
    private void setupMainWindow() {
        primaryStage.setTitle(ApplicationConstants.APPLICATION_TITLE);
        primaryStage.setMaximized(true);
    }
    
    /**
     * Setup video-related components
     */
    private void setupVideoComponents() {
        // Video components will be initialized when needed
        components.videoImageView = new ImageView();
        components.externalVideoImageView = new ImageView();
    }
    
    /**
     * Setup map components
     */
    private void setupMapComponents() {
        components.mapWebView = new WebView();
        components.mapWebView.setPrefSize(800, 600);
    }
    
    /**
     * Setup 3D satellite viewer
     */
    private void setup3DViewer() {
        if (components.satellite3DContainer != null) {
            satellite3DViewer = new Satellite3DViewer(
                components.satellite3DContainer.getWidth(),
                components.satellite3DContainer.getHeight()
            );
            components.satellite3DContainer.getChildren().add(satellite3DViewer.getSubScene());
        }
    }
    
    /**
     * Setup error code display
     */
    private void setupErrorCodeDisplay() {
        if (components.errorCodeContainer != null) {
            // Initialize error code indicators
            for (int i = 0; i < ApplicationConstants.ERROR_CODE_LENGTH; i++) {
                Label errorIndicator = new Label("●");
                errorIndicator.setStyle("-fx-text-fill: green; -fx-font-size: 16px;");
                components.errorCodeContainer.getChildren().add(errorIndicator);
            }
        }
    }
    
    /**
     * Get the UI components
     */
    public UIComponents getComponents() {
        return components;
    }
    
    /**
     * Get the 3D viewer
     */
    public Satellite3DViewer getSatellite3DViewer() {
        return satellite3DViewer;
    }
    
    /**
     * Inner class to hold all UI component references
     */
    public static class UIComponents {
        // Log File Controls
        @FXML public Button openLogViewerBtn;
        @FXML public Button downloadLogsBtn;

        // Connection Controls
        @FXML public ComboBox<SerialPortInfo> telemetryComboBox;
        @FXML public ComboBox<VideoSource> videoComboBox;
        @FXML public Button telemetryRefreshBtn;
        @FXML public Button telemetryConnectBtn;
        @FXML public Button videoRefreshBtn;
        @FXML public Button videoConnectBtn;
        @FXML public Button videoExpandBtn;
        @FXML public Label telemetryStatus;
        @FXML public Label videoStatus;

        // Telemetry Display
        @FXML public HBox telemetryPanel;
        @FXML public VBox telemetryPlaceholder;
        @FXML public Label batteryVoltageLabel;
        @FXML public Label batteryStatusLabel;
        @FXML public Label gpsLatitudeLabel;
        @FXML public Label gpsLongitudeLabel;
        @FXML public Label altitudeLabel;
        @FXML public Label speedLabel;
        @FXML public Label armStatusLabel;
        @FXML public Label flightModeLabel;
        @FXML public Label satellitesLabel;
        @FXML public Label missionTimeLabel;
        @FXML public Label pressureLabel;
        @FXML public Label verticalVelocityLabel;
        @FXML public Label distanceLabel;
        @FXML public Label internalTempLabel;
        @FXML public Label externalTempLabel;
        @FXML public Label gpsAltitudeLabel;
        @FXML public Label pitchLabel;
        @FXML public Label rollLabel;
        @FXML public Label yawLabel;
        @FXML public Label teamIdLabel;
        @FXML public Label satelliteStatusLabel;
        @FXML public Label errorCodeLabel;
        @FXML public Label packetCountLabel;
        @FXML public HBox errorCodeContainer;

        // 3D Orientation Controls
        @FXML public StackPane satellite3DContainer;
        @FXML public Label orientation3DPlaceholder;
        @FXML public Label pitch3DLabel;
        @FXML public Label roll3DLabel;
        @FXML public Label yaw3DLabel;

        // Serial Monitor Components
        @FXML public ScrollPane serialScrollPane;
        @FXML public VBox serialMessageContainer;
        @FXML public TextField serialInputField;
        @FXML public Button serialSendButton;
        @FXML public Button serialClearButton;

        // Video Display
        @FXML public VBox videoPanel;
        @FXML public VBox videoPlaceholder;
        @FXML public StackPane videoContainer;
        public ImageView videoImageView;
        public Stage videoStage;
        public ImageView externalVideoImageView;
        public boolean isExternalVideoMode = false;
        public boolean isReceivingTelemetryData = false;
        public Label dataStatusIndicator;

        // Map Display
        @FXML public VBox mapPanel;
        @FXML public VBox mapPlaceholder;
        @FXML public WebView mapWebView;
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
    
    /**
     * Video source enum
     */
    public enum VideoSource {
        USB_CAMERA("USB Camera"),
        IP_CAMERA("IP Camera"),
        FILE("Video File");

        private final String displayName;

        VideoSource(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
