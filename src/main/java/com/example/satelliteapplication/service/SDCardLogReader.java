package com.example.satelliteapplication.service;

import com.example.satelliteapplication.util.XOREncryption;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SD Card Log Reader with XOR decryption support
 * Reads logs from SpeedyBee SD card and decrypts LoRa messages
 */
public class SDCardLogReader {

    private final XOREncryption xorEncryption;
    private final List<LogEntry> logEntries;
    private final ObservableList<LogEntry> observableLogEntries;

    public SDCardLogReader() {
        this.xorEncryption = new XOREncryption();
        this.logEntries = new ArrayList<>();
        this.observableLogEntries = FXCollections.observableArrayList();
    }

    /**
     * Log entry class
     */
    public static class LogEntry {
        public final long timestamp;
        public final String type;
        public final String originalMessage;
        public final String decryptedMessage;
        public final boolean isEncrypted;

        public LogEntry(long timestamp, String type, String originalMessage,
                        String decryptedMessage, boolean isEncrypted) {
            this.timestamp = timestamp;
            this.type = type;
            this.originalMessage = originalMessage;
            this.decryptedMessage = decryptedMessage;
            this.isEncrypted = isEncrypted;
        }

        public String getDisplayString() {
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss.SSS")
                    .format(new Date(timestamp));

            if (isEncrypted) {
                return String.format("[%s] [%s] DECRYPTED: %s", timeStr, type, decryptedMessage);
            } else {
                return String.format("[%s] [%s] %s", timeStr, type, originalMessage);
            }
        }
    }

    /**
     * Read logs from SD card directory
     * @param sdCardPath Path to SD card mount point
     * @return List of log entries with decrypted messages
     */
    public List<LogEntry> readSDCardLogs(String sdCardPath) throws IOException {
        logEntries.clear();

        // Common log file patterns on SpeedyBee SD cards
        String[] logPatterns = {
                "*.log",
                "*.txt",
                "FLIGHT*.log",
                "LORA*.log",
                "speedybee*.log"
        };

        Path sdPath = Paths.get(sdCardPath);
        if (!Files.exists(sdPath)) {
            throw new IOException("SD card path does not exist: " + sdCardPath);
        }

        // Find all log files
        List<Path> logFiles = findLogFiles(sdPath, logPatterns);

        // Process each log file
        for (Path logFile : logFiles) {
            processLogFile(logFile);
        }

        // Sort by timestamp
        logEntries.sort(Comparator.comparingLong(e -> e.timestamp));

        Platform.runLater(() -> {
            observableLogEntries.clear();
            observableLogEntries.addAll(logEntries);
        });

        return logEntries;
    }

    /**
     * Find log files matching patterns
     */
    private List<Path> findLogFiles(Path directory, String[] patterns) throws IOException {
        List<Path> logFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String fileName = entry.getFileName().toString().toLowerCase();

                    // Check if file matches any pattern
                    for (String pattern : patterns) {
                        if (matchesPattern(fileName, pattern.toLowerCase())) {
                            logFiles.add(entry);
                            break;
                        }
                    }
                }
            }
        }

        return logFiles;
    }

    /**
     * Simple pattern matching
     */
    private boolean matchesPattern(String fileName, String pattern) {
        if (pattern.startsWith("*")) {
            return fileName.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return fileName.startsWith(pattern.substring(0, pattern.length() - 1));
        } else if (pattern.contains("*")) {
            String[] parts = pattern.split("\\*");
            return fileName.startsWith(parts[0]) && fileName.endsWith(parts[1]);
        }
        return fileName.equals(pattern);
    }

    /**
     * Process a single log file
     */
    private void processLogFile(Path logFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                processLogLine(line, logFile.getFileName().toString(), lineNumber);
            }
        }
    }

    /**
     * Process a single log line
     */
    private void processLogLine(String line, String fileName, int lineNumber) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        // Extract timestamp if present
        long timestamp = extractTimestamp(line);
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }

        // Check if this is an encrypted LoRa message
        if (line.contains(XOREncryption.LORA_MSG_PREFIX)) {
            processEncryptedMessage(line, timestamp, fileName);
        } else {
            // Regular log entry
            logEntries.add(new LogEntry(
                    timestamp,
                    "LOG",
                    line,
                    line,
                    false
            ));
        }
    }

    /**
     * Process encrypted LoRa message
     */
    private void processEncryptedMessage(String line, long timestamp, String source) {
        // Find the encrypted portion
        int prefixIndex = line.indexOf(XOREncryption.LORA_MSG_PREFIX);
        if (prefixIndex == -1) {
            return;
        }

        // Extract the encrypted message
        String encryptedPortion = line.substring(prefixIndex);

        // Parse and decrypt
        XOREncryption.LoRaMessage loraMsg = xorEncryption.parseLoRaMessage(encryptedPortion);

        if (loraMsg.isEncrypted) {
            logEntries.add(new LogEntry(
                    loraMsg.timestamp,
                    "LORA",
                    line,
                    loraMsg.decryptedContent,
                    true
            ));
        }
    }

    /**
     * Extract timestamp from log line
     */
    private long extractTimestamp(String line) {
        // Try to find timestamp patterns
        // Common formats: [timestamp], timestamp:, or numeric timestamp

        // Pattern 1: [1234567890]
        if (line.startsWith("[") && line.contains("]")) {
            try {
                int endIndex = line.indexOf("]");
                String timestampStr = line.substring(1, endIndex);
                return Long.parseLong(timestampStr);
            } catch (Exception ignored) {}
        }

        // Pattern 2: timestamp at beginning
        String[] parts = line.split("[:\\s,]");
        if (parts.length > 0) {
            try {
                return Long.parseLong(parts[0]);
            } catch (Exception ignored) {}
        }

        return 0;
    }

    /**
     * Search for specific encrypted messages
     */
    public List<LogEntry> searchEncryptedMessages(String searchTerm) {
        return logEntries.stream()
                .filter(entry -> entry.isEncrypted)
                .filter(entry -> entry.decryptedMessage.toLowerCase()
                        .contains(searchTerm.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Get only encrypted messages
     */
    public List<LogEntry> getEncryptedMessages() {
        return logEntries.stream()
                .filter(entry -> entry.isEncrypted)
                .collect(Collectors.toList());
    }

    /**
     * Export decrypted messages to file
     */
    public void exportDecryptedMessages(File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("=== DECRYPTED LORA MESSAGES ===");
            writer.println("Generated: " + new Date());
            writer.println("Total encrypted messages: " + getEncryptedMessages().size());
            writer.println("=====================================\n");

            for (LogEntry entry : getEncryptedMessages()) {
                writer.println(entry.getDisplayString());
                writer.println("Original: " + entry.originalMessage);
                writer.println("---");
            }
        }
    }

    /**
     * Get observable list for JavaFX TableView
     */
    public ObservableList<LogEntry> getObservableLogEntries() {
        return observableLogEntries;
    }

    /**
     * Clear all log entries
     */
    public void clear() {
        logEntries.clear();
        Platform.runLater(() -> observableLogEntries.clear());
    }
}