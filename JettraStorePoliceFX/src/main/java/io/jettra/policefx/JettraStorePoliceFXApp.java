package io.jettra.policefx;

import io.jettra.driver.JettraClient;
import io.jettra.policefx.world3d.ImmersiveWorld3D;
import io.jettra.policefx.world3d.JettraPoliceAgentMesh;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JettraStorePoliceFXApp extends Application {
    private JettraClient client;
    private ImmersiveWorld3D world3D;
    private AnimationTimer timer;

    // 3D Camera Controls
    private PerspectiveCamera camera;
    // Configuración Inicial Óptima: Plano Cartesiano en Primer Plano
    private final Rotate cameraRotateX = new Rotate(-34, Rotate.X_AXIS);
    private final Rotate cameraRotateY = new Rotate(0, Rotate.Y_AXIS);
    private final Translate cameraTranslate = new Translate(0, -180, -400);
    private double mouseAnchorX, mouseAnchorY;
    private boolean followMode = false;

    // UI Elements
    private Label statusBadge;
    private VBox liveFeedBox;
    private VBox chatHistoryBox;
    private TextField chatInputField;
    private ScrollPane chatScrollPane;

    // Floating Thought Overlay
    private VBox floatingThoughtOverlay;
    private Label floatingThoughtHeader;
    private Label floatingThoughtContent;
    private Label floatingThoughtGoal;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JettraStorePoliceFX - Entorno 3D Inmersivo y Monitoreo Preventivo (Aspecto JettraICore - Java 25)");

        try {
            this.client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra");
        } catch (Exception ignored) {
            // Permite ejecutar visualización en modo offline/autónomo si el nodo local no está activo
        }

        this.world3D = new ImmersiveWorld3D();

        // 1. Configuración de Cámara 3D con Órbita, Zoom y Perspectiva Centrada en Anfiteatro
        this.camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(2800.0);
        camera.getTransforms().addAll(cameraRotateY, cameraRotateX, cameraTranslate);

        SubScene subScene3D = new SubScene(world3D, 920, 720, true, SceneAntialiasing.BALANCED);
        subScene3D.setCamera(camera);

        // Controladores de Ratón para Orbitar y Zoom
        initCameraMouseControls(subScene3D);

        // 2. Layout Principal
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #020617;");

        // Header Superior HUD
        root.setTop(buildTopHeader());

        // Panel Lateral Derecho Organizado (TabPane para Nodos/Bases de Datos, Agente PECS y Chat)
        root.setRight(buildRightSidebar());

        // Contenedor Central con Escena 3D y Overlays Flotantes
        StackPane centerStack = new StackPane();
        centerStack.getChildren().add(subScene3D);
        subScene3D.widthProperty().bind(centerStack.widthProperty());
        subScene3D.heightProperty().bind(centerStack.heightProperty());

        // Overlay de Ayuda (Superior Izquierda)
        VBox helpOverlay = buildHelpOverlay();
        StackPane.setAlignment(helpOverlay, Pos.TOP_LEFT);
        StackPane.setMargin(helpOverlay, new Insets(15));

        // Overlay de Burbuja de Pensamiento del Agente Líder (Superior Centro)
        VBox thoughtOverlay = buildFloatingThoughtBubble();
        StackPane.setAlignment(thoughtOverlay, Pos.TOP_CENTER);
        StackPane.setMargin(thoughtOverlay, new Insets(15, 0, 0, 0));

        // Barra de Balizas de Acceso Rápido a Nodos y Bases de Datos (Inferior Centro de la Escena 3D)
        HBox quickAccessNodesBar = buildQuickAccessBar();
        StackPane.setAlignment(quickAccessNodesBar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(quickAccessNodesBar, new Insets(0, 0, 15, 0));

        centerStack.getChildren().addAll(helpOverlay, thoughtOverlay, quickAccessNodesBar);
        root.setCenter(centerStack);

        // Barra Inferior de Acciones y Telemetría Rápida
        root.setBottom(buildBottomControls());

        // 3. Manejadores de Teclado
        Scene scene = new Scene(root, 1340, 840);
        initKeyboardControls(scene);

        // 4. Bucle de Animación continua 60 FPS (Velocidad Sosegada y Fluida)
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                world3D.tickAnimation();
                if (followMode) {
                    var agent = world3D.getAgentMesh();
                    cameraTranslate.setX(agent.getTranslateX());
                    cameraTranslate.setZ(agent.getTranslateZ() - 260);
                }

                // Sincronización continua de supervisión reactiva de JettraPolice
                var alerts = io.jettra.store.police.JettraPolice.getInstance().getAlerts();
                if (!alerts.isEmpty()) {
                    var lastAlert = alerts.getLast();
                    if ("HEAP_EXHAUSTION_PREVENTED".equals(lastAlert.code())) {
                        world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.WARNING_RAM);
                        world3D.getAgentMesh().setThought("Intervención Anti-OOM: Paginando lazy consulta masiva para proteger el Heap...");
                        if (statusBadge != null && !statusBadge.getText().contains("ANTI-OOM")) {
                            statusBadge.setText("AGENTE: INTERVENCIÓN PREVENTIVA HEAP (ANTI-OOM)");
                            statusBadge.setStyle("-fx-background-color: #EA580C; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
                        }
                    }
                }
            }
        };
        timer.start();

        primaryStage.setScene(scene);
        primaryStage.show();

        // Logs de bienvenida
        addLiveFeedEvent("Plano cartesiano tridimensional y retícula cybernetic listos.");
        addLiveFeedEvent("Clúster Raft activo: Node-01 (Master), Node-02, Node-03.");
        addLiveFeedEvent("Base 'example_factura_db' en línea: 3,000,000 objetos multimodelo.");
    }

    private HBox buildTopHeader() {
        HBox hudTop = new HBox(15);
        hudTop.setPadding(new Insets(10, 20, 10, 20));
        hudTop.setAlignment(Pos.CENTER_LEFT);
        hudTop.setStyle("-fx-background-color: rgba(11, 19, 43, 0.96); -fx-border-color: #0284C7; -fx-border-width: 0 0 1.5 0;");

        Label title = new Label("JETTRA CORE 3D - JAVA 25 EDITION");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #FACC15;");

        Label subTitle = new Label("MONITOREO PREVENTIVO EN TIEMPO REAL");
        subTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #38BDF8; -fx-padding: 2 6 2 6; " +
                          "-fx-background-color: rgba(56, 189, 248, 0.15); -fx-background-radius: 4;");

        this.statusBadge = new Label("AGENTE: PATRULLA PREVENTIVA ACTIVA");
        statusBadge.setStyle("-fx-background-color: #047857; -fx-text-fill: white; -fx-font-weight: bold; " +
                             "-fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label session = new Label("JettraJWT: admin (SUPER_ADMIN) | Panama FFM + ZGC");
        session.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");

        hudTop.getChildren().addAll(title, subTitle, statusBadge, spacer, session);
        return hudTop;
    }

    private VBox buildRightSidebar() {
        VBox sidebarContainer = new VBox(6);
        sidebarContainer.setPrefWidth(350);
        sidebarContainer.setPadding(new Insets(8));
        sidebarContainer.setStyle("-fx-background-color: rgba(11, 19, 43, 0.98); -fx-border-color: #00ADB5; " +
                                  "-fx-border-width: 0 0 0 1.5;");

        Label sidebarTitle = new Label("PANEL DE CONTROL JETTRA POLICE");
        sidebarTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FACC15; -fx-padding: 4 6 2 6;");

        // TabPane Organizado para Nodos & Bases de Datos, Agente PECS y Chat
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: transparent;");

        // TAB 1: NODOS & BASES DE DATOS (Ajustado para que se vean siempre todos los nodos y bases de datos)
        Tab tabDatabases = new Tab("Bases & Nodos", buildDatabasesAndNodesPanel());

        // TAB 2: AGENTE PECS & TELEMETRÍA
        Tab tabAgent = new Tab("Agente PECS", buildAgentPecsPanel());

        // TAB 3: CHAT CON EL AGENTE
        Tab tabChat = new Tab("Chat Sentinel", buildChatPanel());

        tabPane.getTabs().addAll(tabDatabases, tabAgent, tabChat);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        sidebarContainer.getChildren().addAll(sidebarTitle, tabPane);
        return sidebarContainer;
    }

    private ScrollPane buildDatabasesAndNodesPanel() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: transparent;");

        // 1. SECCIÓN: NODOS DEL CLÚSTER RAFT
        Label nodesTitle = new Label("NODOS DEL CLÚSTER (Raft Consensus)");
        nodesTitle.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox nodesBox = new VBox(6);

        // Node-01 (Master)
        nodesBox.getChildren().add(createNodeItemCard(
            "Node-01 [MASTER / LEADER]", "127.0.0.1:9091", "ONLINE", "#22C55E",
            "Rol: Primario | MemTable: 22% | Panama FFM",
            0, -45, -70
        ));

        // Node-02 (Secondary)
        nodesBox.getChildren().add(createNodeItemCard(
            "Node-02 [SECONDARY / REPLICA]", "127.0.0.1:9092", "ONLINE", "#38BDF8",
            "Rol: Réplica Lectura | Latencia: 0.2ms",
            -160, -38, -30
        ));

        // Node-03 (Secondary)
        nodesBox.getChildren().add(createNodeItemCard(
            "Node-03 [SECONDARY / REPLICA]", "127.0.0.1:9093", "ONLINE", "#38BDF8",
            "Rol: Réplica Lectura | Latencia: 0.3ms",
            160, -38, -30
        ));

        // 2. SECCIÓN: BASES DE DATOS MULTIMODELO EN EL CLÚSTER
        Label dbsTitle = new Label("BASES DE DATOS MULTIMODELO");
        dbsTitle.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 0 0 0;");

        VBox dbsBox = new VBox(6);

        // example_factura_db (3M Objetos)
        dbsBox.getChildren().add(createDbItemCard(
            "example_factura_db", "★ 3,000,000 OBJETOS", "#FACC15",
            "9 Buckets: 1M Facturas, 1M Detalles, 200k Clientes, KV, Vectores, Grafos",
            -110, -48, 60
        ));

        // sample_enterprise_db
        dbsBox.getChildren().add(createDbItemCard(
            "sample_enterprise_db", "8 BUCKETS", "#C084FC",
            "Empleados, Departamentos, Biometría 3D, Catálogo, KV",
            110, -40, 60
        ));

        // sample_ecommerce_db
        dbsBox.getChildren().add(createDbItemCard(
            "sample_ecommerce_db", "ECOMMERCE", "#38BDF8",
            "Clientes VIP, Órdenes JettraRef, Analítica Columnar, Carts",
            0, -30, 0
        ));

        // sample_ai_graph_db
        dbsBox.getChildren().add(createDbItemCard(
            "sample_ai_graph_db", "IA & GRAFOS", "#4ADE80",
            "Red de Grafos de Conocimiento, Embeddings 3D, Prompts",
            0, -30, 0
        ));

        // sample_iot_telemetry_db
        dbsBox.getChildren().add(createDbItemCard(
            "sample_iot_telemetry_db", "IOT & GEO", "#FB923C",
            "Sensores Temperatura/Vibración, GIS Sucursales",
            0, -30, 0
        ));

        content.getChildren().addAll(nodesTitle, nodesBox, dbsTitle, dbsBox);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return sp;
    }

    private VBox createNodeItemCard(String name, String host, String status, String statusColor,
                                   String details, double focusX, double focusY, double focusZ) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(6, 8, 6, 8));
        card.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-border-color: #334155; " +
                      "-fx-border-radius: 5; -fx-background-radius: 5;");

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 10px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLbl = new Label(status);
        statusLbl.setStyle("-fx-background-color: " + statusColor + "; -fx-text-fill: #0F172A; " +
                           "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 1 5 1 5; -fx-background-radius: 3;");

        topRow.getChildren().addAll(nameLabel, spacer, statusLbl);

        Label hostLabel = new Label("Host: " + host + " | " + details);
        hostLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9px;");

        Button focusBtn = new Button("Enfocar en 3D");
        focusBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #38BDF8; -fx-font-size: 9px; -fx-padding: 2 6 2 6;");
        focusBtn.setOnAction(e -> focusOnTarget(focusX, focusY, focusZ, name));

        card.getChildren().addAll(topRow, hostLabel, focusBtn);
        return card;
    }

    private VBox createDbItemCard(String dbName, String badgeText, String badgeColor, String summary,
                                 double focusX, double focusY, double focusZ) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(6, 8, 6, 8));
        card.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-border-color: " + badgeColor + "44; " +
                      "-fx-border-radius: 5; -fx-background-radius: 5;");

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(dbName);
        nameLabel.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(badgeText);
        badge.setStyle("-fx-background-color: " + badgeColor + "; -fx-text-fill: #0F172A; " +
                       "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 1 5 1 5; -fx-background-radius: 3;");

        topRow.getChildren().addAll(nameLabel, spacer, badge);

        Label desc = new Label(summary);
        desc.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9px; -fx-wrap-text: true;");

        Button focusBtn = new Button("Enfocar Monolito 3D");
        focusBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: " + badgeColor + "; -fx-font-size: 9px; -fx-padding: 2 6 2 6;");
        focusBtn.setOnAction(e -> focusOnTarget(focusX, focusY, focusZ, dbName));

        card.getChildren().addAll(topRow, desc, focusBtn);
        return card;
    }

    private ScrollPane buildAgentPecsPanel() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setStyle("-fx-background-color: transparent;");

        // 1. Tarjeta Modelo PECS
        VBox pecsCard = new VBox(5);
        pecsCard.setPadding(new Insets(8));
        pecsCard.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-border-color: #38BDF8; " +
                          "-fx-border-radius: 6; -fx-background-radius: 6;");

        Label pecsTitle = new Label("ESTADO PECS DEL AGENTE (JettraICore)");
        pecsTitle.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label agentName = new Label("Agente: Jettra Sentinel [Líder]");
        agentName.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 11px;");

        Label goal = new Label("Meta: Patrulla Preventiva & Monitoreo Raft");
        goal.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 10px;");

        Label mood = new Label("Estado de Ánimo: 98% (Alerta Óptima)");
        mood.setStyle("-fx-text-fill: #C084FC; -fx-font-size: 10px;");

        Label health = new Label("Salud de Nodos: 100% (Clúster Sano)");
        health.setStyle("-fx-text-fill: #4ADE80; -fx-font-size: 10px;");

        Label buffer = new Label("Buffer MemTable: 22% (Off-Heap)");
        buffer.setStyle("-fx-text-fill: #FB923C; -fx-font-size: 10px;");

        Label io = new Label("I/O Panama FFM: 15% (Latencia < 1ms)");
        io.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 10px;");

        Label personality = new Label("Personalidad: Open: 0.92 | Consc: 0.98 | Extr: 0.85");
        personality.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9px; -fx-font-style: italic;");

        pecsCard.getChildren().addAll(pecsTitle, agentName, goal, mood, health, buffer, io, personality);

        // 2. LIVE FEED (Registro en vivo de eventos)
        VBox liveFeedContainer = new VBox(4);
        Label liveFeedTitle = new Label("LIVE FEED (TELEMETRÍA)");
        liveFeedTitle.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 11px; -fx-font-weight: bold;");

        this.liveFeedBox = new VBox(3);
        liveFeedBox.setPadding(new Insets(6));
        liveFeedBox.setStyle("-fx-background-color: rgba(2, 6, 23, 0.8); -fx-border-color: #334155; " +
                             "-fx-border-radius: 4; -fx-background-radius: 4;");
        liveFeedContainer.getChildren().addAll(liveFeedTitle, liveFeedBox);

        // 3. TOP 5 CONOCIMIENTO CRÍTICO
        VBox knowledgeContainer = new VBox(4);
        Label kTitle = new Label("TOP CONOCIMIENTO CRÍTICO");
        kTitle.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox kBox = new VBox(3);
        kBox.setPadding(new Insets(6));
        kBox.setStyle("-fx-background-color: rgba(15, 23, 42, 0.8); -fx-border-color: #334155; " +
                      "-fx-border-radius: 4; -fx-background-radius: 4;");

        Label k1 = new Label("• [99.4%] Mitigación auto saturación MemTable");
        k1.setStyle("-fx-text-fill: #4ADE80; -fx-font-size: 10px;");
        Label k2 = new Label("• [98.9%] Recuperación preventiva nodo réplica");
        k2.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 10px;");
        Label k3 = new Label("• [100.0%] Validación criptográfica JettraJWT");
        k3.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 10px;");
        Label k4 = new Label("• [97.8%] Carga Lazy de referencias en JQL");
        k4.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 10px;");
        kBox.getChildren().addAll(k1, k2, k3, k4);
        knowledgeContainer.getChildren().addAll(kTitle, kBox);

        sidebar.getChildren().addAll(pecsCard, liveFeedContainer, knowledgeContainer);

        ScrollPane sp = new ScrollPane(sidebar);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return sp;
    }

    private VBox buildChatPanel() {
        VBox chatContainer = new VBox(6);
        chatContainer.setPadding(new Insets(10));
        chatContainer.setStyle("-fx-background-color: transparent;");

        Label chatTitle = new Label("CANAL DIRECTO CON SENTINEL");
        chatTitle.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 11px; -fx-font-weight: bold;");

        this.chatHistoryBox = new VBox(4);
        chatHistoryBox.setPadding(new Insets(6));

        this.chatScrollPane = new ScrollPane(chatHistoryBox);
        chatScrollPane.setPrefHeight(380);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setStyle("-fx-background: transparent; -fx-background-color: rgba(2, 6, 23, 0.8); " +
                                "-fx-border-color: #0284C7; -fx-border-radius: 4;");

        addChatMessage("Sentinel", "Sistemas en línea. Vigilando clúster Raft y base example_factura_db.");

        HBox chatInputRow = new HBox(6);
        this.chatInputField = new TextField();
        chatInputField.setPromptText("Preguntar a Sentinel (status, 3m, alerta)...");
        chatInputField.setStyle("-fx-background-color: #0F172A; -fx-text-fill: #4ADE80; -fx-font-size: 11px; " +
                                "-fx-border-color: #38BDF8; -fx-border-radius: 4;");
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        chatInputField.setOnAction(e -> handleUserChat());

        Button sendBtn = new Button("Enviar");
        sendBtn.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> handleUserChat());
        chatInputRow.getChildren().addAll(chatInputField, sendBtn);

        chatContainer.getChildren().addAll(chatTitle, chatScrollPane, chatInputRow);
        return chatContainer;
    }

    private HBox buildQuickAccessBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: rgba(11, 19, 43, 0.88); -fx-border-color: #0284C7; " +
                     "-fx-border-width: 1; -fx-border-radius: 20; -fx-background-radius: 20;");

        Label lbl = new Label("VISTA 3D RÁPIDA:");
        lbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 9px; -fx-font-weight: bold;");

        Button b1 = new Button("★ Facturas (3M)");
        b1.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 12;");
        b1.setOnAction(e -> focusOnTarget(-110, -48, 60, "example_factura_db"));

        Button b2 = new Button("Node-01 (Master)");
        b2.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 12;");
        b2.setOnAction(e -> focusOnTarget(0, -45, -70, "Node-01 Master"));

        Button b3 = new Button("Enterprise DB");
        b3.setStyle("-fx-background-color: #7C3AED; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 12;");
        b3.setOnAction(e -> focusOnTarget(110, -40, 60, "sample_enterprise_db"));

        Button bPlano = new Button("📐 Plano Cartesiano");
        bPlano.setStyle("-fx-background-color: #06B6D4; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 12;");
        bPlano.setOnAction(e -> focusOnCartesianPlane());

        Button b4 = new Button("Origen (0,0,0)");
        b4.setStyle("-fx-background-color: #334155; -fx-text-fill: #E2E8F0; -fx-font-size: 9px; -fx-background-radius: 12;");
        b4.setOnAction(e -> resetCamera());

        bar.getChildren().addAll(lbl, bPlano, b1, b2, b3, b4);
        return bar;
    }

    private void focusOnTarget(double x, double y, double z, String targetName) {
        followMode = false;
        cameraRotateX.setAngle(-20);
        cameraRotateY.setAngle(x > 0 ? -15 : (x < 0 ? 15 : 0));
        cameraTranslate.setX(x);
        cameraTranslate.setY(y - 30);
        cameraTranslate.setZ(z - 280);

        updateAgentThought("Enfocando objetivo 3D: " + targetName, "Inspección Visual Monolito", "[Enfoque 3D]", "#38BDF8");
        addLiveFeedEvent("Cámara 3D orientada hacia: " + targetName);
    }

    private VBox buildFloatingThoughtBubble() {
        this.floatingThoughtOverlay = new VBox(2);
        floatingThoughtOverlay.setAlignment(Pos.CENTER);
        floatingThoughtOverlay.setMaxWidth(400);
        floatingThoughtOverlay.setPadding(new Insets(8, 14, 8, 14));
        floatingThoughtOverlay.setStyle("-fx-background-color: rgba(10, 15, 30, 0.94); " +
                                       "-fx-border-color: #FACC15; -fx-border-width: 1.5; " +
                                       "-fx-border-radius: 8; -fx-background-radius: 8;");

        this.floatingThoughtHeader = new Label("JETTRA SENTINEL [LÍDER] - [Pensando...]");
        floatingThoughtHeader.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 11px; -fx-font-weight: bold;");

        this.floatingThoughtContent = new Label(world3D.getAgentMesh().getThought());
        floatingThoughtContent.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 11px; -fx-wrap-text: true; -fx-text-alignment: center;");

        this.floatingThoughtGoal = new Label("Meta: " + world3D.getAgentMesh().getGoal());
        floatingThoughtGoal.setStyle("-fx-text-fill: #4ADE80; -fx-font-size: 10px; -fx-font-style: italic;");

        floatingThoughtOverlay.getChildren().addAll(floatingThoughtHeader, floatingThoughtContent, floatingThoughtGoal);
        return floatingThoughtOverlay;
    }

    private void updateAgentThought(String thought, String goal, String header, String borderColor) {
        world3D.getAgentMesh().setThought(thought);
        if (goal != null) world3D.getAgentMesh().setGoal(goal);
        if (floatingThoughtContent != null) floatingThoughtContent.setText(thought);
        if (goal != null && floatingThoughtGoal != null) floatingThoughtGoal.setText("Meta: " + goal);
        if (header != null && floatingThoughtHeader != null) floatingThoughtHeader.setText("JETTRA SENTINEL [LÍDER] - " + header);
        if (borderColor != null && floatingThoughtOverlay != null) {
            floatingThoughtOverlay.setStyle("-fx-background-color: rgba(10, 15, 30, 0.94); " +
                                            "-fx-border-color: " + borderColor + "; -fx-border-width: 1.5; " +
                                            "-fx-border-radius: 8; -fx-background-radius: 8;");
        }
    }

    private VBox buildHelpOverlay() {
        VBox overlay = new VBox(4);
        overlay.setPadding(new Insets(10));
        overlay.setMaxWidth(230);
        overlay.setStyle("-fx-background-color: rgba(10, 15, 30, 0.88); -fx-border-color: #FACC15; " +
                         "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label helpTitle = new Label("CONTROLES 3D (JettraICore)");
        helpTitle.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label h1 = new Label("• Arrastrar Ratón: Girar Cámara (Orbit)");
        Label h2 = new Label("• Rueda Ratón: Zoom +/-");
        Label h3 = new Label("• Teclas W,S,A,D: Desplazar");
        Label h4 = new Label("• C: Resetear Cámara");
        Label h5 = new Label("• F: Modo Seguir Agente (Follow)");
        Label h6 = new Label("• P: Plano Cartesiano (Primer Plano)");

        String itemStyle = "-fx-text-fill: #CBD5E1; -fx-font-size: 10px;";
        h1.setStyle(itemStyle);
        h2.setStyle(itemStyle);
        h3.setStyle(itemStyle);
        h4.setStyle(itemStyle);
        h5.setStyle(itemStyle);

        h6.setStyle(itemStyle);
        overlay.getChildren().addAll(helpTitle, h1, h2, h3, h4, h5, h6);
        return overlay;
    }

    private HBox buildBottomControls() {
        HBox bottom = new HBox(12);
        bottom.setPadding(new Insets(10, 20, 10, 20));
        bottom.setAlignment(Pos.CENTER);
        bottom.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-width: 1.5 0 0 0;");

        Button btnNormal = new Button("Patrulla Normal");
        btnNormal.setStyle("-fx-background-color: #047857; -fx-text-fill: white; -fx-font-weight: bold;");
        btnNormal.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.NORMAL);
            updateAgentThought("Monitoreando estado óptimo de los 3 nodos Raft...", "Patrulla Preventiva & Salud Raft", "[Patrulla Normal]", "#4ADE80");
            statusBadge.setText("AGENTE: PATRULLA PREVENTIVA ACTIVA");
            statusBadge.setStyle("-fx-background-color: #047857; -fx-text-fill: white; -fx-padding: 3 10 3 10; -fx-background-radius: 4;");
            addLiveFeedEvent("Patrulla reanudada en modo normal.");
        });

        Button btnRamAlert = new Button("Alerta Saturación RAM");
        btnRamAlert.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-font-weight: bold;");
        btnRamAlert.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.WARNING_RAM);
            updateAgentThought("¡Intervención! Rebalanceando MemTable Off-Heap...", "Mitigación de Presión RAM", "[Alerta RAM]", "#F59E0B");
            statusBadge.setText("AGENTE: ALERTA DE MEMORIA (INTERVENCIÓN)");
            statusBadge.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-padding: 3 10 3 10; -fx-background-radius: 4;");
            addLiveFeedEvent("[WARN] Presión en MemTable. ZGC en pausa de 0.2ms.");
        });

        Button btnSecurityAlert = new Button("Alerta Intrusión");
        btnSecurityAlert.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSecurityAlert.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.CRITICAL_SECURITY);
            updateAgentThought("¡BLOQUEO DE INTRUSIÓN! Revocando token JWT...", "Contención de Seguridad Inmediata", "[¡ALERTA CRÍTICA!]", "#EF4444");
            statusBadge.setText("AGENTE: ¡INTRUSIÓN CRÍTICA BLOQUEADA!");
            statusBadge.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-padding: 3 10 3 10; -fx-background-radius: 4;");
            addLiveFeedEvent("[CRITICAL] Intrusión no autorizada rechazada por JettraJWT.");
        });

        Button btnFacturas = new Button("Base Facturas (3M Objs)");
        btnFacturas.setStyle("-fx-background-color: #FACC15; -fx-text-fill: #0F172A; -fx-font-weight: bold;");
        btnFacturas.setOnAction(e -> {
            focusOnTarget(-110, -48, 60, "example_factura_db");
            updateAgentThought("Inspeccionando 3,000,000 objetos en 'example_factura_db'...", "Validación de Referencias Cruzadas JettraRef", "[Carga Masiva Facturas]", "#FACC15");
            addLiveFeedEvent("[DB] 'example_factura_db' en línea (3,000,000 objetos vinculados)");
        });

        Button btnFollow = new Button("Modo Seguir (F)");
        btnFollow.setStyle("-fx-background-color: #475569; -fx-text-fill: white;");
        btnFollow.setOnAction(e -> {
            followMode = !followMode;
            btnFollow.setStyle(followMode ? "-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold;" 
                                          : "-fx-background-color: #475569; -fx-text-fill: white;");
            addLiveFeedEvent("Modo Seguir Agente: " + (followMode ? "ACTIVADO" : "DESACTIVADO"));
        });

        Button btnCartesian = new Button("Plano Cartesiano (P)");
        btnCartesian.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold;");
        btnCartesian.setOnAction(e -> focusOnCartesianPlane());

        Button btnResetCam = new Button("Reset Cámara (C)");
        btnResetCam.setStyle("-fx-background-color: #334155; -fx-text-fill: #E2E8F0;");
        btnResetCam.setOnAction(e -> resetCamera());

        bottom.getChildren().addAll(btnNormal, btnCartesian, btnRamAlert, btnSecurityAlert, btnFacturas, btnFollow, btnResetCam);
        return bottom;
    }

    private void initCameraMouseControls(SubScene subScene) {
        subScene.setOnMousePressed(e -> {
            mouseAnchorX = e.getSceneX();
            mouseAnchorY = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseAnchorX;
            double deltaY = e.getSceneY() - mouseAnchorY;
            cameraRotateY.setAngle(cameraRotateY.getAngle() + deltaX * 0.25);
            cameraRotateX.setAngle(Math.max(-80, Math.min(10, cameraRotateX.getAngle() - deltaY * 0.25)));
            mouseAnchorX = e.getSceneX();
            mouseAnchorY = e.getSceneY();
        });

        subScene.setOnScroll(e -> {
            double delta = e.getDeltaY();
            cameraTranslate.setZ(Math.min(-120, Math.max(-1300, cameraTranslate.getZ() + delta * 1.5)));
        });
    }

    private void initKeyboardControls(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.P) {
                focusOnCartesianPlane();
            } else if (e.getCode() == KeyCode.C) {
                resetCamera();
            } else if (e.getCode() == KeyCode.F) {
                followMode = !followMode;
                addLiveFeedEvent("Modo Seguir Agente: " + (followMode ? "ON" : "OFF"));
            } else if (e.getCode() == KeyCode.W) {
                cameraTranslate.setY(cameraTranslate.getY() + 15);
            } else if (e.getCode() == KeyCode.S) {
                cameraTranslate.setY(cameraTranslate.getY() - 15);
            } else if (e.getCode() == KeyCode.A) {
                cameraTranslate.setX(cameraTranslate.getX() - 15);
            } else if (e.getCode() == KeyCode.D) {
                cameraTranslate.setX(cameraTranslate.getX() + 15);
            }
        });
    }

    public void focusOnCartesianPlane() {
        followMode = false;
        cameraRotateX.setAngle(-34);
        cameraRotateY.setAngle(0);
        cameraTranslate.setX(0);
        cameraTranslate.setY(-180);
        cameraTranslate.setZ(-400);
        updateAgentThought("Inspeccionando coordenadas en Plano Cartesiano tridimensional...", "Análisis Espacial en Primer Plano", "[Plano Cartesiano]", "#06B6D4");
        addLiveFeedEvent("[3D] Plano cartesiano enfocado en primer plano (Ejes X, Y, Z y retícula activa).");
    }

    private void resetCamera() {
        focusOnCartesianPlane();
        addLiveFeedEvent("Cámara 3D restablecida con Plano Cartesiano en primer plano.");
    }

    private void addLiveFeedEvent(String msg) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Label l = new Label(String.format("[%s] %s", time, msg));
        l.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 10px;");
        if (liveFeedBox != null) {
            liveFeedBox.getChildren().add(0, l);
            if (liveFeedBox.getChildren().size() > 7) {
                liveFeedBox.getChildren().remove(7);
            }
        }
    }

    private void addChatMessage(String sender, String msg) {
        Label l = new Label(sender + ": " + msg);
        if (sender.equals("Tú")) {
            l.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 10px; -fx-font-weight: bold;");
        } else {
            l.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 10px;");
        }
        if (chatHistoryBox != null) {
            chatHistoryBox.getChildren().add(l);
            if (chatScrollPane != null) chatScrollPane.setVvalue(1.0);
        }
    }

    private void handleUserChat() {
        String input = chatInputField.getText().trim();
        if (input.isBlank()) return;

        addChatMessage("Tú", input);
        chatInputField.clear();

        String lower = input.toLowerCase();
        String reply;
        if (lower.contains("status") || lower.contains("estado")) {
            reply = "Clúster 100% operativo. 3 Nodos en anillo Raft, MemTable al 22%, ZGC activo.";
            updateAgentThought("Reportando estado de telemetría del clúster.", "Telemetría de Sistema", "[Reporte]", "#38BDF8");
        } else if (lower.contains("factura") || lower.contains("3m")) {
            reply = "Base 'example_factura_db' cargada con 3,000,000 objetos multimodelo y JettraRef.";
            focusOnTarget(-110, -48, 60, "example_factura_db");
            updateAgentThought("Verificando consistencia de 3M objetos en 'example_factura_db'.", "Integridad de Facturas", "[Facturación 3M]", "#FACC15");
        } else if (lower.contains("alerta") || lower.contains("peligro")) {
            reply = "No hay intrusiones activas. Escaneando puertos Panama FFM cada 50ms.";
            updateAgentThought("Intensificando barrido de seguridad perimetral.", "Barrido Anti-Intrusión", "[Escaneo]", "#EF4444");
        } else {
            reply = "Entendido. Mantengo la patrulla preventiva y la integridad de los datos.";
            updateAgentThought("Conversando con el operador del sistema...", "Interacción con Usuario", "[Comunicación]", "#4ADE80");
        }

        addChatMessage("Sentinel", reply);
    }

    @Override
    public void stop() {
        if (timer != null) timer.stop();
        if (client != null) client.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
