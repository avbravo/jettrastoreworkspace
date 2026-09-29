package io.jettra.policefx.world3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public final class JettraPoliceAgentMesh extends Group {
    public enum AgentStatus { NORMAL, WARNING_RAM, CRITICAL_SECURITY }

    private AgentStatus currentStatus = AgentStatus.NORMAL;

    // Elementos 3D del Agente estilo JettraICore
    private final Box torso;
    private final Sphere head;
    private final Cylinder visor;
    private final Cylinder scanHalo;
    private final Cylinder selectionRing;
    private final Box leftLeg;
    private final Box rightLeg;
    private final Box leftArm;
    private final Box rightArm;

    // Materiales reactivos
    private final PhongMaterial torsoMat;
    private final PhongMaterial headMat;
    private final PhongMaterial visorMat;
    private final PhongMaterial haloMat;
    private final PhongMaterial ringMat;
    private final PhongMaterial limbsMat;

    // Rotación del Halo
    private final Rotate haloRotate;
    private double haloAngle = 0;

    private String currentThought = "Vigilando integridad de example_factura_db (3M objetos)...";
    private String currentGoal = "Patrulla Preventiva & Monitoreo Raft";

    // Trayectoria de patrulla
    private double patrolTime = 0;
    private boolean patrolActive = true;

    public JettraPoliceAgentMesh() {
        // 1. Torso Robótico con acabado metálico
        this.torso = new Box(24, 32, 16);
        this.torsoMat = new PhongMaterial(Color.rgb(15, 23, 42));
        this.torsoMat.setSpecularColor(Color.rgb(56, 189, 248));
        this.torso.setMaterial(torsoMat);
        this.torso.setTranslateY(-16);

        // 2. Cabeza Sensor
        this.head = new Sphere(12);
        this.headMat = new PhongMaterial(Color.rgb(30, 41, 59));
        this.headMat.setSpecularColor(Color.WHITE);
        this.head.setMaterial(headMat);
        this.head.setTranslateY(-38);

        // 3. Visor Cibernético Neón (Indica estado en tiempo real)
        this.visor = new Cylinder(7, 4);
        this.visorMat = new PhongMaterial(Color.CYAN);
        this.visorMat.setSpecularColor(Color.WHITE);
        this.visor.setMaterial(visorMat);
        this.visor.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        this.visor.setTranslateY(-38);
        this.visor.setTranslateZ(-10);

        // 4. Halo holográfico de escaneo perimetral (JettraICore)
        this.scanHalo = new Cylinder(32, 2);
        this.haloMat = new PhongMaterial(Color.AQUAMARINE);
        this.scanHalo.setMaterial(haloMat);
        this.haloRotate = new Rotate(0, Rotate.Y_AXIS);
        this.scanHalo.getTransforms().addAll(new Rotate(90, Rotate.X_AXIS), haloRotate);
        this.scanHalo.setTranslateY(-16);

        // 5. Anillo de Selección en el suelo (drawCircle3D en JettraICore)
        this.selectionRing = new Cylinder(38, 1);
        this.ringMat = new PhongMaterial(Color.rgb(74, 222, 128, 0.7));
        this.selectionRing.setMaterial(ringMat);
        this.selectionRing.setTranslateY(8);

        // 6. Extremidades con cinemática de caminata
        this.limbsMat = new PhongMaterial(Color.rgb(51, 65, 85));
        this.leftLeg = new Box(6, 20, 6);
        this.leftLeg.setMaterial(limbsMat);
        this.leftLeg.setTranslateX(-7);
        this.leftLeg.setTranslateY(10);

        this.rightLeg = new Box(6, 20, 6);
        this.rightLeg.setMaterial(limbsMat);
        this.rightLeg.setTranslateX(7);
        this.rightLeg.setTranslateY(10);

        this.leftArm = new Box(5, 22, 5);
        this.leftArm.setMaterial(limbsMat);
        this.leftArm.setTranslateX(-16);
        this.leftArm.setTranslateY(-14);

        this.rightArm = new Box(5, 22, 5);
        this.rightArm.setMaterial(limbsMat);
        this.rightArm.setTranslateX(16);
        this.rightArm.setTranslateY(-14);

        getChildren().addAll(torso, head, visor, scanHalo, selectionRing,
                             leftLeg, rightLeg, leftArm, rightArm);
    }

    public void tickAnimation(double dt) {
        // Rotación suave del halo de escaneo
        haloAngle = (haloAngle + 0.6) % 360;
        haloRotate.setAngle(haloAngle);

        if (patrolActive) {
            patrolTime += dt;
            // Trayectoria elíptica suave y majestuosa de patrulla en torno a los monolitos
            double radiusX = 130;
            double radiusZ = 90;
            double px = Math.sin(patrolTime * 0.18) * radiusX;
            double pz = Math.cos(patrolTime * 0.18) * radiusZ;
            setTranslateX(px);
            setTranslateZ(pz);

            // Bouncing y swing suave y orgánico de caminata (JettraICore walkBounce & legSwing)
            double bounce = Math.sin(patrolTime * 1.6) * 1.2;
            torso.setTranslateY(-16 + bounce);
            head.setTranslateY(-38 + bounce);
            visor.setTranslateY(-38 + bounce);
            scanHalo.setTranslateY(-16 + bounce);

            double swing = Math.sin(patrolTime * 1.6) * 3.2;
            leftLeg.setTranslateZ(swing);
            rightLeg.setTranslateZ(-swing);
            leftArm.setTranslateZ(-swing * 0.7);
            rightArm.setTranslateZ(swing * 0.7);
        }
    }

    public void setStatus(AgentStatus status) {
        this.currentStatus = status;
        switch (status) {
            case NORMAL -> {
                visorMat.setDiffuseColor(Color.CYAN);
                haloMat.setDiffuseColor(Color.AQUAMARINE);
                ringMat.setDiffuseColor(Color.rgb(74, 222, 128, 0.7));
            }
            case WARNING_RAM -> {
                visorMat.setDiffuseColor(Color.ORANGE);
                haloMat.setDiffuseColor(Color.GOLD);
                ringMat.setDiffuseColor(Color.rgb(245, 158, 11, 0.8));
            }
            case CRITICAL_SECURITY -> {
                visorMat.setDiffuseColor(Color.CRIMSON);
                haloMat.setDiffuseColor(Color.RED);
                ringMat.setDiffuseColor(Color.rgb(239, 68, 68, 0.9));
            }
        }
    }

    public void moveTo(double x, double y, double z) {
        setTranslateX(x);
        setTranslateY(y);
        setTranslateZ(z);
    }

    public void setThought(String thought) {
        this.currentThought = thought;
    }

    public String getThought() {
        return currentThought;
    }

    public void setGoal(String goal) {
        this.currentGoal = goal;
    }

    public String getGoal() {
        return currentGoal;
    }

    public void setPatrolActive(boolean active) {
        this.patrolActive = active;
    }

    public boolean isPatrolActive() {
        return patrolActive;
    }

    public AgentStatus getCurrentStatus() {
        return currentStatus;
    }
}
