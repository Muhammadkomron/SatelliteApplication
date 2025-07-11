package com.example.satelliteapplication;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class LR900Diagnostic {

    public static void main(String[] args) {
        System.out.println("LR900 Diagnostic Tool");
        System.out.println("====================");

        // List all available ports
        SerialPort[] ports = SerialPort.getCommPorts();
        System.out.println("Available serial ports:");
        for (int i = 0; i < ports.length; i++) {
            System.out.println((i + 1) + ". " + ports[i].getSystemPortName() +
                    " - " + ports[i].getDescriptivePortName());
        }

        if (ports.length == 0) {
            System.out.println("No serial ports found!");
            return;
        }

        // Select port
        Scanner scanner = new Scanner(System.in);
        System.out.print("Select port (1-" + ports.length + "): ");
        int selection = scanner.nextInt() - 1;

        if (selection < 0 || selection >= ports.length) {
            System.out.println("Invalid selection!");
            return;
        }

        SerialPort selectedPort = ports[selection];
        System.out.println("Selected: " + selectedPort.getSystemPortName());

        // Test different baud rates
        int[] baudRates = {9600, 19200, 38400, 57600, 115200};

        for (int baudRate : baudRates) {
            System.out.println("\nTesting baud rate: " + baudRate);
            testBaudRate(selectedPort, baudRate);
        }

        scanner.close();
    }

    private static void testBaudRate(SerialPort port, int baudRate) {
        // Configure port
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(1);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 1000);

        if (!port.openPort()) {
            System.out.println("  Failed to open port");
            return;
        }

        try {
            InputStream is = port.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            int totalBytes = 0;
            long startTime = System.currentTimeMillis();

            System.out.println("  Listening for 5 seconds...");

            while (System.currentTimeMillis() - startTime < 5000) {
                bytesRead = is.available();
                if (bytesRead > 0) {
                    bytesRead = is.read(buffer, 0, Math.min(bytesRead, buffer.length));
                    if (bytesRead > 0) {
                        totalBytes += bytesRead;
                        System.out.print("  Data received: ");
                        for (int i = 0; i < Math.min(bytesRead, 20); i++) {
                            System.out.printf("%02X ", buffer[i] & 0xFF);
                        }
                        if (bytesRead > 20) {
                            System.out.print("...");
                        }
                        System.out.println();

                        // Check for MAVLink magic bytes
                        for (int i = 0; i < bytesRead; i++) {
                            if ((buffer[i] & 0xFF) == 0xFE || (buffer[i] & 0xFF) == 0xFD) {
                                System.out.println("  *** MAVLink magic byte detected! ***");
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            System.out.println("  Total bytes received: " + totalBytes);
            if (totalBytes > 0) {
                System.out.println("  *** This baud rate is receiving data! ***");
            }

        } catch (IOException e) {
            System.out.println("  Error: " + e.getMessage());
        } finally {
            port.closePort();
        }
    }
}