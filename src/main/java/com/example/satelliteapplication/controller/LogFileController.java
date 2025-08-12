package com.example.satelliteapplication.controller;

import com.example.satelliteapplication.model.DataFlashLog;
import com.example.satelliteapplication.model.DataFlashLog.LogMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class LogFileController implements Initializable {

    @FXML private Button selectLogFileBtn;
    @FXML private Label logFileStatusLabel;
    @FXML private VBox dashboardContainer;
    @FXML private ProgressBar loadingProgress;
    @FXML private TabPane logTabPane;

    // Charts
    private LineChart<Number, Number> attitudeChart;
    private LineChart<Number, Number> altitudeChart;
    private LineChart<Number, Number> batteryChart;
    private LineChart<Number, Number> gpsChart;
    private LineChart<Number, Number> vibrationChart;
    private LineChart<Number, Number> rcChart;

    // Data table
    @FXML private ComboBox<String> messageTypeCombo;
    @FXML private TableView<Map.Entry<String, Object>> dataTable;
    @FXML private TextArea logMessagesArea;

    private DataFlashLog logParser;
    private File currentLogFile;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        selectLogFileBtn.setOnAction(e -> selectLogFile());

        // Initially hide dashboard
        dashboardContainer.setVisible(false);
        dashboardContainer.setManaged(false);
        loadingProgress.setVisible(false);

        setupCharts();
        setupDataTable();

        logFileStatusLabel.setText("No log file selected");
    }

    private void selectLogFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select ArduPilot BIN Log File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("BIN Files", "*.bin", "*.BIN"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Set initial directory to user's documents or desktop
        String userHome = System.getProperty("user.home");
        File initialDir = new File(userHome, "Documents");
        if (!initialDir.exists()) {
            initialDir = new File(userHome, "Desktop");
        }
        if (initialDir.exists()) {
            fileChooser.setInitialDirectory(initialDir);
        }

        File file = fileChooser.showOpenDialog(selectLogFileBtn.getScene().getWindow());
        if (file != null) {
            loadLogFile(file);
        }
    }

    private void loadLogFile(File file) {
        currentLogFile = file;
        logFileStatusLabel.setText("Loading: " + file.getName());
        loadingProgress.setVisible(true);
        loadingProgress.setProgress(-1);

        // Create background task to load file
        Task<DataFlashLog> loadTask = new Task<>() {
            @Override
            protected DataFlashLog call() throws Exception {
                DataFlashLog parser = new DataFlashLog();
                parser.parseBinFile(file.getAbsolutePath());
                return parser;
            }
        };

        loadTask.setOnSucceeded(e -> {
            logParser = loadTask.getValue();
            Platform.runLater(() -> {
                loadingProgress.setVisible(false);
                logFileStatusLabel.setText("Loaded: " + file.getName() +
                        " (" + logParser.getMessages().size() + " messages)");
                displayDashboard();
            });
        });

        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                loadingProgress.setVisible(false);
                logFileStatusLabel.setText("Failed to load file");
                showError("Error loading file", loadTask.getException().getMessage());
            });
        });

        new Thread(loadTask).start();
    }

    private void displayDashboard() {
        dashboardContainer.setVisible(true);
        dashboardContainer.setManaged(true);

        updateCharts();
        updateMessageTypes();
        displayLogMessages();
    }

    private void setupCharts() {
        // Create charts grid
        GridPane chartGrid = new GridPane();
        chartGrid.setHgap(10);
        chartGrid.setVgap(10);
        chartGrid.setPadding(new Insets(10));

        attitudeChart = createLineChart("Attitude", "Time (s)", "Degrees");
        altitudeChart = createLineChart("Altitude", "Time (s)", "Meters");
        batteryChart = createLineChart("Battery", "Time (s)", "Voltage/Current");
        gpsChart = createLineChart("GPS", "Time (s)", "Satellites/HDOP");
        vibrationChart = createLineChart("Vibration", "Time (s)", "m/s²");
        rcChart = createLineChart("RC Input/Output", "Time (s)", "PWM");

        // Add charts to grid
        chartGrid.add(attitudeChart, 0, 0);
        chartGrid.add(altitudeChart, 1, 0);
        chartGrid.add(batteryChart, 0, 1);
        chartGrid.add(gpsChart, 1, 1);
        chartGrid.add(vibrationChart, 0, 2);
        chartGrid.add(rcChart, 1, 2);

        // Set chart sizes
        for (var node : chartGrid.getChildren()) {
            if (node instanceof LineChart) {
                LineChart<?, ?> chart = (LineChart<?, ?>) node;
                chart.setPrefHeight(250);
                chart.setPrefWidth(450);
                GridPane.setHgrow(chart, Priority.ALWAYS);
                GridPane.setVgrow(chart, Priority.ALWAYS);
            }
        }

        // Add to dashboard if FXML has the container
        if (dashboardContainer != null) {
            Tab chartsTab = new Tab("Charts");
            chartsTab.setClosable(false);
            ScrollPane scrollPane = new ScrollPane(chartGrid);
            scrollPane.setFitToWidth(true);
            chartsTab.setContent(scrollPane);

            if (logTabPane == null) {
                logTabPane = new TabPane();
                dashboardContainer.getChildren().add(logTabPane);
            }
            logTabPane.getTabs().add(chartsTab);
        }
    }

    private void setupDataTable() {
        if (dataTable == null) {
            dataTable = new TableView<>();
        }

        TableColumn<Map.Entry<String, Object>, String> fieldCol = new TableColumn<>("Field");
        fieldCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getKey()));
        fieldCol.setPrefWidth(200);

        TableColumn<Map.Entry<String, Object>, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getValue())));
        valueCol.setPrefWidth(400);

        dataTable.getColumns().addAll(fieldCol, valueCol);

        // Create raw data tab
        Tab rawDataTab = new Tab("Raw Data");
        rawDataTab.setClosable(false);

        VBox rawDataContainer = new VBox(10);
        rawDataContainer.setPadding(new Insets(10));

        // Message type selector
        HBox controls = new HBox(10);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        if (messageTypeCombo == null) {
            messageTypeCombo = new ComboBox<>();
        }
        messageTypeCombo.setPrefWidth(200);
        messageTypeCombo.setOnAction(e -> loadMessageData());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadMessageData());

        controls.getChildren().addAll(
                new Label("Message Type:"),
                messageTypeCombo,
                refreshBtn
        );

        rawDataContainer.getChildren().addAll(controls, dataTable);
        VBox.setVgrow(dataTable, Priority.ALWAYS);

        rawDataTab.setContent(rawDataContainer);

        if (logTabPane != null) {
            logTabPane.getTabs().add(rawDataTab);
        }
    }

    private LineChart<Number, Number> createLineChart(String title, String xLabel, String yLabel) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setAutoRanging(true);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(true);

        return chart;
    }

    private void updateCharts() {
        // Clear all charts
        attitudeChart.getData().clear();
        altitudeChart.getData().clear();
        batteryChart.getData().clear();
        gpsChart.getData().clear();
        vibrationChart.getData().clear();
        rcChart.getData().clear();

        // Update attitude chart
        updateAttitudeChart();

        // Update altitude chart
        updateAltitudeChart();

        // Update battery chart
        updateBatteryChart();

        // Update GPS chart
        updateGPSChart();

        // Update vibration chart
        updateVibrationChart();

        // Update RC chart
        updateRCChart();
    }

    private void updateAttitudeChart() {
        List<LogMessage> messages = logParser.getMessagesByType("ATT");
        if (messages.isEmpty()) return;

        XYChart.Series<Number, Number> rollSeries = new XYChart.Series<>();
        rollSeries.setName("Roll");
        XYChart.Series<Number, Number> pitchSeries = new XYChart.Series<>();
        pitchSeries.setName("Pitch");
        XYChart.Series<Number, Number> yawSeries = new XYChart.Series<>();
        yawSeries.setName("Yaw");

        int sample = 0;
        for (LogMessage msg : messages) {
            if (sample++ % 10 == 0) { // Sample every 10th point
                double time = sample / 100.0;
                addDataPoint(rollSeries, time, msg.fields.get("Roll"));
                addDataPoint(pitchSeries, time, msg.fields.get("Pitch"));
                addDataPoint(yawSeries, time, msg.fields.get("Yaw"));
            }
        }

        attitudeChart.getData().addAll(rollSeries, pitchSeries, yawSeries);
    }

    private void updateAltitudeChart() {
        List<LogMessage> baroMessages = logParser.getMessagesByType("BARO");
        List<LogMessage> gpsMessages = logParser.getMessagesByType("GPS");

        if (!baroMessages.isEmpty()) {
            XYChart.Series<Number, Number> baroSeries = new XYChart.Series<>();
            baroSeries.setName("Barometric");

            int sample = 0;
            for (LogMessage msg : baroMessages) {
                if (sample++ % 10 == 0) {
                    double time = sample / 100.0;
                    addDataPoint(baroSeries, time, msg.fields.get("Alt"));
                }
            }
            altitudeChart.getData().add(baroSeries);
        }

        if (!gpsMessages.isEmpty()) {
            XYChart.Series<Number, Number> gpsSeries = new XYChart.Series<>();
            gpsSeries.setName("GPS");

            int sample = 0;
            for (LogMessage msg : gpsMessages) {
                if (sample++ % 5 == 0) {
                    double time = sample / 10.0;
                    Object alt = msg.fields.get("Alt");
                    if (alt instanceof Number) {
                        gpsSeries.getData().add(new XYChart.Data<>(time, ((Number)alt).doubleValue() / 1000.0));
                    }
                }
            }
            if (!gpsSeries.getData().isEmpty()) {
                altitudeChart.getData().add(gpsSeries);
            }
        }
    }

    private void updateBatteryChart() {
        List<LogMessage> messages = logParser.getMessagesByType("CURR");
        if (messages.isEmpty()) return;

        XYChart.Series<Number, Number> voltSeries = new XYChart.Series<>();
        voltSeries.setName("Voltage (V)");
        XYChart.Series<Number, Number> currSeries = new XYChart.Series<>();
        currSeries.setName("Current (A)");

        int sample = 0;
        for (LogMessage msg : messages) {
            if (sample++ % 10 == 0) {
                double time = sample / 100.0;
                addDataPoint(voltSeries, time, msg.fields.get("Volt"));
                addDataPoint(currSeries, time, msg.fields.get("Curr"));
            }
        }

        batteryChart.getData().addAll(voltSeries, currSeries);
    }

    private void updateGPSChart() {
        List<LogMessage> messages = logParser.getMessagesByType("GPS");
        if (messages.isEmpty()) return;

        XYChart.Series<Number, Number> satSeries = new XYChart.Series<>();
        satSeries.setName("Satellites");
        XYChart.Series<Number, Number> hdopSeries = new XYChart.Series<>();
        hdopSeries.setName("HDOP");

        int sample = 0;
        for (LogMessage msg : messages) {
            if (sample++ % 5 == 0) {
                double time = sample / 10.0;
                addDataPoint(satSeries, time, msg.fields.get("NSats"));
                addDataPoint(hdopSeries, time, msg.fields.get("HDop"));
            }
        }

        gpsChart.getData().addAll(satSeries, hdopSeries);
    }

    private void updateVibrationChart() {
        List<LogMessage> messages = logParser.getMessagesByType("VIBE");
        if (messages.isEmpty()) return;

        XYChart.Series<Number, Number> xSeries = new XYChart.Series<>();
        xSeries.setName("X");
        XYChart.Series<Number, Number> ySeries = new XYChart.Series<>();
        ySeries.setName("Y");
        XYChart.Series<Number, Number> zSeries = new XYChart.Series<>();
        zSeries.setName("Z");

        int sample = 0;
        for (LogMessage msg : messages) {
            if (sample++ % 10 == 0) {
                double time = sample / 100.0;
                addDataPoint(xSeries, time, msg.fields.get("VibeX"));
                addDataPoint(ySeries, time, msg.fields.get("VibeY"));
                addDataPoint(zSeries, time, msg.fields.get("VibeZ"));
            }
        }

        vibrationChart.getData().addAll(xSeries, ySeries, zSeries);
    }

    private void updateRCChart() {
        List<LogMessage> rcinMessages = logParser.getMessagesByType("RCIN");
        List<LogMessage> rcoutMessages = logParser.getMessagesByType("RCOU");

        if (!rcinMessages.isEmpty()) {
            XYChart.Series<Number, Number> throttleIn = new XYChart.Series<>();
            throttleIn.setName("Throttle In");

            int sample = 0;
            for (LogMessage msg : rcinMessages) {
                if (sample++ % 10 == 0) {
                    double time = sample / 100.0;
                    addDataPoint(throttleIn, time, msg.fields.get("C3"));
                }
            }
            rcChart.getData().add(throttleIn);
        }

        if (!rcoutMessages.isEmpty()) {
            XYChart.Series<Number, Number> motor1 = new XYChart.Series<>();
            motor1.setName("Motor 1");

            int sample = 0;
            for (LogMessage msg : rcoutMessages) {
                if (sample++ % 10 == 0) {
                    double time = sample / 100.0;
                    addDataPoint(motor1, time, msg.fields.get("C1"));
                }
            }
            rcChart.getData().add(motor1);
        }
    }

    private void addDataPoint(XYChart.Series<Number, Number> series, double time, Object value) {
        if (value instanceof Number) {
            series.getData().add(new XYChart.Data<>(time, (Number) value));
        }
    }

    private void updateMessageTypes() {
        Set<String> types = logParser.getMessageTypes();
        messageTypeCombo.setItems(FXCollections.observableArrayList(types));
        if (!types.isEmpty()) {
            messageTypeCombo.getSelectionModel().selectFirst();
        }
    }

    private void loadMessageData() {
        String selectedType = messageTypeCombo.getValue();
        if (selectedType == null) return;

        List<LogMessage> messages = logParser.getMessagesByType(selectedType);
        if (!messages.isEmpty()) {
            LogMessage firstMsg = messages.get(0);
            ObservableList<Map.Entry<String, Object>> items =
                    FXCollections.observableArrayList(firstMsg.fields.entrySet());
            dataTable.setItems(items);
        }
    }

    private void displayLogMessages() {
        if (logMessagesArea == null) {
            logMessagesArea = new TextArea();
            logMessagesArea.setEditable(false);

            Tab messagesTab = new Tab("Log Messages");
            messagesTab.setClosable(false);
            messagesTab.setContent(logMessagesArea);

            if (logTabPane != null) {
                logTabPane.getTabs().add(messagesTab);
            }
        }

        StringBuilder sb = new StringBuilder();

        // Display errors
        List<LogMessage> errMessages = logParser.getMessagesByType("ERR");
        if (!errMessages.isEmpty()) {
            sb.append("=== ERRORS ===\n");
            for (LogMessage msg : errMessages) {
                sb.append(formatMessage(msg)).append("\n");
            }
            sb.append("\n");
        }

        // Display mode changes
        List<LogMessage> modeMessages = logParser.getMessagesByType("MODE");
        if (!modeMessages.isEmpty()) {
            sb.append("=== MODE CHANGES ===\n");
            for (LogMessage msg : modeMessages) {
                sb.append(formatMessage(msg)).append("\n");
            }
            sb.append("\n");
        }

        // Display events
        List<LogMessage> eventMessages = logParser.getMessagesByType("EV");
        if (!eventMessages.isEmpty()) {
            sb.append("=== EVENTS ===\n");
            for (LogMessage msg : eventMessages) {
                sb.append(formatMessage(msg)).append("\n");
            }
        }

        logMessagesArea.setText(sb.toString());
    }

    private String formatMessage(LogMessage msg) {
        return msg.fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showLogViewer(Stage parentStage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    LogFileController.class.getResource("/fxml/screen/LogViewer.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("ArduPilot Log File Viewer");
            stage.initModality(Modality.NONE);
            stage.initOwner(parentStage);

            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(
                    LogFileController.class.getResource("/css/application.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText("Failed to open log viewer: " + e.getMessage());
            alert.showAndWait();
        }
    }
}