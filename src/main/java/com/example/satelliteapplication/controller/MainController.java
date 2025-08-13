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
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.example.satelliteapplication.component.Satellite3DViewer;
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

    // ===================================
    // FXML INJECTIONS - ORGANIZED BY FUNCTION
    // ===================================

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
    @FXML private HBox telemetryPanel;
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
    @FXML private HBox errorCodeContainer;

    // 3D Orientation Controls
    @FXML private StackPane satellite3DContainer;
    @FXML private Label orientation3DPlaceholder;
    @FXML private Label pitch3DLabel;
    @FXML private Label roll3DLabel;
    @FXML private Label yaw3DLabel;

    // Serial Monitor Components
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
    private boolean isReceivingTelemetryData = false;
    private Label dataStatusIndicator; // Optional: visual indicator for data status

    // Map Display
    @FXML private VBox mapPanel;
    @FXML private VBox mapPlaceholder;
    @FXML private WebView mapWebView;

    // ===================================
    // SERVICE MANAGERS
    // ===================================
    private TelemetryManager telemetryManager;
    private MapManager mapManager;
    private UIStateManager uiStateManager;
    private VideoCapture videoCapture;
    private Satellite3DViewer satellite3DViewer;

    // ===================================
    // CONSTANTS AND CONFIGURATION
    // ===================================
    private static final String TEAM_ID = "569287";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_SERIAL_MESSAGES = 100;

    // ===================================
    // ERROR CODE MANAGEMENT
    // ===================================
    /**
     * Error Code System Implementation:
     * 
     * 1. Ground Station Communication (Index 0):
     *    - 0 (Green): Communication established
     *    - 1 (Red): Communication not established
     *
     * 2. Payload Position Data (Index 1):
     *    - 0 (Green): Position data obtained
     *    - 1 (Red): Position data cannot be obtained
     * 
     * 3. Payload Pressure Data (Index 2):
     *    - 0 (Green): Pressure data obtained
     *    - 1 (Red): Pressure data cannot be obtained
     * 
     * 4. Reserved Error Code (Index 3):
     *    - 0 (Green): Reserved for future use
     *    - 1 (Red): Reserved for future use
     */
    private static final int ERROR_CODE_LENGTH = 4;
    private final int[] errorCodes = new int[ERROR_CODE_LENGTH];
    
    // Error code indices for better readability
    private static final int GROUND_STATION_COMMUNICATION = 0;
    private static final int PAYLOAD_POSITION_DATA = 1;
    private static final int PAYLOAD_PRESSURE_DATA = 2;
    private static final int RESERVED_ERROR_CODE = 3;

    // ===================================
    // ENUMS AND HELPER CLASSES
    // ===================================

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

    // ===================================
    // INITIALIZATION
    // ===================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeServiceManagers();
        initializeSatellite3DViewer();
        initializeTelemetryCallbacks();
        initializeSerialMonitor();
        initializeVideoImageView();
        setupEventHandlers();
        initializeComboBoxes();
        initializeMap();
        showInitialPlaceholders();
        initializeTelemetryFields();
        initializeStaticFields();
        initializeLogFileButtons();
        initializeErrorCodes();
    }

    private void initializeServiceManagers() {
        telemetryManager = new TelemetryManager();
        mapManager = new MapManager();
        uiStateManager = new UIStateManager();
        videoCapture = new VideoCapture();
    }

    private void initializeTelemetryCallbacks() {
        telemetryManager.setLogCallback(this::handleTelemetryLog);
        telemetryManager.setDataUpdateCallback(this::updateTelemetryDisplay);
        telemetryManager.setDisconnectCallback(this::handleTelemetryDisconnect); // Modified

        // ADD: Set data availability callback
        telemetryManager.setDataAvailabilityCallback(this::handleDataAvailability);
    }

    private void initializeVideoImageView() {
        videoImageView = new ImageView();
        videoImageView.setPreserveRatio(true);
        videoImageView.fitWidthProperty().bind(videoContainer.widthProperty());
        videoImageView.fitHeightProperty().bind(videoContainer.heightProperty());
    }

    private void setupEventHandlers() {
        telemetryRefreshBtn.setOnAction(e -> refreshTelemetrySources());
        telemetryConnectBtn.setOnAction(e -> toggleTelemetryConnection());
        videoRefreshBtn.setOnAction(e -> refreshVideoSources());
        videoConnectBtn.setOnAction(e -> toggleVideoConnection());
        videoExpandBtn.setOnAction(e -> toggleExternalVideo());
    }

    private void initializeComboBoxes() {
        refreshTelemetrySources();
        refreshVideoSources();
    }

    private void initializeMap() {
        mapManager.initialize(mapWebView);
    }

    private void showInitialPlaceholders() {
        uiStateManager.showAllPlaceholders(telemetryPanel, telemetryPlaceholder,
                videoPanel, videoPlaceholder, mapPanel, mapPlaceholder);
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
        altitudeLabel.setText("0.0 m");
        speedLabel.setText("0.0 m/s");
        satellitesLabel.setText("0");
        flightModeLabel.setText("Unknown");
        armStatusLabel.setText("DISARMED");
        pitchLabel.setText("0.0°");
        rollLabel.setText("0.0°");
        yawLabel.setText("0.0°");
        packetCountLabel.setText("0");
    }

    private void initializeStaticFields() {
        teamIdLabel.setText(TEAM_ID);
        satelliteStatusLabel.setText("0");
        updateErrorCodeDisplay();
    }

    private void initializeLogFileButtons() {
        if (openLogViewerBtn != null) {
            openLogViewerBtn.setOnAction(e -> openLogViewer());
        }
        if (downloadLogsBtn != null) {
            downloadLogsBtn.setOnAction(e -> downloadLogsFromSD());
        }
    }

    // ===================================
    // ERROR CODE MANAGEMENT METHODS
    // ===================================

    /**
     * Updates the error code display with color coding
     * 0 = Green (success), 1 = Red (error)
     */
    private void updateErrorCodeDisplay() {
        // Clear existing display
        errorCodeContainer.getChildren().clear();

        // Create individual digit displays
        for (int i = 0; i < ERROR_CODE_LENGTH; i++) {
            Label digitLabel = new Label(String.valueOf(errorCodes[i]));

            // Apply styling for each digit
            digitLabel.setStyle(
                    "-fx-font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;" +
                            "-fx-font-size: 16px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-padding: 4 6;" +
                            "-fx-background-radius: 4;" +
                            "-fx-border-radius: 4;" +
                            "-fx-border-width: 1;" +
                            "-fx-min-width: 20;" +
                            "-fx-alignment: center;" +
                            getDigitStyle(errorCodes[i])
            );

            errorCodeContainer.getChildren().add(digitLabel);
        }
    }

    private String getDigitStyle(int value) {
        if (value == 0) {
            // Green for success
            return "-fx-text-fill: #27ae60;" +
                    "-fx-background-color: #d5f4e6;" +
                    "-fx-border-color: #27ae60;";
        } else {
            // Red for error
            return "-fx-text-fill: #e74c3c;" +
                    "-fx-background-color: #fadbd8;" +
                    "-fx-border-color: #e74c3c;";
        }
    }

    /**
     * Initialize error codes - Updated version
     */
    private void initializeErrorCodes() {
        // Initialize all error codes to 0 (green)
        for (int i = 0; i < ERROR_CODE_LENGTH; i++) {
            errorCodes[i] = 0;
        }

        // Create the error code container if it doesn't exist
        if (errorCodeContainer == null) {
            errorCodeContainer = new HBox(2); // 2px spacing between digits
            errorCodeContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        }

        updateErrorCodeDisplay();
    }

    /**
     * Sets the payload position data error code - FIXED to always show green
     * @param hasPositionData true if position data is obtained, false otherwise
     */
    public void setPayloadPositionDataStatus(boolean hasPositionData) {
        // Always set to 0 (green) for the second digit as requested
        errorCodes[PAYLOAD_POSITION_DATA] = 0; // Always green
        updateErrorCodeDisplay();
    }
    /**
     * Sets the ground station communication error code
     * @param hasCommunication true if communication is established, false otherwise
     */
    public void setGroundStationCommunicationStatus(boolean hasCommunication) {
        errorCodes[GROUND_STATION_COMMUNICATION] = hasCommunication ? 0 : 1;
        updateErrorCodeDisplay();
    }

    /**
     * Sets the payload pressure data error code
     * @param hasPressureData true if pressure data is obtained, false otherwise
     */
    public void setPayloadPressureDataStatus(boolean hasPressureData) {
        errorCodes[PAYLOAD_PRESSURE_DATA] = hasPressureData ? 0 : 1;
        updateErrorCodeDisplay();
    }

    /**
     * Sets the reserved error code (for future use)
     * @param hasReservedData true if reserved data is available, false otherwise
     */
    public void setReservedErrorCodeStatus(boolean hasReservedData) {
        errorCodes[RESERVED_ERROR_CODE] = hasReservedData ? 0 : 1;
        updateErrorCodeDisplay();
    }

    /**
     * Manually test error code display for demonstration purposes
     * This method can be called to test different error code combinations
     */
    public void testErrorCodeDisplay() {
        // Test different error code combinations
        setGroundStationCommunicationStatus(true);   // Green (0)
        setPayloadPositionDataStatus(false);         // Red (1)
        setPayloadPressureDataStatus(true);          // Green (0)
        setReservedErrorCodeStatus(false);           // Red (1)
        
        // This will display: 0101 (Green background for first 0, red for others)
        updateErrorCodeDisplay();
    }

    // ===================================
    // 3D VISUALIZATION METHODS
    // ===================================

    private void initializeSatellite3DViewer() {
        satellite3DViewer = new Satellite3DViewer(230, 200);

        Platform.runLater(() -> {
            satellite3DContainer.getChildren().clear();
            satellite3DContainer.getChildren().add(satellite3DViewer.getSubScene());
        });
    }

    private void updateSatellite3DOrientation(double pitch, double roll, double yaw) {
        if (satellite3DViewer != null) {
            satellite3DViewer.updateOrientation(pitch, roll, yaw);

            Platform.runLater(() -> {
                pitch3DLabel.setText(String.format("%.1f°", pitch));
                roll3DLabel.setText(String.format("%.1f°", roll));
                yaw3DLabel.setText(String.format("%.1f°", yaw));
            });
        }
    }

    // ===================================
    // LOG FILE METHODS
    // ===================================

    private void openLogViewer() {
        Stage currentStage = (Stage) telemetryConnectBtn.getScene().getWindow();
        LogFileController.showLogViewer(currentStage);
    }

    private void downloadLogsFromSD() {
        if (!telemetryManager.isConnected()) {
            showAlert("Not Connected", "Please connect to telemetry first to download logs from SD card.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory to Save Logs");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File selectedDirectory = directoryChooser.showDialog(downloadLogsBtn.getScene().getWindow());
        if (selectedDirectory != null) {
            downloadLogsToDirectory(selectedDirectory);
        }
    }

    private void downloadLogsToDirectory(File directory) {
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Downloading Logs");
        progressAlert.setHeaderText("Downloading logs from SD card...");
        progressAlert.setContentText("This may take a few minutes.");
        progressAlert.getButtonTypes().clear();
        progressAlert.show();

        new Thread(() -> {
            try {
                Thread.sleep(2000);

                Platform.runLater(() -> {
                    progressAlert.close();
                    showAlert("Download Complete", "Logs have been downloaded to:\n" + directory.getAbsolutePath());
                    askToOpenDownloadedLogs(directory);
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    progressAlert.close();
                    showAlert("Download interrupted", "The download process was interrupted.");
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
            }
        });
    }

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

    // ===================================
    // SERIAL MONITOR METHODS
    // ===================================

    private void initializeSerialMonitor() {
        serialSendButton.setOnAction(e -> sendSerialCommand());
        serialClearButton.setOnAction(e -> clearSerialMonitor());

        serialInputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendSerialCommand();
            }
        });

        serialMessageContainer.heightProperty().addListener(
                (obs, oldVal, newVal) -> Platform.runLater(() -> serialScrollPane.setVvalue(1.0))
        );

        setSerialMonitorEnabled(false);
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
            String timestamp = LocalTime.now().format(TIME_FORMATTER);

            Label msgLabel = new Label(String.format("[%s] %s: %s",
                    timestamp, type.prefix, message));
            msgLabel.getStyleClass().add(type.styleClass);
            msgLabel.setMaxWidth(Double.MAX_VALUE);
            msgLabel.setWrapText(true);

            serialMessageContainer.getChildren().add(msgLabel);

            while (serialMessageContainer.getChildren().size() > MAX_SERIAL_MESSAGES) {
                serialMessageContainer.getChildren().remove(0);
            }

            serialScrollPane.setVvalue(1.0);
        });
    }

    private void handleTelemetryLog(String message) {
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
                if (!serialInputField.getText().isEmpty()) {
                    serialInputField.clear();
                }
            }
        });
    }

    // ===================================
    // TELEMETRY METHODS
    // ===================================

    private void updateTelemetryDisplay(TelemetryManager.TelemetryData data) {
        // Add visual indicator if data is stale
        String staleSuffix = data.isStale ? " (old)" : "";

        updateBatteryData(data, staleSuffix);
        updateGpsData(data, staleSuffix);
        updateFlightData(data, staleSuffix);
        updateAttitudeData(data);
        updateEnvironmentalData(data, staleSuffix);
        updatePacketData(data);
        updateMissionTime();
        updateMapPosition(data);

        // Update error codes based on data availability
        updateErrorCodesFromData(data);
    }

    private void updateBatteryData(TelemetryManager.TelemetryData data, String staleSuffix) {
        batteryVoltageLabel.setText(String.format("%.1fV%s", data.batteryVoltage, staleSuffix));
        if (data.batteryPercentage >= 0) {
            batteryStatusLabel.setText(data.batteryPercentage + "%" + staleSuffix);
        } else {
            batteryStatusLabel.setText("N/A");
        }

        // Change color if data is stale
        if (data.isStale) {
            batteryVoltageLabel.setStyle("-fx-text-fill: #7f8c8d;"); // Gray for stale
            batteryStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");
        } else {
            batteryVoltageLabel.setStyle("-fx-text-fill: #27ae60;"); // Normal color
            batteryStatusLabel.setStyle("-fx-text-fill: #27ae60;");
        }
    }

    private void updateGpsData(TelemetryManager.TelemetryData data, String staleSuffix) {
        if (data.hasGpsFix) {
            gpsLatitudeLabel.setText(String.format("%.6f%s", data.latitude, staleSuffix));
            gpsLongitudeLabel.setText(String.format("%.6f%s", data.longitude, staleSuffix));
            satellitesLabel.setText(data.satellites + staleSuffix);
            gpsAltitudeLabel.setText(String.format("%.1f m%s", data.gpsAltitude, staleSuffix));
        } else {
            gpsLatitudeLabel.setText("No Fix" + staleSuffix);
            gpsLongitudeLabel.setText("No Fix" + staleSuffix);
            satellitesLabel.setText(data.satellites + " (no fix)" + staleSuffix);
            gpsAltitudeLabel.setText("No GPS" + staleSuffix);
        }

        // Apply stale styling
        if (data.isStale) {
            gpsLatitudeLabel.setStyle("-fx-text-fill: #7f8c8d;");
            gpsLongitudeLabel.setStyle("-fx-text-fill: #7f8c8d;");
            satellitesLabel.setStyle("-fx-text-fill: #7f8c8d;");
            gpsAltitudeLabel.setStyle("-fx-text-fill: #7f8c8d;");
        } else {
            // Reset to normal colors
            gpsLatitudeLabel.setStyle("");
            gpsLongitudeLabel.setStyle("");
            satellitesLabel.setStyle("");
            gpsAltitudeLabel.setStyle("");
        }
    }

    private void updateFlightData(TelemetryManager.TelemetryData data, String staleSuffix) {
        altitudeLabel.setText(String.format("%.1f m", data.altitude));
        speedLabel.setText(String.format("%.1f m/s", data.groundSpeed));
        verticalVelocityLabel.setText(String.format("%.2f m/s", data.verticalVelocity));
        distanceLabel.setText(String.format("%.1f m", data.distanceFromHome));
        flightModeLabel.setText(data.flightMode);
        
        armStatusLabel.setText(data.isArmed ? "ARMED" : "DISARMED");
        armStatusLabel.setTextFill(data.isArmed ? Color.web("#e74c3c") : Color.web("#27ae60"));
    }

    private void updateAttitudeData(TelemetryManager.TelemetryData data) {
        rollLabel.setText(String.format("%.1f°", data.roll));
        pitchLabel.setText(String.format("%.1f°", data.pitch));
        yawLabel.setText(String.format("%.1f°", data.yaw));
        updateSatellite3DOrientation(data.pitch, data.roll, data.yaw);
    }

    private void updateEnvironmentalData(TelemetryManager.TelemetryData data, String staleSuffix) {
        pressureLabel.setText(String.format("%.1f hPa%s", data.pressure, staleSuffix));
        internalTempLabel.setText(String.format("%.1f°C%s", data.internalTemp, staleSuffix));
        externalTempLabel.setText(String.format("%.1f°C%s", data.externalTemp, staleSuffix));

        if (data.isStale) {
            pressureLabel.setStyle("-fx-text-fill: #7f8c8d;");
            internalTempLabel.setStyle("-fx-text-fill: #7f8c8d;");
            externalTempLabel.setStyle("-fx-text-fill: #7f8c8d;");
        } else {
            pressureLabel.setStyle("");
            internalTempLabel.setStyle("");
            externalTempLabel.setStyle("");
        }
    }

    private void updatePacketData(TelemetryManager.TelemetryData data) {
        packetCountLabel.setText(String.format("%d (seq: %d)", data.totalPackets, data.currentSequence));
    }

    private void updateErrorCodesFromData(TelemetryManager.TelemetryData data) {
        // 1. Ground station communication status
        // If communication is established, error code should be 0 (green)
        // If communication is not established, error code should be 1 (red)
        setGroundStationCommunicationStatus(telemetryManager.isConnected());
        
        // 2. Payload position data status (currently set to green/0)
        // If payload position data cannot be obtained, error code should be 1 (red)
        // If payload position data is obtained, error code should be 0 (green)
        setPayloadPositionDataStatus(data.hasValidPosition());
        
        // 3. Payload pressure data status
        // If payload pressure data cannot be obtained, error code should be 1 (red)
        // If payload pressure data is obtained, error code should be 0 (green)
        setPayloadPressureDataStatus(data.pressure > 0);
        
        // 4. Reserved error code (for future use)
        setReservedErrorCodeStatus(true); // Set to true for now
    }

    private void updateMissionTime() {
        LocalDateTime now = LocalDateTime.now();
        missionTimeLabel.setText(now.format(DATE_TIME_FORMATTER));
    }

    private void updateMapPosition(TelemetryManager.TelemetryData data) {
        if (data.hasValidPosition() && mapManager.isInitialized()) {
            mapManager.updatePosition(data.latitude, data.longitude);
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
            showAlert("Port Selection Required", "Please select a telemetry port");
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
            showAlert("Connection Failed", "Failed to open port: " + selected.port.getSystemPortName());
        }
    }

    private void disconnectTelemetry() {
        telemetryManager.disconnect();

        Platform.runLater(() -> {
            uiStateManager.updateTelemetryConnectionUI(false, telemetryStatus,
                    telemetryConnectBtn, telemetryComboBox);

            // DON'T hide telemetry panel - keep showing last values
            // Just update the status
            if (telemetryStatus != null) {
                telemetryStatus.setText("● Disconnected (Last Values)");
            }

            setSerialMonitorEnabled(false);
            addSerialMessage(MessageType.INFO, "Telemetry disconnected - keeping last known values");

            // Don't reset 3D viewer - keep last orientation
            // if (satellite3DViewer != null) {
            //     satellite3DViewer.reset();
            // }
        });
    }

    // ===================================
    // VIDEO METHODS
    // ===================================

    private void refreshVideoSources() {
        List<VideoSource> sources = new ArrayList<>();

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
            showAlert("Video Source Required", "Please select a video source");
            return;
        }

        uiStateManager.updateVideoConnectingUI(videoStatus, videoConnectBtn);
        videoComboBox.setDisable(true);

        new Thread(() -> {
            try {
                videoCapture.startCapture(selectedSource, videoImageView);

                Platform.runLater(() -> {
                    uiStateManager.updateVideoConnectionUI(true, videoStatus, videoConnectBtn, videoExpandBtn);
                    videoContainer.getChildren().clear();
                    videoContainer.getChildren().add(videoImageView);
                    updateContentDisplay();
                    addSerialMessage(MessageType.INFO, "Video connected: " + selectedSource.getName());
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String errorMessage = e.getMessage();
                    if (errorMessage == null) {
                        errorMessage = "Unknown error";
                    } else if (errorMessage.contains("not available")) {
                        showAlert("Camera Not Available", "Camera " + selectedSource.getName() + " is not available.\n\nPlease try a different camera index.");
                        errorMessage = "Camera not available";
                    } else if (errorMessage.contains("permission")) {
                        showAlert("Camera Permission Denied", "Camera access denied.\n\nPlease grant camera permissions in System Preferences.");
                        errorMessage = "Permission denied";
                    } else {
                        showAlert("Video Connection Failed", "Failed to connect:\n" + errorMessage);
                    }

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

        externalVideoImageView = new ImageView();
        externalVideoImageView.setPreserveRatio(true);

        StackPane videoPane = new StackPane(externalVideoImageView);
        videoPane.setStyle("-fx-background-color: black;");

        videoStage = new Stage();
        videoStage.setTitle("NazarX Video Feed - External Display");

        List<Screen> screens = Screen.getScreens();

        Scene scene = new Scene(videoPane, 1280, 720);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm());
        videoStage.setScene(scene);

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

        externalVideoImageView.fitWidthProperty().bind(videoPane.widthProperty());
        externalVideoImageView.fitHeightProperty().bind(videoPane.heightProperty());

        videoCapture.setExternalImageView(externalVideoImageView);
        videoCapture.setPrimaryImageView(null);

        videoContainer.getChildren().clear();
        Label externalModeLabel = new Label("Video is displayed in external window");
        externalModeLabel.getStyleClass().add("info-label");
        videoContainer.getChildren().add(externalModeLabel);

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

    // ===================================
    // UTILITY METHODS
    // ===================================

    private void handleDataAvailability(boolean isAvailable) {
        isReceivingTelemetryData = isAvailable;

        Platform.runLater(() -> {
            if (isAvailable) {
                // Data is flowing
                addSerialMessage(MessageType.INFO, "Telemetry data stream active");

                // Update error codes to show communication is good
                setGroundStationCommunicationStatus(true);

                // Optional: Update visual indicator
                if (telemetryStatus != null) {
                    telemetryStatus.setText("● Connected (Live)");
                    telemetryStatus.getStyleClass().clear();
                    telemetryStatus.getStyleClass().add("status-connected");
                }
            } else {
                // Data not flowing but connection still active
                addSerialMessage(MessageType.WARNING, "No telemetry data - displaying last known values");

                // Update error codes to show communication issue
                setGroundStationCommunicationStatus(false);

                // Optional: Update visual indicator
                if (telemetryStatus != null) {
                    telemetryStatus.setText("● Connected (No Data)");
                    telemetryStatus.getStyleClass().clear();
                    telemetryStatus.getStyleClass().add("status-connecting"); // Orange color
                }
            }
        });
    }

    private void handleTelemetryDisconnect() {
        // This is called when telemetry has issues
        // Don't auto-disconnect, just notify user
        Platform.runLater(() -> {
            addSerialMessage(MessageType.WARNING, "Telemetry connection issue detected");
            // Don't call disconnectTelemetry() automatically
        });
    }

    private void updateContentDisplay() {
        // Modified to always show telemetry panel if connected, regardless of data availability
        boolean showTelemetry = uiStateManager.isTelemetryConnected();

        if (showTelemetry) {
            telemetryPanel.setVisible(true);
            telemetryPanel.setManaged(true);
            telemetryPlaceholder.setVisible(false);
            telemetryPlaceholder.setManaged(false);

            // Also show map when telemetry is connected
            mapPanel.setVisible(true);
            mapPanel.setManaged(true);
            mapPlaceholder.setVisible(false);
            mapPlaceholder.setManaged(false);

            if (uiStateManager.isTelemetryConnected()) {
                mapManager.forceResize();
            }
        } else {
            telemetryPanel.setVisible(false);
            telemetryPanel.setManaged(false);
            telemetryPlaceholder.setVisible(true);
            telemetryPlaceholder.setManaged(true);

            // Hide map when telemetry is disconnected
            mapPanel.setVisible(false);
            mapPanel.setManaged(false);
            mapPlaceholder.setVisible(true);
            mapPlaceholder.setManaged(true);
        }

        // Video panel management remains the same
        if (uiStateManager.isVideoConnected()) {
            videoPanel.setVisible(true);
            videoPanel.setManaged(true);
            videoPlaceholder.setVisible(false);
            videoPlaceholder.setManaged(false);
        } else {
            videoPanel.setVisible(false);
            videoPanel.setManaged(false);
            videoPlaceholder.setVisible(true);
            videoPlaceholder.setManaged(true);
        }
    }

    private enum MessageType {
        RECEIVED("RX", "serial-message-rx"),
        SENT("TX", "serial-message-tx"),
        ERROR("ERR", "serial-message-error"),
        INFO("INFO", "serial-message-info"),
        WARNING("WARN", "serial-message-warning"); // ADD THIS

        private final String prefix;
        private final String styleClass;

        MessageType(String prefix, String styleClass) {
            this.prefix = prefix;
            this.styleClass = styleClass;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
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