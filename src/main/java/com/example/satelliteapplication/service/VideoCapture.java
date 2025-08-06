package com.example.satelliteapplication.service;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCapture {
    private FrameGrabber grabber;
    private ScheduledExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile ImageView primaryImageView;
    private volatile ImageView externalImageView;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private VideoSource currentSource;

    // Performance optimization
    private BufferedImage reusableBufferedImage;
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL_MS = 33; // 30 FPS

    public enum VideoSourceType {
        USB_CAMERA,
        NETWORK_STREAM
    }

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

    public void startCapture(VideoSource source, ImageView imageView) throws Exception {
        if (isRunning.get()) {
            stopCapture();
        }

        this.primaryImageView = imageView;
        this.currentSource = source;

        if (source.getType() == VideoSourceType.USB_CAMERA) {
            int deviceIndex = (Integer) source.getSource();

            // Try to open the camera
            grabber = new OpenCVFrameGrabber(deviceIndex);
            grabber.setImageWidth(1280);
            grabber.setImageHeight(720);
            grabber.setFrameRate(30);

            try {
                // Try MJPEG first for better performance
                grabber.setFormat("mjpeg");
                grabber.start();

                // Test if we can grab a frame
                Frame testFrame = grabber.grab();
                if (testFrame == null || testFrame.image == null) {
                    throw new Exception("Camera " + deviceIndex + " is not available");
                }
            } catch (FrameGrabber.Exception e) {
                // If MJPEG fails, try without format specification
                try {
                    grabber.stop();
                } catch (Exception ignored) {}

                grabber = new OpenCVFrameGrabber(deviceIndex);
                grabber.setImageWidth(1280);
                grabber.setImageHeight(720);
                grabber.setFrameRate(30);
                grabber.start();

                // Test again
                Frame testFrame = grabber.grab();
                if (testFrame == null || testFrame.image == null) {
                    throw new Exception("Camera " + deviceIndex + " is not available");
                }
            }

            isRunning.set(true);
            System.out.println("Started capturing from: " + source.getName());

            // Start capture thread
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VideoCapture");
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            });

            executor.scheduleAtFixedRate(this::captureFrame, 0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);

        } else {
            throw new Exception("Network sources not yet implemented");
        }
    }

    private void captureFrame() {
        if (!isRunning.get() || grabber == null) {
            return;
        }

        try {
            // Skip frame if we're running behind
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime < FRAME_INTERVAL_MS - 5) {
                return;
            }
            lastFrameTime = currentTime;

            Frame frame = grabber.grab();
            if (frame != null && frame.image != null) {
                // Convert to BufferedImage
                BufferedImage bufferedImage = converter.getBufferedImage(frame);
                if (bufferedImage != null) {
                    reusableBufferedImage = bufferedImage;

                    // Convert to FX Image
                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                    // Update UI on FX thread
                    Platform.runLater(() -> {
                        ImageView primary = primaryImageView;
                        if (primary != null) {
                            primary.setImage(fxImage);
                        }

                        ImageView external = externalImageView;
                        if (external != null) {
                            external.setImage(fxImage);
                        }
                    });
                }
            }
        } catch (Exception e) {
            if (isRunning.get()) {
                System.err.println("Frame capture error: " + e.getMessage());
            }
        }
    }

    public void stopCapture() {
        isRunning.set(false);

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                System.err.println("Error stopping grabber: " + e.getMessage());
            }
            grabber = null;
        }

        // Clear references
        primaryImageView = null;
        externalImageView = null;
        reusableBufferedImage = null;
        currentSource = null;
    }

    public void setPrimaryImageView(ImageView imageView) {
        this.primaryImageView = imageView;
    }

    public void setExternalImageView(ImageView imageView) {
        this.externalImageView = imageView;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public VideoSource getCurrentSource() {
        return currentSource;
    }
}