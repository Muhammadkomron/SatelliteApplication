package com.example.satelliteapplication.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import com.example.satelliteapplication.service.VideoCapture;
import com.example.satelliteapplication.service.VideoCapture.VideoSource;

import com.fazecast.jSerialComm.SerialPort;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.*;
import io.dronefleet.mavlink.minimal.Heartbeat;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController implements Initializable {

    // Connection Controls
    @FXML private ComboBox<SerialPortInfo> telemetryComboBox;
    @FXML private ComboBox<VideoSource> videoComboBox;
    @FXML private Button telemetryRefreshBtn;
    @FXML private Button telemetryConnectBtn;
    @FXML private Button videoRefreshBtn;
    @FXML private Button videoConnectBtn;
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

    // Video Display
    @FXML private VBox videoPanel;
    @FXML private VBox videoPlaceholder;
    @FXML private StackPane videoContainer;
    private ImageView videoImageView;

    // Map Display
    @FXML private VBox mapPanel;
    @FXML private VBox mapPlaceholder;
    @FXML private WebView mapWebView;
    @FXML private Button clearPathBtn;
    @FXML private Button centerMapBtn;

    // Connection states
    private boolean telemetryConnected = false;
    private boolean videoConnected = false;

    // MAVLink components
    private SerialPort serialPort;
    private MavlinkConnection mavlinkConnection;
    private Thread readThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Video capture
    private VideoCapture videoCapture;

    // Map data
    private WebEngine webEngine;
    private double currentLat = 41.2995;
    private double currentLon = 69.2401;
    private boolean mapInitialized = false;
    private boolean hasValidGpsPosition = false;

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
        // Initialize video capture
        videoCapture = new VideoCapture();

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
        clearPathBtn.setOnAction(e -> clearPath());
        centerMapBtn.setOnAction(e -> centerMap());

        // Initialize combo boxes
        refreshTelemetrySources();
        refreshVideoSources();

        // Initialize map
        initializeMap();

        // Initially show placeholders
        showAllPlaceholders();
    }

    private void initializeMap() {
        webEngine = mapWebView.getEngine();

        // Enable JavaScript
        webEngine.setJavaScriptEnabled(true);

        // Set up console logging for debugging
        webEngine.setOnAlert(event -> {
            System.out.println("JavaScript alert: " + event.getData());
        });

        // Add error handler
        webEngine.setOnError(event -> {
            System.err.println("WebEngine error: " + event.getMessage());
        });

        // Load the map HTML
        webEngine.loadContent(generateMapHTML());

        // Add load listener
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Map loaded successfully");

                // Delay initialization to ensure map tiles load
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        Platform.runLater(() -> {
                            mapInitialized = true;
                            initializeMapFunctions();
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            } else if (newState == Worker.State.FAILED) {
                System.err.println("Failed to load map");
                Throwable exception = webEngine.getLoadWorker().getException();
                if (exception != null) {
                    exception.printStackTrace();
                }
            }
        });
    }

    private void initializeMapFunctions() {
        try {
            // Force a resize first
            webEngine.executeScript("if (window.map) { window.map.invalidateSize(true); }");

            // Wait a bit for tiles to load
            Thread.sleep(500);

            // Test if map is initialized
            Object result = webEngine.executeScript("typeof window.map !== 'undefined'");
            if (Boolean.TRUE.equals(result)) {
                System.out.println("Map JavaScript objects initialized");

                // Force another resize and center
                webEngine.executeScript("window.map.invalidateSize(true);");
                webEngine.executeScript("window.map.setView([41.2995, 69.2401], 15);");

                // Clear any cached tiles
                webEngine.executeScript("if (window.map._container) { window.map._container.style.background = '#f0f0f0'; }");

            } else {
                System.err.println("Map JavaScript objects not initialized");
                mapInitialized = false;
            }
        } catch (Exception e) {
            System.err.println("Error initializing map functions: " + e.getMessage());
            mapInitialized = false;
        }
    }

    private String generateMapHTML() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Satellite Map</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.7.1/dist/leaflet.css" />
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                    }
                    #map {
                        width: 100%;
                        height: 100vh;
                        position: relative;
                    }
                    .leaflet-container {
                        background: #ddd;
                    }
                    .satellite-marker {
                        background-color: #e74c3c;
                        border: 3px solid white;
                        border-radius: 50%;
                        width: 16px;
                        height: 16px;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.4);
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script src="https://unpkg.com/leaflet@1.7.1/dist/leaflet.js"></script>
                <script>
                    var map = null;
                    var marker = null;
                    var pathLine = null;
                    var pathCoordinates = [];
                    
                    function initializeMap() {
                        try {
                            // Create map instance
                            map = L.map('map', {
                                center: [41.2995, 69.2401],
                                zoom: 13,
                                preferCanvas: false,
                                zoomControl: true
                            });
                            
                            // Add tile layer
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                attribution: 'OpenStreetMap',
                                maxZoom: 18,
                                tileSize: 256,
                                zoomOffset: 0
                            }).addTo(map);
                            
                            // Create custom icon
                            var satelliteIcon = L.divIcon({
                                className: 'satellite-marker',
                                iconSize: [16, 16],
                                iconAnchor: [8, 8]
                            });
                            
                            // Add marker
                            marker = L.marker([41.2995, 69.2401], {
                                icon: satelliteIcon
                            }).addTo(map);
                            
                            // Add path line
                            pathLine = L.polyline([], {
                                color: '#3498db',
                                weight: 4,
                                opacity: 0.7
                            }).addTo(map);
                            
                            // Force resize after a delay
                            setTimeout(function() {
                                map.invalidateSize();
                            }, 250);
                            
                            console.log('Map initialized successfully');
                            return true;
                        } catch (error) {
                            console.error('Error initializing map:', error);
                            return false;
                        }
                    }
                    
                    // Update marker position
                    function updatePosition(lat, lon) {
                        if (!map || !marker || !pathLine) {
                            console.error('Map not initialized');
                            return;
                        }
                        
                        try {
                            var newLatLng = L.latLng(lat, lon);
                            
                            // Update marker position
                            marker.setLatLng(newLatLng);
                            
                            // Add to path
                            pathCoordinates.push([lat, lon]);
                            pathLine.setLatLngs(pathCoordinates);
                            
                            // Pan to marker if out of view
                            if (!map.getBounds().contains(newLatLng)) {
                                map.panTo(newLatLng);
                            }
                        } catch (error) {
                            console.error('Error updating position:', error);
                        }
                    }
                    
                    // Clear the path
                    function clearPath() {
                        if (pathLine) {
                            pathCoordinates = [];
                            pathLine.setLatLngs([]);
                        }
                    }
                    
                    // Center map on marker
                    function centerMap() {
                        if (map && marker) {
                            map.setView(marker.getLatLng(), map.getZoom());
                        }
                    }
                    
                    // Handle window resize
                    window.addEventListener('resize', function() {
                        if (map) {
                            map.invalidateSize();
                        }
                    });
                    
                    // Initialize map when page loads
                    window.addEventListener('load', function() {
                        initializeMap();
                    });
                    
                    // Also try to initialize immediately
                    if (document.readyState === 'complete') {
                        initializeMap();
                    }
                </script>
            </body>
            </html>
            """;
    }

    private void refreshTelemetrySources() {
        telemetryComboBox.getItems().clear();
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            // Look for CP2102 or similar descriptors that might indicate the LR900
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
        // Create list of available video sources without detection
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
        if (!telemetryConnected) {
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

        serialPort = selected.port;

        // Configure for LR900 defaults
        serialPort.setBaudRate(57600); // Default for USB interface
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        // Increase timeout and add write timeout for LR900
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 1000);

        if (!serialPort.openPort()) {
            showAlert("Failed to open port: " + serialPort.getSystemPortName());
            return;
        }

        // Add a small delay to let the port stabilize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        mavlinkConnection = MavlinkConnection.create(
                serialPort.getInputStream(),
                serialPort.getOutputStream()
        );

        isRunning.set(true);

        // Start reading thread
        readThread = new Thread(this::readMavlinkMessages);
        readThread.setDaemon(true);
        readThread.start();

        // Update UI
        Platform.runLater(() -> {
            telemetryConnected = true;
            telemetryStatus.setText("● Connected");
            telemetryStatus.getStyleClass().clear();
            telemetryStatus.getStyleClass().add("status-connected");
            telemetryConnectBtn.setText("Disconnect");
            telemetryConnectBtn.getStyleClass().remove("connect-button");
            telemetryConnectBtn.getStyleClass().add("disconnect-button");
            telemetryComboBox.setDisable(true);

            // Update display
            updateContentDisplay();
        });

        log("Connected to " + serialPort.getSystemPortName() + " at 57600 baud");
        log("Waiting for MAVLink data... (Make sure the remote LR900 is connected to a flight controller)");

        // Start requesting data streams after 2 seconds
        scheduler.schedule(this::requestDataStreams, 2, TimeUnit.SECONDS);

        // Request streams periodically every 5 seconds
        scheduler.scheduleAtFixedRate(this::requestDataStreams, 2, 5, TimeUnit.SECONDS);
    }

    private void requestDataStreams() {
        if (!isRunning.get() || mavlinkConnection == null) {
            return;
        }

        log("Requesting telemetry streams from flight controller...");

        try {
            // Use the modern approach: request specific message intervals
            // Message IDs for common telemetry
            int[] messageIds = {
                    1,    // SYS_STATUS (battery, etc)
                    24,   // GPS_RAW_INT
                    30,   // ATTITUDE
                    33,   // GLOBAL_POSITION_INT
                    74,   // VFR_HUD
                    42,   // MISSION_CURRENT
                    77,   // COMMAND_ACK
                    147,  // BATTERY_STATUS
                    241,  // VIBRATION
                    242   // HOME_POSITION
            };

            // Request each message at 2Hz (500000 microseconds interval)
            for (int msgId : messageIds) {
                CommandLong cmd = CommandLong.builder()
                        .targetSystem(1)
                        .targetComponent(1)
                        .command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL)
                        .confirmation(0)
                        .param1(msgId)          // Message ID
                        .param2(500000)         // Interval in microseconds (2Hz = 500000)
                        .param3(0)
                        .param4(0)
                        .param5(0)
                        .param6(0)
                        .param7(0)
                        .build();

                mavlinkConnection.send1(255, 0, cmd);
            }

            // Alternative: Try requesting a single parameter to trigger streams
            // Some autopilots start streaming when they receive any parameter request
            ParamRequestRead paramRequest = ParamRequestRead.builder()
                    .targetSystem(1)
                    .targetComponent(1)
                    .paramId("SYSID_THISMAV")  // A common parameter that should exist
                    .paramIndex(-1)  // Use parameter ID, not index
                    .build();

            mavlinkConnection.send1(255, 0, paramRequest);

        } catch (IOException e) {
            log("Failed to request data streams: " + e.getMessage());
        }
    }

    private void disconnectTelemetry() {
        isRunning.set(false);

        if (readThread != null) {
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }

        Platform.runLater(() -> {
            telemetryConnected = false;
            telemetryStatus.setText("● Disconnected");
            telemetryStatus.getStyleClass().clear();
            telemetryStatus.getStyleClass().add("status-disconnected");
            telemetryConnectBtn.setText("Connect");
            telemetryConnectBtn.getStyleClass().remove("disconnect-button");
            telemetryConnectBtn.getStyleClass().add("connect-button");
            telemetryComboBox.setDisable(false);

            // Update content display
            updateContentDisplay();
        });

        log("Disconnected");
    }

    private void readMavlinkMessages() {
        log("Starting MAVLink message reader thread...");
        int timeoutCount = 0;
        final int MAX_TIMEOUTS = 5;

        while (isRunning.get()) {
            try {
                MavlinkMessage<?> message = mavlinkConnection.next();
                if (message != null) {
                    timeoutCount = 0; // Reset timeout counter on successful read
                    handleMavlinkMessage(message);
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("timed out")) {
                        timeoutCount++;
                        if (timeoutCount >= MAX_TIMEOUTS) {
                            Platform.runLater(() -> {
                                log("No MAVLink data received after " + MAX_TIMEOUTS + " timeouts. Check:");
                                log("1. Remote LR900 is powered and connected to flight controller");
                                log("2. Flight controller is sending telemetry data");
                                log("3. Both LR900 radios are properly paired");
                                log("4. Correct baud rate (try 57600 or 115200)");
                                disconnectTelemetry();
                            });
                            break;
                        } else {
                            int finalTimeoutCount = timeoutCount;
                            Platform.runLater(() -> log("Timeout " + finalTimeoutCount + "/" + MAX_TIMEOUTS + " - still waiting for data..."));
                        }
                    } else {
                        Platform.runLater(() -> {
                            log("Read error: " + errorMsg);
                            disconnectTelemetry();
                        });
                        break;
                    }
                }
            }
        }
        log("MAVLink reader thread stopped");
    }

    private void handleMavlinkMessage(MavlinkMessage<?> message) {
        Object payload = message.getPayload();

        Platform.runLater(() -> {
            if (payload instanceof Heartbeat hb) {
                String mode = "Mode " + hb.customMode();
                flightModeLabel.setText(mode);

                boolean armed = (hb.baseMode().value() & 128) != 0;
                armStatusLabel.setText(armed ? "ARMED" : "DISARMED");
                armStatusLabel.setStyle(armed ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");

            } else if (payload instanceof SysStatus sys) {
                double voltage = sys.voltageBattery() / 1000.0;
                int percentage = sys.batteryRemaining();
                if (percentage == -1) {
                    batteryLabel.setText(String.format("%.1fV", voltage));
                } else {
                    batteryLabel.setText(String.format("%.1fV (%d%%)", voltage, percentage));
                }

            } else if (payload instanceof GpsRawInt gps) {
                double lat = gps.lat() / 1e7;
                double lon = gps.lon() / 1e7;
                if (gps.fixType().value() >= 2) {  // 2D fix or better
                    gpsLabel.setText(String.format("%.6f, %.6f", lat, lon));
                    satellitesLabel.setText(String.valueOf(gps.satellitesVisible()));

                    // Update current position and map
                    if (lat != 0 && lon != 0) {
                        currentLat = lat;
                        currentLon = lon;
                        hasValidGpsPosition = true;

                        // Update map position
                        if (mapInitialized) {
                            updateMapPosition(currentLat, currentLon);
                        }
                    }
                } else {
                    gpsLabel.setText("No Fix");
                    satellitesLabel.setText(gps.satellitesVisible() + " (no fix)");
                }

            } else if (payload instanceof GlobalPositionInt pos) {
                double alt = pos.relativeAlt() / 1000.0;
                altitudeLabel.setText(String.format("%.1f m", alt));

                // Also update GPS position from this message if valid
                if (!hasValidGpsPosition) {
                    double lat = pos.lat() / 1e7;
                    double lon = pos.lon() / 1e7;
                    if (lat != 0 && lon != 0) {
                        currentLat = lat;
                        currentLon = lon;
                        hasValidGpsPosition = true;
                        gpsLabel.setText(String.format("%.6f, %.6f", lat, lon));

                        // Update map position
                        if (mapInitialized) {
                            updateMapPosition(currentLat, currentLon);
                        }
                    }
                }

            } else if (payload instanceof VfrHud hud) {
                speedLabel.setText(String.format("%.1f m/s", hud.groundspeed()));
                if (hud.alt() > 0) {  // Use VFR HUD altitude if available
                    altitudeLabel.setText(String.format("%.1f m", hud.alt()));
                }

            } else if (payload instanceof Attitude att) {
                double roll = Math.toDegrees(att.roll());
                double pitch = Math.toDegrees(att.pitch());
                double yaw = Math.toDegrees(att.yaw());
                rollPitchYawLabel.setText(String.format("R:%.0f° P:%.0f° Y:%.0f°", roll, pitch, yaw));
            }

            // Log the message
            String msgType = payload.getClass().getSimpleName();
            log("Received: " + msgType + " from Sys:" + message.getOriginSystemId());
        });
    }

    private void log(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + message);
        // TODO: If you have a log text area in your UI, you can also append to it here
    }

    private void toggleVideoConnection() {
        if (!videoConnected) {
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

        // Update UI
        videoStatus.setText("● Connecting...");
        videoStatus.getStyleClass().clear();
        videoStatus.getStyleClass().add("status-connecting");
        videoConnectBtn.setDisable(true);

        // Connect in background thread
        new Thread(() -> {
            try {
                // Only process USB cameras for now
                if (selectedSource.getType() == VideoCapture.VideoSourceType.USB_CAMERA) {
                    videoCapture.startCapture(selectedSource, videoImageView);

                    Platform.runLater(() -> {
                        videoConnected = true;
                        videoStatus.setText("● Connected");
                        videoStatus.getStyleClass().clear();
                        videoStatus.getStyleClass().add("status-connected");
                        videoConnectBtn.setText("Disconnect");
                        videoConnectBtn.getStyleClass().remove("connect-button");
                        videoConnectBtn.getStyleClass().add("disconnect-button");
                        videoConnectBtn.setDisable(false);

                        // Clear existing content and add video view
                        videoContainer.getChildren().clear();
                        videoContainer.getChildren().add(videoImageView);

                        // Update content display
                        updateContentDisplay();
                    });
                } else {
                    // Network sources not implemented yet
                    Platform.runLater(() -> {
                        videoStatus.setText("● Network sources not implemented");
                        videoStatus.getStyleClass().clear();
                        videoStatus.getStyleClass().add("status-disconnected");
                        videoConnectBtn.setDisable(false);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    videoStatus.setText("● Failed to connect to camera");
                    videoStatus.getStyleClass().clear();
                    videoStatus.getStyleClass().add("status-disconnected");
                    videoConnectBtn.setDisable(false);
                });
            }
        }).start();
    }

    private void disconnectVideo() {
        videoCapture.stopCapture();

        videoConnected = false;
        videoStatus.setText("● Disconnected");
        videoStatus.getStyleClass().clear();
        videoStatus.getStyleClass().add("status-disconnected");
        videoConnectBtn.setText("Connect");
        videoConnectBtn.getStyleClass().remove("disconnect-button");
        videoConnectBtn.getStyleClass().add("connect-button");

        // Clear video container
        videoContainer.getChildren().clear();
        Label placeholder = new Label("Video feed disconnected");
        placeholder.getStyleClass().add("info-label");
        videoContainer.getChildren().add(placeholder);

        // Update content display
        updateContentDisplay();
    }

    private void updateContentDisplay() {
        // Telemetry panel
        if (telemetryConnected) {
            telemetryPanel.setVisible(true);
            telemetryPanel.setManaged(true);
            telemetryPlaceholder.setVisible(false);
            telemetryPlaceholder.setManaged(false);

            // Also show map when telemetry is connected
            mapPanel.setVisible(true);
            mapPanel.setManaged(true);
            mapPlaceholder.setVisible(false);
            mapPlaceholder.setManaged(false);

            // Force map resize after showing with multiple attempts
            Platform.runLater(() -> {
                if (webEngine != null) {
                    // First resize
                    webEngine.executeScript("if (window.map) { window.map.invalidateSize(true); }");

                    // Second resize after delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            Platform.runLater(() -> {
                                webEngine.executeScript("if (window.map) { window.map.invalidateSize(true); window.map.setView([41.2995, 69.2401], 15); }");
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
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

        // Video panel
        if (videoConnected) {
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

    private void showAllPlaceholders() {
        telemetryPanel.setVisible(false);
        telemetryPanel.setManaged(false);
        telemetryPlaceholder.setVisible(true);
        telemetryPlaceholder.setManaged(true);

        videoPanel.setVisible(false);
        videoPanel.setManaged(false);
        videoPlaceholder.setVisible(true);
        videoPlaceholder.setManaged(true);

        mapPanel.setVisible(false);
        mapPanel.setManaged(false);
        mapPlaceholder.setVisible(true);
        mapPlaceholder.setManaged(true);
    }

    private void updateMapPosition(double lat, double lon) {
        if (webEngine != null && mapInitialized) {
            try {
                String script = String.format("updatePosition(%f, %f)", lat, lon);
                webEngine.executeScript(script);
            } catch (Exception e) {
                System.err.println("Error updating map position: " + e.getMessage());
            }
        }
    }

    private void clearPath() {
        if (webEngine != null && mapInitialized) {
            try {
                webEngine.executeScript("clearPath()");
            } catch (Exception e) {
                System.err.println("Error clearing path: " + e.getMessage());
            }
        }
    }

    private void centerMap() {
        if (webEngine != null && mapInitialized) {
            try {
                webEngine.executeScript("centerMap()");
            } catch (Exception e) {
                System.err.println("Error centering map: " + e.getMessage());
            }
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
        scheduler.shutdownNow();
        isRunning.set(false);

        if (readThread != null) {
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }

        if (videoCapture != null) {
            videoCapture.stopCapture();
        }
    }
}