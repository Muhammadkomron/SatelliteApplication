package com.example.satelliteapplication.manager;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;

public class UIStateManager {

    // Connection states
    private boolean telemetryConnected = false;
    private boolean videoConnected = false;

    public void updateTelemetryConnectionUI(boolean connected, Label statusLabel, Button connectBtn, ComboBox<?> comboBox) {
        telemetryConnected = connected;

        if (connected) {
            statusLabel.setText("● Connected");
            statusLabel.getStyleClass().clear();
            statusLabel.getStyleClass().add("status-connected");
            connectBtn.setText("Disconnect");
            connectBtn.getStyleClass().remove("connect-button");
            connectBtn.getStyleClass().add("disconnect-button");
            comboBox.setDisable(true);
        } else {
            statusLabel.setText("● Disconnected");
            statusLabel.getStyleClass().clear();
            statusLabel.getStyleClass().add("status-disconnected");
            connectBtn.setText("Connect");
            connectBtn.getStyleClass().remove("disconnect-button");
            connectBtn.getStyleClass().add("connect-button");
            comboBox.setDisable(false);
        }
    }

    public void updateVideoConnectionUI(boolean connected, Label statusLabel, Button connectBtn, Button expandBtn) {
        videoConnected = connected;

        if (connected) {
            statusLabel.setText("● Connected");
            statusLabel.getStyleClass().clear();
            statusLabel.getStyleClass().add("status-connected");
            connectBtn.setText("Disconnect");
            connectBtn.getStyleClass().remove("connect-button");
            connectBtn.getStyleClass().add("disconnect-button");
            connectBtn.setDisable(false);

            // Show expand button
            expandBtn.setVisible(true);
            expandBtn.setManaged(true);
        } else {
            statusLabel.setText("● Disconnected");
            statusLabel.getStyleClass().clear();
            statusLabel.getStyleClass().add("status-disconnected");
            connectBtn.setText("Connect");
            connectBtn.getStyleClass().remove("disconnect-button");
            connectBtn.getStyleClass().add("connect-button");
            connectBtn.setDisable(false);

            // Hide expand button
            expandBtn.setVisible(false);
            expandBtn.setManaged(false);
            expandBtn.setText("Expand Video");
        }
    }

    public void updateVideoConnectingUI(Label statusLabel, Button connectBtn) {
        statusLabel.setText("● Connecting...");
        statusLabel.getStyleClass().clear();
        statusLabel.getStyleClass().add("status-connecting");
        connectBtn.setDisable(true);
    }

    public void updateVideoConnectionError(String message, Label statusLabel, Button connectBtn) {
        statusLabel.setText("● " + message);
        statusLabel.getStyleClass().clear();
        statusLabel.getStyleClass().add("status-disconnected");
        connectBtn.setText("Connect");
        connectBtn.getStyleClass().remove("disconnect-button");
        connectBtn.getStyleClass().add("connect-button");
        connectBtn.setDisable(false);
    }

    public void updateContentPanels(Pane telemetryPanel, VBox telemetryPlaceholder,
                                    VBox videoPanel, VBox videoPlaceholder,
                                    VBox mapPanel, VBox mapPlaceholder) {
        // Telemetry panel (can be HBox or VBox)
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

    // Updated method to handle both HBox and VBox for telemetryPanel
    public void showAllPlaceholders(Pane telemetryPanel, VBox telemetryPlaceholder,
                                    VBox videoPanel, VBox videoPlaceholder,
                                    VBox mapPanel, VBox mapPlaceholder) {
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

    public boolean isTelemetryConnected() {
        return telemetryConnected;
    }

    public boolean isVideoConnected() {
        return videoConnected;
    }
}