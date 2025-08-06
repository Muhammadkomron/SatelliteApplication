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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Insets;

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

    // Telemetry Display - Updated fields
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
    @FXML private Label teamIdLabel;

    // Static status fields
    @FXML private Label satelliteStatusLabel;
    @FXML private Label errorCodeLabel;

    // Serial Monitor Components
    @FXML private VBox serialMonitorContainer;
    @FXML private Label serialMonitorTitle;
    @FXML private ScrollPane serialScrollPane;
    @FXML private VBox serialMessageContainer;
    @FXML private HBox serialInputContainer;
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
    private static final int MAX_SERIAL_MESSAGES = 100; // Maximum lines to keep in serial monitor

    // Serial monitor messages
    private List<SerialMessage> serialMessages = new ArrayList<>();

    // Message types for serial monitor
    private enum MessageType {
        RECEIVED("RX", "#2ecc71"),  // Green for received
        SENT("TX", "#3498db"),       // Blue for sent
        ERROR("ERR", "#e74c3c"),     // Red for errors
        INFO("INFO", "#95a5a6");     // Gray for info

        private final String prefix;
        private final String color;

        MessageType(String prefix, String color) {
            this.prefix = prefix;
            this.color = color;
        }
    }

    // Serial message class
    private static class SerialMessage {
        private final String timestamp;
        private final MessageType type;
        private final String message;

        SerialMessage(MessageType type, String message) {
            this.timestamp = LocalTime.now().format(TIME_FORMATTER);
            this.type = type;
            this.message = message;
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
        telemetryManager.setLogCallback(this::addSerialMessage);
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

        // Initialize Serial Monitor UI
        initializeSerialMonitor();

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
    }

    private void initializeSerialMonitor() {
        // Create UI components if they don't exist in FXML
        if (serialMonitorContainer == null) {
            serialMonitorContainer = new VBox(5);
            serialMonitorContainer.setPadding(new Insets(10));
            serialMonitorContainer.setStyle("-fx-background-color: #2c3e50; -fx-border-color: #34495e; -fx-border-radius: 5;");
        }

        if (serialMonitorTitle == null) {
            serialMonitorTitle = new Label("Serial Monitor");
            serialMonitorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
            serialMonitorContainer.getChildren().add(serialMonitorTitle);
        }

        if (serialScrollPane == null) {
            serialScrollPane = new ScrollPane();
            serialScrollPane.setFitToWidth(true);
            serialScrollPane.setPrefHeight(200);
            serialScrollPane.setMinHeight(150);
            serialScrollPane.setMaxHeight(300);
            serialScrollPane.setStyle("-fx-background: #1e2329; -fx-background-color: #1e2329;");
            VBox.setVgrow(serialScrollPane, Priority.ALWAYS);

            serialMessageContainer = new VBox(2);
            serialMessageContainer.setPadding(new Insets(5));
            serialMessageContainer.setStyle("-fx-background-color: #1e2329;");
            serialScrollPane.setContent(serialMessageContainer);

            serialMonitorContainer.getChildren().add(serialScrollPane);
        }

        // Create input controls
        if (serialInputContainer == null) {
            serialInputContainer = new HBox(5);
            serialInputContainer.setPadding(new Insets(5, 0, 0, 0));

            serialInputField = new TextField();
            serialInputField.setPromptText("Enter command to send...");
            serialInputField.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-prompt-text-fill: #7f8c8d;");
            HBox.setHgrow(serialInputField, Priority.ALWAYS);

            serialSendButton = new Button("Send");
            serialSendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
            serialSendButton.setOnAction(e -> sendSerialCommand());

            serialClearButton = new Button("Clear");
            serialClearButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            serialClearButton.setOnAction(e -> clearSerialMonitor());

            serialInputContainer.getChildren().addAll(serialInputField, serialSendButton, serialClearButton);
            serialMonitorContainer.getChildren().add(serialInputContainer);
        }

        // Set up keyboard shortcuts
        serialInputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendSerialCommand();
            }
        });

        // Initially disable input controls
        updateSerialMonitorState(false);

        // Auto-scroll to bottom when new messages are added
        serialMessageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> serialScrollPane.setVvalue(1.0));
        });
    }

    private void addSerialMessage(String message) {
        // Determine message type based on content
        MessageType type = MessageType.RECEIVED;
        if (message.startsWith("Sending:") || message.startsWith("TX:")) {
            type = MessageType.SENT;
        } else if (message.contains("Error") || message.contains("Failed")) {
            type = MessageType.ERROR;
        } else if (message.contains("Connected") || message.contains("Disconnected")) {
            type = MessageType.INFO;
        }

        addSerialMessage(type, message);
    }

    private void addSerialMessage(MessageType type, String message) {
        if (message == null || message.trim().isEmpty()) {
            return; // Don't display empty messages
        }

        SerialMessage serialMsg = new SerialMessage(type, message);
        serialMessages.add(serialMsg);

        // Remove old messages if exceeding limit
        while (serialMessages.size() > MAX_SERIAL_MESSAGES) {
            serialMessages.remove(0);
        }

        Platform.runLater(() -> {
            // Create message label
            Label msgLabel = new Label(String.format("[%s] %s: %s",
                    serialMsg.timestamp, type.prefix, serialMsg.message));
            msgLabel.setStyle(String.format("-fx-font-family: 'Courier New', monospace; " +
                    "-fx-font-size: 11px; -fx-text-fill: %s; -fx-wrap-text: true;", type.color));
            msgLabel.setMaxWidth(Double.MAX_VALUE);

            // Add to container
            serialMessageContainer.getChildren().add(msgLabel);

            // Remove old labels if exceeding limit
            while (serialMessageContainer.getChildren().size() > MAX_SERIAL_MESSAGES) {
                serialMessageContainer.getChildren().remove(0);
            }

            // Auto-scroll to bottom
            serialScrollPane.setVvalue(1.0);
        });
    }

    private void sendSerialCommand() {
        String command = serialInputField.getText().trim();
        if (command.isEmpty() || !telemetryManager.isConnected()) {
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
        serialMessages.clear();
        Platform.runLater(() -> {
            serialMessageContainer.getChildren().clear();
            addSerialMessage(MessageType.INFO, "Serial monitor cleared");
        });
    }

    private void updateSerialMonitorState(boolean connected) {
        Platform.runLater(() -> {
            serialInputField.setDisable(!connected);
            serialSendButton.setDisable(!connected);

            if (connected) {
                serialInputField.setPromptText("Enter command to send...");
                addSerialMessage(MessageType.INFO, "Serial monitor connected");
            } else {
                serialInputField.setPromptText("Connect to enable sending...");
                if (!serialMessages.isEmpty()) {
                    addSerialMessage(MessageType.INFO, "Serial monitor disconnected");
                }
            }
        });
    }

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
        pitchLabel.setText("0.0°");
        rollLabel.setText("0.0°");
        yawLabel.setText("0.0°");
    }

    private void updateTelemetryDisplay(TelemetryManager.TelemetryData data) {
        // Battery - separate voltage and percentage
        batteryVoltageLabel.setText(String.format("%.1fV", data.batteryVoltage));
        if (data.batteryPercentage >= 0) {
            batteryStatusLabel.setText(data.batteryPercentage + "%");
        } else {
            batteryStatusLabel.setText("N/A");
        }

        // GPS - separate latitude and longitude
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

        // Distance from home
        distanceLabel.setText(String.format("%.1f m", data.distanceFromHome));

        // Flight status
        flightModeLabel.setText(data.flightMode);
        armStatusLabel.setText(data.isArmed ? "ARMED" : "DISARMED");
        armStatusLabel.setStyle(data.isArmed ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");

        // Attitude - individual labels
        rollLabel.setText(String.format("%.1f°", data.roll));
        pitchLabel.setText(String.format("%.1f°", data.pitch));
        yawLabel.setText(String.format("%.1f°", data.yaw));

        // Environmental
        pressureLabel.setText(String.format("%.1f hPa", data.pressure));
        internalTempLabel.setText(String.format("%.1f°C", data.internalTemp));
        externalTempLabel.setText(String.format("%.1f°C", data.externalTemp));

        // Status message - add to serial monitor if not empty
        if (data.statusMessage != null && !data.statusMessage.trim().isEmpty()) {
            addSerialMessage(MessageType.RECEIVED, "STATUS: " + data.statusMessage);
        }

        // Update mission time with full date/time format
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

        addSerialMessage(MessageType.INFO, "Found " + telemetryComboBox.getItems().size() + " potential LR900 ports");
    }

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
                updateSerialMonitorState(true);
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
            updateSerialMonitorState(false);
        });
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
                    log("Connected to " + selectedSource.getName());
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
                        showAlert("Camera access denied.\n\n" +
                                "Please grant camera permissions in System Preferences.");
                        errorMessage = "Permission denied";
                    } else if (errorMessage.contains("Network sources")) {
                        showAlert("Network sources are not yet implemented.");
                        errorMessage = "Not implemented";
                    } else {
                        showAlert("Failed to connect:\n" + errorMessage);
                    }

                    // Update UI state
                    uiStateManager.updateVideoConnectionError(errorMessage, videoStatus, videoConnectBtn);
                    videoComboBox.setDisable(false);
                    log("Video connection failed: " + errorMessage);
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
        log("Video disconnected");
    }

    private void toggleExternalVideo() {
        if (!isExternalVideoMode) {
            expandVideoToNewWindow();
        } else {
            closeExternalVideo();
        }
    }

    private void expandVideoToNewWindow() {
        if (uiStateManager.isVideoConnected() || videoStage != null) {
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