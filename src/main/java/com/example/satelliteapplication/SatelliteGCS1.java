package com.example.satelliteapplication;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import com.fazecast.jSerialComm.SerialPort;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.*;
import io.dronefleet.mavlink.minimal.Heartbeat;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class SatelliteGCS1 extends Application {

    private SerialPort serialPort;
    private MavlinkConnection mavlinkConnection;
    private Thread readThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // UI Components
    private ComboBox<SerialPortInfo> portComboBox;
    private Button connectButton;
    private Label connectionStatus;
    private TextArea logArea;

    // Telemetry Labels
    private Label batteryLabel;
    private Label gpsLabel;
    private Label altitudeLabel;
    private Label speedLabel;
    private Label armStatusLabel;
    private Label flightModeLabel;
    private Label satellitesLabel;
    private Label rollPitchYawLabel;

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
    public void start(Stage stage) throws IOException {
        Image icon = new Image("/images/light-icon.png");
        stage.getIcons().add(icon);
        stage.setTitle("NazarX Ground Control Station");
        Parent root = FXMLLoader.load(Objects.requireNonNull(
                getClass().getResource("/fxml/screen/MainScreen.fxml"))
        );
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/application.css")).toExternalForm()
        );
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
        // Initialize
        // refreshPorts();
    }

    private HBox createConnectionPanel() {
        HBox panel = new HBox(10);
        panel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        panel.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5;");

        portComboBox = new ComboBox<>();
        portComboBox.setPrefWidth(300);

        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setOnAction(e -> refreshPorts());

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        connectButton.setOnAction(e -> toggleConnection());

        connectionStatus = new Label("● Disconnected");
        connectionStatus.setTextFill(Color.RED);

        panel.getChildren().addAll(
                new Label("LR900 Port:"), portComboBox,
                refreshButton, connectButton, connectionStatus
        );

        return panel;
    }

    private GridPane createTelemetryPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: white; -fx-background-radius: 5;");

        // Initialize telemetry labels
        batteryLabel = createDataLabel("0.0V (0%)");
        gpsLabel = createDataLabel("No GPS");
        altitudeLabel = createDataLabel("0.0 m");
        speedLabel = createDataLabel("0.0 m/s");
        armStatusLabel = createDataLabel("DISARMED");
        flightModeLabel = createDataLabel("Unknown");
        satellitesLabel = createDataLabel("0");
        rollPitchYawLabel = createDataLabel("R:0° P:0° Y:0°");

        // Add labels to grid
        grid.add(createTitleLabel("Battery:"), 0, 0);
        grid.add(batteryLabel, 1, 0);

        grid.add(createTitleLabel("GPS Position:"), 2, 0);
        grid.add(gpsLabel, 3, 0);

        grid.add(createTitleLabel("Altitude:"), 0, 1);
        grid.add(altitudeLabel, 1, 1);

        grid.add(createTitleLabel("Ground Speed:"), 2, 1);
        grid.add(speedLabel, 3, 1);

        grid.add(createTitleLabel("Arm Status:"), 0, 2);
        grid.add(armStatusLabel, 1, 2);

        grid.add(createTitleLabel("Flight Mode:"), 2, 2);
        grid.add(flightModeLabel, 3, 2);

        grid.add(createTitleLabel("Satellites:"), 0, 3);
        grid.add(satellitesLabel, 1, 3);

        grid.add(createTitleLabel("Attitude:"), 2, 3);
        grid.add(rollPitchYawLabel, 3, 3);

        return grid;
    }

    private VBox createLogPanel() {
        VBox panel = new VBox(5);
        panel.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5;");

        Label logTitle = new Label("Message Log");
        logTitle.setStyle("-fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setPrefRowCount(10);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        Button clearButton = new Button("Clear Log");
        clearButton.setOnAction(e -> logArea.clear());

        Button requestStreamsButton = new Button("Request All Streams");
        requestStreamsButton.setOnAction(e -> requestDataStreams());

        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(clearButton, requestStreamsButton);

        panel.getChildren().addAll(logTitle, logArea, buttonBox);
        return panel;
    }

    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        return label;
    }

    private Label createDataLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Monaco", FontPosture.REGULAR, 12));
        return label;
    }

    private void refreshPorts() {
        portComboBox.getItems().clear();
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            // Look for CP2102 or similar descriptors that might indicate the LR900
            String description = port.getDescriptivePortName().toLowerCase();
            if (description.contains("cp210") || description.contains("usb") ||
                    description.contains("serial") || description.contains("uart")) {
                portComboBox.getItems().add(new SerialPortInfo(port));
            }
        }

        if (!portComboBox.getItems().isEmpty()) {
            portComboBox.getSelectionModel().select(0);
        }

        log("Found " + portComboBox.getItems().size() + " potential LR900 ports");
    }

    private void toggleConnection() {
        if (isRunning.get()) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        SerialPortInfo selected = portComboBox.getValue();
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
            connectButton.setText("Disconnect");
            connectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            connectionStatus.setText("● Connected");
            connectionStatus.setTextFill(Color.GREEN);
            portComboBox.setDisable(true);
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

    private void disconnect() {
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
            connectButton.setText("Connect");
            connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            connectionStatus.setText("● Disconnected");
            connectionStatus.setTextFill(Color.RED);
            portComboBox.setDisable(false);
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
                                disconnect();
                            });
                            break;
                        } else {
                            int finalTimeoutCount = timeoutCount;
                            Platform.runLater(() -> log("Timeout " + finalTimeoutCount + "/" + MAX_TIMEOUTS + " - still waiting for data..."));
                        }
                    } else {
                        Platform.runLater(() -> {
                            log("Read error: " + errorMsg);
                            disconnect();
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
                armStatusLabel.setTextFill(armed ? Color.RED : Color.GREEN);

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
                } else {
                    gpsLabel.setText("No Fix");
                    satellitesLabel.setText(gps.satellitesVisible() + " (no fix)");
                }

            } else if (payload instanceof GlobalPositionInt pos) {
                double alt = pos.relativeAlt() / 1000.0;
                altitudeLabel.setText(String.format("%.1f m", alt));

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
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

//    @Override
//    public void stop() {
//        scheduler.shutdownNow();
//        disconnect();
//    }

    public static void main(String[] args) {
        launch(args);
    }
}