package io.jettra.policefx.world3d;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;

public final class DataStreamParticles extends Group {
    private final List<Sphere> particles = new ArrayList<>();
    private double phase = 0.0;

    public DataStreamParticles(int count) {
        PhongMaterial mat = new PhongMaterial(Color.LIGHTSKYBLUE);
        for (int i = 0; i < count; i++) {
            Sphere p = new Sphere(3);
            p.setMaterial(mat);
            particles.add(p);
            getChildren().add(p);
        }
        updatePositions(0.0);
    }

    public void updatePositions(double step) {
        this.phase += step;
        for (int i = 0; i < particles.size(); i++) {
            Sphere p = particles.get(i);
            double progress = ((double) i / particles.size() + phase) % 1.0;
            // Trayectoria orbital circular entre nodos
            double radius = 120.0;
            double angle = progress * 2 * Math.PI;
            p.setTranslateX(Math.cos(angle) * radius);
            p.setTranslateZ(Math.sin(angle) * radius);
            p.setTranslateY(-10 + Math.sin(angle * 3) * 15);
        }
    }
}
