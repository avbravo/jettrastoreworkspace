package io.jettra.policefx.world3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/**
 * Objeto 3D autónomo con movimiento dinámico en el plano cartesiano.
 * Representa centinelas, sondas de telemetría y paquetes de datos en tránsito sobre el clúster.
 */
public final class AutonomousEntityMesh extends Group {

    public enum MovementPattern {
        LISSAJOUS_3D,
        ORBITAL_PATROL,
        AXIS_SWEEPER_X,
        AXIS_SWEEPER_Z,
        NODE_CARRIER_INTERCEPT
    }

    private final String entityId;
    private final MovementPattern pattern;
    private final Sphere coreSphere;
    private final Cylinder scanRing;
    private final Cylinder probeBeam;
    private final PhongMaterial coreMat;
    private final PhongMaterial ringMat;
    private final Rotate ringRotate = new Rotate(0, Rotate.Y_AXIS);

    private double time = 0.0;
    private final double speed;
    private final double radiusX;
    private final double radiusZ;
    private final double baseY;
    private final double phase;

    public AutonomousEntityMesh(String entityId, MovementPattern pattern, Color coreColor, Color ringColor,
                                double speed, double radiusX, double radiusZ, double baseY, double phase) {
        this.entityId = entityId;
        this.pattern = pattern;
        this.speed = speed;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.baseY = baseY;
        this.phase = phase;

        // 1. Núcleo cuántico esférico
        this.coreSphere = new Sphere(7.0);
        this.coreMat = new PhongMaterial(coreColor);
        coreMat.setSpecularColor(Color.WHITE);
        coreSphere.setMaterial(coreMat);

        // 2. Anillo holográfico de escaneo autónomo
        this.scanRing = new Cylinder(15.0, 1.5);
        this.ringMat = new PhongMaterial(ringColor);
        scanRing.setMaterial(ringMat);
        scanRing.getTransforms().addAll(new Rotate(90, Rotate.X_AXIS), ringRotate);

        // 3. Haz de luz detector hacia el plano cartesiano
        this.probeBeam = new Cylinder(1.2, Math.abs(baseY) + 5);
        PhongMaterial beamMat = new PhongMaterial(Color.rgb(
            (int)(coreColor.getRed() * 255),
            (int)(coreColor.getGreen() * 255),
            (int)(coreColor.getBlue() * 255),
            0.55
        ));
        probeBeam.setMaterial(beamMat);
        probeBeam.setTranslateY(Math.abs(baseY) / 2.0);

        getChildren().addAll(probeBeam, scanRing, coreSphere);
    }

    public void tickAnimation(double dt) {
        time += dt * speed;
        ringRotate.setAngle((ringRotate.getAngle() + 3.0) % 360);

        double curX = 0;
        double curY = baseY;
        double curZ = 0;

        switch (pattern) {
            case LISSAJOUS_3D -> {
                curX = Math.sin(time + phase) * radiusX;
                curZ = Math.cos(time * 0.7 + phase) * radiusZ;
                curY = baseY + Math.sin(time * 1.5) * 12.0;
            }
            case ORBITAL_PATROL -> {
                curX = Math.cos(time + phase) * radiusX;
                curZ = Math.sin(time + phase) * radiusZ;
                curY = baseY + Math.sin(time * 2.0) * 8.0;
            }
            case AXIS_SWEEPER_X -> {
                curX = Math.sin(time + phase) * radiusX;
                curZ = Math.sin(time * 0.2) * 20.0;
                curY = baseY + Math.cos(time * 1.8) * 6.0;
            }
            case AXIS_SWEEPER_Z -> {
                curX = Math.cos(time * 0.2) * 20.0;
                curZ = Math.sin(time + phase) * radiusZ;
                curY = baseY + Math.sin(time * 1.8) * 6.0;
            }
            case NODE_CARRIER_INTERCEPT -> {
                // Trayectoria en figura de 8 o infinito (Lemniscata)
                curX = (radiusX * Math.cos(time + phase)) / (1 + Math.sin(time + phase) * Math.sin(time + phase));
                curZ = (radiusZ * Math.sin(time + phase) * Math.cos(time + phase)) / (1 + Math.sin(time + phase) * Math.sin(time + phase));
                curY = baseY + Math.sin(time * 2.2) * 10.0;
            }
        }

        setTranslateX(curX);
        setTranslateY(curY);
        setTranslateZ(curZ);

        // Reajustar longitud del haz detector respecto al plano cartesiano (Y=0)
        double distToGround = Math.max(2.0, -curY);
        probeBeam.setHeight(distToGround);
        probeBeam.setTranslateY(distToGround / 2.0);
    }

    public String getEntityId() {
        return entityId;
    }

    public String getCoordinatesFormatted() {
        return String.format("[%s] X:%+06.1f | Y:%+05.1f | Z:%+06.1f", entityId, getTranslateX(), getTranslateY(), getTranslateZ());
    }
}
