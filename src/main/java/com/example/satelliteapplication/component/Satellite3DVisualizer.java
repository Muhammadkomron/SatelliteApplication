package com.example.satelliteapplication.component;

import javafx.animation.AnimationTimer;
import javafx.scene.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * 3D Visualization component for satellite orientation (pitch, roll, yaw)
 * Shows a 3D rectangular box representing the satellite with real-time rotation
 */
public class Satellite3DVisualizer extends Pane {

    // 3D Scene components
    private SubScene subScene;
    private Group root3D;
    private PerspectiveCamera camera;

    // Satellite model
    private Box satelliteBody;
    private Group satelliteGroup;

    // Reference axes
    private Cylinder xAxis, yAxis, zAxis;
    private Group axesGroup;

    // Rotation transforms
    private Rotate rotateX, rotateY, rotateZ;

    // Current orientation values
    private double currentPitch = 0;
    private double currentRoll = 0;
    private double currentYaw = 0;

    // Target orientation values (for smooth animation)
    private double targetPitch = 0;
    private double targetRoll = 0;
    private double targetYaw = 0;

    // Animation
    private AnimationTimer animationTimer;
    private static final double SMOOTHING_FACTOR = 0.15; // Smooth rotation transitions

    // Display options
    private boolean showAxes = true;
    private boolean showLabels = true;

    public Satellite3DVisualizer() {
        initialize();
    }

    private void initialize() {
        // Create 3D root group
        root3D = new Group();

        // Create satellite model
        createSatelliteModel();

        // Create reference axes
        createReferenceAxes();

        // Set up camera
        setupCamera();

        // Create SubScene for 3D content
        subScene = new SubScene(root3D, 400, 300, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(30, 30, 35)); // Dark background
        subScene.setCamera(camera);

        // Bind SubScene size to parent
        subScene.widthProperty().bind(this.widthProperty());
        subScene.heightProperty().bind(this.heightProperty());

        // Add SubScene to this pane
        getChildren().add(subScene);

        // Set up animation timer for smooth transitions
        setupAnimationTimer();

        // Add lighting
        addLighting();

        // Set initial style
        setStyle("-fx-background-color: #1e1e23; -fx-border-color: #3498db; -fx-border-width: 2; -fx-border-radius: 10;");
        setMinHeight(250);
        setPrefHeight(300);
    }

    private void createSatelliteModel() {
        // Create main satellite body (rectangular box)
        satelliteBody = new Box(100, 40, 60);

        // Create material with metallic appearance
        PhongMaterial bodyMaterial = new PhongMaterial();
        bodyMaterial.setDiffuseColor(Color.rgb(70, 130, 180)); // Steel blue
        bodyMaterial.setSpecularColor(Color.rgb(200, 200, 200));
        bodyMaterial.setSpecularPower(32);
        satelliteBody.setMaterial(bodyMaterial);

        // Create satellite group with transformations
        satelliteGroup = new Group();

        // Add solar panels (simplified as flat boxes)
        Box leftPanel = new Box(150, 2, 40);
        PhongMaterial panelMaterial = new PhongMaterial();
        panelMaterial.setDiffuseColor(Color.rgb(50, 50, 150)); // Dark blue for solar panels
        panelMaterial.setSpecularColor(Color.rgb(100, 100, 200));
        leftPanel.setMaterial(panelMaterial);
        leftPanel.setTranslateX(-125);

        Box rightPanel = new Box(150, 2, 40);
        rightPanel.setMaterial(panelMaterial);
        rightPanel.setTranslateX(125);

        // Add antenna (simplified as cylinder)
        Cylinder antenna = new Cylinder(2, 30);
        PhongMaterial antennaMaterial = new PhongMaterial();
        antennaMaterial.setDiffuseColor(Color.SILVER);
        antenna.setMaterial(antennaMaterial);
        antenna.setTranslateY(-35);
        antenna.setRotationAxis(Rotate.X_AXIS);
        antenna.setRotate(90);

        // Add components to satellite group
        satelliteGroup.getChildren().addAll(satelliteBody, leftPanel, rightPanel, antenna);

        // Create rotation transforms
        rotateX = new Rotate(0, Rotate.X_AXIS);
        rotateY = new Rotate(0, Rotate.Y_AXIS);
        rotateZ = new Rotate(0, Rotate.Z_AXIS);

        // Apply transforms to satellite group
        satelliteGroup.getTransforms().addAll(rotateY, rotateX, rotateZ);

        // Add satellite to root
        root3D.getChildren().add(satelliteGroup);
    }

    private void createReferenceAxes() {
        axesGroup = new Group();

        // X-axis (Roll) - Red
        xAxis = new Cylinder(1, 200);
        PhongMaterial xMaterial = new PhongMaterial();
        xMaterial.setDiffuseColor(Color.RED);
        xMaterial.setSpecularColor(Color.DARKRED);
        xAxis.setMaterial(xMaterial);
        xAxis.setRotationAxis(Rotate.Z_AXIS);
        xAxis.setRotate(90);

        // Y-axis (Pitch) - Green
        yAxis = new Cylinder(1, 200);
        PhongMaterial yMaterial = new PhongMaterial();
        yMaterial.setDiffuseColor(Color.LIGHTGREEN);
        yMaterial.setSpecularColor(Color.DARKGREEN);
        yAxis.setMaterial(yMaterial);

        // Z-axis (Yaw) - Blue
        zAxis = new Cylinder(1, 200);
        PhongMaterial zMaterial = new PhongMaterial();
        zMaterial.setDiffuseColor(Color.LIGHTBLUE);
        zMaterial.setSpecularColor(Color.DARKBLUE);
        zAxis.setMaterial(zMaterial);
        zAxis.setRotationAxis(Rotate.X_AXIS);
        zAxis.setRotate(90);

        // Add axis end markers (small spheres)
        Sphere xMarker = new Sphere(3);
        xMarker.setMaterial(xMaterial);
        xMarker.setTranslateX(100);

        Sphere yMarker = new Sphere(3);
        yMarker.setMaterial(yMaterial);
        yMarker.setTranslateY(-100);

        Sphere zMarker = new Sphere(3);
        zMarker.setMaterial(zMaterial);
        zMarker.setTranslateZ(100);

        axesGroup.getChildren().addAll(xAxis, yAxis, zAxis, xMarker, yMarker, zMarker);

        // Make axes slightly transparent
        axesGroup.setOpacity(0.5);

        root3D.getChildren().add(axesGroup);
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000);
        camera.setFieldOfView(35);

        // Position camera
        camera.getTransforms().addAll(
                new Translate(0, 0, -500),
                new Rotate(-20, Rotate.X_AXIS),
                new Rotate(-30, Rotate.Y_AXIS)
        );
    }

    private void addLighting() {
        // Ambient light for overall illumination
        AmbientLight ambientLight = new AmbientLight(Color.rgb(60, 60, 70));

        // Point light from top-front
        PointLight pointLight1 = new PointLight(Color.WHITE);
        pointLight1.setTranslateX(100);
        pointLight1.setTranslateY(-100);
        pointLight1.setTranslateZ(-100);

        // Point light from bottom-back
        PointLight pointLight2 = new PointLight(Color.rgb(150, 150, 180));
        pointLight2.setTranslateX(-100);
        pointLight2.setTranslateY(100);
        pointLight2.setTranslateZ(100);

        root3D.getChildren().addAll(ambientLight, pointLight1, pointLight2);
    }

    private void setupAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Smoothly interpolate between current and target values
                currentPitch += (targetPitch - currentPitch) * SMOOTHING_FACTOR;
                currentRoll += (targetRoll - currentRoll) * SMOOTHING_FACTOR;
                currentYaw += (targetYaw - currentYaw) * SMOOTHING_FACTOR;

                // Apply rotations
                rotateX.setAngle(currentPitch);
                rotateZ.setAngle(currentRoll);
                rotateY.setAngle(currentYaw);
            }
        };
        animationTimer.start();
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
    }

    /**
     * Reset orientation to zero
     */
    public void resetOrientation() {
        updateOrientation(0, 0, 0);
    }

    /**
     * Toggle axes visibility
     */
    public void toggleAxes() {
        showAxes = !showAxes;
        axesGroup.setVisible(showAxes);
    }

    /**
     * Set axes visibility
     */
    public void setAxesVisible(boolean visible) {
        showAxes = visible;
        axesGroup.setVisible(visible);
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
     * Get current pitch value
     */
    public double getCurrentPitch() {
        return currentPitch;
    }

    /**
     * Get current roll value
     */
    public double getCurrentRoll() {
        return currentRoll;
    }

    /**
     * Get current yaw value
     */
    public double getCurrentYaw() {
        return currentYaw;
    }
}