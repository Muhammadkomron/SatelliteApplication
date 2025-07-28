package com.example.satelliteapplication.manager;

import com.fazecast.jSerialComm.SerialPort;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.*;
import io.dronefleet.mavlink.minimal.Heartbeat;
import javafx.application.Platform;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TelemetryManager {
    // Connection components
    private SerialPort serialPort;
    private MavlinkConnection mavlinkConnection;
    private Thread readThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Mission data
    private LocalDateTime missionStartTime;
    private double homeLatitude = 0;
    private double homeLongitude = 0;
    private boolean homePositionSet = false;

    // Telemetry data
    private final TelemetryData telemetryData = new TelemetryData();

    // Callbacks
    private Consumer<String> logCallback;
    private Consumer<TelemetryData> dataUpdateCallback;
    private Runnable disconnectCallback;

    // Helper class for telemetry data
    public static class TelemetryData {
        // Basic telemetry
        public double batteryVoltage;
        public int batteryPercentage = -1;
        public double latitude;
        public double longitude;
        public double altitude;
        public double gpsAltitude;
        public double groundSpeed;
        public double verticalVelocity;
        public int satellites;
        public boolean hasGpsFix;
        public String flightMode = "Unknown";
        public boolean isArmed;

        // Attitude
        public double roll;
        public double pitch;
        public double yaw;

        // Environmental
        public double pressure;
        public double internalTemp;
        public double externalTemp;

        // Mission
        public double distanceFromHome;
        public String statusMessage = "";

        // GPS status
        public boolean hasValidPosition() {
            return latitude != 0 && longitude != 0;
        }
    }

    public TelemetryManager() {
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public void setDataUpdateCallback(Consumer<TelemetryData> callback) {
        this.dataUpdateCallback = callback;
    }

    public void setDisconnectCallback(Runnable callback) {
        this.disconnectCallback = callback;
    }

    public boolean connect(SerialPort port) {
        if (isRunning.get()) {
            disconnect();
        }

        this.serialPort = port;

        // Configure for LR900 defaults
        serialPort.setBaudRate(57600);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 1000);

        if (!serialPort.openPort()) {
            log("Failed to open port: " + serialPort.getSystemPortName());
            return false;
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
        missionStartTime = LocalDateTime.now();

        // Start mission time updater
        scheduler.scheduleAtFixedRate(this::updateMissionTime, 0, 1, TimeUnit.SECONDS);

        // Start reading thread
        readThread = new Thread(this::readMavlinkMessages);
        readThread.setDaemon(true);
        readThread.start();

        log("Connected to " + serialPort.getSystemPortName() + " at 57600 baud");
        log("Waiting for MAVLink data...");

        // Request data streams
        scheduler.schedule(this::requestDataStreams, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::requestDataStreams, 2, 5, TimeUnit.SECONDS);

        return true;
    }

    public void disconnect() {
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
                    timeoutCount = 0;
                    handleMavlinkMessage(message);
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("timed out")) {
                        timeoutCount++;
                        if (timeoutCount >= MAX_TIMEOUTS) {
                            Platform.runLater(() -> {
                                log("No MAVLink data received. Check connection.");
                                if (disconnectCallback != null) {
                                    disconnectCallback.run();
                                }
                            });
                            break;
                        }
                    } else {
                        Platform.runLater(() -> {
                            log("Read error: " + errorMsg);
                            if (disconnectCallback != null) {
                                disconnectCallback.run();
                            }
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

        if (payload instanceof Heartbeat hb) {
            telemetryData.flightMode = "Mode " + hb.customMode();
            telemetryData.isArmed = (hb.baseMode().value() & 128) != 0;

        } else if (payload instanceof SysStatus sys) {
            telemetryData.batteryVoltage = sys.voltageBattery() / 1000.0;
            telemetryData.batteryPercentage = sys.batteryRemaining();

        } else if (payload instanceof GpsRawInt gps) {
            double lat = gps.lat() / 1e7;
            double lon = gps.lon() / 1e7;
            telemetryData.gpsAltitude = gps.alt() / 1000.0;

            if (gps.fixType().value() >= 2) {
                telemetryData.latitude = lat;
                telemetryData.longitude = lon;
                telemetryData.hasGpsFix = true;
                telemetryData.satellites = gps.satellitesVisible();

                // Set home position on first valid GPS fix
                if (!homePositionSet && lat != 0 && lon != 0) {
                    homeLatitude = lat;
                    homeLongitude = lon;
                    homePositionSet = true;
                    log("Home position set: " + homeLatitude + ", " + homeLongitude);
                }

                // Calculate distance from home
                if (homePositionSet) {
                    telemetryData.distanceFromHome = calculateDistance(
                            homeLatitude, homeLongitude, lat, lon
                    );
                }
            } else {
                telemetryData.hasGpsFix = false;
                telemetryData.satellites = gps.satellitesVisible();
            }

        } else if (payload instanceof GlobalPositionInt pos) {
            telemetryData.altitude = pos.relativeAlt() / 1000.0;
            telemetryData.verticalVelocity = pos.vz() / 100.0;

            // Update position if GPS not available
            if (!telemetryData.hasGpsFix) {
                double lat = pos.lat() / 1e7;
                double lon = pos.lon() / 1e7;
                if (lat != 0 && lon != 0) {
                    telemetryData.latitude = lat;
                    telemetryData.longitude = lon;
                }
            }

        } else if (payload instanceof VfrHud hud) {
            telemetryData.groundSpeed = hud.groundspeed();
            if (hud.alt() > 0) {
                telemetryData.altitude = hud.alt();
            }
            telemetryData.verticalVelocity = hud.climb();

        } else if (payload instanceof Attitude att) {
            telemetryData.roll = Math.toDegrees(att.roll());
            telemetryData.pitch = Math.toDegrees(att.pitch());
            telemetryData.yaw = Math.toDegrees(att.yaw());

        } else if (payload instanceof ScaledPressure pressure) {
            telemetryData.pressure = pressure.pressAbs();
            telemetryData.externalTemp = pressure.temperature() / 100.0;

        } else if (payload instanceof ScaledImu2 imu2) {
            telemetryData.internalTemp = imu2.temperature() / 100.0;

        } else if (payload instanceof HighresImu highres) {
            if (highres.temperature() != 0) {
                telemetryData.internalTemp = highres.temperature();
            }
        }

        // Handle STATUSTEXT dynamically
        handleStatusText(payload);

        // Log the message
        String msgType = payload.getClass().getSimpleName();
        log("Received: " + msgType + " from Sys:" + message.getOriginSystemId());

        // Notify callback
        if (dataUpdateCallback != null) {
            Platform.runLater(() -> dataUpdateCallback.accept(telemetryData));
        }
    }

    private void handleStatusText(Object payload) {
        String className = payload.getClass().getSimpleName().toLowerCase();
        if (className.contains("statustext")) {
            try {
                java.lang.reflect.Method textMethod = payload.getClass().getMethod("text");
                Object textObj = textMethod.invoke(payload);

                String text = null;
                if (textObj instanceof byte[]) {
                    text = new String((byte[]) textObj).replace("\0", "").trim();
                } else if (textObj instanceof String) {
                    text = ((String) textObj).trim();
                }

                if (text != null && !text.isEmpty()) {
                    telemetryData.statusMessage = text;
                    log("Status message: " + text);
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
    }

    private void requestDataStreams() {
        if (!isRunning.get() || mavlinkConnection == null) {
            return;
        }

        log("Requesting telemetry streams...");

        try {
            int[] messageIds = {
                    1, 24, 30, 33, 74, 42, 77, 147, 241, 242, 29, 105, 141, 253
            };

            for (int msgId : messageIds) {
                CommandLong cmd = CommandLong.builder()
                        .targetSystem(1)
                        .targetComponent(1)
                        .command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL)
                        .confirmation(0)
                        .param1(msgId)
                        .param2(500000)
                        .param3(0)
                        .param4(0)
                        .param5(0)
                        .param6(0)
                        .param7(0)
                        .build();

                mavlinkConnection.send1(255, 0, cmd);
            }

            // Request a parameter to trigger streams
            ParamRequestRead paramRequest = ParamRequestRead.builder()
                    .targetSystem(1)
                    .targetComponent(1)
                    .paramId("SYSID_THISMAV")
                    .paramIndex(-1)
                    .build();

            mavlinkConnection.send1(255, 0, paramRequest);

        } catch (IOException e) {
            log("Failed to request data streams: " + e.getMessage());
        }
    }

    private void updateMissionTime() {
        // Mission time is calculated in the UI
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
        disconnect();
    }

    public LocalDateTime getMissionStartTime() {
        return missionStartTime;
    }

    public boolean isConnected() {
        return isRunning.get();
    }
}