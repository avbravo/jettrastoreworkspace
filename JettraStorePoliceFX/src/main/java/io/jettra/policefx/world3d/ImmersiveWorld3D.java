package io.jettra.policefx.world3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

public final class ImmersiveWorld3D extends Group {
    private final JettraPoliceAgentMesh agentMesh;
    private final DataStreamParticles particles;

    public ImmersiveWorld3D() {
        // 1. Suelo reticular
        Box ground = new Box(600, 2, 600);
        PhongMaterial groundMat = new PhongMaterial(Color.rgb(15, 23, 42));
        ground.setMaterial(groundMat);
        ground.setTranslateY(50);
        getChildren().add(ground);

        // 2. Nodos del clúster (Monolitos representando almacenamiento físico .jettra)
        Box node1 = createNodeMonolith("Node-01 (Primary) [/var/jettra/data]", 0, 0, 0, Color.DODGERBLUE);
        Box node2 = createNodeMonolith("Node-02 (Secondary)", -180, 0, -80, Color.TEAL);
        Box node3 = createNodeMonolith("Node-03 (Secondary)", 180, 0, -80, Color.TEAL);
        getChildren().addAll(node1, node2, node3);

        // 3. Sistema de partículas de datos
        this.particles = new DataStreamParticles(25);
        getChildren().add(particles);

        // 4. Agente centinela autónomo JettraPolice
        this.agentMesh = new JettraPoliceAgentMesh();
        this.agentMesh.moveTo(0, -40, 0);
        getChildren().add(agentMesh);
    }

    private Box createNodeMonolith(String label, double x, double y, double z, Color color) {
        Box box = new Box(50, 90, 50);
        PhongMaterial mat = new PhongMaterial(color);
        mat.setSpecularColor(Color.WHITE);
        box.setMaterial(mat);
        box.setTranslateX(x);
        box.setTranslateY(y);
        box.setTranslateZ(z);
        return box;
    }

    public void tickAnimation() {
        particles.updatePositions(0.015);
    }

    public JettraPoliceAgentMesh getAgentMesh() {
        return agentMesh;
    }
}
