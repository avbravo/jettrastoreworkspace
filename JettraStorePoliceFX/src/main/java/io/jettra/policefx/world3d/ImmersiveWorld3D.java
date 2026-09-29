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
        // Base de obsidiana espacial profunda
        Box ground = new Box(720, 2.5, 720);
        PhongMaterial groundMat = new PhongMaterial(Color.rgb(15, 23, 42));
        groundMat.setSpecularColor(Color.rgb(30, 41, 59));
        ground.setMaterial(groundMat);
        ground.setTranslateY(10);

        // Marco Perimetral Neón Cian (Borde elegante del plano)
        Box borderN = new Box(724, 3, 4);
        borderN.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199, 0.85)));
        borderN.setTranslateZ(360);
        borderN.setTranslateY(9);

        Box borderS = new Box(724, 3, 4);
        borderS.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199, 0.85)));
        borderS.setTranslateZ(-360);
        borderS.setTranslateY(9);

        Box borderE = new Box(4, 3, 724);
        borderE.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199, 0.85)));
        borderE.setTranslateX(360);
        borderE.setTranslateY(9);

        Box borderW = new Box(4, 3, 724);
        borderW.setMaterial(new PhongMaterial(Color.rgb(2, 132, 199, 0.85)));
        borderW.setTranslateX(-360);
        borderW.setTranslateY(9);

        getChildren().addAll(ground, borderN, borderS, borderE, borderW);
    }

    private void buildCartesianAxes() {
        // EJE X (Rojo Coral Neón) - 700 unidades
        Box xAxis = new Box(700, 2.5, 2.5);
        PhongMaterial xMat = new PhongMaterial(Color.rgb(244, 63, 94));
        xMat.setSpecularColor(Color.WHITE);
        xAxis.setMaterial(xMat);
        xAxis.setTranslateY(7.5);

        // Punteros de flecha/extremo Eje X
        Cylinder xTipPos = new Cylinder(5, 12);
        xTipPos.setMaterial(xMat);
        xTipPos.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        xTipPos.setTranslateX(350);
        xTipPos.setTranslateY(7.5);

        Cylinder xTipNeg = new Cylinder(5, 12);
        xTipNeg.setMaterial(xMat);
        xTipNeg.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        xTipNeg.setTranslateX(-350);
        xTipNeg.setTranslateY(7.5);

        // EJE Y (Verde Esmeralda Vertical) - 180 unidades apuntando al cenit
        Box yAxis = new Box(2.5, 180, 2.5);
        PhongMaterial yMat = new PhongMaterial(Color.rgb(34, 197, 94));
        yMat.setSpecularColor(Color.WHITE);
        yAxis.setMaterial(yMat);
        yAxis.setTranslateY(-80);

        // Anillos de elevación vertical cada 40 unidades en Eje Y
        Group yRings = new Group();
        for (int y = -30; y >= -150; y -= 40) {
            Cylinder yr = new Cylinder(10, 1.2);
            yr.setMaterial(new PhongMaterial(Color.rgb(74, 222, 128, 0.6)));
            yr.setTranslateY(y);
            yRings.getChildren().add(yr);
        }

        // EJE Z (Azul Eléctrico Neón) - 700 unidades
        Box zAxis = new Box(2.5, 2.5, 700);
        PhongMaterial zMat = new PhongMaterial(Color.rgb(56, 189, 248));
        zMat.setSpecularColor(Color.WHITE);
        zAxis.setMaterial(zMat);
        zAxis.setTranslateY(7.5);

        // MARCADOR DE ORIGEN (0,0,0) MULTI-NIVEL (Baliza de Precisión JettraICore)
        Sphere originCore = new Sphere(6);
        PhongMaterial goldCoreMat = new PhongMaterial(Color.rgb(250, 204, 21));
        goldCoreMat.setSpecularColor(Color.WHITE);
        originCore.setMaterial(goldCoreMat);
        originCore.setTranslateY(7.5);

        Cylinder originRing1 = new Cylinder(16, 1.0);
        originRing1.setMaterial(new PhongMaterial(Color.rgb(245, 158, 11, 0.75)));
        originRing1.setTranslateY(8);

        Cylinder originRing2 = new Cylinder(28, 0.8);
        originRing2.setMaterial(new PhongMaterial(Color.rgb(56, 189, 248, 0.5)));
        originRing2.setTranslateY(8.2);

        getChildren().addAll(xAxis, xTipPos, xTipNeg, yAxis, yRings, zAxis,
                             originCore, originRing1, originRing2);
    }

    private void buildCartesianGrid() {
        Group gridGroup = new Group();

        // 1. Líneas Primarias cada 60 unidades (Brillantes, Cian suave)
        PhongMaterial primaryGridMat = new PhongMaterial(Color.rgb(56, 189, 248, 0.40));
        // 2. Líneas Secundarias cada 30 unidades (Sub-retícula sutil)
        PhongMaterial secondaryGridMat = new PhongMaterial(Color.rgb(51, 65, 85, 0.22));

        for (int i = -330; i <= 330; i += 30) {
            if (i == 0) continue;
            boolean isPrimary = (i % 60 == 0);
            PhongMaterial mat = isPrimary ? primaryGridMat : secondaryGridMat;
            double thickness = isPrimary ? 1.0 : 0.6;

            // Línea Paralela a X
            Box lineX = new Box(660, thickness, thickness);
            lineX.setMaterial(mat);
            lineX.setTranslateZ(i);
            lineX.setTranslateY(8.5);

            // Línea Paralela a Z
            Box lineZ = new Box(thickness, thickness, 660);
            lineZ.setMaterial(mat);
            lineZ.setTranslateX(i);
            lineZ.setTranslateY(8.5);

            gridGroup.getChildren().addAll(lineX, lineZ);

            // Marcadores de hito en coordenadas principales
            if (isPrimary && Math.abs(i) <= 240) {
                Box markX = new Box(4, 1.5, 4);
                markX.setMaterial(new PhongMaterial(Color.rgb(250, 204, 21, 0.6)));
                markX.setTranslateX(i);
                markX.setTranslateY(8.2);
                markX.setTranslateZ(0);

                Box markZ = new Box(4, 1.5, 4);
                markZ.setMaterial(new PhongMaterial(Color.rgb(56, 189, 248, 0.6)));
                markZ.setTranslateX(0);
                markZ.setTranslateY(8.2);
                markZ.setTranslateZ(i);

                gridGroup.getChildren().addAll(markX, markZ);
            }
        }
        getChildren().add(gridGroup);
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

    public JettraPoliceAgentMesh getAgentMesh() {
        return agentMesh;
    }
}
