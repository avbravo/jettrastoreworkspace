package io.jettra.fx.view3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Cluster3DVisualizer extends Group {
    private final Map<String, NodeVisual> nodeMeshes = new ConcurrentHashMap<>();
    private final Group ringOrbitGroup = new Group();
    private boolean ringTransitionActive = false;

    public record NodeVisual(Sphere sphere, Box base, double x, double y, double z) {}

    public Cluster3DVisualizer() {
        // Inicializar los 3 nodos del cluster en el espacio 3D
        addNode("node-01", true, 0, 0, 0);       // Primario en el centro
        addNode("node-02", false, -150, 0, -50); // Secundario 1
        addNode("node-03", false, 150, 0, -50);  // Secundario 2

        getChildren().add(ringOrbitGroup);
    }

    public void addNode(String nodeId, boolean isPrimary, double x, double y, double z) {
        Sphere sphere = new Sphere(isPrimary ? 35 : 25);
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(isPrimary ? Color.DODGERBLUE : Color.MEDIUMSEAGREEN);
        mat.setSpecularColor(Color.WHITE);
        sphere.setMaterial(mat);
        sphere.setTranslateX(x);
        sphere.setTranslateY(y);
        sphere.setTranslateZ(z);

        Box base = new Box(40, 10, 40);
        PhongMaterial baseMat = new PhongMaterial(Color.DARKSLATEGRAY);
        base.setMaterial(baseMat);
        base.setTranslateX(x);
        base.setTranslateY(y + 35);
        base.setTranslateZ(z);

        NodeVisual visual = new NodeVisual(sphere, base, x, y, z);
        nodeMeshes.put(nodeId, visual);

        getChildren().addAll(sphere, base);
    }

    public void removeNode(String nodeId) {
        NodeVisual visual = nodeMeshes.remove(nodeId);
        if (visual != null) {
            getChildren().removeAll(visual.sphere(), visual.base());
        }
    }

    public void setRingTransitionActive(boolean active) {
        this.ringTransitionActive = active;
        ringOrbitGroup.getChildren().clear();

        if (active) {
            // Animar banda o anillo de conexión entre los 3 nodos
            Cylinder link1 = createLink(0, 0, 0, -150, 0, -50, Color.GOLD);
            Cylinder link2 = createLink(0, 0, 0, 150, 0, -50, Color.GOLD);
            Cylinder link3 = createLink(-150, 0, -50, 150, 0, -50, Color.ORANGE);
            ringOrbitGroup.getChildren().addAll(link1, link2, link3);
        }
    }

    private Cylinder createLink(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        Cylinder cyl = new Cylinder(3, 160);
        PhongMaterial mat = new PhongMaterial(color);
        cyl.setMaterial(mat);
        cyl.setTranslateX((x1 + x2) / 2);
        cyl.setTranslateY((y1 + y2) / 2);
        cyl.setTranslateZ((z1 + z2) / 2);
        cyl.getTransforms().add(new Rotate(45, Rotate.Z_AXIS));
        return cyl;
    }

    public boolean isRingTransitionActive() { return ringTransitionActive; }
    public Map<String, NodeVisual> getNodeMeshes() { return nodeMeshes; }
}
