package com.example.satelliteapplication.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import com.example.satelliteapplication.service.VideoCapture;
import com.example.satelliteapplication.service.VideoCapture.VideoSource;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class MainController implements Initializable {

    // Connection Controls
    @FXML private ComboBox<String> telemetryComboBox;
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

    // Timer for telemetry updates
    private Timer telemetryTimer;

    // Video capture
    private VideoCapture videoCapture;

    // Map data
    private WebEngine webEngine;
    private double currentLat = 41.2995;
    private double currentLon = 69.2401;
    private List<double[]> pathPoints = new ArrayList<>();
    private boolean mapInitialized = false;

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
        ObservableList<String> telemetrySources = FXCollections.observableArrayList();

        // TODO: Add actual telemetry source detection
        // For now, add some example sources
        telemetrySources.add("MAVLink - COM3");
        telemetrySources.add("MAVLink - COM4");
        telemetrySources.add("TCP - 127.0.0.1:14550");
        telemetrySources.add("UDP - 0.0.0.0:14550");

        telemetryComboBox.setItems(telemetrySources);
        if (!telemetrySources.isEmpty()) {
            telemetryComboBox.getSelectionModel().selectFirst();
        }
    }

    private void refreshVideoSources() {
        videoComboBox.setDisable(true);
        videoRefreshBtn.setDisable(true);

        // Run detection in background thread
        new Thread(() -> {
            List<VideoSource> sources = VideoCapture.getAvailableVideoSources();

            Platform.runLater(() -> {
                ObservableList<VideoSource> videoSources = FXCollections.observableArrayList(sources);
                videoComboBox.setItems(videoSources);

                if (!videoSources.isEmpty()) {
                    videoComboBox.getSelectionModel().selectFirst();
                }

                videoComboBox.setDisable(false);
                videoRefreshBtn.setDisable(false);
            });
        }).start();
    }

    private void toggleTelemetryConnection() {
        if (!telemetryConnected) {
            connectTelemetry();
        } else {
            disconnectTelemetry();
        }
    }

    private void connectTelemetry() {
        String selectedSource = telemetryComboBox.getValue();
        if (selectedSource == null) {
            return;
        }

        // Update UI
        telemetryStatus.setText("● Connecting...");
        telemetryStatus.getStyleClass().clear();
        telemetryStatus.getStyleClass().add("status-connecting");
        telemetryConnectBtn.setDisable(true);

        // Simulate connection delay
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate connection time

                Platform.runLater(() -> {
                    // TODO: Implement actual telemetry connection logic here

                    telemetryConnected = true;
                    telemetryStatus.setText("● Connected");
                    telemetryStatus.getStyleClass().clear();
                    telemetryStatus.getStyleClass().add("status-connected");
                    telemetryConnectBtn.setText("Disconnect");
                    telemetryConnectBtn.getStyleClass().remove("connect-button");
                    telemetryConnectBtn.getStyleClass().add("disconnect-button");
                    telemetryConnectBtn.setDisable(false);

                    // Update display
                    updateContentDisplay();

                    // Start telemetry updates
                    startTelemetryUpdates();
                });

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void disconnectTelemetry() {
        telemetryConnected = false;
        telemetryStatus.setText("● Disconnected");
        telemetryStatus.getStyleClass().clear();
        telemetryStatus.getStyleClass().add("status-disconnected");
        telemetryConnectBtn.setText("Connect");
        telemetryConnectBtn.getStyleClass().remove("disconnect-button");
        telemetryConnectBtn.getStyleClass().add("connect-button");

        // Stop telemetry updates
        stopTelemetryUpdates();

        // Update content display
        updateContentDisplay();
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
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "Failed to connect to camera";
                    }
                    videoStatus.setText("● " + errorMsg);
                    videoStatus.getStyleClass().clear();
                    videoStatus.getStyleClass().add("status-disconnected");
                    videoConnectBtn.setDisable(false);

                    // Show alert to user
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR
                    );
                    alert.setTitle("Camera Connection Error");
                    alert.setHeaderText("Failed to connect to " + selectedSource.getName());
                    alert.setContentText(errorMsg + "\n\nPlease make sure:\n" +
                            "1. The camera is connected\n" +
                            "2. No other application is using the camera\n" +
                            "3. You have permission to access the camera");
                    alert.showAndWait();
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

    private void startTelemetryUpdates() {
        telemetryTimer = new Timer(true);
        telemetryTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> updateTelemetryData());
            }
        }, 0, 100); // Update every 100ms
    }

    private void stopTelemetryUpdates() {
        if (telemetryTimer != null) {
            telemetryTimer.cancel();
            telemetryTimer = null;
        }
    }

    private void updateTelemetryData() {
        // TODO: Get actual telemetry data from your telemetry source
        // For now, using simulated data

        // Simulate battery voltage (11.0V - 12.6V)
        double voltage = 11.0 + Math.random() * 1.6;
        int batteryPercent = (int)((voltage - 11.0) / 1.6 * 100);
        batteryLabel.setText(String.format("%.1fV (%d%%)", voltage, batteryPercent));

        // Simulate GPS coordinates with slight movement
        currentLat = currentLat + (Math.random() - 0.5) * 0.0001;
        currentLon = currentLon + (Math.random() - 0.5) * 0.0001;
        gpsLabel.setText(String.format("%.6f, %.6f", currentLat, currentLon));

        // Update map position
        if (mapInitialized) {
            updateMapPosition(currentLat, currentLon);
        }

        // Simulate altitude (0 - 500m)
        double altitude = Math.random() * 500;
        altitudeLabel.setText(String.format("%.1f m", altitude));

        // Simulate speed (0 - 50 m/s)
        double speed = Math.random() * 50;
        speedLabel.setText(String.format("%.1f m/s", speed));

        // Simulate arm status
        armStatusLabel.setText(Math.random() > 0.5 ? "ARMED" : "DISARMED");
        if ("ARMED".equals(armStatusLabel.getText())) {
            armStatusLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            armStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }

        // Simulate flight mode
        String[] modes = {"STABILIZE", "LOITER", "AUTO", "RTL", "LAND"};
        flightModeLabel.setText(modes[(int)(Math.random() * modes.length)]);

        // Simulate satellite count
        satellitesLabel.setText(String.valueOf(8 + (int)(Math.random() * 8)));

        // Simulate attitude
        double roll = (Math.random() - 0.5) * 60;
        double pitch = (Math.random() - 0.5) * 60;
        double yaw = Math.random() * 360;
        rollPitchYawLabel.setText(String.format("R:%.0f° P:%.0f° Y:%.0f°", roll, pitch, yaw));
    }

    private void updateMapPosition(double lat, double lon) {
        if (webEngine != null && mapInitialized) {
            try {
                pathPoints.add(new double[]{lat, lon});
                String script = String.format("updatePosition(%f, %f)", lat, lon);
                webEngine.executeScript(script);
            } catch (Exception e) {
                System.err.println("Error updating map position: " + e.getMessage());
            }
        }
    }

    private void clearPath() {
        pathPoints.clear();
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

    public void shutdown() {
        stopTelemetryUpdates();
        if (videoCapture != null) {
            videoCapture.stopCapture();
        }
        // TODO: Clean up any other resources
    }
}