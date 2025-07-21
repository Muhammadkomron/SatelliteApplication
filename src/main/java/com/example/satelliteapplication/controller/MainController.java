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
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class MainController implements Initializable {

    // Connection Controls
    @FXML private ComboBox<String> telemetryComboBox;
    @FXML private ComboBox<String> videoComboBox;
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

    // Map data
    private WebEngine webEngine;
    private double currentLat = 41.2995;
    private double currentLon = 69.2401;
    private List<double[]> pathPoints = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
        webEngine.loadContent(generateMapHTML());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // Map loaded successfully
                centerMap();
            }
        });
    }

    private String generateMapHTML() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin: 0; padding: 0; }
                    #map { height: 100vh; width: 100%; }
                    .satellite-icon {
                        background-color: #e74c3c;
                        border: 2px solid white;
                        border-radius: 50%;
                        width: 12px;
                        height: 12px;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([41.2995, 69.2401], 13);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '© OpenStreetMap contributors'
                    }).addTo(map);
                    var satelliteIcon = L.divIcon({
                        className: 'satellite-icon',
                        iconSize: [12, 12]
                    });
                    var marker = L.marker([41.2995, 69.2401], {icon: satelliteIcon}).addTo(map);
                    var pathLine = L.polyline([], {color: '#3498db', weight: 3}).addTo(map);
            
                    function updatePosition(lat, lon) {
                        marker.setLatLng([lat, lon]);
                        pathLine.addLatLng([lat, lon]);
                    }
                    function clearPath() {
                        pathLine.setLatLngs([]);
                    }
                    function centerMap() {
                        var latlng = marker.getLatLng();
                        map.setView(latlng, map.getZoom());
                    }
                    function setPath(points) {
                        pathLine.setLatLngs(points);
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
        ObservableList<String> videoSources = FXCollections.observableArrayList();

        // TODO: Add actual video source detection
        // For now, add some example sources
        videoSources.add("UDP - 5600");
        videoSources.add("RTSP - rtsp://192.168.1.100:8554");
        videoSources.add("Webcam - Camera 1");

        videoComboBox.setItems(videoSources);
        if (!videoSources.isEmpty()) {
            videoComboBox.getSelectionModel().selectFirst();
        }
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
        String selectedSource = videoComboBox.getValue();
        if (selectedSource == null) {
            return;
        }

        // Update UI
        videoStatus.setText("● Connecting...");
        videoStatus.getStyleClass().clear();
        videoStatus.getStyleClass().add("status-connecting");
        videoConnectBtn.setDisable(true);

        // Simulate connection delay
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate connection time

                Platform.runLater(() -> {
                    // TODO: Implement actual video connection logic here

                    videoConnected = true;
                    videoStatus.setText("● Connected");
                    videoStatus.getStyleClass().clear();
                    videoStatus.getStyleClass().add("status-connected");
                    videoConnectBtn.setText("Disconnect");
                    videoConnectBtn.getStyleClass().remove("connect-button");
                    videoConnectBtn.getStyleClass().add("disconnect-button");
                    videoConnectBtn.setDisable(false);

                    // Update content display
                    updateContentDisplay();
                });

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void disconnectVideo() {
        videoConnected = false;
        videoStatus.setText("● Disconnected");
        videoStatus.getStyleClass().clear();
        videoStatus.getStyleClass().add("status-disconnected");
        videoConnectBtn.setText("Connect");
        videoConnectBtn.getStyleClass().remove("disconnect-button");
        videoConnectBtn.getStyleClass().add("connect-button");

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
        updateMapPosition(currentLat, currentLon);

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
        if (webEngine != null) {
            pathPoints.add(new double[]{lat, lon});
            webEngine.executeScript(String.format("updatePosition(%f, %f)", lat, lon));
        }
    }

    private void clearPath() {
        pathPoints.clear();
        if (webEngine != null) {
            webEngine.executeScript("clearPath()");
        }
    }

    private void centerMap() {
        if (webEngine != null) {
            webEngine.executeScript("centerMap()");
        }
    }

    public void shutdown() {
        stopTelemetryUpdates();
        // TODO: Clean up any other resources
    }
}