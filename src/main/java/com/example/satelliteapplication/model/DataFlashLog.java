package com.example.satelliteapplication.model;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class DataFlashLog {
    private static final int HEAD_BYTE1 = 0xA3;
    private static final int HEAD_BYTE2 = 0x95;
    private static final int MSG_FORMAT = 0x80;

    private final Map<Integer, LogFormat> formats = new HashMap<>();
    private final List<LogMessage> messages = new ArrayList<>();
    private final Map<String, List<LogMessage>> messagesByType = new HashMap<>();

    public static class LogFormat {
        public final int type;
        public final int length;
        public final String name;
        public final String format;
        public final String labels;

        public LogFormat(int type, int length, String name, String format, String labels) {
            this.type = type;
            this.length = length;
            this.name = name;
            this.format = format;
            this.labels = labels;
        }
    }

    public static class LogMessage {
        public final String name;
        public long timestamp;
        public final Map<String, Object> fields = new HashMap<>();

        public LogMessage(String name) {
            this.name = name;
        }
    }

    public void parseBinFile(String filename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filename, "r");
             FileChannel channel = file.getChannel()) {

            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            while (buffer.remaining() > 0) {
                if (buffer.get() != (byte) HEAD_BYTE1) continue;
                if (buffer.remaining() == 0 || buffer.get() != (byte) HEAD_BYTE2) continue;

                if (buffer.remaining() < 1) break;
                int msgType = buffer.get() & 0xFF;

                if (msgType == MSG_FORMAT) {
                    parseFormatMessage(buffer);
                } else if (formats.containsKey(msgType)) {
                    parseDataMessage(buffer, msgType);
                }
            }
        }
    }

    private void parseFormatMessage(ByteBuffer buffer) {
        if (buffer.remaining() < 86) return;

        int type = buffer.get() & 0xFF;
        int length = buffer.get() & 0xFF;

        byte[] nameBytes = new byte[4];
        buffer.get(nameBytes);
        String name = new String(nameBytes).trim();

        byte[] formatBytes = new byte[16];
        buffer.get(formatBytes);
        String format = new String(formatBytes).trim();

        byte[] labelBytes = new byte[64];
        buffer.get(labelBytes);
        String labels = new String(labelBytes).trim();

        formats.put(type, new LogFormat(type, length, name, format, labels));
    }

    private void parseDataMessage(ByteBuffer buffer, int msgType) {
        LogFormat format = formats.get(msgType);
        if (format == null || buffer.remaining() < format.length - 3) return;

        LogMessage message = new LogMessage(format.name);
        String[] labelArray = format.labels.split(",");

        for (int i = 0; i < format.format.length() && i < labelArray.length; i++) {
            char type = format.format.charAt(i);
            String label = labelArray[i];

            try {
                Object value = parseField(buffer, type);
                message.fields.put(label, value);

                if (label.equals("TimeUS") && value instanceof Long) {
                    message.timestamp = (Long) value;
                }
            } catch (BufferUnderflowException e) {
                break;
            }
        }

        messages.add(message);
        messagesByType.computeIfAbsent(message.name, k -> new ArrayList<>()).add(message);
    }

    private Object parseField(ByteBuffer buffer, char type) {
        switch (type) {
            case 'b': return buffer.get();
            case 'B': return buffer.get() & 0xFF;
            case 'h': return buffer.getShort();
            case 'H': return buffer.getShort() & 0xFFFF;
            case 'i': return buffer.getInt();
            case 'I': return buffer.getInt() & 0xFFFFFFFFL;
            case 'f': return buffer.getFloat();
            case 'd': return buffer.getDouble();
            case 'n':
                byte[] n = new byte[4];
                buffer.get(n);
                return new String(n).trim();
            case 'N':
                byte[] N = new byte[16];
                buffer.get(N);
                return new String(N).trim();
            case 'Z':
                byte[] Z = new byte[64];
                buffer.get(Z);
                return new String(Z).trim();
            case 'q': return buffer.getLong();
            case 'Q': return buffer.getLong();
            default: return null;
        }
    }

    public List<LogMessage> getMessages() {
        return messages;
    }

    public List<LogMessage> getMessagesByType(String type) {
        return messagesByType.getOrDefault(type, new ArrayList<>());
    }

    public Set<String> getMessageTypes() {
        return new TreeSet<>(messagesByType.keySet());
    }
}