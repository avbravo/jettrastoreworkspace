package io.jettra.policefx.world3d;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public final class ImmersiveWorld3D extends Group {
    private final JettraPoliceAgentMesh agentMesh;
    private final DataStreamParticles particles;

    // Elementos dinámicos
    private final Group rotatingRingsGroup = new Group();
    private final Group knowledgeBaseGroup = new Group();
    private final Group beaconsGroup = new Group();
    private double worldTime = 0;
    private final Cylinder dynamicRadarPulse;
    private final Group quadrantNodesGroup = new Group();

    // Posiciones de Nodos y Bases de Datos (Disposición en Anfiteatro frente a Cámara)
    public static final double[][] NODE_POSITIONS = {
        {0, -45, -70},      // Node-01 (Primary Cluster Master)
        {-160, -38, -30},   // Node-02 (Secondary 1)
        {160, -38, -30},    // Node-03 (Secondary 2)
        {-110, -48, 60},    // example_factura_db (3M Objetos)
        {110, -40, 60}      // sample_enterprise_db
    };

    public ImmersiveWorld3D() {
        // 1. Iluminación Inmersiva Escénica
        AmbientLight ambient = new AmbientLight(Color.rgb(50, 65, 95));
        PointLight mainLight = new PointLight(Color.rgb(56, 189, 248));
        mainLight.setTranslateY(-160);
        mainLight.setTranslateZ(-50);

        PointLight goldLight = new PointLight(Color.rgb(250, 204, 21, 0.7));
        goldLight.setTranslateX(-110);
        goldLight.setTranslateY(-90);
        goldLight.setTranslateZ(60);

        getChildren().addAll(ambient, mainLight, goldLight);

        // 2. Plano Cartesiano Estético de Alto Contraste (Cybernetic Grid)
        buildCartesianGroundPlane();

        // 3. Ejes Cartesianos 3D Tridimensionales con Marcas y Origen de Precisión
        buildCartesianAxes();

        // 4. Rejilla Reticular de Doble Nivel (Líneas Primarias y Secundarias)
        buildCartesianGrid();

        // 4.1 Anillos de Alcance Radar Cartesiano y Balizas Perimetrales
        buildCartesianRadarRings();
        buildPerimeterBeacons();
        buildQuadrantWaypoints();
        getChildren().add(quadrantNodesGroup);

        this.dynamicRadarPulse = new Cylinder(50, 1.0);
        dynamicRadarPulse.setMaterial(new PhongMaterial(Color.rgb(0, 240, 255, 0.4)));
        dynamicRadarPulse.setTranslateY(0.9);
        getChildren().add(dynamicRadarPulse);

        // 5. Monolitos de Almacenamiento (Disposición en Anfiteatro Visible)
        buildClusterMonoliths();

        // 6. Anillos Holográficos Orbitales sobre los Nodos y Bases de Datos
        buildHolographicRings();
        getChildren().add(rotatingRingsGroup);

        // 7. Esferas de Conocimiento Flotante (Knowledge Base de JettraICore)
        buildKnowledgeBase();
        getChildren().add(knowledgeBaseGroup);

        // 8. Partículas y Canales de Flujo de Datos
        this.particles = new DataStreamParticles(35);
        getChildren().add(particles);

        // 9. Agente Autónomo Patrullero JettraPolice Sentinel (Líder)
        this.agentMesh = new JettraPoliceAgentMesh();
        this.agentMesh.moveTo(0, 0, 70);
        getChildren().add(agentMesh);
    }

    private void buildCartesianGroundPlane() {
        // Base de obsidiana espacial profunda situada a profundidad Y=16
        Box ground = new Box(740, 4.0, 740);
        PhongMaterial groundMat = new PhongMaterial(Color.rgb(10, 15, 28));
        groundMat.setSpecularColor(Color.rgb(20, 30, 50));
        ground.setMaterial(groundMat);
        ground.setTranslateY(16);

        // Marco Perimetral Neón Cian Resplandeciente
        Box borderN = new Box(744, 4, 5);
        borderN.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199)));
        borderN.setTranslateZ(370);
        borderN.setTranslateY(14);

        Box borderS = new Box(744, 4, 5);
        borderS.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199)));
        borderS.setTranslateZ(-370);
        borderS.setTranslateY(14);

        Box borderE = new Box(5, 4, 744);
        borderE.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199)));
        borderE.setTranslateX(370);
        borderE.setTranslateY(14);

        Box borderW = new Box(5, 4, 744);
        borderW.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199)));
        borderW.setTranslateX(-370);
        borderW.setTranslateY(14);

        getChildren().addAll(ground, borderN, borderS, borderE, borderW);
    }

    private void buildCartesianAxes() {
        // EJE X (Rojo Coral Neón) - 720 unidades en Primer Plano (Y = 0.0)
        Box xAxis = new Box(720, 3.5, 3.5);
        PhongMaterial xMat = new PhongMaterial(Color.rgb(255, 42, 109));
        xMat.setSpecularColor(Color.WHITE);
        xAxis.setMaterial(xMat);
        xAxis.setTranslateY(0.0);

        // Punteros de flecha/extremo Eje X (+X y -X)
        Cylinder xTipPos = new Cylinder(7, 18);
        xTipPos.setMaterial(xMat);
        xTipPos.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        xTipPos.setTranslateX(360);
        xTipPos.setTranslateY(0.0);

        Cylinder xTipNeg = new Cylinder(7, 18);
        xTipNeg.setMaterial(xMat);
        xTipNeg.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        xTipNeg.setTranslateX(-360);
        xTipNeg.setTranslateY(0.0);

        // EJE Y (Verde Esmeralda Neón) - 200 unidades apuntando al cenit
        Box yAxis = new Box(3.5, 200, 3.5);
        PhongMaterial yMat = new PhongMaterial(Color.rgb(0, 255, 102));
        yMat.setSpecularColor(Color.WHITE);
        yAxis.setMaterial(yMat);
        yAxis.setTranslateY(-90.0);

        Cylinder yTipPos = new Cylinder(7, 18);
        yTipPos.setMaterial(yMat);
        yTipPos.setTranslateY(-190.0);

        // Anillos de elevación vertical cada 40 unidades en Eje Y
        Group yRings = new Group();
        for (int y = -30; y >= -170; y -= 40) {
            Cylinder yr = new Cylinder(12, 1.8);
            yr.setMaterial(new PhongMaterial(Color.rgb(74, 222, 128, 0.8)));
            yr.setTranslateY(y);
            yRings.getChildren().add(yr);
        }

        // EJE Z (Azul Eléctrico Neón / Cian) - 720 unidades en Primer Plano (Y = 0.0)
        Box zAxis = new Box(3.5, 3.5, 720);
        PhongMaterial zMat = new PhongMaterial(Color.rgb(0, 242, 254));
        zMat.setSpecularColor(Color.WHITE);
        zAxis.setMaterial(zMat);
        zAxis.setTranslateY(0.0);

        // Punteros de flecha/extremo Eje Z (+Z y -Z)
        Cylinder zTipPos = new Cylinder(7, 18);
        zTipPos.setMaterial(zMat);
        zTipPos.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        zTipPos.setTranslateZ(360);
        zTipPos.setTranslateY(0.0);

        Cylinder zTipNeg = new Cylinder(7, 18);
        zTipNeg.setMaterial(zMat);
        zTipNeg.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        zTipNeg.setTranslateZ(-360);
        zTipNeg.setTranslateY(0.0);

        // MARCADOR DE ORIGEN (0,0,0) HIPER-TECNOLÓGICO EN PRIMER PLANO
        Sphere originCore = new Sphere(8);
        PhongMaterial goldCoreMat = new PhongMaterial(Color.rgb(250, 204, 21));
        goldCoreMat.setSpecularColor(Color.WHITE);
        originCore.setMaterial(goldCoreMat);
        originCore.setTranslateY(0.0);

        Cylinder originRing1 = new Cylinder(20, 1.5);
        originRing1.setMaterial(new PhongMaterial(Color.rgb(245, 158, 11, 0.85)));
        originRing1.setTranslateY(0.5);

        Cylinder originRing2 = new Cylinder(34, 1.2);
        originRing2.setMaterial(new PhongMaterial(Color.rgb(56, 189, 248, 0.75)));
        originRing2.setTranslateY(0.8);

        getChildren().addAll(xAxis, xTipPos, xTipNeg, yAxis, yTipPos, yRings, zAxis, zTipPos, zTipNeg,
                             originCore, originRing1, originRing2);
    }

    private void buildCartesianGrid() {
        Group gridGroup = new Group();

        // 1. Líneas Primarias cada 60 unidades (Brillantes, Cian Eléctrico en Primer Plano)
        PhongMaterial primaryGridMat = new PhongMaterial(Color.rgb(0, 242, 254, 0.75));
        primaryGridMat.setSpecularColor(Color.CYAN);

        // 2. Líneas Secundarias cada 30 unidades (Sub-retícula Tech Nítida)
        PhongMaterial secondaryGridMat = new PhongMaterial(Color.rgb(14, 165, 233, 0.45));

        for (int i = -330; i <= 330; i += 30) {
            if (i == 0) continue;
            boolean isPrimary = (i % 60 == 0);
            PhongMaterial mat = isPrimary ? primaryGridMat : secondaryGridMat;
            double thickness = isPrimary ? 1.6 : 0.9;

            // Línea Paralela a X (Elevada en Y = 0.5 para Máxima Visibilidad Frontal)
            Box lineX = new Box(680, thickness, thickness);
            lineX.setMaterial(mat);
            lineX.setTranslateZ(i);
            lineX.setTranslateY(0.5);

            // Línea Paralela a Z (Elevada en Y = 0.5)
            Box lineZ = new Box(thickness, thickness, 680);
            lineZ.setMaterial(mat);
            lineZ.setTranslateX(i);
            lineZ.setTranslateY(0.5);

            gridGroup.getChildren().addAll(lineX, lineZ);

            // Marcadores de hito y coordenadas en primer plano
            if (isPrimary && Math.abs(i) <= 300) {
                Box markX = new Box(6, 2.2, 6);
                markX.setMaterial(new PhongMaterial(Color.rgb(250, 204, 21, 0.85)));
                markX.setTranslateX(i);
                markX.setTranslateY(0.0);
                markX.setTranslateZ(0);

                Box markZ = new Box(6, 2.2, 6);
                markZ.setMaterial(new PhongMaterial(Color.rgb(56, 189, 248, 0.85)));
                markZ.setTranslateX(0);
                markZ.setTranslateY(0.0);
                markZ.setTranslateZ(i);

                // Cuadrantes luminosos en las esquinas principales del plano
                Box cornerBeacon = new Box(5, 4.0, 5);
                cornerBeacon.setMaterial(new PhongMaterial(Color.rgb(168, 85, 247, 0.70)));
                cornerBeacon.setTranslateX(i);
                cornerBeacon.setTranslateY(-1.0);
                cornerBeacon.setTranslateZ(i);

                gridGroup.getChildren().addAll(markX, markZ, cornerBeacon);
            }
        }
        getChildren().add(gridGroup);
    }

    private void buildCartesianRadarRings() {
        Group rings = new Group();
        int[] radii = {100, 200, 300, 360};
        Color[] colors = {
            Color.rgb(56, 189, 248, 0.50),
            Color.rgb(16, 185, 129, 0.45),
            Color.rgb(250, 204, 21, 0.40),
            Color.rgb(2, 132, 199, 0.65)
        };

        for (int i = 0; i < radii.length; i++) {
            Cylinder ring = new Cylinder(radii[i], 1.2);
            ring.setMaterial(new PhongMaterial(colors[i]));
            ring.setTranslateY(0.7);
            rings.getChildren().add(ring);
        }
        getChildren().add(rings);
    }

    private void buildPerimeterBeacons() {
        Group beacons = new Group();
        double[][] corners = {
            {360, 360}, {-360, 360}, {360, -360}, {-360, -360}
        };

        for (double[] c : corners) {
            // Poste base
            Box base = new Box(12, 24, 12);
            base.setMaterial(new PhongMaterial(Color.rgb(15, 23, 42)));
            base.setTranslateX(c[0]);
            base.setTranslateY(4);
            base.setTranslateZ(c[1]);

            // Haz de luz vertical
            Cylinder beam = new Cylinder(2.5, 90);
            PhongMaterial beamMat = new PhongMaterial(Color.rgb(0, 242, 254, 0.75));
            beamMat.setSpecularColor(Color.WHITE);
            beam.setMaterial(beamMat);
            beam.setTranslateX(c[0]);
            beam.setTranslateY(-45);
            beam.setTranslateZ(c[1]);

            // Esfera de energía superior
            Sphere orb = new Sphere(6);
            PhongMaterial orbMat = new PhongMaterial(Color.rgb(250, 204, 21));
            orbMat.setSpecularColor(Color.WHITE);
            orb.setMaterial(orbMat);
            orb.setTranslateX(c[0]);
            orb.setTranslateY(-90);
            orb.setTranslateZ(c[1]);

            beacons.getChildren().addAll(base, beam, orb);
        }
        getChildren().add(beacons);
    }

    private void buildClusterMonoliths() {
        Group monolithsGroup = new Group();

        // 1. Node-01 (Primary Cluster Leader / Raft Master)
        monolithsGroup.getChildren().add(createMonolithWithBeacon(
            0, -45, -70, Color.rgb(2, 132, 199), Color.CYAN, 54, 96, 54, 10
        ));

        // 2. Node-02 (Secondary Replica 1)
        monolithsGroup.getChildren().add(createMonolithWithBeacon(
            -160, -38, -30, Color.rgb(13, 148, 136), Color.AQUAMARINE, 44, 82, 44, 8
        ));

        // 3. Node-03 (Secondary Replica 2)
        monolithsGroup.getChildren().add(createMonolithWithBeacon(
            160, -38, -30, Color.rgb(13, 148, 136), Color.AQUAMARINE, 44, 82, 44, 8
        ));

        // 4. Base de Datos Masiva: example_factura_db (3 Millones de Objetos)
        monolithsGroup.getChildren().add(createMonolithWithBeacon(
            -110, -48, 60, Color.rgb(217, 119, 6), Color.GOLD, 58, 112, 58, 11
        ));

        // 5. Base de Datos: sample_enterprise_db (Multimodelo Empresarial)
        monolithsGroup.getChildren().add(createMonolithWithBeacon(
            110, -40, 60, Color.rgb(124, 58, 237), Color.rgb(216, 180, 254), 48, 92, 48, 9
        ));

        getChildren().add(monolithsGroup);
    }

    private Group createMonolithWithBeacon(double x, double y, double z,
                                          Color bodyColor, Color beaconColor,
                                          double w, double h, double d, double beaconRadius) {
        Group g = new Group();

        // Pedestal base oscuro
        Box pedestal = new Box(w + 12, 6, d + 12);
        pedestal.setMaterial(new PhongMaterial(Color.rgb(30, 41, 59)));
        pedestal.setTranslateX(x);
        pedestal.setTranslateY(7);
        pedestal.setTranslateZ(z);

        // Cuerpo principal del monolito
        Box body = new Box(w, h, d);
        PhongMaterial bodyMat = new PhongMaterial(bodyColor);
        bodyMat.setSpecularColor(Color.WHITE);
        body.setMaterial(bodyMat);
        body.setTranslateX(x);
        body.setTranslateY(y);
        body.setTranslateZ(z);

        // Baliza/Cristal Neón superior (Señal de estado activo)
        Sphere beacon = new Sphere(beaconRadius);
        PhongMaterial bMat = new PhongMaterial(beaconColor);
        bMat.setSpecularColor(Color.WHITE);
        beacon.setMaterial(bMat);
        beacon.setTranslateX(x);
        beacon.setTranslateY(y - h / 2 - beaconRadius);
        beacon.setTranslateZ(z);

        beaconsGroup.getChildren().add(beacon);
        g.getChildren().addAll(pedestal, body, beacon);
        return g;
    }

    private void buildHolographicRings() {
        // Anillo orbital sobre example_factura_db (Dorado)
        Cylinder ringFactura = new Cylinder(42, 1.8);
        PhongMaterial ringMat = new PhongMaterial(Color.rgb(250, 204, 21, 0.75));
        ringFactura.setMaterial(ringMat);
        ringFactura.setTranslateX(-110);
        ringFactura.setTranslateY(-115);
        ringFactura.setTranslateZ(60);
        ringFactura.getTransforms().add(new Rotate(25, Rotate.X_AXIS));

        // Anillo orbital sobre Node-01 (Cian)
        Cylinder ringMaster = new Cylinder(38, 1.8);
        PhongMaterial masterRingMat = new PhongMaterial(Color.rgb(56, 189, 248, 0.75));
        ringMaster.setMaterial(masterRingMat);
        ringMaster.setTranslateX(0);
        ringMaster.setTranslateY(-105);
        ringMaster.setTranslateZ(-70);
        ringMaster.getTransforms().add(new Rotate(18, Rotate.Z_AXIS));

        // Anillo orbital sobre sample_enterprise_db (Violeta)
        Cylinder ringEnterprise = new Cylinder(34, 1.8);
        ringEnterprise.setMaterial(new PhongMaterial(Color.rgb(192, 132, 252, 0.75)));
        ringEnterprise.setTranslateX(110);
        ringEnterprise.setTranslateY(-98);
        ringEnterprise.setTranslateZ(60);
        ringEnterprise.getTransforms().add(new Rotate(-20, Rotate.X_AXIS));

        rotatingRingsGroup.getChildren().addAll(ringFactura, ringMaster, ringEnterprise);
    }

    private void buildKnowledgeBase() {
        // Esferas flotantes de conocimiento heurístico (Knowledge Base de JettraICore)
        double[][] kCoords = {
            {-60, -75, -20},
            {60, -80, -20},
            {-50, -85, 80},
            {70, -75, 70}
        };
        for (double[] pos : kCoords) {
            Sphere kSphere = new Sphere(6.5);
            PhongMaterial kMat = new PhongMaterial(Color.rgb(250, 204, 21, 0.85));
            kMat.setSpecularColor(Color.WHITE);
            kSphere.setMaterial(kMat);
            kSphere.setTranslateX(pos[0]);
            kSphere.setTranslateY(pos[1]);
            kSphere.setTranslateZ(pos[2]);
            knowledgeBaseGroup.getChildren().add(kSphere);
        }
    }

    public void tickAnimation() {
        // Velocidad ajustada para animación suave, majestuosa y no acelerada
        worldTime += 0.008;
        particles.updatePositions(0.006);
        agentMesh.tickAnimation(0.008);

        // Rotación lenta y elegante de anillos holográficos
        for (int i = 0; i < rotatingRingsGroup.getChildren().size(); i++) {
            var node = rotatingRingsGroup.getChildren().get(i);
            node.setRotate((node.getRotate() + (i % 2 == 0 ? 0.35 : -0.30)) % 360);
        }

        // Pulsación suave y relajante de las esferas de conocimiento y balizas
        double pulse = Math.sin(worldTime * 1.5) * 0.12;
        for (var node : knowledgeBaseGroup.getChildren()) {
            node.setScaleX(1.0 + pulse);
            node.setScaleY(1.0 + pulse);
            node.setScaleZ(1.0 + pulse);
        }
        for (var node : beaconsGroup.getChildren()) {
            node.setScaleX(1.0 + pulse * 0.8);
            node.setScaleY(1.0 + pulse * 0.8);
            node.setScaleZ(1.0 + pulse * 0.8);
        }
    }


    private void buildQuadrantWaypoints() {
        double[][] quadCoords = {
            {180, 180, 1}, {-180, 180, 2}, {-180, -180, 3}, {180, -180, 4}
        };

        for (double[] q : quadCoords) {
            Sphere nodeOrb = new Sphere(5);
            PhongMaterial orbMat = new PhongMaterial(Color.rgb(56, 189, 248));
            orbMat.setSpecularColor(Color.WHITE);
            nodeOrb.setMaterial(orbMat);
            nodeOrb.setTranslateX(q[0]);
            nodeOrb.setTranslateY(-4);
            nodeOrb.setTranslateZ(q[1]);

            Cylinder nodeRing = new Cylinder(14, 1.2);
            nodeRing.setMaterial(new PhongMaterial(Color.rgb(14, 165, 233, 0.6)));
            nodeRing.setTranslateX(q[0]);
            nodeRing.setTranslateY(-2);
            nodeRing.setTranslateZ(q[1]);

            quadrantNodesGroup.getChildren().addAll(nodeOrb, nodeRing);
        }
    }

    public JettraPoliceAgentMesh getAgentMesh() {
        return agentMesh;
    }
}
