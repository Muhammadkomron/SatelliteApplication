package com.example.satelliteapplication.manager;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MapManager {
    private WebEngine webEngine;
    private boolean mapInitialized = false;
    private double currentLat = 41.2995;
    private double currentLon = 69.2401;
    private double currentHeading = 0;

    public MapManager() {
    }

    public void initialize(WebView mapWebView) {
        this.webEngine = mapWebView.getEngine();

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

        // Load the map HTML from resources
        try {
            String mapHtml = loadMapHtml();
            webEngine.loadContent(mapHtml);
        } catch (IOException e) {
            System.err.println("Failed to load map HTML: " + e.getMessage());
            e.printStackTrace();
            // Load fallback content
            webEngine.loadContent("<html><body><h1>Error loading map</h1></body></html>");
        }

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

    private String loadMapHtml() throws IOException {
        // Load HTML from resources
        try (InputStream inputStream = getClass().getResourceAsStream("/html/map.html")) {
            if (inputStream == null) {
                throw new IOException("Could not find map.html in resources/html/");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    public void updatePosition(double lat, double lon) {
        updatePosition(lat, lon, currentHeading);
    }

    public void updatePosition(double lat, double lon, double heading) {
        if (webEngine != null && mapInitialized) {
            try {
                currentLat = lat;
                currentLon = lon;
                currentHeading = heading;
                String script = String.format("updatePosition(%f, %f, %f)", lat, lon, heading);
                webEngine.executeScript(script);
            } catch (Exception e) {
                System.err.println("Error updating map position: " + e.getMessage());
            }
        }
    }

    public void clearPath() {
        if (webEngine != null && mapInitialized) {
            try {
                webEngine.executeScript("clearPath()");
            } catch (Exception e) {
                System.err.println("Error clearing path: " + e.getMessage());
            }
        }
    }

    public void centerMap() {
        if (webEngine != null && mapInitialized) {
            try {
                webEngine.executeScript("centerMap()");
            } catch (Exception e) {
                System.err.println("Error centering map: " + e.getMessage());
            }
        }
    }

    public void forceResize() {
        if (webEngine != null) {
            Platform.runLater(() -> {
                webEngine.executeScript("if (window.map) { window.map.invalidateSize(true); }");

                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        Platform.runLater(() -> {
                            webEngine.executeScript("if (window.map) { window.map.invalidateSize(true); window.map.setView([" + currentLat + ", " + currentLon + "], 15); }");
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            });
        }
    }

    public boolean isInitialized() {
        return mapInitialized;
    }
}