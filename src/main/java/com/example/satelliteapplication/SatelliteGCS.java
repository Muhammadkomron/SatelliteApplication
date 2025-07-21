package com.example.satelliteapplication;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

import com.example.satelliteapplication.controller.MainController;

public class SatelliteGCS extends Application {
    private MainController mainController;

    @Override
    public void start(Stage stage) throws IOException {
        // Set application icon
        Image icon = new Image("/images/light-icon.png");
        stage.getIcons().add(icon);
        stage.setTitle("NazarX Ground Control Station");

        // Load FXML with controller
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/fxml/screen/MainScreen.fxml"))
        );
        Parent root = loader.load();

        // Get controller reference
        mainController = loader.getController();

        // Create and set scene
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setMaximized(true);

        // Set up shutdown hook
        stage.setOnCloseRequest(event -> {
            if (mainController != null) {
                mainController.shutdown();
            }
        });

        stage.show();
    }

    @Override
    public void stop() {
        // Clean up resources when application closes
        if (mainController != null) {
            mainController.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
