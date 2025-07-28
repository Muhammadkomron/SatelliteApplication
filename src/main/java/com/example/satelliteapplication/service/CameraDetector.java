package com.example.satelliteapplication.service;

import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.VideoInputFrameGrabber;

import java.util.ArrayList;
import java.util.List;

public class CameraDetector {

    /**
     * Get default camera list without detection
     */
    public static List<VideoCapture.VideoSource> getDefaultCameras() {
        List<VideoCapture.VideoSource> sources = new ArrayList<>();

        // Add default camera options
        sources.add(new VideoCapture.VideoSource(
                "Built-in Webcam (0)",
                0,
                VideoCapture.VideoSourceType.USB_CAMERA
        ));

        // Add additional camera indices
        for (int i = 1; i < 4; i++) {
            sources.add(new VideoCapture.VideoSource(
                    "USB Camera " + i,
                    i,
                    VideoCapture.VideoSourceType.USB_CAMERA
            ));
        }

        // Add network sources
        sources.add(new VideoCapture.VideoSource(
                "UDP - 5600",
                "udp://0.0.0.0:5600",
                VideoCapture.VideoSourceType.NETWORK
        ));
        sources.add(new VideoCapture.VideoSource(
                "RTSP - rtsp://192.168.1.100:8554",
                "rtsp://192.168.1.100:8554",
                VideoCapture.VideoSourceType.NETWORK
        ));

        return sources;
    }

    /**
     * Detect available cameras using multiple methods (optional, can be called later)
     */
    public static List<VideoCapture.VideoSource> detectCameras() {
        List<VideoCapture.VideoSource> sources = new ArrayList<>();

        // Method 1: Try VideoInputFrameGrabber for Windows
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            sources.addAll(detectWithVideoInput());
        }

        // Method 2: Try OpenCVFrameGrabber for all platforms
        sources.addAll(detectWithOpenCV());

        // Method 3: Add default cameras if no cameras found
        if (sources.isEmpty()) {
            return getDefaultCameras();
        }

        // Add network sources
        sources.add(new VideoCapture.VideoSource(
                "UDP - 5600",
                "udp://0.0.0.0:5600",
                VideoCapture.VideoSourceType.NETWORK
        ));
        sources.add(new VideoCapture.VideoSource(
                "RTSP - rtsp://192.168.1.100:8554",
                "rtsp://192.168.1.100:8554",
                VideoCapture.VideoSourceType.NETWORK
        ));

        return sources;
    }

    private static List<VideoCapture.VideoSource> detectWithVideoInput() {
        List<VideoCapture.VideoSource> sources = new ArrayList<>();

        try {
            VideoInputFrameGrabber grabber = new VideoInputFrameGrabber(0);
            String[] deviceDescriptions = VideoInputFrameGrabber.getDeviceDescriptions();

            if (deviceDescriptions != null) {
                for (int i = 0; i < deviceDescriptions.length; i++) {
                    sources.add(new VideoCapture.VideoSource(
                            deviceDescriptions[i],
                            i,
                            VideoCapture.VideoSourceType.USB_CAMERA
                    ));
                }
            }
        } catch (Exception e) {
            // VideoInput not available on this platform
        }

        return sources;
    }

    private static List<VideoCapture.VideoSource> detectWithOpenCV() {
        List<VideoCapture.VideoSource> sources = new ArrayList<>();

        // On macOS, we typically have fewer cameras, so check only 0-2
        int maxCameras = System.getProperty("os.name").toLowerCase().contains("mac") ? 3 : 6;

        for (int i = 0; i < maxCameras; i++) {
            if (isCameraAvailable(i)) {
                String name = (i == 0) ? "Built-in Webcam" : "USB Camera " + i;
                sources.add(new VideoCapture.VideoSource(
                        name,
                        i,
                        VideoCapture.VideoSourceType.USB_CAMERA
                ));
            }
        }

        return sources;
    }

    private static boolean isCameraAvailable(int index) {
        OpenCVFrameGrabber grabber = null;
        try {
            grabber = new OpenCVFrameGrabber(index);
            // Use minimal settings for testing
            grabber.setImageWidth(160);
            grabber.setImageHeight(120);
            grabber.setFrameRate(1);

            // Quick test - just try to initialize
            grabber.start();

            // If we get here, camera exists
            return true;

        } catch (Exception e) {
            // Camera doesn't exist or can't be accessed
            return false;
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception ignored) {}
            }
        }
    }
}