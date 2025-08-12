package com.example.satelliteapplication.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.example.satelliteapplication.manager.TelemetryManager;
import com.example.satelliteapplication.manager.MapManager;
import com.example.satelliteapplication.manager.UIStateManager;

import com.example.satelliteapplication.service.VideoCapture;
import com.example.satelliteapplication.service.VideoCapture.VideoSource;

import com.fazecast.jSerialComm.SerialPort;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // Log File Controls
    @FXML private Button openLogViewerBtn;
    @FXML private Button downloadLogsBtn;

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
    @FXML private Label batteryVoltageLabel;
    @FXML private Label batteryStatusLabel;
    @FXML private Label gpsLatitudeLabel;
    @FXML private Label gpsLongitudeLabel;
    @FXML private Label altitudeLabel;
    @FXML private Label speedLabel;
    @FXML private Label armStatusLabel;
    @FXML private Label flightModeLabel;
    @FXML private Label satellitesLabel;
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
    @FXML private Label teamIdLabel;
    @FXML private Label satelliteStatusLabel;
    @FXML private Label errorCodeLabel;
    @FXML private Label packetCountLabel;

    // Serial Monitor Components (from FXML)
    @FXML private ScrollPane serialScrollPane;
    @FXML private VBox serialMessageContainer;
    @FXML private TextField serialInputField;
    @FXML private Button serialSendButton;
    @FXML private Button serialClearButton;

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

    // Service managers
    private TelemetryManager telemetryManager;
    private MapManager mapManager;
    private UIStateManager uiStateManager;
    private VideoCapture videoCapture;

    // Constants
    private static final String TEAM_ID = "569287";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_SERIAL_MESSAGES = 100;

    // Message types for serial monitor
    private enum MessageType {
        RECEIVED("RX", "serial-message-rx"),
        SENT("TX", "serial-message-tx"),
        ERROR("ERR", "serial-message-error"),
        INFO("INFO", "serial-message-info");

        private final String prefix;
        private final String styleClass;

        MessageType(String prefix, String styleClass) {
            this.prefix = prefix;
            this.styleClass = styleClass;
        }
    }

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
        telemetryManager.setLogCallback(this::handleTelemetryLog);
        telemetryManager.setDataUpdateCallback(this::updateTelemetryDisplay);
        telemetryManager.setDisconnectCallback(this::disconnectTelemetry);

        // Initialize Serial Monitor
        initializeSerialMonitor();

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

        // Initialize static fields
        teamIdLabel.setText(TEAM_ID);
        satelliteStatusLabel.setText("0");
        errorCodeLabel.setText("0000");

        // Initialize log file buttons
        if (openLogViewerBtn != null) {
            openLogViewerBtn.setOnAction(e -> openLogViewer());
        }
        if (downloadLogsBtn != null) {
            downloadLogsBtn.setOnAction(e -> downloadLogsFromSD());
        }
    }

    // ============ LOG FILE METHODS ============

    private void initializeLogViewer() {
        // Create a button for opening log viewer
        Button openLogViewerBtn = new Button("Open Log Viewer");
        openLogViewerBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        openLogViewerBtn.setPrefWidth(150);
        openLogViewerBtn.setOnAction(e -> openLogViewer());

        // Add the button to your UI - you can place it in the connection panel or create a new section
        // For example, add it to the telemetry connection HBox:
        // Find the telemetry connection HBox in your FXML and add this button
    }

    private void openLogViewer() {
        Stage currentStage = (Stage) telemetryConnectBtn.getScene().getWindow();
        LogFileController.showLogViewer(currentStage);
    }

    private void downloadLogsFromSD() {
        if (!telemetryManager.isConnected()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Not Connected");
            alert.setHeaderText(null);
            alert.setContentText("Please connect to telemetry first to download logs from SD card.");
            alert.showAndWait();
            return;
        }

        // Choose directory to save logs
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory to Save Logs");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File selectedDirectory = directoryChooser.showDialog(downloadLogsBtn.getScene().getWindow());
        if (selectedDirectory != null) {
            downloadLogsToDirectory(selectedDirectory);
        }
    }

    private void downloadLogsToDirectory(File directory) {
        // Create a progress dialog
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Downloading Logs");
        progressAlert.setHeaderText("Downloading logs from SD card...");
        progressAlert.setContentText("This may take a few minutes.");
        progressAlert.getButtonTypes().clear();
        progressAlert.show();

        // In a real implementation, you would:
        // 1. Send MAVLink commands to list logs
        // 2. Download each log file
        // 3. Save to the selected directory

        // For now, show a placeholder implementation
        new Thread(() -> {
            try {
                // Simulate download delay
                Thread.sleep(2000);

                Platform.runLater(() -> {
                    progressAlert.close();

                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Download Complete");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("Logs have been downloaded to:\n" + directory.getAbsolutePath());
                    successAlert.showAndWait();

                    // Optionally open the log viewer with the downloaded files
                    askToOpenDownloadedLogs(directory);
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    progressAlert.close();
                    showAlert("Download interrupted");
                });
            }
        }).start();
    }

    private void askToOpenDownloadedLogs(File directory) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Open Log Viewer");
        alert.setHeaderText(null);
        alert.setContentText("Would you like to open the log viewer to analyze the downloaded logs?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                openLogViewer();
                // You could also pass the directory path to the log viewer
                // to automatically load the first log file
            }
        });
    }

    // Add this helper method to find log files in a directory
    private List<File> findLogFiles(File directory) {
        try {
            return Files.walk(Paths.get(directory.getPath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().toLowerCase().endsWith(".bin"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============ SERIAL MONITOR METHODS ============

    private void initializeSerialMonitor() {
        // Set up event handlers
        serialSendButton.setOnAction(e -> sendSerialCommand());
        serialClearButton.setOnAction(e -> clearSerialMonitor());

        // Handle Enter key in input field
        serialInputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendSerialCommand();
            }
        });

        // Auto-scroll to bottom when new messages are added
        serialMessageContainer.heightProperty().addListener(
                (obs, oldVal, newVal) -> Platform.runLater(() -> serialScrollPane.setVvalue(1.0))
        );

        // Initially disable input controls
        setSerialMonitorEnabled(false);

        // Add initial info message
        addSerialMessage(MessageType.INFO, "Serial monitor ready. Connect telemetry to start.");
    }

    private void sendSerialCommand() {
        String command = serialInputField.getText().trim();
        if (command.isEmpty()) {
            return;
        }

        if (!telemetryManager.isConnected()) {
            addSerialMessage(MessageType.ERROR, "Not connected to telemetry");
            return;
        }

        // Send command through telemetry manager
        boolean sent = telemetryManager.sendCommand(command);

        if (sent) {
            addSerialMessage(MessageType.SENT, command);
            serialInputField.clear();
        } else {
            addSerialMessage(MessageType.ERROR, "Failed to send: " + command);
        }
    }

    private void clearSerialMonitor() {
        Platform.runLater(() -> {
            serialMessageContainer.getChildren().clear();
            addSerialMessage(MessageType.INFO, "Serial monitor cleared");
        });
    }

    private void addSerialMessage(MessageType type, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            // Create timestamp
            String timestamp = LocalTime.now().format(TIME_FORMATTER);

            // Create message label
            Label msgLabel = new Label(String.format("[%s] %s: %s",
                    timestamp, type.prefix, message));
            msgLabel.getStyleClass().add(type.styleClass);
            msgLabel.setMaxWidth(Double.MAX_VALUE);
            msgLabel.setWrapText(true);

            // Add to container
            serialMessageContainer.getChildren().add(msgLabel);

            // Remove old messages if exceeding limit
            while (serialMessageContainer.getChildren().size() > MAX_SERIAL_MESSAGES) {
                serialMessageContainer.getChildren().remove(0);
            }

            // Auto-scroll to bottom
            serialScrollPane.setVvalue(1.0);
        });
    }

    private void handleTelemetryLog(String message) {
        // Determine message type based on content
        MessageType type = MessageType.RECEIVED;

        if (message.startsWith("Sent:") || message.startsWith("Sending")) {
            type = MessageType.SENT;
        } else if (message.contains("Error") || message.contains("Failed")) {
            type = MessageType.ERROR;
        } else if (message.contains("Connected") || message.contains("Disconnected") ||
                message.contains("Waiting") || message.contains("Requesting")) {
            type = MessageType.INFO;
        }

        addSerialMessage(type, message);
    }

    private void setSerialMonitorEnabled(boolean enabled) {
        Platform.runLater(() -> {
            serialInputField.setDisable(!enabled);
            serialSendButton.setDisable(!enabled);

            if (enabled) {
                serialInputField.setPromptText("Enter command...");
            } else {
                serialInputField.setPromptText("Connect telemetry to enable...");

                // Clear input if something was typed
                if (!serialInputField.getText().isEmpty()) {
                    serialInputField.clear();
                }
            }
        });
    }

    // ============ TELEMETRY METHODS ============

    private void initializeTelemetryFields() {
        LocalDateTime now = LocalDateTime.now();
        missionTimeLabel.setText(now.format(DATE_TIME_FORMATTER));
        batteryVoltageLabel.setText("0.0V");
        batteryStatusLabel.setText("0%");
        pressureLabel.setText("0.0 hPa");
        verticalVelocityLabel.setText("0.0 m/s");
        distanceLabel.setText("0.0 m");
        internalTempLabel.setText("0.0°C");
        externalTempLabel.setText("0.0°C");
        gpsAltitudeLabel.setText("0.0 m");
        gpsLatitudeLabel.setText("0.000000");
        gpsLongitudeLabel.setText("0.000000");
        altitudeLabel.setText("0.0 m");
        speedLabel.setText("0.0 m/s");
        satellitesLabel.setText("0");
        flightModeLabel.setText("Unknown");
        armStatusLabel.setText("DISARMED");
        pitchLabel.setText("0.0°");
        rollLabel.setText("0.0°");
        yawLabel.setText("0.0°");
        packetCountLabel.setText("0");  // Add this line
    }

    private void updateTelemetryDisplay(TelemetryManager.TelemetryData data) {
        // Battery
        batteryVoltageLabel.setText(String.format("%.1fV", data.batteryVoltage));
        if (data.batteryPercentage >= 0) {
            batteryStatusLabel.setText(data.batteryPercentage + "%");
        } else {
            batteryStatusLabel.setText("N/A");
        }

        // GPS
        if (data.hasGpsFix) {
            gpsLatitudeLabel.setText(String.format("%.6f", data.latitude));
            gpsLongitudeLabel.setText(String.format("%.6f", data.longitude));
            satellitesLabel.setText(String.valueOf(data.satellites));
            gpsAltitudeLabel.setText(String.format("%.1f m", data.gpsAltitude));
        } else {
            gpsLatitudeLabel.setText("No Fix");
            gpsLongitudeLabel.setText("No Fix");
            satellitesLabel.setText(data.satellites + " (no fix)");
            gpsAltitudeLabel.setText("No GPS");
        }

        // Altitude and speed
        altitudeLabel.setText(String.format("%.1f m", data.altitude));
        speedLabel.setText(String.format("%.1f m/s", data.groundSpeed));
        verticalVelocityLabel.setText(String.format("%.2f m/s", data.verticalVelocity));
        distanceLabel.setText(String.format("%.1f m", data.distanceFromHome));

        // Flight status
        flightModeLabel.setText(data.flightMode);
        armStatusLabel.setText(data.isArmed ? "ARMED" : "DISARMED");
        armStatusLabel.setTextFill(data.isArmed ? Color.web("#e74c3c") : Color.web("#27ae60"));

        // Attitude
        rollLabel.setText(String.format("%.1f°", data.roll));
        pitchLabel.setText(String.format("%.1f°", data.pitch));
        yawLabel.setText(String.format("%.1f°", data.yaw));

        // Environmental
        pressureLabel.setText(String.format("%.1f hPa", data.pressure));
        internalTempLabel.setText(String.format("%.1f°C", data.internalTemp));
        externalTempLabel.setText(String.format("%.1f°C", data.externalTemp));

        // Packet counter - show both total and current sequence
        packetCountLabel.setText(String.format("%d (seq: %d)", data.totalPackets, data.currentSequence));

        // Update mission time
        updateMissionTime();

        // Update map position
        if (data.hasValidPosition() && mapManager.isInitialized()) {
            mapManager.updatePosition(data.latitude, data.longitude);
        }
    }

    private void updateMissionTime() {
        LocalDateTime now = LocalDateTime.now();
        missionTimeLabel.setText(now.format(DATE_TIME_FORMATTER));
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

        addSerialMessage(MessageType.INFO,
                "Found " + telemetryComboBox.getItems().size() + " serial ports");
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

        addSerialMessage(MessageType.INFO, "Connecting to " + selected.port.getSystemPortName() + "...");

        if (telemetryManager.connect(selected.port)) {
            Platform.runLater(() -> {
                uiStateManager.updateTelemetryConnectionUI(true, telemetryStatus,
                        telemetryConnectBtn, telemetryComboBox);
                updateContentDisplay();
                setSerialMonitorEnabled(true);
                addSerialMessage(MessageType.INFO, "Telemetry connected successfully");
            });
        } else {
            addSerialMessage(MessageType.ERROR, "Failed to connect to " + selected.port.getSystemPortName());
            showAlert("Failed to open port: " + selected.port.getSystemPortName());
        }
    }

    private void disconnectTelemetry() {
        telemetryManager.disconnect();

        Platform.runLater(() -> {
            uiStateManager.updateTelemetryConnectionUI(false, telemetryStatus,
                    telemetryConnectBtn, telemetryComboBox);
            updateContentDisplay();
            setSerialMonitorEnabled(false);
            addSerialMessage(MessageType.INFO, "Telemetry disconnected");
        });
    }

    // ============ VIDEO METHODS ============

    private void refreshVideoSources() {
        List<VideoSource> sources = new ArrayList<>();

        // Add camera options
        sources.add(new VideoSource("Camera 0 (Built-in)", 0, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("Camera 1 (USB)", 1, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("Camera 2 (USB)", 2, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("Camera 3 (HDMI)", 3, VideoCapture.VideoSourceType.USB_CAMERA));
        sources.add(new VideoSource("Camera 4 (HDMI)", 4, VideoCapture.VideoSourceType.USB_CAMERA));

        ObservableList<VideoSource> videoSources = FXCollections.observableArrayList(sources);
        videoComboBox.setItems(videoSources);

        if (!videoComboBox.getItems().isEmpty()) {
            videoComboBox.getSelectionModel().select(0);
        }
    }

    private void toggleVideoConnection() {
        if (!videoCapture.isRunning()) {
            connectVideo();
        } else {
            disconnectVideo();
        }
    }

    private void connectVideo() {
        VideoSource selectedSource = videoComboBox.getValue();
        if (selectedSource == null) {
            showAlert("Please select a video source");
            return;
        }

        // Disable controls while connecting
        uiStateManager.updateVideoConnectingUI(videoStatus, videoConnectBtn);
        videoComboBox.setDisable(true);

        // Try to connect in a background thread
        new Thread(() -> {
            try {
                // Attempt to start capture
                videoCapture.startCapture(selectedSource, videoImageView);

                // If successful, update UI
                Platform.runLater(() -> {
                    uiStateManager.updateVideoConnectionUI(true, videoStatus, videoConnectBtn, videoExpandBtn);

                    // Clear existing content and add video view
                    videoContainer.getChildren().clear();
                    videoContainer.getChildren().add(videoImageView);

                    updateContentDisplay();
                    addSerialMessage(MessageType.INFO, "Video connected: " + selectedSource.getName());
                });

            } catch (Exception e) {
                // Handle connection failure
                Platform.runLater(() -> {
                    String errorMessage = e.getMessage();

                    // Provide user-friendly error messages
                    if (errorMessage == null) {
                        errorMessage = "Unknown error";
                    } else if (errorMessage.contains("not available")) {
                        showAlert("Camera " + selectedSource.getName() + " is not available.\n\n" +
                                "Please try a different camera index.");
                        errorMessage = "Camera not available";
                    } else if (errorMessage.contains("permission")) {
                        showAlert("Camera access denied.\n\nPlease grant camera permissions in System Preferences.");
                        errorMessage = "Permission denied";
                    } else {
                        showAlert("Failed to connect:\n" + errorMessage);
                    }

                    // Update UI state
                    uiStateManager.updateVideoConnectionError(errorMessage, videoStatus, videoConnectBtn);
                    videoComboBox.setDisable(false);
                    addSerialMessage(MessageType.ERROR, "Video connection failed: " + errorMessage);
                });
            }
        }).start();
    }

    private void disconnectVideo() {
        closeExternalVideo();
        videoCapture.stopCapture();

        uiStateManager.updateVideoConnectionUI(false, videoStatus, videoConnectBtn, videoExpandBtn);
        videoComboBox.setDisable(false);

        // Clear video container
        videoContainer.getChildren().clear();
        Label placeholder = new Label("Video feed disconnected");
        placeholder.getStyleClass().add("info-label");
        videoContainer.getChildren().add(placeholder);

        updateContentDisplay();
        addSerialMessage(MessageType.INFO, "Video disconnected");
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

    // ============ UTILITY METHODS ============

    private void updateContentDisplay() {
        uiStateManager.updateContentPanels(telemetryPanel, telemetryPlaceholder,
                videoPanel, videoPlaceholder, mapPanel, mapPlaceholder);

        // Force map resize when showing
        if (uiStateManager.isTelemetryConnected()) {
            mapManager.forceResize();
        }
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