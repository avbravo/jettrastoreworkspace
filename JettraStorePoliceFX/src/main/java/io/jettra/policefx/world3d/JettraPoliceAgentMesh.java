package io.jettra.policefx.world3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public final class JettraPoliceAgentMesh extends Group {
    public enum AgentStatus { NORMAL, WARNING_RAM, CRITICAL_SECURITY }

    private final Sphere coreSphere;
    private final Cylinder scanHalo;
    private final PhongMaterial coreMat;
    private final PhongMaterial haloMat;
    private AgentStatus currentStatus = AgentStatus.NORMAL;

    public JettraPoliceAgentMesh() {
        // Esfera central del centinela JettraPolice
        this.coreSphere = new Sphere(16);
        this.coreMat = new PhongMaterial(Color.CYAN);
        this.coreMat.setSpecularColor(Color.WHITE);
        this.coreSphere.setMaterial(coreMat);

        // Halo holográfico de escaneo que rodea al agente
        this.scanHalo = new Cylinder(24, 2);
        this.haloMat = new PhongMaterial(Color.AQUAMARINE);
        this.scanHalo.setMaterial(haloMat);
        this.scanHalo.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        getChildren().addAll(coreSphere, scanHalo);
    }

    public void setStatus(AgentStatus status) {
        this.currentStatus = status;
        switch (status) {
            case NORMAL -> {
                coreMat.setDiffuseColor(Color.CYAN);
                haloMat.setDiffuseColor(Color.AQUAMARINE);
            }
            case WARNING_RAM -> {
                coreMat.setDiffuseColor(Color.ORANGE);
                haloMat.setDiffuseColor(Color.GOLD);
            }
            case CRITICAL_SECURITY -> {
                coreMat.setDiffuseColor(Color.CRIMSON);
                haloMat.setDiffuseColor(Color.RED);
            }
        }
    }

    public void moveTo(double x, double y, double z) {
        setTranslateX(x);
        setTranslateY(y);
        setTranslateZ(z);
    }

    public AgentStatus getCurrentStatus() { return currentStatus; }
}
