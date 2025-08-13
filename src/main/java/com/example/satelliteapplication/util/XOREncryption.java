package com.example.satelliteapplication.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * XOR Encryption utility for LoRa (LR900) messages
 * Provides encryption/decryption for messages sent to SpeedyBee via LoRa
 */
public class XOREncryption {

    // Default XOR key - should match between GCS and satellite
    private static final String DEFAULT_KEY = "NAZARX_569287_KEY";

    // Message prefix to identify encrypted LoRa messages in SD card logs
    public static final String LORA_MSG_PREFIX = "LORA_ENC:";

    private final byte[] encryptionKey;

    /**
     * Create encryption utility with default key
     */
    public XOREncryption() {
        this(DEFAULT_KEY);
    }

    /**
     * Create encryption utility with custom key
     * @param key The encryption key to use
     */
    public XOREncryption(String key) {
        this.encryptionKey = key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encrypt a message using XOR cipher
     * @param message The plain text message to encrypt
     * @return Base64 encoded encrypted message
     */
    public String encrypt(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = new byte[messageBytes.length];

        // XOR each byte with the key (cycling through key if message is longer)
        for (int i = 0; i < messageBytes.length; i++) {
            encrypted[i] = (byte) (messageBytes[i] ^ encryptionKey[i % encryptionKey.length]);
        }

        // Encode to Base64 for safe transmission
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypt a message using XOR cipher
     * @param encryptedMessage Base64 encoded encrypted message
     * @return The decrypted plain text message
     */
    public String decrypt(String encryptedMessage) {
        if (encryptedMessage == null || encryptedMessage.isEmpty()) {
            return "";
        }

        try {
            // Decode from Base64
            byte[] encrypted = Base64.getDecoder().decode(encryptedMessage);
            byte[] decrypted = new byte[encrypted.length];

            // XOR each byte with the key (same operation as encryption)
            for (int i = 0; i < encrypted.length; i++) {
                decrypted[i] = (byte) (encrypted[i] ^ encryptionKey[i % encryptionKey.length]);
            }

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Handle invalid Base64
            System.err.println("Failed to decrypt message: Invalid Base64 encoding");
            return "DECRYPT_ERROR: " + encryptedMessage;
        }
    }

    /**
     * Format a message for LoRa transmission with encryption
     * Adds prefix and timestamp for SD card logging
     * @param message The message to send
     * @return Formatted encrypted message ready for transmission
     */
    public String formatLoRaMessage(String message) {
        String encrypted = encrypt(message);
        long timestamp = System.currentTimeMillis();

        // Format: LORA_ENC:<timestamp>:<encrypted_base64>
        return String.format("%s%d:%s", LORA_MSG_PREFIX, timestamp, encrypted);
    }

    /**
     * Parse and decrypt a LoRa message from SD card log
     * @param logLine The log line from SD card
     * @return Decrypted message with metadata, or original line if not encrypted
     */
    public LoRaMessage parseLoRaMessage(String logLine) {
        if (logLine == null || !logLine.startsWith(LORA_MSG_PREFIX)) {
            return new LoRaMessage(logLine, false, 0, logLine);
        }

        try {
            // Remove prefix
            String content = logLine.substring(LORA_MSG_PREFIX.length());

            // Split timestamp and encrypted data
            int colonIndex = content.indexOf(':');
            if (colonIndex == -1) {
                return new LoRaMessage(logLine, false, 0, logLine);
            }

            long timestamp = Long.parseLong(content.substring(0, colonIndex));
            String encryptedData = content.substring(colonIndex + 1);

            // Decrypt the message
            String decrypted = decrypt(encryptedData);

            return new LoRaMessage(logLine, true, timestamp, decrypted);

        } catch (Exception e) {
            System.err.println("Failed to parse LoRa message: " + e.getMessage());
            return new LoRaMessage(logLine, false, 0, "PARSE_ERROR: " + logLine);
        }
    }

    /**
     * Container class for parsed LoRa messages
     */
    public static class LoRaMessage {
        public final String originalLine;
        public final boolean isEncrypted;
        public final long timestamp;
        public final String decryptedContent;

        public LoRaMessage(String originalLine, boolean isEncrypted,
                           long timestamp, String decryptedContent) {
            this.originalLine = originalLine;
            this.isEncrypted = isEncrypted;
            this.timestamp = timestamp;
            this.decryptedContent = decryptedContent;
        }

        /**
         * Get formatted display string
         */
        public String getDisplayString() {
            if (!isEncrypted) {
                return decryptedContent;
            }

            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss.SSS")
                    .format(new java.util.Date(timestamp));

            return String.format("[%s] DECRYPTED: %s", timeStr, decryptedContent);
        }
    }

    /**
     * Validate if a message can be encrypted/decrypted properly
     * Useful for testing the encryption key
     */
    public boolean validateEncryption(String testMessage) {
        try {
            String encrypted = encrypt(testMessage);
            String decrypted = decrypt(encrypted);
            return testMessage.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }
}