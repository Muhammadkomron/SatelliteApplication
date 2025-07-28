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
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCapture {
    private FrameGrabber grabber;
    private Thread captureThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ImageView primaryImageView;
    private ImageView externalImageView;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();

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

    public void startCapture(VideoSource source, ImageView imageView) {
        if (isRunning.get()) {
            stopCapture();
        }

        this.primaryImageView = imageView;
        isRunning.set(true);

        captureThread = new Thread(() -> {
            try {
                if (source.getType() == VideoSourceType.USB_CAMERA) {
                    int deviceIndex = (Integer) source.getSource();
                    grabber = new OpenCVFrameGrabber(deviceIndex);

                    // Set capture parameters
                    grabber.setImageWidth(1280);
                    grabber.setImageHeight(720);
                    grabber.setFrameRate(30);

                    grabber.start();

                    System.out.println("Started capturing from: " + source.getName());

                    while (isRunning.get()) {
                        Frame frame = grabber.grab();
                        if (frame != null && frame.image != null) {
                            BufferedImage bufferedImage = converter.convert(frame);
                            if (bufferedImage != null) {
                                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                                Platform.runLater(() -> {
                                    // Update primary display
                                    if (primaryImageView != null) {
                                        primaryImageView.setImage(fxImage);
                                    }

                                    // Update external display if available
                                    if (externalImageView != null) {
                                        externalImageView.setImage(fxImage);
                                    }
                                });
                            }
                        }

                        // Small delay to control frame rate
                        Thread.sleep(33); // ~30 FPS
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    System.err.println("Error capturing video: " + e.getMessage());
                });
            } finally {
                try {
                    if (grabber != null) {
                        grabber.stop();
                        grabber.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stopCapture() {
        isRunning.set(false);

        if (captureThread != null) {
            try {
                captureThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        primaryImageView = null;
        externalImageView = null;
    }

    public void setExternalImageView(ImageView imageView) {
        this.externalImageView = imageView;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}