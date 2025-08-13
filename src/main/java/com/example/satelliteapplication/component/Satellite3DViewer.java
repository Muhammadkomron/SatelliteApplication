package com.example.satelliteapplication.component;

import javafx.animation.AnimationTimer;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;

import com.example.satelliteapplication.constants.ApplicationConstants;

/**
 * 3D viewer component for visualizing satellite orientation (yaw, pitch, roll)
 */
public class Satellite3DViewer {

    private final SubScene subScene;

    // Rotation transforms
    private final Rotate rotateX;
    private final Rotate rotateY;
    private final Rotate rotateZ;

    // Target angles for smooth animation
    private double targetPitch = 0;
    private double targetRoll = 0;
    private double targetYaw = 0;

    // Current angles for smooth interpolation
    private double currentPitch = 0;
    private double currentRoll = 0;
    private double currentYaw = 0;

    private AnimationTimer animationTimer;

    public Satellite3DViewer(double width, double height) {
        // Create 3D root
        Group root3D = new Group();

        // Create satellite group for transformations
        Group satelliteGroup = new Group();

        // Create satellite body (rectangular box)
        Box satelliteBody = new Box(
            ApplicationConstants.SATELLITE_BODY_WIDTH, 
            ApplicationConstants.SATELLITE_BODY_HEIGHT, 
            ApplicationConstants.SATELLITE_BODY_DEPTH
        );
        PhongMaterial bodyMaterial = new PhongMaterial();
        bodyMaterial.setDiffuseColor(Color.web(ApplicationConstants.PRIMARY_COLOR));
        bodyMaterial.setSpecularColor(Color.web("#5dade2"));
        satelliteBody.setMaterial(bodyMaterial);

        // Create front indicator (small cylinder to show forward direction)
        Cylinder frontIndicator = new Cylinder(3, 15);
        PhongMaterial indicatorMaterial = new PhongMaterial();
        indicatorMaterial.setDiffuseColor(Color.web(ApplicationConstants.ACCENT_COLOR));
        indicatorMaterial.setSpecularColor(Color.web("#ec7063"));
        frontIndicator.setMaterial(indicatorMaterial);
        frontIndicator.setTranslateX(35); // Position at front of satellite
        frontIndicator.setRotationAxis(Rotate.Z_AXIS);
        frontIndicator.setRotate(90);

        // Add solar panels (simplified as thin boxes)
        Box leftPanel = new Box(5, 18, 60);
        PhongMaterial panelMaterial = new PhongMaterial();
        panelMaterial.setDiffuseColor(Color.web(ApplicationConstants.SECONDARY_COLOR));
        panelMaterial.setSpecularColor(Color.web("#34495e"));
        leftPanel.setMaterial(panelMaterial);
        leftPanel.setTranslateX(-35);

        Box rightPanel = new Box(5, 18, 60);
        rightPanel.setMaterial(panelMaterial);
        rightPanel.setTranslateX(35);

        // Add antenna (simplified as thin cylinder)
        Cylinder antenna = new Cylinder(1, 25);
        PhongMaterial antennaMaterial = new PhongMaterial();
        antennaMaterial.setDiffuseColor(Color.web(ApplicationConstants.NEUTRAL_COLOR));
        antenna.setMaterial(antennaMaterial);
        antenna.setTranslateY(-20);

        // Assemble satellite
        satelliteGroup.getChildren().addAll(
                satelliteBody,
                frontIndicator,
                leftPanel,
                rightPanel,
                antenna
        );

        // Create rotation transforms
        rotateX = new Rotate(0, Rotate.X_AXIS);
        rotateY = new Rotate(0, Rotate.Y_AXIS);
        rotateZ = new Rotate(0, Rotate.Z_AXIS);

        satelliteGroup.getTransforms().addAll(rotateX, rotateY, rotateZ);

        root3D.getChildren().add(satelliteGroup);

        // Create camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(ApplicationConstants.CAMERA_DISTANCE);
        camera.setNearClip(0.1);
        camera.setFarClip(1000);
        camera.setFieldOfView(35);

        // Create subscene with transparent background
        subScene = new SubScene(root3D, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCamera(camera);

        // Add subtle ambient light
        AmbientLight ambientLight = new AmbientLight(Color.web("#f0f0f0", 0.6));
        root3D.getChildren().add(ambientLight);

        // Add directional light for better 3D effect
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(100);
        pointLight.setTranslateY(-100);
        pointLight.setTranslateZ(-150);
        root3D.getChildren().add(pointLight);

        // Initialize animation timer for smooth transitions
        setupAnimationTimer();
    }

    private void setupAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Smoothly interpolate to target angles
                currentPitch += (targetPitch - currentPitch) * ApplicationConstants.INTERPOLATION_SPEED;
                currentRoll += (targetRoll - currentRoll) * ApplicationConstants.INTERPOLATION_SPEED;
                currentYaw += (targetYaw - currentYaw) * ApplicationConstants.INTERPOLATION_SPEED;

                // Apply rotations
                rotateX.setAngle(currentPitch);
                rotateZ.setAngle(currentRoll);
                rotateY.setAngle(currentYaw);

                // Stop timer if close enough to target
                if (Math.abs(targetPitch - currentPitch) < 0.1 &&
                        Math.abs(targetRoll - currentRoll) < 0.1 &&
                        Math.abs(targetYaw - currentYaw) < 0.1) {
                    currentPitch = targetPitch;
                    currentRoll = targetRoll;
                    currentYaw = targetYaw;
                    rotateX.setAngle(currentPitch);
                    rotateZ.setAngle(currentRoll);
                    rotateY.setAngle(currentYaw);
                    stop();
                }
            }
        };
    }

    /**
     * Update satellite orientation
     * @param pitch Pitch angle in degrees
     * @param roll Roll angle in degrees
     * @param yaw Yaw angle in degrees
     */
    public void updateOrientation(double pitch, double roll, double yaw) {
        this.targetPitch = pitch;
        this.targetRoll = roll;
        this.targetYaw = yaw;

        // Start animation if not running
        if (animationTimer != null) {
            animationTimer.start();
        }
    }

    /**
     * Set orientation immediately without animation
     */
    public void setOrientationImmediate(double pitch, double roll, double yaw) {
        this.targetPitch = pitch;
        this.targetRoll = roll;
        this.targetYaw = yaw;
        this.currentPitch = pitch;
        this.currentRoll = roll;
        this.currentYaw = yaw;

        rotateX.setAngle(pitch);
        rotateZ.setAngle(roll);
        rotateY.setAngle(yaw);
    }

    /**
     * Get the SubScene containing the 3D view
     */
    public SubScene getSubScene() {
        return subScene;
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /**
     * Reset orientation to zero
     */
    public void reset() {
        updateOrientation(0, 0, 0);
    }
}