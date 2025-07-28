package com.example.satelliteapplication.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.example.satelliteapplication.manager.TelemetryManager;
import com.example.satelliteapplication.manager.MapManager;
import com.example.satelliteapplication.manager.UIStateManager;

import com.example.satelliteapplication.service.VideoCapture;
import com.example.satelliteapplication.service.VideoCapture.VideoSource;

import com.fazecast.jSerialComm.SerialPort;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // Connection Controls
    @FXML private ComboBox<SerialPortInfo> telemetryComboBox;
    @FXML private ComboBox<VideoSource> videoComboBox;
    @FXML private Button telemetryRefreshBtn;
    @FXML private Button telemetryConnectBtn;
    @FXML private Button videoRefreshBtn;
    @FXML private Button videoConnectBtn;
    @FXML private Button videoExpandBtn;
    @FXML private Label telemetryStatus;
    @FXML private Label videoStatus;

    // Telemetry Display
    @FXML private VBox telemetryPanel;
    @FXML private VBox telemetryPlaceholder;
    @FXML private Label batteryLabel;
    @FXML private Label gpsLabel;
    @FXML private Label altitudeLabel;
    @FXML private Label speedLabel;
    @FXML private Label armStatusLabel;
    @FXML private Label flightModeLabel;
    @FXML private Label satellitesLabel;
    @FXML private Label rollPitchYawLabel;

    // New telemetry fields
    @FXML private Label missionTimeLabel;
    @FXML private Label pressureLabel;
    @FXML private Label verticalVelocityLabel;
    @FXML private Label distanceLabel;
    @FXML private Label internalTempLabel;
    @FXML private Label externalTempLabel;
    @FXML private Label gpsAltitudeLabel;
    @FXML private Label pitchLabel;
    @FXML private Label rollLabel;
    @FXML private Label yawLabel;
    @FXML private Label messageLabel;
    @FXML private Label teamIdLabel;

    // Video Display
    @FXML private VBox videoPanel;
    @FXML private VBox videoPlaceholder;
    @FXML private StackPane videoContainer;
    private ImageView videoImageView;
    private Stage videoStage;
    private ImageView externalVideoImageView;
    private boolean isExternalVideoMode = false;

    // Map Display
    @FXML private VBox mapPanel;
    @FXML private VBox mapPlaceholder;
    @FXML private WebView mapWebView;
    @FXML private Button clearPathBtn;
    @FXML private Button centerMapBtn;

    // Service managers
    private TelemetryManager telemetryManager;
    private MapManager mapManager;
    private UIStateManager uiStateManager;
    private VideoCapture videoCapture;

    // Constants
    private static final String TEAM_ID = "001";

    // Helper class for serial port display
    private static class SerialPortInfo {
        SerialPort port;
        String displayName;

        SerialPortInfo(SerialPort port) {
            this.port = port;
            this.displayName = port.getSystemPortName() + " - " + port.getDescriptivePortName();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize service managers
        telemetryManager = new TelemetryManager();
        mapManager = new MapManager();
        uiStateManager = new UIStateManager();
        videoCapture = new VideoCapture();

        // Set up telemetry callbacks
        telemetryManager.setLogCallback(this::log);
        telemetryManager.setDataUpdateCallback(this::updateTelemetryDisplay);
        telemetryManager.setDisconnectCallback(this::disconnectTelemetry);

        // Create and configure ImageView for video display
        videoImageView = new ImageView();
        videoImageView.setPreserveRatio(true);
        videoImageView.fitWidthProperty().bind(videoContainer.widthProperty());
        videoImageView.fitHeightProperty().bind(videoContainer.heightProperty());

        // Set up event handlers
        telemetryRefreshBtn.setOnAction(e -> refreshTelemetrySources());
        telemetryConnectBtn.setOnAction(e -> toggleTelemetryConnection());
        videoRefreshBtn.setOnAction(e -> refreshVideoSources());
        videoConnectBtn.setOnAction(e -> toggleVideoConnection());
        videoExpandBtn.setOnAction(e -> toggleExternalVideo());
        clearPathBtn.setOnAction(e -> mapManager.clearPath());
        centerMapBtn.setOnAction(e -> mapManager.centerMap());

        // Initially hide expand button
        videoExpandBtn.setVisible(false);
        videoExpandBtn.setManaged(false);

        // Initialize combo boxes
        refreshTelemetrySources();
        refreshVideoSources();

        // Initialize map
        mapManager.initialize(mapWebView);

        // Initially show placeholders
        uiStateManager.showAllPlaceholders(telemetryPanel, telemetryPlaceholder,
                videoPanel, videoPlaceholder, mapPanel, mapPlaceholder);

        // Initialize telemetry fields with default values
        initializeTelemetryFields();

        // Initialize team ID
        teamIdLabel.setText("Team: " + TEAM_ID);
    }

    private void initializeTelemetryFields() {
        missionTimeLabel.setText("00:00:00");
        pressureLabel.setText("0.0 hPa");
        verticalVelocityLabel.setText("0.0 m/s");
        distanceLabel.setText("0.0 m");
        internalTempLabel.setText("0.0°C");
        externalTempLabel.setText("0.0°C");
        gpsAltitudeLabel.setText("0.0 m");
        pitchLabel.setText("0.0°");
        rollLabel.setText("0.0°");
        yawLabel.setText("0.0°");
        messageLabel.setText("No message");
    }

    private void updateTelemetryDisplay(TelemetryManager.TelemetryData data) {
        // Battery
        if (data.batteryPercentage == -1) {
            batteryLabel.setText(String.format("%.1fV", data.batteryVoltage));
        } else {
            batteryLabel.setText(String.format("%.1fV (%d%%)", data.batteryVoltage, data.batteryPercentage));
        }

        // GPS
        if (data.hasGpsFix) {
            gpsLabel.setText(String.format("%.6f, %.6f", data.latitude, data.longitude));
            satellitesLabel.setText(String.valueOf(data.satellites));
            gpsAltitudeLabel.setText(String.format("%.1f m", data.gpsAltitude));
        } else {
            gpsLabel.setText("No Fix");
            satellitesLabel.setText(data.satellites + " (no fix)");
            gpsAltitudeLabel.setText("No GPS");
        }

        // Altitude and speed
        altitudeLabel.setText(String.format("%.1f m", data.altitude));
        speedLabel.setText(String.format("%.1f m/s", data.groundSpeed));
        verticalVelocityLabel.setText(String.format("%.2f m/s", data.verticalVelocity));

        // Distance from home
        distanceLabel.setText(String.format("%.1f m", data.distanceFromHome));

        // Flight status
        flightModeLabel.setText(data.flightMode);
        armStatusLabel.setText(data.isArmed ? "ARMED" : "DISARMED");
        armStatusLabel.setStyle(data.isArmed ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");

        // Attitude
        rollPitchYawLabel.setText(String.format("R:%.0f° P:%.0f° Y:%.0f°", data.roll, data.pitch, data.yaw));
        rollLabel.setText(String.format("%.1f°", data.roll));
        pitchLabel.setText(String.format("%.1f°", data.pitch));
        yawLabel.setText(String.format("%.1f°", data.yaw));

        // Environmental
        pressureLabel.setText(String.format("%.1f hPa", data.pressure));
        internalTempLabel.setText(String.format("%.1f°C", data.internalTemp));
        externalTempLabel.setText(String.format("%.1f°C", data.externalTemp));

        // Status message
        if (!data.statusMessage.isEmpty()) {
            messageLabel.setText(data.statusMessage.substring(0, Math.min(data.statusMessage.length(), 50)));
        }

        // Update mission time
        updateMissionTime();

        // Update map position
        if (data.hasValidPosition() && mapManager.isInitialized()) {
            mapManager.updatePosition(data.latitude, data.longitude);
        }
    }

    private void updateMissionTime() {
        LocalDateTime missionStartTime = telemetryManager.getMissionStartTime();
        if (missionStartTime != null) {
            LocalDateTime now = LocalDateTime.now();
            long seconds = java.time.Duration.between(missionStartTime, now).getSeconds();
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;

            String timeString = String.format("%02d:%02d:%02d", hours, minutes, secs);
            missionTimeLabel.setText(timeString);
        }
    }

    private void refreshTelemetrySources() {
        telemetryComboBox.getItems().clear();
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            String description = port.getDescriptivePortName().toLowerCase();
            if (description.contains("cp210") || description.contains("usb") ||
                    description.contains("serial") || description.contains("uart")) {
                telemetryComboBox.getItems().add(new SerialPortInfo(port));
            }
        }

        if (!telemetryComboBox.getItems().isEmpty()) {
            telemetryComboBox.getSelectionModel().select(0);
        }

        log("Found " + telemetryComboBox.getItems().size() + " potential LR900 ports");
    }

    private void refreshVideoSources() {
        List<VideoSource> sources = new ArrayList<>();

        // Add default USB camera options
        sources.add(new VideoSource("USB Camera 0", 0, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("USB Camera 1", 1, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("USB Camera 2", 2, VideoCapture.VideoSourceType.USB_CAMERA));

        // Add HDMI capture options
        sources.add(new VideoSource("HDMI Capture 0", 3, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("HDMI Capture 1", 4, VideoCapture.VideoSourceType.USB_CAMERA));

        ObservableList<VideoSource> videoSources = FXCollections.observableArrayList(sources);
        videoComboBox.setItems(videoSources);
    }

    private void toggleTelemetryConnection() {
        if (!uiStateManager.isTelemetryConnected()) {
            connectTelemetry();
        } else {
            disconnectTelemetry();
        }
    }

    private void connectTelemetry() {
        SerialPortInfo selected = telemetryComboBox.getValue();
        if (selected == null) {
            showAlert("Please select a port");
            return;
        }

        if (telemetryManager.connect(selected.port)) {
            Platform.runLater(() -> {
                uiStateManager.updateTelemetryConnectionUI(true, telemetryStatus, telemetryConnectBtn, telemetryComboBox);
                updateContentDisplay();
            });
        } else {
            showAlert("Failed to open port: " + selected.port.getSystemPortName());
        }
    }

    private void disconnectTelemetry() {
        telemetryManager.disconnect();

        Platform.runLater(() -> {
            uiStateManager.updateTelemetryConnectionUI(false, telemetryStatus, telemetryConnectBtn, telemetryComboBox);
            updateContentDisplay();
        });
    }

    private void toggleVideoConnection() {
        if (!uiStateManager.isVideoConnected()) {
            connectVideo();
        } else {
            disconnectVideo();
        }
    }

    private void connectVideo() {
        VideoSource selectedSource = videoComboBox.getValue();
        if (selectedSource == null) {
            return;
        }

        uiStateManager.updateVideoConnectingUI(videoStatus, videoConnectBtn);

        new Thread(() -> {
            try {
                if (selectedSource.getType() == VideoCapture.VideoSourceType.USB_CAMERA) {
                    videoCapture.startCapture(selectedSource, videoImageView);

                    Platform.runLater(() -> {
                        uiStateManager.updateVideoConnectionUI(true, videoStatus, videoConnectBtn, videoExpandBtn);

                        // Clear existing content and add video view
                        videoContainer.getChildren().clear();
                        videoContainer.getChildren().add(videoImageView);

                        updateContentDisplay();
                    });
                } else {
                    Platform.runLater(() -> {
                        uiStateManager.updateVideoConnectionError("Network sources not implemented", videoStatus, videoConnectBtn);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    uiStateManager.updateVideoConnectionError("Failed to connect to camera", videoStatus, videoConnectBtn);
                });
            }
        }).start();
    }

    private void disconnectVideo() {
        closeExternalVideo();
        videoCapture.stopCapture();

        uiStateManager.updateVideoConnectionUI(false, videoStatus, videoConnectBtn, videoExpandBtn);

        // Clear video container
        videoContainer.getChildren().clear();
        Label placeholder = new Label("Video feed disconnected");
        placeholder.getStyleClass().add("info-label");
        videoContainer.getChildren().add(placeholder);

        updateContentDisplay();
    }

    private void toggleExternalVideo() {
        if (!isExternalVideoMode) {
            expandVideoToNewWindow();
        } else {
            closeExternalVideo();
        }
    }

    private void expandVideoToNewWindow() {
        if (!uiStateManager.isVideoConnected() || videoStage != null) {
            return;
        }

        // Create external video ImageView
        externalVideoImageView = new ImageView();
        externalVideoImageView.setPreserveRatio(true);

        // Create StackPane to hold the video
        StackPane videoPane = new StackPane(externalVideoImageView);
        videoPane.setStyle("-fx-background-color: black;");

        // Create new stage
        videoStage = new Stage();
        videoStage.setTitle("NazarX Video Feed - External Display");

        // Get screen information
        List<Screen> screens = Screen.getScreens();

        // Create scene
        Scene scene = new Scene(videoPane, 1280, 720);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm());
        videoStage.setScene(scene);

        // Position on second monitor if available
        if (screens.size() > 1) {
            Screen secondScreen = screens.get(1);
            Rectangle2D bounds = secondScreen.getVisualBounds();

            videoStage.setX(bounds.getMinX());
            videoStage.setY(bounds.getMinY());
            videoStage.setWidth(bounds.getWidth());
            videoStage.setHeight(bounds.getHeight());
            videoStage.setMaximized(true);
        } else {
            videoStage.setX(Screen.getPrimary().getVisualBounds().getWidth() / 2);
            videoStage.setY(0);
        }

        // Bind video size to stage size
        externalVideoImageView.fitWidthProperty().bind(videoPane.widthProperty());
        externalVideoImageView.fitHeightProperty().bind(videoPane.heightProperty());

        // Update video capture
        videoCapture.setExternalImageView(externalVideoImageView);
        videoCapture.setPrimaryImageView(null);

        // Update main window
        videoContainer.getChildren().clear();
        Label externalModeLabel = new Label("Video is displayed in external window");
        externalModeLabel.getStyleClass().add("info-label");
        videoContainer.getChildren().add(externalModeLabel);

        // Handle stage close
        videoStage.setOnCloseRequest(event -> {
            event.consume();
            closeExternalVideo();
        });

        videoStage.show();

        videoExpandBtn.setText("Close External");
        isExternalVideoMode = true;
    }

    private void closeExternalVideo() {
        if (videoStage != null) {
            videoCapture.setPrimaryImageView(videoImageView);
            videoCapture.setExternalImageView(null);

            videoStage.close();
            videoStage = null;
            externalVideoImageView = null;

            videoContainer.getChildren().clear();
            videoContainer.getChildren().add(videoImageView);

            videoExpandBtn.setText("Expand Video");
            isExternalVideoMode = false;
        }
    }

    private void updateContentDisplay() {
        uiStateManager.updateContentPanels(telemetryPanel, telemetryPlaceholder,
                videoPanel, videoPlaceholder, mapPanel, mapPlaceholder);

        // Force map resize when showing
        if (uiStateManager.isTelemetryConnected()) {
            mapManager.forceResize();
        }
    }

    private void log(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + message);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (telemetryManager != null) {
            telemetryManager.shutdown();
        }

        if (videoCapture != null) {
            videoCapture.stopCapture();
        }

        closeExternalVideo();
    }
}