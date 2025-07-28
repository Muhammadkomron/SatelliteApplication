package com.example.satelliteapplication.service;

import org.bytedeco.javacv.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCapture {
    private FrameGrabber grabber;
    private final Java2DFrameConverter converter;
    private Thread captureThread;
    private final AtomicBoolean isCapturing;
    private ImageView targetImageView;

    public VideoCapture() {
        this.converter = new Java2DFrameConverter();
        this.isCapturing = new AtomicBoolean(false);
    }

    /**
     * Get available video sources (USB cameras and webcams)
     */
    public static List<VideoSource> getAvailableVideoSources() {
        List<VideoSource> sources = new ArrayList<>();

        // Check for available cameras (0-5 typical range)
        for (int i = 0; i < 6; i++) {
            try {
                OpenCVFrameGrabber testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.start();

                // If we can start it, it exists
                String name = "Camera " + i;
                if (i == 0) {
                    name = "Built-in Webcam";
                } else {
                    name = "USB Camera " + i;
                }

                sources.add(new VideoSource(name, i, VideoSourceType.USB_CAMERA));
                testGrabber.stop();
                testGrabber.release();

            } catch (Exception e) {
                // Camera index doesn't exist, continue checking
            }
        }

        // Add network sources as examples (not implemented)
        sources.add(new VideoSource("UDP - 5600", "udp://0.0.0.0:5600", VideoSourceType.NETWORK));
        sources.add(new VideoSource("RTSP - rtsp://192.168.1.100:8554", "rtsp://192.168.1.100:8554", VideoSourceType.NETWORK));

        return sources;
    }

    /**
     * Start video capture from the specified source
     */
    public void startCapture(VideoSource source, ImageView imageView) throws FrameGrabber.Exception {
        if (isCapturing.get()) {
            stopCapture();
        }

        this.targetImageView = imageView;

        // Create appropriate grabber based on source type
        switch (source.getType()) {
            case USB_CAMERA:
                grabber = new OpenCVFrameGrabber((Integer) source.getSource());
                break;
            case NETWORK:
                // For network sources, you would use FFmpegFrameGrabber
                grabber = new FFmpegFrameGrabber((String) source.getSource());
                break;
            default:
                throw new IllegalArgumentException("Unsupported video source type");
        }

        // Configure grabber
        if (grabber instanceof OpenCVFrameGrabber cvGrabber) {
            cvGrabber.setImageWidth(640);
            cvGrabber.setImageHeight(480);
            cvGrabber.setFrameRate(30);
        }

        grabber.start();
        isCapturing.set(true);

        // Start capture thread
        captureThread = new Thread(() -> {
            try {
                while (isCapturing.get()) {
                    Frame frame = grabber.grab();
                    if (frame != null && frame.image != null) {
                        BufferedImage bufferedImage = converter.convert(frame);
                        if (bufferedImage != null) {
                            Platform.runLater(() -> {
                                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                                targetImageView.setImage(fxImage);
                            });
                        }
                    }

                    // Control frame rate
                    Thread.sleep(33); // ~30 FPS
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    // Handle error in UI
                    System.err.println("Video capture error: " + e.getMessage());
                });
            }
        });

        captureThread.setDaemon(true);
        captureThread.start();
    }

    /**
     * Stop video capture
     */
    public void stopCapture() {
        isCapturing.set(false);

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
            }
            grabber = null;
        }
    }

    /**
     * Check if currently capturing
     */
    public boolean isCapturing() {
        return isCapturing.get();
    }

    /**
     * Video source representation
     */
    public static class VideoSource {
        private final String name;
        private final Object source;
        private final VideoSourceType type;

        public VideoSource(String name, Object source, VideoSourceType type) {
            this.name = name;
            this.source = source;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Object getSource() {
            return source;
        }

        public VideoSourceType getType() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Video source types
     */
    public enum VideoSourceType {
        USB_CAMERA,
        NETWORK
    }
}