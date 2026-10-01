package io.jettra.core.three.d;

import java.util.List;

import static com.raylib.Raylib.*;
import com.raylib.Color;
import com.raylib.Vector3;
import com.raylib.Vector2;
import com.raylib.Rectangle;
import com.raylib.Camera3D;
import com.raylib.Font;

import io.jettra.core.three.d.model.Artifact;
import io.jettra.core.three.d.model.HumanEntity;
import io.jettra.core.three.d.model.MegaProject;
import io.jettra.core.three.d.model.Thought;
import io.jettra.core.three.d.model.WorldEvent;
import io.jettra.core.three.d.model.ServerNode3D;
import io.jettra.core.three.d.model.DatabaseInfo3D;
import io.jettra.core.three.d.police.JettraStorePoliceMonitor;
import io.jettra.core.three.d.voice.JettraVoiceNarrator;
import io.jettra.core.three.d.config.ConnectionManager;
import io.jettra.core.three.d.config.ConnectionProfile;
import io.jettra.core.three.d.model.ClusterDataTraffic;
import io.jettra.core.three.d.model.JettraLiveSession;
import io.jettra.core.three.d.model.JettraPoliceAgent;
import io.jettra.core.three.d.model.UserZoneGroup;
import io.jettra.store.cluster.ClusterNode;
import com.raylib.BoundingBox;
import com.raylib.Ray;
import com.raylib.RayCollision;
import static com.raylib.Raylib.ConfigFlags.*;
import static com.raylib.Raylib.KeyboardKey.*;
import static com.raylib.Raylib.MouseButton.*;
import static com.raylib.Raylib.CameraProjection.*;
import static com.raylib.Raylib.CameraMode.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class Jettra3DApp {
    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 720;

    private Font mainFont;
    private Camera3D camera;
    private List<Artifact> artifacts = new CopyOnWriteArrayList<>();
    private List<HumanEntity> entities = new CopyOnWriteArrayList<>();
    private List<MegaProject> megaProjects = new CopyOnWriteArrayList<>();
    private List<Thought> thoughts = new CopyOnWriteArrayList<>();
    private List<WorldEvent> worldEvents = new CopyOnWriteArrayList<>();

    private float worldTime = 0;

    private void triggerWorldEvent(String text, int r, int g, int b) {
        WorldEvent ev = new WorldEvent(text, worldTime, r, g, b);
        worldEvents.add(ev);
        if (worldEvents.size() > 60) {
            worldEvents.remove(0);
        }
        if (voiceEnabled) {
            JettraVoiceNarrator.getInstance().speak(text);
        }
    }

    // Panel de Gestión de Conexiones a JettraStore
    private boolean showConnectionModal = false;
    private String formConnId = "";
    private String formConnName = "JettraStore Local Master";
    private String formConnUrl = "tcp://127.0.0.1:8765";
    private String formConnUsername = "admin";
    private String formConnPassword = "admin";
    private boolean formConnIsDefault = true;
    private int activeConnField = 0; // 0: Ninguno, 1: Nombre, 2: URL, 3: Usuario, 4: Password
    private String selectedProfileId = "";
    private String connStatusFeedback = "";
    private float connStatusFeedbackTimer = 0f;
    private int selectedAgentIndex = -1;
    private int selectedArtifactIndex = -1;
    private boolean followMode = false;
    private boolean directorMode = false;
    private float directorTimer = 0;
    private float planeScale = 1.0f;
    private boolean isAnchored = true;
    private boolean cameraLocked = false;
    private boolean showHelp = true;
    private boolean showConfigModal = false;
    private boolean voiceEnabled = true;

    // Config Toggles
    private boolean configEnabledApps = true;
    private boolean configEnabledInternet = true;
    private boolean configEnabledFiles = true;
    private boolean configEnabledLife = true;
    private boolean configEnabledSocial = true;
    
    // Chat & Knowledge UI
    private boolean showChat = false;
    private String chatInput = "";
    private List<String> chatHistory = new java.util.ArrayList<>();
    private List<KnowledgeEntry> allKnowledge = new java.util.ArrayList<>();
    private List<KnowledgeEntry> topKnowledge = new java.util.ArrayList<>();
    private float topKnowledgeUpdateTimer = 0;

    private float howlTimer = 0;
    private float globalSaveTimer = 60.0f; // Save state every 60 seconds
    private float pdfScanTimer = 10.0f;
    private float timeScale = 1.0f;
    private int weatherMode = 0; // 0: Sunny, 1: Night, 2: Storm
    private boolean sfxEnabled = true;

    // JettraStore Cluster Monitoring & Police Sentinel
    private JettraStorePoliceMonitor policeMonitor;

    // Modos de mundo e inmersión submundos (Nodos / Bases de Datos)
    public enum WorldMode {
        MAIN_WORLD,
        EXPANDING_TRANSITION,
        INNER_NODE_WORLD,
        EXITING_TRANSITION
    }

    private WorldMode worldMode = WorldMode.MAIN_WORLD;
    private ServerNode3D expandedNode = null;
    private DatabaseInfo3D selectedDatabase = null;
    private float transitionTimer = 0f;
    private float innerWorldTime = 0f;
    private Vector3 savedMainCameraPos = new Vector3().x(35).y(35).z(35);
    private Vector3 savedMainCameraTarget = new Vector3().x(0).y(0).z(0);
    private float savedMainCameraFov = 45f;
    private ServerNode3D selectedServerNode = null;
    private boolean showNodeInspectorModal = false;
    private HumanEntity policeSentinelEntity = null;
    private float policePatrolTimer = 0f;
    private int currentPatrolTargetNodeIndex = 0;

    public void run() {
        setConfigFlags(FLAG_WINDOW_RESIZABLE);
        initWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Jettra 3D Core - Java 25 Native");
        maximizeWindow();
        setTargetFPS(60);

        initAudioDevice();
        initSfx();

        mainFont = loadFont("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf");
        if (mainFont != null) {
            setTextureFilter(mainFont.texture(), 1); // 1 = FILTER_TRILINEAR
        }

        initCamera();
        initPoliceMonitor();
        initPopulation();

        while (!windowShouldClose()) {
            float dt = getFrameTime();
            float scaledDt = dt * timeScale;
            worldTime += scaledDt;

            handleInput();
            update(scaledDt);
            draw();
        }

        if (policeMonitor != null) {
            policeMonitor.close();
        }
        closeAudioDevice();
        closeWindow();
    }

    private void initCamera() {
        camera = new Camera3D();
        camera.position(new Vector3().x(35).y(35).z(35));
        camera.target(new Vector3().x(0).y(0).z(0));
        camera.up(new Vector3().x(0).y(1).z(0));
        camera.fovy(45);
        camera.projection(CAMERA_PERSPECTIVE);
    }
    private void handleInput() {
        if (showConfigModal) return;

        // Atajo 'K': Abrir / Cerrar Panel de Gestión de Conexiones
        if (isKeyPressed(KEY_K)) {
            showConnectionModal = !showConnectionModal;
            if (showConnectionModal) {
                loadSelectedProfileIntoForm();
            }
        }

        // Atajo 'R': Resetear el mundo e interactuar con JettraStore en tiempo real
        if (isKeyPressed(KEY_R) && worldMode == WorldMode.MAIN_WORLD) {
            resetWorldWithJettraStore();
        }

        if (isKeyPressed(KEY_TAB)) showHelp = !showHelp;
        if (isKeyPressed(KEY_C)) {
            initCamera();
            followMode = false;
            cameraLocked = false;
        }
        if (isKeyPressed(KEY_F)) {
            followMode = !followMode;
            if (followMode && selectedAgentIndex == -1 && !entities.isEmpty()) {
                selectedAgentIndex = 0;
            }
        }
        if (isKeyPressed(KEY_L)) isAnchored = !isAnchored;
        
        // Chat Toggle
        if (isKeyPressed(KEY_ENTER)) {
            if (showChat && !chatInput.trim().isEmpty()) {
                sendChatMessage(chatInput);
                chatInput = "";
            } else {
                showChat = !showChat;
            }
        }

        if (showConnectionModal) {
            if (isKeyPressed(KEY_ESCAPE)) {
                showConnectionModal = false;
            }
            if (isKeyPressed(KEY_TAB)) {
                activeConnField = (activeConnField >= 4) ? 1 : activeConnField + 1;
            }
            int key = getCharPressed();
            while (key > 0) {
                if ((key >= 32) && (key <= 126)) {
                    char c = (char) key;
                    switch (activeConnField) {
                        case 1 -> formConnName += c;
                        case 2 -> formConnUrl += c;
                        case 3 -> formConnUsername += c;
                        case 4 -> formConnPassword += c;
                    }
                }
                key = getCharPressed();
            }
            if (isKeyPressed(KEY_BACKSPACE)) {
                switch (activeConnField) {
                    case 1 -> { if (!formConnName.isEmpty()) formConnName = formConnName.substring(0, formConnName.length() - 1); }
                    case 2 -> { if (!formConnUrl.isEmpty()) formConnUrl = formConnUrl.substring(0, formConnUrl.length() - 1); }
                    case 3 -> { if (!formConnUsername.isEmpty()) formConnUsername = formConnUsername.substring(0, formConnUsername.length() - 1); }
                    case 4 -> { if (!formConnPassword.isEmpty()) formConnPassword = formConnPassword.substring(0, formConnPassword.length() - 1); }
                }
            }
            return;
        }

        if (showChat) {
            int key = getCharPressed();
            while (key > 0) {
                if ((key >= 32) && (key <= 125)) {
                    chatInput += (char)key;
                }
                key = getCharPressed();
            }
            if (isKeyPressed(KEY_BACKSPACE) && !chatInput.isEmpty()) {
                chatInput = chatInput.substring(0, chatInput.length() - 1);
            }
        }

        float wheel = getMouseWheelMove();
        if (wheel != 0) {
            camera.fovy(camera.fovy() - wheel * 2);
            if (camera.fovy() < 5) camera.fovy(5);
            if (camera.fovy() > 120) camera.fovy(120);
        }

        // --- MODO MUNDO PRINCIPAL: CLIC IZQUIERDO PARA EXPANDIR NODO ---
        if (worldMode == WorldMode.MAIN_WORLD) {
            if (isMouseButtonPressed(MOUSE_BUTTON_LEFT) && !showConfigModal && !showNodeInspectorModal && !showChat) {
                Vector2 mouse = getMousePosition();
                Ray ray = getScreenToWorldRay(mouse, camera);
                if (policeMonitor != null) {
                    float minDist = Float.MAX_VALUE;
                    ServerNode3D hitNode = null;
                    for (ServerNode3D node : policeMonitor.getServerNodes()) {
                        RayCollision col = getRayCollisionBox(ray, node.getBoundingBox());
                        if (col.hit() && col.distance() < minDist) {
                            minDist = col.distance();
                            hitNode = node;
                        }
                    }
                    if (hitNode != null) {
                        startNodeExpansion(hitNode);
                        return;
                    }
                }
            }
        }

        // --- MODO MUNDO INTERIOR DEL NODO: CLIC EN PUERTA O EN BASES DE DATOS ---
        if (worldMode == WorldMode.INNER_NODE_WORLD) {
            if (isKeyPressed(KEY_ESCAPE) || isKeyPressed(KEY_BACKSPACE) || isKeyPressed(KEY_P)) {
                if (selectedDatabase != null) {
                    selectedDatabase = null;
                } else {
                    startExitTransition();
                }
                return;
            }

            if (isMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                Vector2 mouse = getMousePosition();

                // 1. Botón cerrar detalle de base de datos
                if (selectedDatabase != null) {
                    Rectangle detailCloseRec = new Rectangle().x(getScreenWidth() / 2 + 200).y(120).width(30).height(30);
                    if (checkCollisionPointRec(mouse, detailCloseRec)) {
                        selectedDatabase = null;
                        return;
                    }
                }

                // 2. Botón superior: Atravesar Puerta y Salir
                Rectangle topExitBtnRec = new Rectangle().x(getScreenWidth() - 410).y(12).width(390).height(36);
                if (checkCollisionPointRec(mouse, topExitBtnRec)) {
                    startExitTransition();
                    return;
                }

                // 3. Botón desconectar / reconectar nodo en panel de recursos
                if (expandedNode != null) {
                    Rectangle toggleRec = new Rectangle().x(35).y(515).width(350).height(32);
                    if (checkCollisionPointRec(mouse, toggleRec)) {
                        expandedNode.toggleOffline();
                        worldEvents.add(new WorldEvent(
                            "JettraStorePolice: Servidor " + expandedNode.getId() + " conmutado a " + (expandedNode.isOnline() ? "EN LÍNEA" : "FUERA DE SERVICIO"),
                            worldTime, expandedNode.isOnline() ? 50 : 255, expandedNode.isOnline() ? 255 : 50, 50));
                        return;
                    }
                }

                // 4. Raycasting en el Mundo 3D
                Ray ray = getScreenToWorldRay(mouse, camera);

                // Clic en la Puerta Dimensional de Regreso
                RayCollision doorCol = getRayCollisionBox(ray, getDoorBoundingBox());
                if (doorCol.hit()) {
                    startExitTransition();
                    return;
                }

                // Clic en alguna Base de Datos 3D
                if (expandedNode != null) {
                    float minDbDist = Float.MAX_VALUE;
                    DatabaseInfo3D hitDb = null;
                    for (DatabaseInfo3D db : expandedNode.getDatabases()) {
                        RayCollision dbCol = getRayCollisionBox(ray, db.getBoundingBox());
                        if (dbCol.hit() && dbCol.distance() < minDbDist) {
                            minDbDist = dbCol.distance();
                            hitDb = db;
                        }
                    }
                    if (hitDb != null) {
                        selectedDatabase = hitDb;
                        return;
                    }
                }
            }
        }

        // Clic Derecho en Nodos para ver recursos consumidos
        if (isMouseButtonPressed(MOUSE_BUTTON_RIGHT)) {
            Vector2 mouse = getMousePosition();
            Ray ray = getScreenToWorldRay(mouse, camera);
            ServerNode3D clicked = null;
            float minDistance = Float.MAX_VALUE;

            if (policeMonitor != null) {
                for (ServerNode3D node : policeMonitor.getServerNodes()) {
                    RayCollision col = getRayCollisionBox(ray, node.getBoundingBox());
                    if (col.hit() && col.distance() < minDistance) {
                        minDistance = col.distance();
                        clicked = node;
                    }
                }
            }

            if (clicked != null) {
                selectedServerNode = clicked;
                showNodeInspectorModal = true;
            } else if (showNodeInspectorModal) {
                Rectangle modalRec = new Rectangle()
                    .x((getScreenWidth() - 560) / 2)
                    .y((getScreenHeight() - 480) / 2)
                    .width(560)
                    .height(530);
                if (!checkCollisionPointRec(mouse, modalRec)) {
                    showNodeInspectorModal = false;
                }
            }
        }

        if (isKeyPressed(KEY_ESCAPE) && showNodeInspectorModal) {
            showNodeInspectorModal = false;
        }

        if (isKeyPressed(KEY_N) && policeMonitor != null) {
            List<ServerNode3D> nodes = policeMonitor.getServerNodes();
            if (!nodes.isEmpty()) {
                if (selectedServerNode == null) {
                    selectedServerNode = nodes.get(0);
                } else {
                    int idx = nodes.indexOf(selectedServerNode);
                    selectedServerNode = nodes.get((idx + 1) % nodes.size());
                }
                showNodeInspectorModal = true;
            }
        }
    }

    private void update(float dt) {
        if (worldMode == WorldMode.EXPANDING_TRANSITION) {
            transitionTimer -= dt;
            if (transitionTimer <= 0) {
                worldMode = WorldMode.INNER_NODE_WORLD;
                camera.position(new Vector3().x(0).y(4.5f).z(12f));
                camera.target(new Vector3().x(0).y(2.0f).z(0));
                camera.fovy(50);
                worldEvents.add(new WorldEvent(
                    "¡Bienvenido al Mundo Interior del Servidor '" + expandedNode.getId() + "'!",
                    worldTime, 0, 255, 180));
            }
            return;
        }

        if (worldMode == WorldMode.EXITING_TRANSITION) {
            transitionTimer -= dt;
            if (transitionTimer <= 0) {
                worldMode = WorldMode.MAIN_WORLD;
                camera.position(new Vector3().x(savedMainCameraPos.x()).y(savedMainCameraPos.y()).z(savedMainCameraPos.z()));
                camera.target(new Vector3().x(savedMainCameraTarget.x()).y(savedMainCameraTarget.y()).z(savedMainCameraTarget.z()));
                camera.fovy(savedMainCameraFov);
                expandedNode = null;
                selectedDatabase = null;
                worldEvents.add(new WorldEvent("Retornado al Mundo Principal de la Ciudad.", worldTime, 100, 255, 100));
            }
            return;
        }

        if (worldMode == WorldMode.INNER_NODE_WORLD) {
            innerWorldTime += dt;
            if (!cameraLocked) {
                updateCamera(camera, CAMERA_FREE);
            }
            if (expandedNode != null) {
                for (DatabaseInfo3D db : expandedNode.getDatabases()) {
                    db.setPulsePhase(db.getPulsePhase() + dt * 2.5f);
                }
            }

            // Proximidad a la Puerta: pulsar 'E' para atravesarla
            float cdx = camera.position().x();
            float cdz = camera.position().z() - (-11.0f);
            if ((cdx * cdx + cdz * cdz) < 6.0f && isKeyPressed(KEY_E)) {
                startExitTransition();
            }
            return;
        }

        if (followMode && selectedAgentIndex != -1 && selectedAgentIndex < entities.size()) {
            HumanEntity e = entities.get(selectedAgentIndex);
            camera.target(new Vector3().x(e.x).y(e.y).z(e.z));
            camera.position(new Vector3().x(e.x + 10).y(e.y + 10).z(e.z + 10));
        } else if (!cameraLocked && !isAnchored) {
            updateCamera(camera, CAMERA_FREE);
        }

        updateEntities(dt);
        updateThoughts(dt);
        updatePolicePatrol(dt);
        
        topKnowledgeUpdateTimer += dt;
        if (topKnowledgeUpdateTimer > 2.0f) {
            updateTopKnowledge();
            topKnowledgeUpdateTimer = 0;
        }

        if (directorMode) {
            updateDirectorMode(dt);
        }

        updateSfx();
        
        if (howlTimer > 0) howlTimer -= dt;
        checkPackSafety(dt);

        globalSaveTimer -= dt;
        if (globalSaveTimer <= 0) {
            saveWorldState();
            globalSaveTimer = 60.0f;
        }

        pdfScanTimer -= dt;
        if (pdfScanTimer <= 0) {
            ingestPdfs();
            pdfScanTimer = 10.0f;
        }
    }

    private void checkPackSafety(float dt) {
        if (weatherMode == 2 && howlTimer <= 0) {
            long total = 0;
            long sheltered = 0;
            HumanEntity jettra = null;
            
            for (HumanEntity e : entities) {
                if (e.name.contains("Jettra")) { jettra = e; continue; }
                if (e.isCar || e.isAnimal) continue;
                total++;
                if ("SHELTERED".equals(e.action)) sheltered++;
            }
            
            if (total > 0 && sheltered == total && jettra != null) {
                // Reward agents for successful survival tactics
                for (HumanEntity re : entities) {
                    if ("SHELTERED".equals(re.action) && !re.name.contains("Jettra")) {
                        re.intelligence += 2;
                        re.currentThought = "He aprendido a sobrevivir (+2 Intel)";
                        re.thoughtTimer = 4.0f;
                        final HumanEntity fRe = re;
                        Thought t = new Thought();
                        t.content = "LEARNED: SURVIVAL (+2 Intel)";
                        t.owner = fRe; t.x = fRe.x; t.y = fRe.y + 3.0f; t.z = fRe.z;
                        t.timer = 4.0f; t.isThought = false;
                        thoughts.add(t);
                    }
                }

                final String msg = "¡AWOOOOOOOOOO! La manada está a salvo de la tormenta.";
                final HumanEntity fJettra = jettra;
                jettra.currentThought = msg;
                jettra.thoughtTimer = 6.0f;
                Thought t = new Thought();
                t.content = msg; t.owner = fJettra;
                t.x = fJettra.x; t.y = fJettra.y + 4.0f; t.z = fJettra.z;
                t.timer = 6.0f; t.isThought = false;
                thoughts.add(t);
                worldEvents.add(new WorldEvent("Aullido de Jettra: Supervivencia Exitosa", worldTime, 255, 255, 0));
                howlTimer = 40.0f; // Relax for 40s
            }
        }
    }

    private void ingestPdfs() {
        if (!configEnabledFiles) return;
        java.io.File dir = new java.io.File("memory/pdfs");
        if (!dir.exists()) dir.mkdirs();
        java.io.File processedDir = new java.io.File("memory/pdfs/processed");
        if (!processedDir.exists()) processedDir.mkdirs();

        java.io.File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files == null || files.length == 0) return;

        for (java.io.File pdf : files) {
            try (PDDocument document = PDDocument.load(pdf)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document).toLowerCase();
                String topic = pdf.getName().replace(".pdf", "");
                
                KnowledgeEntry entry = new KnowledgeEntry();
                entry.topic = "Conocimiento de " + topic;
                entry.source = "Archivo PDF: " + pdf.getName();
                entry.x = (float)(Math.random() * 80 - 40);
                entry.y = 5.0f;
                entry.z = (float)(Math.random() * 80 - 40);
                entry.confidence = 0.95;
                entry.keywords = new String[]{topic, "pdf", "lectura"};
                
                if (allKnowledge == null) allKnowledge = new java.util.ArrayList<>();
                allKnowledge.add(entry);
                
                // Save knowledge globally
                try {
                    java.io.File kFile = new java.io.File("memory/world/knowledge.json");
                    com.fasterxml.jackson.databind.ObjectMapper tempMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    tempMapper.writerWithDefaultPrettyPrinter().writeValue(kFile, allKnowledge);
                } catch (Exception e) {}

                worldEvents.add(new WorldEvent("PDF Asimilado: " + pdf.getName(), worldTime, 255, 215, 0));

                if (text.contains("salud") || text.contains("medicina") || text.contains("cura") || text.contains("health")) {
                    for (HumanEntity e : entities) {
                        e.health = Math.min(100, e.health + 30);
                        e.infectionLevel = Math.max(0, e.infectionLevel - 20);
                    }
                    worldEvents.add(new WorldEvent("Avance Médico aplicado", worldTime, 0, 255, 100));
                }
                if (text.contains("construcción") || text.contains("arquitectura") || text.contains("ingeniería")) {
                    for (MegaProject p : megaProjects) {
                        if (!p.finished) p.progress += 0.2f;
                    }
                    worldEvents.add(new WorldEvent("MegaProyectos acelerados", worldTime, 255, 200, 0));
                }
                if (text.contains("psicología") || text.contains("sociedad") || text.contains("comunidad")) {
                    for (HumanEntity e : entities) {
                        e.mood = Math.min(100, e.mood + 25);
                    }
                    worldEvents.add(new WorldEvent("Bienestar Social incrementado", worldTime, 255, 100, 255));
                }

                pdf.renameTo(new java.io.File(processedDir, pdf.getName()));
            } catch (Exception ex) {
                System.err.println("Error procesando PDF " + pdf.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void updateEntities(float dt) {
        // Telemetría y cinemática en tiempo real sincronizada con JettraStore
        if (policeMonitor != null) {
            // A. Personas (Usuarios conectados caminando entre sus edificios de zona y los nodos)
            for (JettraLiveSession session : policeMonitor.getLiveSessions()) {
                session.advance(dt);
                HumanEntity person = findEntityByName(session.getUsername());
                if (person != null) {
                    UserZoneGroup zone = policeMonitor.getZoneById(session.getZoneId());
                    ServerNode3D node = policeMonitor.getNodeById(session.getTargetNodeId());
                    if (zone != null && node != null) {
                        float bx = zone.getBuildingX(); float bz = zone.getBuildingZ();
                        float nx = node.getX(); float nz = node.getZ();
                        float p = session.getProgress();
                        float newX = bx + (nx - bx) * p;
                        float newZ = bz + (nz - bz) * p;

                        float dx = newX - person.x;
                        float dz = newZ - person.z;
                        if (Math.abs(dx) > 0.001f || Math.abs(dz) > 0.001f) {
                            person.rotation = (float) Math.atan2(dx, dz) * (180.0f / (float) Math.PI);
                        }
                        person.x = newX;
                        person.z = newZ;
                        person.action = (session.getPhase() == JettraLiveSession.SessionPhase.EXECUTING_QUERY) ? "QUERYING" : "WALKING";
                        person.currentThought = session.getCurrentOperation();
                        person.thoughtTimer = 4.0f;
                    }
                }
            }

            // B. Perros (Agentes Caninos JettraPolice patrullando en tiempo real sus nodos asignados)
            for (JettraPoliceAgent agent : policeMonitor.getActivePoliceAgents()) {
                agent.advance(dt);
                HumanEntity dog = findEntityByName(agent.getName());
                if (dog != null) {
                    ServerNode3D node = policeMonitor.getNodeById(agent.getTargetNodeId());
                    if (node != null) {
                        float radius = 3.6f;
                        float ang = agent.getPatrolAngle();
                        float targetX = node.getX() + (float) Math.cos(ang) * radius;
                        float targetZ = node.getZ() + (float) Math.sin(ang) * radius;

                        float forwardAngle = ang + (float)(Math.PI / 2.0);
                        dog.rotation = forwardAngle * (180.0f / (float) Math.PI);
                        dog.x = targetX;
                        dog.z = targetZ;
                        dog.action = "PATROLLING";
                        dog.currentThought = agent.getCurrentMission();
                        dog.thoughtTimer = 4.0f;

                        if (agent.isAlertActive()) {
                            dog.r = 255; dog.g = 50; dog.b = 50;
                        } else if (dog.isJettraMascot) {
                            dog.r = 255; dog.g = 215; dog.b = 0;
                        } else {
                            dog.r = 30; dog.g = 144; dog.b = 255;
                        }
                    }
                }
            }

            // C. Camiones (Tráfico de datos real entre nodos del clúster)
            for (ClusterDataTraffic traffic : policeMonitor.getActiveTraffic()) {
                traffic.advance(dt);
                HumanEntity truck = findEntityByName(traffic.getName());
                if (truck != null) {
                    ServerNode3D src = policeMonitor.getNodeById(traffic.getSourceNodeId());
                    ServerNode3D tgt = policeMonitor.getNodeById(traffic.getTargetNodeId());
                    if (src != null && tgt != null) {
                        float sx = src.getX(); float sz = src.getZ();
                        float tx = tgt.getX(); float tz = tgt.getZ();
                        float p = traffic.getProgress();
                        truck.x = sx + (tx - sx) * p;
                        truck.z = sz + (tz - sz) * p;

                        float dirX = traffic.isReversing() ? (sx - tx) : (tx - sx);
                        float dirZ = traffic.isReversing() ? (sz - tz) : (tz - sz);
                        truck.rotation = (float) Math.atan2(dirX, dirZ) * (180.0f / (float) Math.PI);

                        truck.dataPayload = traffic.getPayloadSummary();
                        truck.currentThought = "🚚 " + traffic.getPayloadSummary();
                        truck.thoughtTimer = 4.0f;
                    }
                }
            }
        }

        boolean isNight = (weatherMode == 1);
        int schoolCount = 0; int hospitalCount = 0;
        int aliveCount = 0; float globalHealth = 0;
        for (Artifact a : artifacts) {
            if (a.type == 1) schoolCount++;
            else if (a.type == 4) hospitalCount++;
        }
        for (HumanEntity e : entities) {
            if (!e.isDead && !e.name.contains("Jettra")) { aliveCount++; globalHealth += e.health; }
        }
        if (aliveCount > 0) globalHealth /= aliveCount;
        
        for (HumanEntity e : entities) {
            if (e == policeSentinelEntity) {
                continue; // El policía centinela JettraStorePolice se actualiza en updatePolicePatrol(dt)
            }
            if (e.isDead && (e.name != null && !e.name.contains("Jettra"))) continue;

            // --- PHYSICAL (PECS) ---
            if (!e.isCar) {
                float baseRate = e.metabolismRate * dt;
                
                // Aging: 1 year per ~15 mins real time (approx 1/900 year per second)
                if (worldTime % 1.0 < dt) {
                    e.age = (int)(20 + (worldTime / 600.0f)); // Simple aging for demo
                }

                // Metabolism
                e.hunger = Math.max(0, e.hunger - baseRate * (isNight ? 0.3f : 0.6f));
                e.thirst = Math.max(0, e.thirst - baseRate * (isNight ? 0.4f : 0.8f));
                e.energy = Math.max(0, e.energy - baseRate * ("IDLE".equals(e.action) ? 0.2f : 1.0f));
                
                // Disease Spread
                if (e.infectionLevel > 0) {
                    e.infectionLevel += baseRate * 2.0f;
                    e.health -= baseRate * 0.5f;
                    if (e.health < 20 && e.thoughtTimer <= 0) {
                        e.currentThought = "Me siento muy enfermo...";
                        e.thoughtTimer = 5.0f;
                    }
                }

                // Personality Impact on Emotional State (Neuroticism)
                float moodDecay = baseRate * (0.1f + e.neuroticism * 0.5f);
                e.mood -= moodDecay;

                // Recovery
                if ("SHELTERED".equals(e.action) || "RESTING".equals(e.action)) {
                    e.energy = Math.min(100, e.energy + baseRate * 5.0f);
                    e.stamina = Math.min(100, e.stamina + baseRate * 3.0f);
                    if (isNight) e.mood = Math.min(100, e.mood + baseRate);
                }
                // Hospital Aura
                for (Artifact a : artifacts) {
                    if (a.type == 4) {
                        float dSq = (a.x - e.x)*(a.x - e.x) + (a.z - e.z)*(a.z - e.z);
                        if (dSq < 100.0f) {
                            e.health = Math.min(100, e.health + baseRate * 10.0f);
                            e.infectionLevel = Math.max(0, e.infectionLevel - baseRate * 10.0f);
                        }
                    }
                }

                // Health checks
                if (e.hunger < 10 || e.thirst < 10 || e.energy < 5) {
                    e.health -= baseRate * 1.5f;
                }
                e.health = Math.max(0, Math.min(100, e.health));
                
                if (e.health <= 0 && !e.name.contains("Jettra")) {
                    e.isDead = true;
                    e.action = "DEAD";
                    worldEvents.add(new WorldEvent(e.name + " ha fallecido por causas naturales", worldTime, 200, 0, 0));
                    continue;
                }
            }

            // --- COGNITIVE (PECS) - Goal Selection ---
            if (!e.isCar && !e.isDead) {
                if (e.hunger < 30) e.currentGoal = "BUSCAR_COMIDA";
                else if (e.thirst < 30) e.currentGoal = "BUSCAR_AGUA";
                else if (e.energy < 30 || isNight) e.currentGoal = "BUSCAR_REFUGIO";
                else if (e.mood < 40) e.currentGoal = "SOCIALIZAR";
                else if (e.age >= 18 && e.spouse.isEmpty() && Math.random() < 0.05) e.currentGoal = "BUSCAR_PAREJA";
                else if (!e.spouse.isEmpty() && e.children.size() < 2 && Math.random() < 0.02) e.currentGoal = "FORMAR_FAMILIA";
                else if (!e.hasHome && e.energy > 50) e.currentGoal = "BUILDING_HOME";
                else if (aliveCount > 10 && schoolCount == 0 && e.energy > 60 && e.intelligence > 0.5f) e.currentGoal = "BUILDING_SCHOOL";
                else if (globalHealth < 60 && hospitalCount == 0 && e.energy > 60) e.currentGoal = "BUILDING_HOSPITAL";
                else if (e.energy > 60 && Math.random() < 0.15) e.currentGoal = "TRABAJAR";
                else if (e.energy > 70 && Math.random() < 0.15) e.currentGoal = "PRACTICAR_DEPORTE";
                else e.currentGoal = "EXPLORAR";
                
                // --- CONVERSATIONAL FLUIDITY (BABBLE) ---
                if (e.thoughtTimer <= 0 && !e.name.contains("Jettra") && Math.random() < 0.01) {
                    if ("TRABAJAR".equals(e.currentGoal)) {
                        String[] work = {"Procesando datos del sistema.", "Construyendo el futuro de Jettra.", "Trabajar dignifica al agente.", "Produciendo recursos numéricos."};
                        e.currentThought = work[(int)(Math.random() * work.length)];
                        e.thoughtTimer = 5.0f;
                    } else if ("PRACTICAR_DEPORTE".equals(e.currentGoal)) {
                        String[] sport = {"¡Un, dos, un, dos!", "Aumentando mi stamina basal.", "Correr despeja la red neuronal.", "Mejorando mi estatus físico."};
                        e.currentThought = sport[(int)(Math.random() * sport.length)];
                        e.thoughtTimer = 5.0f;
                    } else if ("BUSCAR_PAREJA".equals(e.currentGoal)) {
                        e.currentThought = "Espero encontrar a un igual compatible...";
                        e.thoughtTimer = 5.0f;
                    } else if ("FORMAR_FAMILIA".equals(e.currentGoal)) {
                        e.currentThought = "Pensando en nuestra descendencia con " + e.spouse;
                        e.thoughtTimer = 5.0f;
                    } else if (e.health > 80 && e.mood > 70) {
                        String[] happy = {"Me siento genial hoy.", "¡Qué buen día para prosperar!", "Nuestra red es fuerte.", "La energía fluye."};
                        e.currentThought = happy[(int)(Math.random() * happy.length)];
                        e.thoughtTimer = 5.0f;
                    } else if (e.health < 40) {
                        String[] sick = {"El dolor es insoportable...", "Necesito curarme o reseteo.", "Mi energía vital se desvanece.", "Ayuda sistémica..."};
                        e.currentThought = sick[(int)(Math.random() * sick.length)];
                        e.thoughtTimer = 5.0f;
                    } else if (e.mood < 40) {
                        String[] sad = {"Todo parece tan sombrío...", "Extraño a los míos.", "La soledad abruma mis rutinas.", "¿Cuál es el propósito del loop?"};
                        e.currentThought = sad[(int)(Math.random() * sad.length)];
                        e.thoughtTimer = 5.0f;
                    } else if ("EXPLORAR".equals(e.currentGoal)) {
                        String[] explore = {"Hay tanto plano por calcular...", "Buscando nuevos vectores.", "Investigando el horizonte 3D.", "Bip. Bip. Mapeando entorno."};
                        e.currentThought = explore[(int)(Math.random() * explore.length)];
                        e.thoughtTimer = 5.0f;
                    }
                }
            }

            // --- MOVEMENT & ENVIRONMENTAL REACTION ---
            float envSpeed = isNight ? 0.5f : 1.0f;
            if (weatherMode == 2) envSpeed *= 0.7f;

            float dx = e.targetX - e.x;
            float dz = e.targetZ - e.z;
            float dist = (float)Math.sqrt(dx*dx + dz*dz);

            if (dist > 0.1f && !Float.isNaN(dist)) {
                float speed = (e.isCar ? 8.0f : 2.0f) * envSpeed;
                e.x += (dx / dist) * speed * dt;
                e.z += (dz / dist) * speed * dt;
                e.rotation = (float)Math.atan2(dx, dz) * (180.0f / (float)Math.PI);
                
                // Autonomous Driving Activation
                if (dist > 80.0f && !e.isCar && !e.name.contains("Jettra") && e.energy > 20 && Math.random() < 0.05) {
                    e.isCar = true; e.action = "DRIVING";
                    if (Math.random() < 0.5) { e.r=255; e.g=50; e.b=50; } else { e.r=50; e.g=50; e.b=255; }
                } else if (!e.isCar && !e.isMachine && !e.name.contains("Jettra")) {
                    e.action = "WALKING";
                }
                // Road Laying
                if (e.isCar && Math.random() < 0.05) {
                    Artifact road = new Artifact();
                    road.x = e.x; road.y = -0.04f; road.z = e.z; road.type = 5;
                    road.r = 60; road.g = 60; road.b = 60; road.a = 255;
                    artifacts.add(road);
                }
            } else if (!Float.isNaN(dist)) {
                if (e.isCar) { e.isCar = false; e.r = 200; e.g = 200; e.b = 200; }
                if (!e.currentGoal.startsWith("BUILDING_")) {
                    e.isMachine = false;
                    e.action = "IDLE";
                }
                
                // Goal-specific target selection
                if (e.currentGoal.startsWith("BUILDING_")) {
                    e.action = "BUILDING";
                    e.isMachine = true;
                    e.buildTimer += dt;
                    if (e.buildTimer > 5.0f) {
                        Artifact art = new Artifact();
                        art.x = e.x; art.y = 0; art.z = e.z; art.a = 255;
                        if ("BUILDING_HOME".equals(e.currentGoal)) {
                            art.type = 0; art.r = 200; art.g = 200; art.b = 200;
                            e.hasHome = true; e.homeX = e.x; e.homeY = 0; e.homeZ = e.z;
                            worldEvents.add(new WorldEvent(e.name + " construyó un hogar", worldTime, 0, 255, 100));
                        } else if ("BUILDING_SCHOOL".equals(e.currentGoal)) {
                            art.type = 1; art.r = 100; art.g = 100; art.b = 255;
                            worldEvents.add(new WorldEvent(e.name + " inauguró una Escuela", worldTime, 0, 100, 255));
                        } else if ("BUILDING_HOSPITAL".equals(e.currentGoal)) {
                            art.type = 4; art.r = 255; art.g = 255; art.b = 255;
                            worldEvents.add(new WorldEvent(e.name + " construyó un Hospital", worldTime, 0, 255, 100));
                        }
                        artifacts.add(art);
                        e.isMachine = false;
                        e.buildTimer = 0; e.energy -= 20; e.currentGoal = "EXPLORAR";
                    }
                } else if (Math.random() < 0.02) {
                    if ("BUSCAR_REFUGIO".equals(e.currentGoal) && e.hasHome) {
                        e.targetX = e.homeX; e.targetZ = e.homeZ;
                    } else if ("BUSCAR_REFUGIO".equals(e.currentGoal) || "BUSCAR_COMIDA".equals(e.currentGoal) || "SOCIALIZAR".equals(e.currentGoal)) {
                        // Find nearest house/school/hospital
                        Artifact best = null; float mDS = Float.MAX_VALUE;
                        for(Artifact a : artifacts) {
                            if (a.type == 5) continue;
                            float d = (a.x-e.x)*(a.x-e.x)+(a.z-e.z)*(a.z-e.z);
                            if (d < mDS) { mDS = d; best = a; }
                        }
                        if (best != null) { e.targetX = best.x; e.targetZ = best.z; }
                    } else {
                        e.targetX = (float)(Math.random()*160-80);
                        e.targetZ = (float)(Math.random()*160-80);
                    }
                }
                if (dist < 1.0f && "BUSCAR_REFUGIO".equals(e.currentGoal)) {
                    e.action = "SHELTERED";
                }
            }

            // --- SOCIAL (PECS) & LEADERSHIP ---
            if (!e.isCar && !e.isDead && worldTime % 4.0 < dt) {
                for (HumanEntity other : entities) {
                    if (other == e || other.isDead) continue;
                    float d2 = (other.x-e.x)*(other.x-e.x) + (other.z-e.z)*(other.z-e.z);
                    if (d2 < 25.0f) { // Interaction range
                        // Disease spread
                        if (e.infectionLevel > 30 && Math.random() < 0.1) other.infectionLevel = 1;

                        // Social interaction
                        if ("SOCIALIZAR".equals(e.currentGoal)) {
                            e.mood = Math.min(100, e.mood + 5.0f * (1.0f + e.extraversion));
                            if (e.thoughtTimer <= 0 && Math.random() < 0.2) {
                                e.currentThought = "Hablando con " + other.name;
                                e.thoughtTimer = 3.0f;
                            }
                        }

                        // Romance & Family
                        if ("BUSCAR_PAREJA".equals(e.currentGoal) && "BUSCAR_PAREJA".equals(other.currentGoal) && (e.spouse == null || e.spouse.isEmpty()) && (other.spouse == null || other.spouse.isEmpty())) {
                            if (e.age >= 18 && other.age >= 18 && Math.random() < 0.2) {
                                e.spouse = other.name;
                                other.spouse = e.name;
                                e.currentGoal = "SOCIALIZAR"; other.currentGoal = "SOCIALIZAR";
                                worldEvents.add(new WorldEvent(e.name + " y " + other.name + " son ahora pareja", worldTime, 255, 105, 180));
                            }
                        }
                        if ("FORMAR_FAMILIA".equals(e.currentGoal) && e.spouse != null && !e.spouse.isEmpty() && e.spouse.equals(other.name)) {
                            if (Math.random() < 0.05) {
                                String childName = "Hijo-" + (int)(Math.random()*1000);
                                e.children.add(childName); other.children.add(childName);
                                generateEntity(childName, e.x, e.y, e.z, false, false, false);
                                worldEvents.add(new WorldEvent("Nueva vida: " + childName + " nació de " + e.name, worldTime, 100, 255, 100));
                                e.currentGoal = "SOCIALIZAR";
                            }
                        }

                        // Leadership influence (Jettra Wolf)
                        if (e.name.contains("Jettra") && Math.random() < 0.1) {
                            other.intelligence = Math.min(1.0f, other.intelligence + 0.01f);
                            other.mood = Math.min(100, other.mood + 10.0f);
                        }
                    }
                }
            }

            // --- PERSISTENCE & LEARNING ---
            if (!e.isCar && !e.isDead && Math.random() < 0.0005) {
                saveKnowledge(e, "Observación Directa", "Evolución de la Especie");
            }
        }
    }

    private void updateThoughts(float dt) {
        entities.removeIf(e -> {
            if (e.thoughtTimer > 0) {
                e.thoughtTimer -= dt;
            }
            return false;
        });

        thoughts.removeIf(t -> {
            t.timer -= dt;
            return t.timer <= 0;
        });
    }

    private void updateDirectorMode(float dt) {
        directorTimer += dt;
        if (directorTimer > 8.0f || selectedAgentIndex == -1) {
            directorTimer = 0;
            for (int i = 0; i < entities.size(); i++) {
                HumanEntity e = entities.get(i);
                if ("Building".equals(e.action) || "Socializing".equals(e.action)) {
                    selectedAgentIndex = i;
                    followMode = true;
                    break;
                }
            }
        }
    }

    private void draw() {
        beginDrawing();
        
        Color skyColor = switch(weatherMode) {
            case 1 -> new Color().r((byte)2).g((byte)2).b((byte)5).a((byte)255); // Night
            case 2 -> new Color().r((byte)30).g((byte)35).b((byte)45).a((byte)255); // Storm
            default -> new Color().r((byte)5).g((byte)5).b((byte)12).a((byte)255); // Default
        };
        clearBackground(skyColor);

        if (worldMode == WorldMode.MAIN_WORLD || worldMode == WorldMode.EXPANDING_TRANSITION) {
            beginMode3D(camera);
            drawCartesianPlane();

            drawArtifacts();
            drawEntities();
            drawThoughts();
            drawKnowledgeBase();
            drawServerClusterNodes();

            if (weatherMode == 2) { // Draw Rain
                for(int j=0; j<100; j++) {
                    float rx = (float)(Math.random()*80-40);
                    float rz = (float)(Math.random()*80-40);
                    float ry = (float)(Math.random()*20);
                    drawLine3D(new Vector3().x(rx).y(ry).z(rz), new Vector3().x(rx).y(ry-0.5f).z(rz), SKYBLUE);
                }
            }

            if (worldMode == WorldMode.EXPANDING_TRANSITION) {
                drawNodeExpansionEffect3D();
            }

            endMode3D();

            drawUI();
            drawServerLabelsAndHUD();

            if (showHelp) drawHelpOverlay();
            if (showConfigModal) drawConfigModal();
            if (showConnectionModal) drawConnectionManagerModal();
            if (showChat) drawChatWindow();
            if (showNodeInspectorModal) drawNodeInspectorModal();
            
            drawTopKnowledgePanel();
            drawJettraStatusTooltip();

            if (worldMode == WorldMode.EXPANDING_TRANSITION) {
                drawExpandingTransitionOverlay();
            }
        } else {
            // MUNDO INTERIOR DEL NODO (Y TRANSICIÓN DE RETORNO)
            clearBackground(new Color().r((byte)4).g((byte)6).b((byte)16).a((byte)255));

            beginMode3D(camera);
            drawInnerNodeWorld3D();

            if (worldMode == WorldMode.EXITING_TRANSITION) {
                drawExitingPortalEffect3D();
            }

            endMode3D();

            drawInnerNodeWorldHUD();

            if (selectedDatabase != null) {
                drawDatabaseDetailModal();
            }

            if (worldMode == WorldMode.EXITING_TRANSITION) {
                drawExitingTransitionOverlay();
            }
        }

        endDrawing();
    }

    private void drawArtifacts() {
        for (Artifact a : artifacts) {
            Vector3 pos = new Vector3().x(a.x).y(a.y).z(a.z);
            Color color = new Color().r((byte)a.r).g((byte)a.g).b((byte)a.b).a((byte)a.a);
            
            switch (a.type) {
                case 0 -> { // House
                    drawCube(pos, 2, 2, 2, color);
                    drawCubeWires(pos, 2, 2, 2, BLACK);
                    drawCylinder(new Vector3().x(pos.x()).y(pos.y() + 1).z(pos.z()), 0, 1.5f, 2, 4, MAROON);
                }
                case 1 -> { // School
                    drawCube(pos, 4, 3, 4, color);
                    drawCubeWires(pos, 4, 3, 4, BLACK);
                    drawCube(new Vector3().x(pos.x()).y(pos.y() + 2).z(pos.z()), 1, 2, 1, DARKGRAY);
                }
                case 2 -> { // City Center
                    drawCube(pos, 5, 2, 5, color);
                    drawSphere(new Vector3().x(pos.x()).y(pos.y() + 2).z(pos.z()), 1.5f, GOLD);
                }
                case 3 -> { // Radio
                    drawCylinder(pos, 0.5f, 0.5f, 5, 8, DARKGRAY);
                    if (( (int)(worldTime * 2) % 2) == 0) {
                        drawSphere(new Vector3().x(pos.x()).y(pos.y() + 5).z(pos.z()), 0.3f, RED);
                    }
                }
                case 4 -> { // Hospital
                    drawCube(pos, 4, 3, 4, WHITE);
                    drawCubeWires(pos, 4, 3, 4, BLACK);
                    drawCube(new Vector3().x(pos.x()).y(pos.y() + 1.6f).z(pos.z()), 1f, 2f, 0.1f, RED);
                    drawCube(new Vector3().x(pos.x()).y(pos.y() + 1.6f).z(pos.z()), 2f, 1f, 0.1f, RED);
                }
                case 5 -> { // Road Plate
                    drawCube(new Vector3().x(pos.x()).y(-0.04f).z(pos.z()), 2f, 0.02f, 2f, color);
                }
            }
        }
        
        for (MegaProject p : megaProjects) {
            if (p.finished) continue;
            Vector3 pos = new Vector3().x(p.x).y(p.y).z(p.z);
            drawCubeWires(pos, 2.1f, 2.1f, 2.1f, YELLOW);
            drawCube(pos, 2.0f, 2.0f * p.progress, 2.0f, fade(SKYBLUE, 0.5f));
        }
    }

    private void drawEntities() {
        int i = 0;
        for (HumanEntity e : entities) {
            if (e.isDead && !e.name.contains("Jettra")) {
                i++;
                continue;
            }
            if (Float.isNaN(e.x) || Float.isNaN(e.y) || Float.isNaN(e.z)) {
                e.x = 0; e.y = 0; e.z = 0;
            }
            Vector3 pos = new Vector3().x(e.x).y(e.y).z(e.z);
            Color color = new Color().r((byte)e.r).g((byte)e.g).b((byte)e.b).a((byte)255);

            if (e.isWolf) {
                drawWolf(pos, e.rotation, color);
            } else if (e.isMachine) {
                // Tractor body
                drawCube(new Vector3().x(pos.x()).y(pos.y()+0.5f).z(pos.z()), 2.0f, 1.0f, 1.5f, YELLOW);
                drawCubeWires(new Vector3().x(pos.x()).y(pos.y()+0.5f).z(pos.z()), 2.0f, 1.0f, 1.5f, BLACK);
                // Tractor cabin
                drawCube(new Vector3().x(pos.x()-0.5f).y(pos.y()+1.5f).z(pos.z()), 1.0f, 1.0f, 1.0f, fade(BLACK, 0.8f));
                // Wheels
                drawCylinderEx(new Vector3().x(pos.x()-1f).y(pos.y()+0.5f).z(pos.z()-1.0f), new Vector3().x(pos.x()-1f).y(pos.y()+0.5f).z(pos.z()+1.0f), 0.6f, 0.6f, 12, DARKGRAY);
                drawCylinderEx(new Vector3().x(pos.x()+0.8f).y(pos.y()+0.3f).z(pos.z()-0.9f), new Vector3().x(pos.x()+0.8f).y(pos.y()+0.3f).z(pos.z()+0.9f), 0.4f, 0.4f, 12, DARKGRAY);
                // Crane Arm
                drawCylinderEx(new Vector3().x(pos.x()+1f).y(pos.y()+0.5f).z(pos.z()), new Vector3().x(pos.x()+3.0f).y(pos.y()+2.0f).z(pos.z()), 0.15f, 0.15f, 8, BLACK);
            } else if (e.isCar) {
                // Car Chassis
                drawCube(new Vector3().x(pos.x()).y(pos.y()+0.4f).z(pos.z()), 2.5f, 0.6f, 1.2f, color);
                drawCubeWires(new Vector3().x(pos.x()).y(pos.y()+0.4f).z(pos.z()), 2.5f, 0.6f, 1.2f, BLACK);
                // Car Cabin
                drawCube(new Vector3().x(pos.x()-0.2f).y(pos.y()+1.0f).z(pos.z()), 1.2f, 0.6f, 1.1f, fade(RAYWHITE, 0.9f));
                // Agent Driver Head
                drawSphere(new Vector3().x(pos.x()-0.2f).y(pos.y()+1.0f).z(pos.z()), 0.35f, color);
                // Wheels
                drawCylinderEx(new Vector3().x(pos.x()-0.8f).y(pos.y()+0.2f).z(pos.z()-0.7f), new Vector3().x(pos.x()-0.8f).y(pos.y()+0.2f).z(pos.z()+0.7f), 0.3f, 0.3f, 12, BLACK);
                drawCylinderEx(new Vector3().x(pos.x()+0.8f).y(pos.y()+0.2f).z(pos.z()-0.7f), new Vector3().x(pos.x()+0.8f).y(pos.y()+0.2f).z(pos.z()+0.7f), 0.3f, 0.3f, 12, BLACK);
            } else {
                // Human Shape
                float walkBounce = ("WALKING".equals(e.action)) ? (float)Math.sin(worldTime * 20.0f) * 0.1f : 0.0f;
                float legSwing = ("WALKING".equals(e.action)) ? (float)Math.sin(worldTime * 15.0f) * 0.3f : 0.0f;
                // Torso
                drawCube(new Vector3().x(pos.x()).y(pos.y() + 1.2f + walkBounce).z(pos.z()), 0.6f, 0.8f, 0.4f, color);
                drawCubeWires(new Vector3().x(pos.x()).y(pos.y() + 1.2f + walkBounce).z(pos.z()), 0.6f, 0.8f, 0.4f, BLACK);
                // Head
                drawSphere(new Vector3().x(pos.x()).y(pos.y() + 1.8f + walkBounce).z(pos.z()), 0.3f, color);
                // Legs
                drawCube(new Vector3().x(pos.x()-0.15f).y(pos.y() + 0.4f).z(pos.z() + legSwing), 0.2f, 0.8f, 0.2f, DARKGRAY);
                drawCube(new Vector3().x(pos.x()+0.15f).y(pos.y() + 0.4f).z(pos.z() - legSwing), 0.2f, 0.8f, 0.2f, DARKGRAY);
                // Arms
                drawCube(new Vector3().x(pos.x()-0.4f).y(pos.y() + 1.2f + walkBounce).z(pos.z() - legSwing), 0.15f, 0.7f, 0.15f, color);
                drawCube(new Vector3().x(pos.x()+0.4f).y(pos.y() + 1.2f + walkBounce).z(pos.z() + legSwing), 0.15f, 0.7f, 0.15f, color);
            }

            if (selectedAgentIndex == i) {
                drawCircle3D(new Vector3().x(pos.x()).y(pos.y() + 0.01f).z(pos.z()), 1.5f, new Vector3().x(1).y(0).z(0), 90, LIME);
            }

            // Name Tag
            Vector2 screenPos = getWorldToScreen(new Vector3().x(pos.x()).y(pos.y() + 2.5f).z(pos.z()), camera);
            drawLegibleText(e.name, (int)screenPos.x() - measureLegibleText(e.name, 12)/2, (int)screenPos.y(), 12, RAYWHITE);
            i++;
        }
    }

    private void drawThoughts() {
        // Render thoughts from the global list
        for (Thought t : thoughts) {
            drawThoughtBubble(t.content, t.owner, t.x, t.y, t.z, t.timer, t.isThought);
        }
        
        // Render current thoughts from entities if not already in list
        for (HumanEntity e : entities) {
            if (e.thoughtTimer > 0 && e.currentThought != null && !e.currentThought.isEmpty()) {
                // Check if this entity already has a thought in the global list to avoid overlaps
                boolean alreadyShown = false;
                for (Thought t : thoughts) {
                    if (t.owner == e) { alreadyShown = true; break; }
                }
                if (!alreadyShown) {
                    drawThoughtBubble(e.currentThought, e, e.x, e.y + (e.isWolf ? 1.5f : 3.0f), e.z, e.thoughtTimer, true);
                }
            }
        }
    }

    private void drawThoughtBubble(String content, HumanEntity owner, float x, float y, float z, float timer, boolean isThought) {
        Vector3 worldPos = (owner != null) ? new Vector3().x(owner.x).y(owner.y + (owner.isWolf ? 1.5f : 3.0f)).z(owner.z) 
                                           : new Vector3().x(x).y(y).z(z);
        Vector2 screenPos = getWorldToScreen(worldPos, camera);
        
        if (screenPos.x() > 0 && screenPos.x() < getScreenWidth() && screenPos.y() > 0 && screenPos.y() < getScreenHeight()) {
            boolean isJettra = owner != null && owner.name.contains("Jettra");
            String label = isThought ? "[Pensando...]" : "[Hablando]";
            int fontSize = isJettra ? 18 : 16;
            int textW = measureText(content, fontSize);
            
            float alpha = Math.min(1.0f, timer); // Fade out
            Color bubbleColor = isJettra ? fade(GOLD, 0.8f * alpha) : (isThought ? fade(PURPLE, 0.7f * alpha) : fade(DARKBLUE, 0.8f * alpha));
            Color textColor = isJettra ? fade(BLACK, alpha) : fade(RAYWHITE, alpha);
            Color labelColor = isJettra ? fade(MAROON, alpha) : fade(GOLD, alpha);
            Color borderColor = isJettra ? fade(WHITE, 0.9f * alpha) : fade(RAYWHITE, 0.3f * alpha);

            // Bubble popup background
            drawRectangleRounded(new Rectangle().x(screenPos.x() - textW/2.0f - 10).y(screenPos.y() - 60).width(textW + 20).height(45), 0.4f, 8, bubbleColor);
            drawRectangleRoundedLines(new Rectangle().x(screenPos.x() - textW/2.0f - 10).y(screenPos.y() - 60).width(textW + 20).height(45), 0.4f, 8, borderColor);
            
            // Triangle pointer
            drawTriangle(new Vector2().x(screenPos.x()).y(screenPos.y() - 15),
                         new Vector2().x(screenPos.x() - 10).y(screenPos.y() - 25),
                         new Vector2().x(screenPos.x() + 10).y(screenPos.y() - 25), bubbleColor);

            drawLegibleText(label, (int)(screenPos.x() - textW/2.0f), (int)screenPos.y() - 55, 12, labelColor);
            drawLegibleText(content, (int)(screenPos.x() - textW/2.0f), (int)screenPos.y() - 40, fontSize, textColor);
            if (owner != null && owner.currentGoal != null) {
                drawLegibleText("Meta: " + owner.currentGoal, (int)(screenPos.x() - textW/2.0f), (int)screenPos.y() - 28, 10, GOLD);
            }
        }
    }

    private void drawCartesianPlane() {
        // Ground Plane
        Color floorColor = (weatherMode == 2) ? DARKGRAY : new Color().r((byte)25).g((byte)25).b((byte)45).a((byte)255);
        drawPlane(new Vector3().x(0).y(-0.05f).z(0), new Vector2().x(200).y(200), floorColor);
        
        // Extend Grid
        drawGrid(100, 1.0f);
        
        // Standard Cartesian Axes (Red X, Green Y, Blue Z)
        drawLine3D(new Vector3().x(-100).y(0.01f).z(0), new Vector3().x(100).y(0.01f).z(0), RED);   // X Axis
        drawLine3D(new Vector3().x(0).y(-100).z(0), new Vector3().x(0).y(100).z(0), GREEN);    // Y Axis
        drawLine3D(new Vector3().x(0).y(0.01f).z(-100), new Vector3().x(0).y(0.01f).z(100), BLUE); // Z Axis
        
        // Origin Marker
        drawSphere(new Vector3().x(0).y(0).z(0), 0.25f, GOLD);
    }

    private void drawLegibleText(String text, int x, int y, int fontSize, Color color) {
        if (mainFont != null) {
            drawTextEx(mainFont, text, new Vector2().x(x).y(y), (float)fontSize, 1.0f, color);
        } else {
            drawText(text, x, y, fontSize, color);
        }
    }

    private int measureLegibleText(String text, int fontSize) {
        if (mainFont != null) {
            Vector2 size = measureTextEx(mainFont, text, (float)fontSize, 1.0f);
            return (int)size.x();
        } else {
            return measureText(text, fontSize);
        }
    }

    private void drawUI() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();

        // Right Sidebar
        drawRectangle(sw - 200, 0, 200, sh, fade(DARKGRAY, 0.9f));
        drawLine(sw - 200, 0, sw - 200, sh, RAYWHITE);

        drawText("JETTRA CORE", sw - 190, 20, 20, GOLD);
        drawText("JAVA 25 EDITION", sw - 190, 45, 10, SKYBLUE);

        // Buttons
        if (guiButton(sw - 190, 75, 180, 28, "🔌 CONEXIONES", GOLD)) {
            showConnectionModal = !showConnectionModal;
            if (showConnectionModal) {
                loadSelectedProfileIntoForm();
            }
        }

        if (guiButton(sw - 190, 110, 180, 28, "RESET JETTRASTORE", RED)) {
            resetWorldWithJettraStore();
        }

        if (guiButton(sw - 190, 145, 180, 28, "SAVE STATE", LIME)) {
            saveWorldState();
        }

        if (guiButton(sw - 190, 180, 180, 28, "CONFIGURACIÓN", BLUE)) {
            showConfigModal = !showConfigModal;
            if (showConfigModal) {
                initCamera();
                followMode = false;
                cameraLocked = true;
            } else {
                cameraLocked = false;
            }
        }

        if (guiButton(sw - 190, 200, 180, 30, directorMode ? "DIRECTOR: ON" : "DIRECTOR: OFF", directorMode ? ORANGE : GRAY)) {
            directorMode = !directorMode;
            if (directorMode) followMode = true;
        }

        // --- NEW BUTTONS ---
        if (guiButton(sw - 190, 240, 180, 25, isAnchored ? "PLANE: LOCKED" : "PLANE: UNLOCKED", isAnchored ? RED : GRAY)) {
            isAnchored = !isAnchored;
        }

        if (guiButton(sw - 190, 270, 180, 25, "CENTER MAP", DARKGRAY)) {
            initCamera();
            followMode = false;
            cameraLocked = false;
        }

        if (guiButton(sw - 190, 305, 180, 30, showChat ? "CERRAR CHAT" : "ABRIR CHAT", PURPLE)) {
            showChat = !showChat;
        }

        if (guiButton(sw - 190, 345, 85, 25, "ZOOM +", GRAY)) {
            camera.fovy(Math.max(5, camera.fovy() - 5));
        }

        if (guiButton(sw - 100, 345, 85, 25, "ZOOM -", GRAY)) {
            camera.fovy(Math.min(120, camera.fovy() + 5));
        }

        if (guiButton(sw - 190, 330, 180, 25, voiceEnabled ? "🔊 VOZ: ACTIVA" : "🔇 VOZ: MUTE", voiceEnabled ? LIME : RED)) {
            voiceEnabled = !voiceEnabled;
            JettraVoiceNarrator.getInstance().setEnabled(voiceEnabled);
            triggerWorldEvent("Voz " + (voiceEnabled ? "activada" : "desactivada"), 200, 200, 0);
        }

        if (guiButton(sw - 190, 365, 85, 30, sfxEnabled ? "SFX: ON" : "SFX: OFF", sfxEnabled ? LIME : RED)) {
            sfxEnabled = !sfxEnabled;
            worldEvents.add(new WorldEvent("Efectos " + (sfxEnabled ? "activados" : "desactivados"), worldTime, 100, 255, 100));
        }

        if (guiButton(sw - 100, 365, 85, 30, "SALIR", DARKGRAY)) {
            closeWindow();
            System.exit(0);
        }

        if (guiButton(sw - 190, 405, 180, 28, "CLÚSTER JETTRA", SKYBLUE)) {
            if (selectedServerNode == null && policeMonitor != null && !policeMonitor.getServerNodes().isEmpty()) {
                selectedServerNode = policeMonitor.getServerNodes().get(0);
            }
            showNodeInspectorModal = !showNodeInspectorModal;
        }

        // Selected Info
        int infoY = 445;
        if (selectedAgentIndex != -1 && selectedAgentIndex < entities.size()) {
            HumanEntity e = entities.get(selectedAgentIndex);
            drawLegibleText("AGENT: " + e.name, sw - 190, infoY, 15, RAYWHITE);
            drawLegibleText("GOAL: " + e.currentGoal, sw - 190, infoY + 20, 11, GOLD);
            drawLegibleText("MOOD: " + (int)e.mood + "% (" + (e.mood > 50 ? "Feliz" : "Estresado") + ")", sw - 190, infoY + 35, 11, fade(PURPLE, 0.8f));
            drawLegibleText("HEALTH: " + (int)e.health + "%", sw - 190, infoY + 50, 11, fade(RED, 0.8f));
            drawLegibleText("HUNGER: " + (int)e.hunger + "%", sw - 190, infoY + 65, 11, fade(ORANGE, 0.8f));
            drawLegibleText("THIRST: " + (int)e.thirst + "%", sw - 190, infoY + 80, 11, fade(SKYBLUE, 0.8f));
            
            // Personality (Big Five)
            int py = infoY + 105;
            drawLegibleText("PERSONALIDAD:", sw - 190, py, 12, SKYBLUE);
            drawLegibleText("- Openness: " + String.format("%.2f", e.openness), sw - 180, py + 15, 10, RAYWHITE);
            drawLegibleText("- Conscien: " + String.format("%.2f", e.conscientiousness), sw - 180, py + 27, 10, RAYWHITE);
            drawLegibleText("- Extraver: " + String.format("%.2f", e.extraversion), sw - 180, py + 39, 10, RAYWHITE);
            drawLegibleText("- Agreeabl: " + String.format("%.2f", e.agreeableness), sw - 180, py + 51, 10, RAYWHITE);
            drawLegibleText("- Neurotic: " + String.format("%.2f", e.neuroticism), sw - 180, py + 63, 10, RAYWHITE);

            if (!e.spouse.isEmpty()) {
                drawLegibleText("SPOUSE: " + e.spouse, sw - 190, py + 80, 11, PINK);
            }
            if (e.infectionLevel > 0) {
                drawLegibleText("INFECTION: " + (int)e.infectionLevel + "%", sw - 190, py + 95, 11, LIME);
            }
        }

        // Live Feed
        drawLiveFeed(sw, sh);
        // History Log
        drawEventLog();
    }

    private boolean guiButton(int x, int y, int w, int h, String text, Color baseColor) {
        Vector2 mouse = getMousePosition();
        Rectangle rec = new Rectangle().x(x).y(y).width(w).height(h);
        boolean hovered = checkCollisionPointRec(mouse, rec);
        boolean clicked = hovered && isMouseButtonPressed(MOUSE_BUTTON_LEFT);

        drawRectangleRounded(rec, 0.2f, 8, hovered ? fade(baseColor, 0.8f) : fade(baseColor, 0.6f));
        drawRectangleRoundedLines(rec, 0.2f, 8, hovered ? WHITE : fade(WHITE, 0.4f));

        int fontSize = 14;
        int tw = measureLegibleText(text, fontSize);
        drawLegibleText(text, x + (w - tw)/2, y + (h - fontSize)/2, fontSize, RAYWHITE);

        return clicked;
    }

    private void drawLiveFeed(int sw, int sh) {
        int fy = sh - 160;
        drawRectangle(sw - 350, fy, 140, 100, fade(BLACK, 0.6f));
        drawLegibleText("LIVE FEED", sw - 340, fy + 5, 13, GOLD);
        
        for (int j = 0; j < Math.min(5, worldEvents.size()); j++) {
            WorldEvent ev = worldEvents.get(worldEvents.size() - 1 - j);
            drawLegibleText("> " + ev.message, sw - 340, fy + 25 + (j * 14), 11, new Color().r((byte)ev.r).g((byte)ev.g).b((byte)ev.b).a((byte)ev.a));
        }
    }

    private void drawEventLog() {
        int w = 600; int h = 300;
        int x = 15;
        int y = getScreenHeight() - h - 15;
        drawRectangle(x, y, w, h, fade(BLACK, 0.85f));
        drawRectangleLines(x, y, w, h, GOLD);
        drawLegibleText("LOG DE EVENTOS DEL SISTEMA", x + 10, y + 10, 14, GOLD);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        for (int j = 0; j < Math.min(15, worldEvents.size()); j++) {
            WorldEvent ev = worldEvents.get(worldEvents.size() - 1 - j);
            String timeStr = sdf.format(new java.util.Date((long)(ev.timestamp * 1000)));
            drawLegibleText("[" + timeStr + "] " + ev.message, x + 15, y + 30 + (j * 16), 11, new Color().r((byte)ev.r).g((byte)ev.g).b((byte)ev.b).a((byte)ev.a));
        }
    }

    private void drawHelpOverlay() {
        drawRectangle(15, 15, 285, 245, fade(BLACK, 0.75f));
        drawRectangleLines(15, 15, 285, 245, GOLD);
        drawLegibleText("CONTROLES DE CÁMARA", 20, 20, 14, GOLD);
        drawLegibleText("- Teclas: W,S,A,D,Q,E", 25, 45, 12, RAYWHITE);
        drawLegibleText("- Mouse: Click Derecho Girar", 25, 60, 12, RAYWHITE);
        drawLegibleText("- Rueda Mouse: Zoom +/-", 25, 75, 12, RAYWHITE);
        drawLegibleText("- C: Cambiar Cámara / Reset", 25, 90, 12, RAYWHITE);
        drawLegibleText("- F: Modo Seguir Agente", 25, 105, 12, RAYWHITE);
        drawLegibleText("- L: Lock/Unlock Plano", 25, 120, 12, RAYWHITE);
        drawLegibleText("- Esc: Cerrar App", 25, 135, 12, RAYWHITE);
        drawLegibleText("- Tab: Toggle esta Ayuda", 25, 150, 12, RAYWHITE);
        drawLegibleText("- Enter: Abrir/Cerrar Chat", 25, 165, 12, LIME);
        drawLegibleText("- Clic Derecho en Nodo: Ver Recursos", 25, 180, 11, GOLD);
        drawLegibleText("- Tecla N: Ciclar Servidores y Abrir Inspector", 25, 195, 11, GOLD);
        drawLegibleText("- Tecla K: Gestión de Conexiones JettraStore", 25, 210, 11, LIME);
        drawLegibleText("- Tecla R: Sincronizar Mundo en Tiempo Real", 25, 225, 11, SKYBLUE);
    }

    private void drawConfigModal() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();
        drawRectangle(0, 0, sw, sh, fade(BLACK, 0.4f));
        
        int mw = 400; int mh = 350;
        int mx = (sw - mw) / 2;
        int my = (sh - mh) / 2;
        
        drawRectangle(mx, my, mw, mh, DARKGRAY);
        drawRectangleLines(mx, my, mw, mh, RAYWHITE);
        drawText("CONFIGURACIÓN DE AUTONOMÍA", mx + 20, my + 20, 20, GOLD);

        configEnabledApps = guiToggle(mx + 20, my + 60, "Auto-abrir Aplicaciones (VLC/Chrome)", configEnabledApps);
        configEnabledInternet = guiToggle(mx + 20, my + 90, "Búsqueda de Aprendizaje en Internet", configEnabledInternet);
        configEnabledFiles = guiToggle(mx + 20, my + 120, "Estudio de Archivos Locales (PDF/MD)", configEnabledFiles);
        configEnabledLife = guiToggle(mx + 20, my + 150, "Población Silvestre (Animales/Autos)", configEnabledLife);
        configEnabledSocial = guiToggle(mx + 20, my + 180, "Simulación Social Avanzada", configEnabledSocial);

        if (guiButton(mx + mw - 120, my + mh - 50, 100, 30, "CERRAR", GRAY)) {
            showConfigModal = false;
        }
    }

    private boolean guiToggle(int x, int y, String text, boolean value) {
        if (guiButton(x, y, 20, 20, value ? "X" : " ", value ? LIME : GRAY)) {
            value = !value;
        }
        drawText(text, x + 30, y + 5, 12, RAYWHITE);
        return value;
    }
    
    private void initPopulation() {
        resetWorldWithJettraStore();
    }

    private void resetWorldWithJettraStore() {
        entities.clear();
        artifacts.clear();
        megaProjects.clear();

        if (policeMonitor == null) {
            policeMonitor = new JettraStorePoliceMonitor();
        }

        // 1. Edificios (Lugares donde se conectan los usuarios, agrupados por zonas cercanas)
        for (UserZoneGroup zone : policeMonitor.getUserZones()) {
            artifacts.add(new Artifact(
                zone.getBuildingName(),
                zone.getPrimaryDatabase(),
                zone.getBuildingDescription(),
                zone.getBuildingType(),
                zone.getBuildingX(), zone.getBuildingY(), zone.getBuildingZ(),
                zone.getColorR(), zone.getColorG(), zone.getColorB(),
                zone.getConnectedUserCount() + " usuarios activos | " + zone.getSubnetPrefix() + ".x"
            ));
        }

        // 2. Oficial Supervisor JettraStorePolice
        if (policeSentinelEntity == null) {
            policeSentinelEntity = new HumanEntity();
            policeSentinelEntity.name = "JettraStorePolice";
            policeSentinelEntity.action = "PATROLLING";
            policeSentinelEntity.job = "Centinela Supervisor de Servidores JettraStore";
            policeSentinelEntity.r = 30; policeSentinelEntity.g = 144; policeSentinelEntity.b = 255;
            policeSentinelEntity.isPoliceOfficer = true;
        }
        policeSentinelEntity.x = -14.0f; policeSentinelEntity.y = 0.0f; policeSentinelEntity.z = -8.5f;
        policeSentinelEntity.targetX = -14.0f; policeSentinelEntity.targetZ = -8.5f; policeSentinelEntity.targetY = 0.0f;
        policeSentinelEntity.currentThought = "🛡️ JettraStorePolice: Supervisando estabilidad Heap y quórum Raft...";
        policeSentinelEntity.thoughtTimer = 6.0f;
        entities.add(policeSentinelEntity);

        // 3. Perros: Agentes JettraPolice que se activan en JettraStore
        for (JettraPoliceAgent agent : policeMonitor.getActivePoliceAgents()) {
            HumanEntity k9 = new HumanEntity();
            k9.name = agent.getName();
            k9.isWolf = true;
            k9.isPoliceK9 = true;
            if (agent.getName().contains("Alpha")) k9.isJettraMascot = true;
            k9.action = "PATROLLING";
            ServerNode3D node = policeMonitor.getNodeById(agent.getTargetNodeId());
            float nx = (node != null) ? node.getX() : 0f;
            float nz = (node != null) ? node.getZ() : -8f;
            k9.x = nx + 3.0f; k9.y = 0; k9.z = nz;
            k9.targetX = nx; k9.targetZ = nz;
            if (agent.isAlertActive()) {
                k9.r = 255; k9.g = 50; k9.b = 50;
            } else if (k9.isJettraMascot) {
                k9.r = 255; k9.g = 215; k9.b = 0;
            } else {
                k9.r = 30; k9.g = 144; k9.b = 255;
            }
            k9.currentThought = agent.getCurrentMission();
            k9.thoughtTimer = 6.0f;
            entities.add(k9);
        }

        // 4. Camiones: Tráfico de datos en tiempo real analizando el clúster
        for (ClusterDataTraffic traffic : policeMonitor.getActiveTraffic()) {
            HumanEntity truck = new HumanEntity();
            truck.name = traffic.getName();
            truck.isCar = true;
            truck.action = "DRIVING";
            ServerNode3D src = policeMonitor.getNodeById(traffic.getSourceNodeId());
            ServerNode3D tgt = policeMonitor.getNodeById(traffic.getTargetNodeId());
            float sx = (src != null) ? src.getX() : 0f;
            float sz = (src != null) ? src.getZ() : -8f;
            float tx = (tgt != null) ? tgt.getX() : 14f;
            float tz = (tgt != null) ? tgt.getZ() : -8f;
            truck.x = sx; truck.y = 0; truck.z = sz;
            truck.targetX = tx; truck.targetZ = tz;
            truck.dataPayload = traffic.getPayloadSummary();
            truck.connectedDatabase = traffic.getSourceNodeId() + " -> " + traffic.getTargetNodeId();
            truck.r = 255; truck.g = 180; truck.b = 40;
            truck.currentThought = "🚚 " + traffic.getPayloadSummary();
            truck.thoughtTimer = 5.0f;
            entities.add(truck);
        }

        // 5. Personas: Usuarios conectados en tiempo real a JettraStore
        for (JettraLiveSession session : policeMonitor.getLiveSessions()) {
            UserZoneGroup zone = policeMonitor.getZoneById(session.getZoneId());
            ServerNode3D node = policeMonitor.getNodeById(session.getTargetNodeId());
            HumanEntity u = new HumanEntity();
            u.name = session.getUsername();
            u.connectedDatabase = session.getDatabase();
            u.connectionFacility = (zone != null) ? zone.getBuildingName() : "Sede Central";
            u.targetServer = session.getTargetNodeId();
            u.job = "Usuario Conectado (" + session.getClientIp() + ")";
            u.action = "WALKING";
            if (zone != null) {
                u.r = zone.getColorR(); u.g = zone.getColorG(); u.b = zone.getColorB();
                u.x = zone.getBuildingX() + (float)(Math.random() * 2 - 1);
                u.y = 0;
                u.z = zone.getBuildingZ() + (float)(Math.random() * 2 - 1);
            }
            if (node != null) {
                u.targetX = node.getX();
                u.targetZ = node.getZ();
            }
            u.currentThought = session.getCurrentOperation();
            u.thoughtTimer = 5.0f;
            entities.add(u);
        }

        String connName = (policeMonitor.getCurrentProfile() != null) ? policeMonitor.getCurrentProfile().getName() : "Local";
        triggerWorldEvent("Mundo sincronizado en tiempo real con [" + connName + "]: Edificios por zonas, usuarios en vivo, tráfico de clúster y agentes JettraPolice activos.", 0, 255, 180);
    }

    private boolean loadWorldState() {
        try {
            java.io.File file = new java.io.File("memory/world/world_state.json");
            if (file.exists()) {
                WorldState state = mapper.readValue(file, WorldState.class);
                entities.clear();
                entities.addAll(state.entities);
                artifacts.clear();
                artifacts.addAll(state.artifacts);
                megaProjects.clear();
                megaProjects.addAll(state.megaProjects);
                worldTime = state.worldTime;
                return true;
            }
        } catch (Exception ex) {
            System.err.println("Error loading world state: " + ex.getMessage());
        }
        return false;
    }

    private void drawKnowledgeBase() {
        for (KnowledgeEntry k : allKnowledge) {
            Vector3 pos = new Vector3().x(k.x).y(k.y + 0.5f).z(k.z);
            // Floating glowing sphere for knowledge
            float pulse = (float)Math.sin(worldTime * 2.0f) * 0.1f;
            drawSphere(pos, 0.2f + pulse, fade(GOLD, 0.6f));
            drawSphereWires(pos, 0.25f + pulse, 8, 8, fade(SKYBLUE, 0.4f));
        }
    }

    private void drawWolf(Vector3 pos, float rotDeg, Color color) {
        // Wolf Model - Native Shapes
        float x = pos.x(); float y = pos.y(); float z = pos.z();
        float rotRad = rotDeg * ( (float)Math.PI / 180.0f);
        
        // Body (Capsule/Cylinder)
        drawCapsule(new Vector3().x(x).y(y + 0.5f).z(z), 
                   new Vector3().x(x + (float)Math.sin(rotRad)*1.2f).y(y + 0.6f).z(z + (float)Math.cos(rotRad)*1.2f), 
                   0.5f, 8, 8, DARKGRAY);

        // Legs (4 cylinders)
        float legOff = 0.3f;
        drawCylinder(new Vector3().x(x - legOff).y(y).z(z - legOff), 0.1f, 0.1f, 0.6f, 6, DARKGRAY);
        drawCylinder(new Vector3().x(x + legOff).y(y).z(z - legOff), 0.1f, 0.1f, 0.6f, 6, DARKGRAY);
        drawCylinder(new Vector3().x(x - legOff + (float)Math.sin(rotRad)).y(y).z(z - legOff + (float)Math.cos(rotRad)), 0.1f, 0.1f, 0.6f, 6, DARKGRAY);
        drawCylinder(new Vector3().x(x + legOff + (float)Math.sin(rotRad)).y(y).z(z + legOff + (float)Math.cos(rotRad)), 0.1f, 0.1f, 0.6f, 6, DARKGRAY);

        // Head (Sphere + Snout)
        float headX = x + (float)Math.sin(rotRad)*1.6f;
        float headZ = z + (float)Math.cos(rotRad)*1.6f;
        drawSphere(new Vector3().x(headX).y(y + 1.2f).z(headZ), 0.45f, DARKGRAY);
        drawCylinder(new Vector3().x(headX + (float)Math.sin(rotRad)*0.3f).y(y + 1.1f).z(headZ + (float)Math.cos(rotRad)*0.3f), 
                     0.2f, 0.1f, 0.4f, 6, BLACK); // Snout
        
        // Ears (Triangles represent ears)
        drawSphere(new Vector3().x(headX).y(y + 1.6f).z(headZ + 0.15f), 0.15f, LIGHTGRAY); // Ear L
        drawSphere(new Vector3().x(headX).y(y + 1.6f).z(headZ - 0.15f), 0.15f, LIGHTGRAY); // Ear R

        // Tail
        drawCapsule(new Vector3().x(x).y(y + 0.6f).z(z), 
                   new Vector3().x(x - (float)Math.sin(rotRad)*0.8f).y(y + 0.8f).z(z - (float)Math.cos(rotRad)*0.8f), 
                   0.15f, 4, 4, DARKGRAY);
    }

    private void generateEntity(String name, float x, float y, float z, boolean isWolf, boolean isAnimal, boolean isCar) {
        HumanEntity e = new HumanEntity();
        e.name = name;
        e.x = x; e.y = y; e.z = z;
        e.targetX = x; e.targetY = y; e.targetZ = z;
        e.isWolf = isWolf; e.isAnimal = isAnimal; e.isCar = isCar;
        e.energy = 100;
        e.action = "IDLE";
        e.r = (int)(Math.random()*255); e.g = (int)(Math.random()*255); e.b = (int)(Math.random()*255);
        entities.add(e);
    }

    private static class KnowledgeEntry {
        public long timestamp;
        public String agent;
        public String source;
        public String topic;
        public float x, y, z;
        public double confidence;
        public String context;
        public String[] keywords;
    }

    private com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private void saveKnowledge(HumanEntity agent, String source, String topic) {
        try {
            java.io.File dir = new java.io.File("memory/world");
            if (!dir.exists()) dir.mkdirs();
            
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.agent = agent.name;
            entry.source = source;
            entry.topic = topic;
            entry.x = agent.x; entry.y = agent.y; entry.z = agent.z;
            entry.confidence = 0.7 + (Math.random() * 0.3); // High confidence
            entry.context = agent.action;
            entry.keywords = new String[]{topic.toLowerCase().replace(" ", "-"), "jettra-learning", source.toLowerCase()};

            java.io.File file = new java.io.File("memory/world/knowledge.json");
            java.util.List<KnowledgeEntry> list;
            if (file.exists()) {
                list = mapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<KnowledgeEntry>>() {});
            } else {
                list = new java.util.ArrayList<>();
            }

            // --- KNOWLEDGE FUSION LOGIC ---
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                KnowledgeEntry existing = list.get(i);
                if (existing.topic.equalsIgnoreCase(entry.topic)) {
                    found = true;
                    // Keep the one with highest confidence
                    if (entry.confidence > existing.confidence) {
                        list.set(i, entry);
                    }
                    break;
                }
            }
            if (!found) {
                list.add(entry);
            }
            // ------------------------------
            allKnowledge = list;
            updateTopKnowledge();

            mapper.writerWithDefaultPrettyPrinter().writeValue(file, list);

        } catch (java.io.IOException ex) {
            System.err.println("Error guardando conocimiento JSON: " + ex.getMessage());
        }
    }

    private void updateTopKnowledge() {
        if (allKnowledge == null || allKnowledge.isEmpty()) {
            java.io.File file = new java.io.File("memory/world/knowledge.json");
            if (file.exists()) {
                try {
                    allKnowledge = mapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<List<KnowledgeEntry>>() {});
                } catch (Exception e) {}
            }
        }
        
        if (allKnowledge != null) {
            allKnowledge.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            topKnowledge = allKnowledge.stream().limit(5).collect(java.util.stream.Collectors.toList());
        }
    }

    private void drawTopKnowledgePanel() {
        int x = SCREEN_WIDTH/2 - 200;
        int y = 10;
        drawRectangleRounded(new Rectangle().x(x).y(y).width(400).height(110), 0.2f, 8, fade(DARKGRAY, 0.8f));
        drawRectangleRoundedLines(new Rectangle().x(x).y(y).width(400).height(110), 0.2f, 8, fade(GOLD, 0.5f));
        drawText("TOP 5 CONOCIMIENTO CRÍTICO (MAX CONFIDENCE)", x + 10, y + 10, 10, GOLD);
        
        for (int i = 0; i < topKnowledge.size(); i++) {
            KnowledgeEntry k = topKnowledge.get(i);
            String txt = (i+1) + ". " + k.topic + " (" + String.format("%.2f", k.confidence) + ")";
            drawText(txt, x + 15, y + 30 + (i * 15), 11, RAYWHITE);
        }
    }

    private void drawChatWindow() {
        int w = 400; int h = 250;
        int x = 280; int y = SCREEN_HEIGHT - h - 10;
        drawRectangleRounded(new Rectangle().x(x).y(y).width(w).height(h), 0.1f, 8, fade(BLACK, 0.8f));
        drawRectangleRoundedLines(new Rectangle().x(x).y(y).width(w).height(h), 0.1f, 8, SKYBLUE);
        drawText("CHAT CON JETTRA WOLF (EL LÍDER)", x + 15, y + 15, 14, SKYBLUE);
        
        // History
        for (int i = 0; i < chatHistory.size(); i++) {
            if (i > 8) break;
            drawText("> " + chatHistory.get(chatHistory.size() - 1 - i), x + 15, y + h - 60 - (i * 18), 12, RAYWHITE);
        }

        // Input Box
        drawRectangle(x + 10, y + h - 35, w - 20, 25, DARKGRAY);
        drawRectangleLines(x + 10, y + h - 35, w - 20, 25, RAYWHITE);
        drawText(chatInput + "_", x + 20, y + h - 28, 12, LIME);
        drawText("[ENTER] para enviar", x + 150, y + 20, 10, GRAY);
    }

    private String getRandomResponse(String type) {
        String[] greetings = {"Saludos, creador. Escucho tus órdenes.", "La manada te saluda.", "Aquí el Lobo Jettra.", "Mi conocimiento está a tu disposición."};
        String[] confirms = {"¡Entendido! Mis agentes ya están en ello.", "Como ordenes. Ejecutando mandato.", "Hecho. La red neutral lo sabe.", "La orden ha sido asimilada."};
        String[] weather = {"Alterando la atmósfera.", "Cambiando las variables climáticas.", "Modificando el entorno visible.", "Que cambien los cielos de este mundo."};
        String[] clean = {"Limpiando el plano existencial.", "Todo polvo estelar y artefacto obsoleto ha sido purgado.", "Mundo purificado por el líder.", "He reiniciado las líneas de tiempo de los proyectos."};
        String[] dunno = {"Esa información no está en mis datos cartesianos.", "Mis agentes aún no han aprendido eso.", "Los vectores apuntan a lo desconocido... no lo sé.", "Interesante pregunta, pero carezco de esos cálculos."};

        java.util.Random r = new java.util.Random();
        if ("GREETING".equals(type)) return greetings[r.nextInt(greetings.length)];
        if ("CONFIRM".equals(type)) return confirms[r.nextInt(confirms.length)];
        if ("WEATHER".equals(type)) return weather[r.nextInt(weather.length)];
        if ("CLEAN".equals(type)) return clean[r.nextInt(clean.length)];
        return dunno[r.nextInt(dunno.length)];
    }

    private void sendChatMessage(String msg) {
        chatHistory.add("Tú: " + msg);
        for (HumanEntity e : entities) {
            if (e.name.contains("Jettra")) {
                String res = getRandomResponse("UNKNOWN");
                String lowerMsg = msg.toLowerCase();
                
                if (lowerMsg.matches(".*\\b(hola|saludos|buenas|hey)\\b.*")) res = getRandomResponse("GREETING");
                if (lowerMsg.matches(".*\\b(construye|haz|crea|edifica)\\b.*")) res = getRandomResponse("CONFIRM");
                if (lowerMsg.matches(".*\\b(quien eres|tu nombre)\\b.*")) res = "Soy Jettra Wolf, el guía alfa de esta simulación 3D.";

                // Knowledge Search
                if (res.equals(getRandomResponse("UNKNOWN")) || res.startsWith("Soy Jettra")) {
                    try {
                        java.io.File file = new java.io.File("memory/world/knowledge.json");
                        if (file.exists()) {
                            List<KnowledgeEntry> all = mapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<List<KnowledgeEntry>>() {});
                            boolean foundInfo = false;
                            for (KnowledgeEntry k : all) {
                                if (lowerMsg.contains(k.topic.toLowerCase()) || 
                                   (k.keywords != null && java.util.Arrays.stream(k.keywords).anyMatch(lowerMsg::contains))) {
                                    res = "He procesado '" + k.topic + "' desde " + k.source + ". Mis agentes se benefician de ello.";
                                    foundInfo = true;
                                    break;
                                }
                            }
                            if (!foundInfo && lowerMsg.matches(".*\\b(aprender|sabes|conocimiento)\\b.*")) {
                                res = "Estamos asimilando datos de todo tu universo local. Dame PDFs y aprenderé más.";
                            }
                        }
                    } catch (Exception ex) {}
                }

                // Physical Commands
                if (lowerMsg.matches(".*\\b(teletransportar|mover|viajar|centro)\\b.*")) {
                    res = "Teletransporte cuántico activado. Vuelvan a casa.";
                    for (HumanEntity target : entities) {
                        if (!target.name.contains("Jettra")) {
                            target.x = 0; target.z = 0; target.targetX = 0; target.targetZ = 0;
                        }
                    }
                    worldEvents.add(new WorldEvent("Teletransporte masivo activado por Jettra", worldTime, 255, 255, 0));
                }
                if (lowerMsg.matches(".*\\b(poblacion|agentes|crear)\\b.*")) {
                    res = getRandomResponse("CONFIRM") + " Población aumentada.";
                    for(int i=0; i<5; i++) generateEntity("Sentry-" + (int)(Math.random()*1000), (float)(Math.random()*20-10), 0, (float)(Math.random()*20-10), false, false, false);
                }
                if (lowerMsg.matches(".*\\b(limpiar|borrar|eliminar|quitar)\\b.*")) {
                    res = getRandomResponse("CLEAN");
                    artifacts.clear();
                    megaProjects.clear();
                    worldEvents.add(new WorldEvent(res, worldTime, 100, 100, 255));
                }
                if (lowerMsg.matches(".*\\b(acelerar|rapido|tiempo)\\b.*")) {
                    timeScale = (timeScale == 1.0f) ? 5.0f : 1.0f;
                    res = "He ajustado las manecillas del reloj a " + timeScale + "x.";
                    worldEvents.add(new WorldEvent("Manipulación temporal: " + res, worldTime, 0, 255, 200));
                }
                if (lowerMsg.matches(".*\\b(clima|tiempo atmosferico|llover|sol|noche|dia|día)\\b.*")) {
                    weatherMode = (weatherMode + 1) % 3;
                    String[] m = {"Soleado", "Nocturno", "Tormentoso"};
                    res = getRandomResponse("WEATHER") + " Ahora estamos en modo " + m[weatherMode] + ".";
                    worldEvents.add(new WorldEvent("Cambio climático a " + m[weatherMode], worldTime, 200, 0, 255));
                }
                if (lowerMsg.matches(".*\\b(estado|reporte|como estan|salud global)\\b.*")) {
                    int vivos = 0; int muertos = 0; float saludMedia = 0;
                    for (HumanEntity a : entities) {
                        if (a.isDead) muertos++; else { vivos++; saludMedia += a.health; }
                    }
                    if (vivos > 0) saludMedia /= vivos;
                    String contextual = (saludMedia > 60) ? "La manada prospera bajo mi guía." : "La manada está sufriendo. Sus métricas vitales caen.";
                    res = contextual + " " + vivos + " vivos, " + muertos + " fallecidos. " + megaProjects.size() + " proyectos.";
                }
                                
                final String finalRes = res;
                chatHistory.add("Jettra: " + finalRes);
                e.currentThought = finalRes;
                e.thoughtTimer = 5.0f;
                Thought t = new Thought();
                t.content = finalRes; t.owner = e; t.x = e.x; t.y = e.y + 3.5f; t.z = e.z;
                t.timer = 5.0f; t.isThought = false;
                thoughts.add(t);
                break;
            }
        }
        if (chatHistory.size() > 20) chatHistory.remove(0);
    }

    private void initSfx() {
        // Here we could loadSound("rain.wav") or generate procedural waves.
        // For simplicity and to avoid crashes with missing files or JNA-Wave complex buffers, 
        // we'll use a simulation with world events that emit a 'play' signal.
    }

    private void updateSfx() {
        if (!sfxEnabled) return;
        // In reality, this would play/stop a loop based on weatherMode.
    }

    private static class WorldState {
        public List<HumanEntity> entities;
        public List<Artifact> artifacts;
        public List<MegaProject> megaProjects;
        public float worldTime;
    }

    private void saveWorldState() {
        try {
            java.io.File dir = new java.io.File("memory/world");
            if (!dir.exists()) dir.mkdirs();

            WorldState state = new WorldState();
            state.entities = new java.util.ArrayList<>(entities);
            state.artifacts = new java.util.ArrayList<>(artifacts);
            state.megaProjects = new java.util.ArrayList<>(megaProjects);
            state.worldTime = worldTime;

            java.io.File file = new java.io.File("memory/world/world_state.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, state);
            worldEvents.add(new WorldEvent("Mundo guardado automáticamente", worldTime, 0, 255, 100));
        } catch (Exception ex) {
            System.err.println("Error saving world state: " + ex.getMessage());
            worldEvents.add(new WorldEvent("Error guardando mundo", worldTime, 255, 0, 0));
        }
    }

    private void drawJettraStatusTooltip() {
        HumanEntity jettra = null;
        for (HumanEntity e : entities) {
            if (e.name.contains("Jettra")) { jettra = e; break; }
        }
        if (jettra == null) return;

        int x = 10;
        int y = 200;
        int w = 280;
        int h = 100;

        drawRectangleRounded(new Rectangle().x(x).y(y).width(w).height(h), 0.2f, 8, fade(DARKGRAY, 0.9f));
        drawRectangleRoundedLines(new Rectangle().x(x).y(y).width(w).height(h), 0.2f, 8, GOLD);
        
        drawText("VOZ DEL LÍDER: " + jettra.name.toUpperCase(), x + 10, y + 10, 12, GOLD);
        drawText("ACCIÓN: " + jettra.action, x + 10, y + 30, 10, RAYWHITE);
        drawText("Población Total: " + entities.size(), x + 10, y + 45, 10, SKYBLUE);
        drawText("Proyectos Activos: " + megaProjects.size(), x + 140, y + 45, 10, SKYBLUE);
        
        if (jettra.currentThought != null && jettra.thoughtTimer > 0) {
            String thought = jettra.currentThought;
            int maxChars = 40;
            if (thought.length() > maxChars) thought = thought.substring(0, maxChars-3) + "...";
            drawText("PENSANDO: " + thought, x + 10, y + 70, 11, LIME);
        } else {
            drawText("PENSANDO: Vigilando el conocimiento...", x + 10, y + 70, 11, GRAY);
        }
    }

    public static void main(String[] args) {
        new Jettra3DApp().run();
    }

    private void initPoliceMonitor() {
        this.policeMonitor = new JettraStorePoliceMonitor();

        // Agente Centinela JettraStorePolice
        policeSentinelEntity = new HumanEntity();
        policeSentinelEntity.name = "JettraStorePolice";
        policeSentinelEntity.action = "PATROLLING";
        policeSentinelEntity.job = "Centinela Supervisor de Servidores JettraStore";
        policeSentinelEntity.r = 30;
        policeSentinelEntity.g = 144;
        policeSentinelEntity.b = 255;
        policeSentinelEntity.x = -14.0f;
        policeSentinelEntity.y = 0.0f;
        policeSentinelEntity.z = -8.5f;
        policeSentinelEntity.targetX = -14.0f;
        policeSentinelEntity.targetZ = -8.5f;
        policeSentinelEntity.targetY = 0.0f;
        policeSentinelEntity.currentThought = "🛡️ JettraStorePolice: Iniciando inspección de nodos y supervisión de Heap...";
        policeSentinelEntity.thoughtTimer = 6.0f;
        policeSentinelEntity.currentGoal = "Supervisión de Nodos y Estabilidad Heap";
        entities.add(policeSentinelEntity);

        worldEvents.add(new WorldEvent("JettraStorePolice: Centinela de Clúster activado.", worldTime, 30, 144, 255));
    }

    private void updatePolicePatrol(float dt) {
        if (policeMonitor == null) return;
        List<ServerNode3D> nodes = policeMonitor.getServerNodes();
        if (nodes.isEmpty()) return;

        for (ServerNode3D node : nodes) {
            node.setPulsePhase(node.getPulsePhase() + dt * 2.5f);
        }

        if (policeSentinelEntity == null) return;

        policePatrolTimer += dt;
        if (policePatrolTimer >= 5.0f) {
            policePatrolTimer = 0f;
            currentPatrolTargetNodeIndex = (currentPatrolTargetNodeIndex + 1) % nodes.size();
            ServerNode3D target = nodes.get(currentPatrolTargetNodeIndex);
            policeSentinelEntity.targetX = target.getX();
            policeSentinelEntity.targetZ = target.getZ() + 3.2f;
            policeSentinelEntity.targetY = 0f;

            if (target.isOnline()) {
                String thought = String.format("🛡️ JettraStorePolice: Servidor '%s' SALUDABLE (RAM %.0f%%)",
                    target.getId(), target.getHeapSaturationPercent());
                policeSentinelEntity.currentThought = thought;
                policeSentinelEntity.thoughtTimer = 4.5f;
            } else {
                String alert = String.format("🚨 ¡ALERTA POLICIAL! Servidor '%s' FUERA DE SERVICIO", target.getId());
                policeSentinelEntity.currentThought = alert;
                policeSentinelEntity.thoughtTimer = 5.0f;
                worldEvents.add(new WorldEvent("JettraStorePolice: Servidor '" + target.getId() + "' FUERA DE SERVICIO", worldTime, 255, 50, 50));
            }
        }

        float dx = policeSentinelEntity.targetX - policeSentinelEntity.x;
        float dz = policeSentinelEntity.targetZ - policeSentinelEntity.z;
        float dist = (float)Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.4f) {
            policeSentinelEntity.action = "WALKING";
            float speed = 3.5f * dt;
            policeSentinelEntity.x += (dx / dist) * Math.min(speed, dist);
            policeSentinelEntity.z += (dz / dist) * Math.min(speed, dist);
            policeSentinelEntity.rotation = (float)Math.toDegrees(Math.atan2(dx, dz));
        } else {
            policeSentinelEntity.action = "PATROLLING";
        }
    }

    private void drawServerClusterNodes() {
        if (policeMonitor == null) return;
        List<ServerNode3D> nodes = policeMonitor.getServerNodes();

        // 1. Líneas de haz de datos del anillo Raft
        for (int i = 0; i < nodes.size(); i++) {
            ServerNode3D current = nodes.get(i);
            ServerNode3D next = nodes.get((i + 1) % nodes.size());
            Vector3 startPos = new Vector3().x(current.getX()).y(0.3f).z(current.getZ());
            Vector3 endPos = new Vector3().x(next.getX()).y(0.3f).z(next.getZ());

            boolean ringActive = current.isOnline() && next.isOnline();
            Color lineColor = ringActive ? fade(SKYBLUE, 0.7f) : fade(RED, 0.45f);
            drawLine3D(startPos, endPos, lineColor);

            if (ringActive) {
                float progress = (worldTime * 0.8f + (i * 0.33f)) % 1.0f;
                float px = current.getX() + (next.getX() - current.getX()) * progress;
                float pz = current.getZ() + (next.getZ() - current.getZ()) * progress;
                drawSphere(new Vector3().x(px).y(0.35f).z(pz), 0.22f, GOLD);
            }
        }

        // 2. Gabinetes 3D de servidores (Server Racks)
        for (ServerNode3D node : nodes) {
            float x = node.getX();
            float y = node.getY();
            float z = node.getZ();
            float w = node.getWidth();
            float h = node.getHeight();
            float d = node.getDepth();
            boolean online = node.isOnline();

            // Pedestal Base
            Vector3 basePos = new Vector3().x(x).y(y + 0.2f).z(z);
            drawCube(basePos, w + 0.8f, 0.4f, d + 0.8f, new Color().r((byte)30).g((byte)35).b((byte)45).a((byte)255));
            drawCubeWires(basePos, w + 0.8f, 0.4f, d + 0.8f, online ? SKYBLUE : RED);

            // Chasis
            Vector3 bodyPos = new Vector3().x(x).y(y + h / 2.0f + 0.4f).z(z);
            Color chassisColor = online 
                ? new Color().r((byte)18).g((byte)24).b((byte)36).a((byte)255)
                : new Color().r((byte)55).g((byte)18).b((byte)22).a((byte)255);
            drawCube(bodyPos, w, h, d, chassisColor);

            Color wireColor = online ? SKYBLUE : RED;
            drawCubeWires(bodyPos, w, h, d, wireColor);

            // Ranuras Rack y LEDs
            int rackUnits = 5;
            float unitHeight = (h - 0.8f) / rackUnits;
            for (int r = 0; r < rackUnits; r++) {
                float slotY = y + 0.8f + r * unitHeight + unitHeight / 2.0f;
                Vector3 slotPos = new Vector3().x(x).y(slotY).z(z + d / 2.0f + 0.05f);
                drawCube(slotPos, w - 0.4f, unitHeight * 0.7f, 0.1f, new Color().r((byte)10).g((byte)12).b((byte)18).a((byte)255));

                float ledX1 = x - (w / 2.0f) + 0.5f;
                float ledX2 = ledX1 + 0.4f;
                Vector3 led1 = new Vector3().x(ledX1).y(slotY).z(z + d / 2.0f + 0.12f);
                Vector3 led2 = new Vector3().x(ledX2).y(slotY).z(z + d / 2.0f + 0.12f);

                if (online) {
                    drawSphere(led1, 0.08f, LIME);
                    boolean blink = ((int)(worldTime * 4.0f + r) % 2 == 0);
                    drawSphere(led2, 0.08f, blink ? SKYBLUE : DARKGRAY);
                } else {
                    boolean blinkRed = ((int)(worldTime * 2.0f) % 2 == 0);
                    drawSphere(led1, 0.09f, blinkRed ? RED : MAROON);
                    drawSphere(led2, 0.09f, blinkRed ? ORANGE : BLACK);
                }
            }

            // Baliza Superior
            float beaconY = y + h + 0.8f;
            Vector3 beaconPos = new Vector3().x(x).y(beaconY).z(z);
            if (online) {
                Color beaconColor = node.getRole() == ClusterNode.Role.PRIMARY ? GOLD : LIME;
                float pulse = (float)Math.sin(worldTime * 3.0f + x) * 0.08f;
                drawSphere(beaconPos, 0.35f + pulse, beaconColor);
                drawSphereWires(beaconPos, 0.45f + pulse, 8, 8, SKYBLUE);
            } else {
                boolean alertBlink = ((int)(worldTime * 3.0f) % 2 == 0);
                drawCube(beaconPos, 0.6f, 0.6f, 0.6f, alertBlink ? RED : MAROON);
                drawCubeWires(beaconPos, 0.8f, 0.8f, 0.8f, RED);
            }

            if (node == selectedServerNode) {
                drawCubeWires(bodyPos, w + 0.3f, h + 0.3f, d + 0.3f, YELLOW);
            }
        }
    }

    private void drawServerLabelsAndHUD() {
        if (policeMonitor == null) return;
        int sw = getScreenWidth();
        int sh = getScreenHeight();

        // 1. Barra superior de telemetría de clúster JettraStore
        drawRectangle(20, 10, sw - 240, 36, fade(BLACK, 0.75f));
        drawRectangleLines(20, 10, sw - 240, 36, GOLD);
        drawLegibleText("JETTRASTORE CLUSTER MONITOR", 30, 20, 14, GOLD);

        int curX = 300;
        for (ServerNode3D node : policeMonitor.getServerNodes()) {
            boolean isOnline = node.isOnline();
            Color stColor = isOnline ? LIME : RED;
            String label = String.format("%s [%s]: %s", 
                node.getId(), 
                node.getRole() == ClusterNode.Role.PRIMARY ? "LEADER" : "REPLICA",
                isOnline ? "ONLINE" : "OFFLINE");
            int lw = measureLegibleText(label, 12);
            drawRectangle(curX - 5, 15, lw + 22, 24, fade(BLACK, 0.6f));
            drawRectangleLines(curX - 5, 15, lw + 22, 24, stColor);
            drawCircle(curX + 4, 27, 4.0f, stColor);
            drawLegibleText(label, curX + 14, 21, 12, stColor);
            curX += lw + 32;
        }

        drawLegibleText("Click Derecho en Nodo: Ver Recursos | 'N': Ciclar", sw - 560, 22, 11, RAYWHITE);

        // 2. Badges flotantes 3D sobre cada servidor
        for (ServerNode3D node : policeMonitor.getServerNodes()) {
            Vector3 top3D = new Vector3().x(node.getX()).y(node.getY() + node.getHeight() + 1.6f).z(node.getZ());
            Vector2 screenPos = getWorldToScreen(top3D, camera);
            int sx = (int)screenPos.x();
            int sy = (int)screenPos.y();

            if (sx > 20 && sx < sw - 220 && sy > 40 && sy < sh - 20) {
                String title = node.getName();
                String status = node.isOnline() ? "● EN LÍNEA" : "▲ FUERA DE SERVICIO";
                Color stColor = node.isOnline() ? LIME : RED;

                int bw = Math.max(measureLegibleText(title, 12), measureLegibleText(status, 11)) + 18;
                int bh = 38;
                int bx = sx - bw / 2;
                int by = sy - bh;

                drawRectangleRounded(new Rectangle().x(bx).y(by).width(bw).height(bh), 0.25f, 6, fade(BLACK, 0.8f));
                drawRectangleRoundedLines(new Rectangle().x(bx).y(by).width(bw).height(bh), 0.25f, 6, stColor);

                drawLegibleText(title, bx + (bw - measureLegibleText(title, 12)) / 2, by + 4, 12, RAYWHITE);
                drawLegibleText(status, bx + (bw - measureLegibleText(status, 11)) / 2, by + 20, 11, stColor);
            }
        }
    }

    private void drawNodeInspectorModal() {
        if (!showNodeInspectorModal || selectedServerNode == null) return;

        int sw = getScreenWidth();
        int sh = getScreenHeight();

        drawRectangle(0, 0, sw, sh, fade(BLACK, 0.45f));

        int mw = 560;
        int mh = 480;
        int mx = (sw - mw) / 2;
        int my = (sh - mh) / 2;

        Rectangle modalRec = new Rectangle().x(mx).y(my).width(mw).height(mh);
        drawRectangleRounded(modalRec, 0.08f, 10, new Color().r((byte)14).g((byte)20).b((byte)32).a((byte)250));
        drawRectangleRoundedLines(modalRec, 0.08f, 10, selectedServerNode.isOnline() ? GOLD : RED);

        drawText("INSPECTOR DE NODO JETTRASTORE", mx + 25, my + 20, 18, GOLD);
        drawLegibleText("Servidor: " + selectedServerNode.getName() + " (" + selectedServerNode.getHost() + ":" + selectedServerNode.getPort() + ")", 
            mx + 25, my + 46, 12, SKYBLUE);

        if (guiButton(mx + mw - 45, my + 15, 30, 25, "X", RED)) {
            showNodeInspectorModal = false;
        }

        int cy = my + 75;
        boolean online = selectedServerNode.isOnline();
        Color stBannerColor = online ? new Color().r((byte)20).g((byte)60).b((byte)30).a((byte)255) 
                                     : new Color().r((byte)80).g((byte)20).b((byte)25).a((byte)255);
        Rectangle stRec = new Rectangle().x(mx + 25).y(cy).width(mw - 50).height(32);
        drawRectangleRounded(stRec, 0.2f, 6, stBannerColor);
        drawRectangleRoundedLines(stRec, 0.2f, 6, online ? LIME : RED);

        String stText = online 
            ? "● ESTADO: EN LÍNEA (RUNNING) - Latencia: " + selectedServerNode.getLatencyMs() + " ms | Rol: " + selectedServerNode.getRole() + " (" + selectedServerNode.getRaftState() + ")"
            : "▲ ESTADO: FUERA DE SERVICIO (OFFLINE) - " + selectedServerNode.getStatusMessage();
        drawLegibleText(stText, mx + 35, cy + 9, 12, online ? LIME : RED);

        cy += 45;
        drawRectangle(mx + 25, cy, mw - 50, 48, fade(BLACK, 0.5f));
        drawRectangleLines(mx + 25, cy, mw - 50, 48, online ? SKYBLUE : RED);
        drawLegibleText("🛡️ JettraStorePolice (Evaluación de Estabilidad & Heap):", mx + 35, cy + 6, 11, GOLD);
        drawLegibleText(selectedServerNode.getPoliceDiagnosis(), mx + 35, cy + 24, 11, online ? RAYWHITE : RED);

        cy += 60;
        drawText("RECURSOS CONSUMIDOS:", mx + 25, cy, 14, GOLD);
        cy += 22;

        long hUsed = selectedServerNode.getHeapUsedMb();
        long hMax = selectedServerNode.getHeapMaxMb();
        double satPct = selectedServerNode.getHeapSaturationPercent();
        drawLegibleText(String.format("• Memoria Heap JVM: %d MB / %d MB (Máx: %d MB) - Saturación: %.1f%%",
            hUsed, selectedServerNode.getHeapTotalMb(), hMax, satPct), mx + 30, cy, 12, RAYWHITE);
        cy += 18;

        int barW = mw - 60;
        int barH = 12;
        drawRectangle(mx + 30, cy, barW, barH, DARKGRAY);
        Color barColor = satPct > 85 ? RED : (satPct > 70 ? YELLOW : LIME);
        int fillW = online ? (int)((satPct / 100.0) * barW) : 0;
        drawRectangle(mx + 30, cy, Math.min(barW, Math.max(0, fillW)), barH, barColor);
        drawRectangleLines(mx + 30, cy, barW, barH, WHITE);
        cy += 20;

        drawLegibleText("• Memoria Panama FFM Off-Heap: " + selectedServerNode.getPanamaDirectMemMb() + " MB (Arena Cero-Copia Directa)", mx + 30, cy, 12, RAYWHITE);
        cy += 18;
        drawLegibleText("• Procesador Loom: " + selectedServerNode.getCpuCores() + " Cores CPU | " + selectedServerNode.getActiveVirtualThreads() + " Virtual Threads I/O", mx + 30, cy, 12, RAYWHITE);
        cy += 18;
        drawLegibleText(String.format("• Almacenamiento LSM: MemTable %d MB | SSTables %.2f MB (%d archivos .jettra)",
            selectedServerNode.getMemTableMb(), (selectedServerNode.getDiskSSTablesBytes() / (1024.0 * 1024.0)), selectedServerNode.getDiskFilesCount()),
            mx + 30, cy, 12, RAYWHITE);
        cy += 18;
        drawLegibleText("• Latidos y Telemetría: Último heartbeat recibido hace " + (System.currentTimeMillis() - selectedServerNode.getLastHeartbeat()) + " ms", mx + 30, cy, 12, RAYWHITE);

        cy += 35;
        String toggleBtnText = online 
            ? "DESCONECTAR (SIMULAR FUERA DE SERVICIO)" 
            : "RECONECTAR (ACTIVAR EN LÍNEA)";
        Color toggleBtnColor = online ? RED : LIME;

        // Botón para expandir y entrar al submundo del nodo
        if (guiButton(mx + 30, cy - 42, mw - 60, 32, "🌐 EXPANDIR Y ENTRAR AL MUNDO DEL SERVIDOR (INTERIOR)", SKYBLUE)) {
            startNodeExpansion(selectedServerNode);
            return;
        }

        if (guiButton(mx + 30, cy, 320, 32, toggleBtnText, toggleBtnColor)) {
            selectedServerNode.toggleOffline();
            worldEvents.add(new WorldEvent(
                "JettraStorePolice: Servidor " + selectedServerNode.getId() + " conmutado a " + (selectedServerNode.isOnline() ? "EN LÍNEA" : "FUERA DE SERVICIO"),
                worldTime, online ? 255 : 0, online ? 60 : 255, 60));
        }

        if (guiButton(mx + 365, cy, 160, 32, "CERRAR [ESC]", DARKGRAY)) {
            showNodeInspectorModal = false;
        }
    }


    // =========================================================================
    // IMPLEMENTACIÓN DEL MUNDO INTERIOR DEL NODO, BASES DE DATOS Y PUERTA 3D
    // =========================================================================

    private BoundingBox getDoorBoundingBox() {
        Vector3 min = new Vector3().x(-2.8f).y(0f).z(-12.5f);
        Vector3 max = new Vector3().x(2.8f).y(6.0f).z(-9.5f);
        return new BoundingBox(min, max);
    }

    private void startNodeExpansion(ServerNode3D node) {
        this.expandedNode = node;
        this.selectedDatabase = null;
        this.worldMode = WorldMode.EXPANDING_TRANSITION;
        this.transitionTimer = 0.85f;
        this.showNodeInspectorModal = false;
        this.savedMainCameraPos = new Vector3().x(camera.position().x()).y(camera.position().y()).z(camera.position().z());
        this.savedMainCameraTarget = new Vector3().x(camera.target().x()).y(camera.target().y()).z(camera.target().z());
        this.savedMainCameraFov = camera.fovy();
        worldEvents.add(new WorldEvent(
            "Expandiendo dimensión interior del servidor '" + node.getId() + "'...",
            worldTime, 0, 220, 255));
    }

    private void startExitTransition() {
        this.worldMode = WorldMode.EXITING_TRANSITION;
        this.transitionTimer = 0.85f;
        this.selectedDatabase = null;
        worldEvents.add(new WorldEvent(
            "Atravesando Puerta Dimensional... Retornando al Mundo Principal.",
            worldTime, 255, 215, 0));
    }

    private void drawInnerNodeWorld3D() {
        if (expandedNode == null) return;
        boolean online = expandedNode.isOnline();

        // 1. Piso Ciberespacial
        drawGrid(40, 1.2f);
        for (int r = 4; r <= 16; r += 4) {
            Color ringCol = online ? fade(SKYBLUE, 0.45f) : fade(RED, 0.45f);
            drawCircle3D(new Vector3().x(0).y(0.02f).z(0), r, new Vector3().x(1).y(0).z(0), 90.0f, ringCol);
        }

        // Plataforma central
        drawCylinder(new Vector3().x(0).y(0.05f).z(0), 3.2f, 3.5f, 0.1f, 24, DARKGRAY);
        drawCylinderWires(new Vector3().x(0).y(0.05f).z(0), 3.2f, 3.5f, 0.1f, 24, online ? SKYBLUE : RED);

        // 2. Columnas perimetrales de memoria y almacenamiento (Bancos de Servidores Tron)
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2.0;
            float px = (float)(Math.cos(angle) * 16.5);
            float pz = (float)(Math.sin(angle) * 16.5);
            Vector3 pillarPos = new Vector3().x(px).y(4.0f).z(pz);
            drawCube(pillarPos, 1.4f, 8.0f, 1.4f, new Color().r((byte)15).g((byte)20).b((byte)35).a((byte)255));
            drawCubeWires(pillarPos, 1.42f, 8.02f, 1.42f, online ? DARKBLUE : MAROON);

            // Luces LED de actividad en las columnas
            for (int l = 0; l < 5; l++) {
                float ly = 1.0f + l * 1.4f;
                Color ledCol = ((int)(innerWorldTime * 4 + i + l) % 2 == 0) ? (online ? LIME : RED) : DARKGRAY;
                drawSphere(new Vector3().x(px).y(ly).z(pz + 0.72f), 0.12f, ledCol);
            }
        }

        // 3. Bases de Datos 3D Alojadas en este Nodo
        for (DatabaseInfo3D db : expandedNode.getDatabases()) {
            Vector3 basePos = new Vector3().x(db.getX()).y(0.25f).z(db.getZ());
            Color dbColor = new Color().r((byte)db.getR()).g((byte)db.getG()).b((byte)db.getB()).a((byte)255);
            if (!online) {
                dbColor = RED;
            }

            // Pedestal metálico cilíndrico
            drawCylinder(basePos, db.getRadius(), db.getRadius() * 1.15f, 0.5f, 20, DARKGRAY);
            drawCylinderWires(basePos, db.getRadius(), db.getRadius() * 1.15f, 0.5f, 20, dbColor);

            // Núcleo cristalino flotante y rotando
            float coreY = basePos.y() + 1.8f + (float)Math.sin(innerWorldTime * 2.2f + db.getPulsePhase()) * 0.25f;
            Vector3 corePos = new Vector3().x(db.getX()).y(coreY).z(db.getZ());

            drawCube(corePos, 1.2f, 1.2f, 1.2f, dbColor);
            drawCubeWires(corePos, 1.22f, 1.22f, 1.22f, WHITE);

            // Anillos orbitales giratorios de datos
            float orbitAngle = innerWorldTime * 45.0f + db.getPulsePhase() * 10.0f;
            drawCircle3D(corePos, 1.8f, new Vector3().x(1).y(1).z(0), orbitAngle, fade(dbColor, 0.65f));

            // Si está seleccionada, resaltar caja delimitadora
            if (db == selectedDatabase) {
                drawBoundingBox(db.getBoundingBox(), GOLD);
            }

            // Rayo de haz conectando la base con la plataforma central
            drawLine3D(new Vector3().x(0).y(0.1f).z(0), basePos, fade(dbColor, 0.5f));
        }

        // 4. LA PUERTA AL MUNDO PRINCIPAL (Return Portal Gate)
        drawReturnDoor3D(online);
    }

    private void drawReturnDoor3D(boolean online) {
        float doorZ = -11.0f;
        Color archFrameColor = new Color().r((byte)20).g((byte)25).b((byte)45).a((byte)255);
        Color neonColor = online ? SKYBLUE : RED;

        // Columnas laterales de la puerta
        Vector3 leftCol = new Vector3().x(-2.6f).y(3.0f).z(doorZ);
        Vector3 rightCol = new Vector3().x(2.6f).y(3.0f).z(doorZ);
        drawCube(leftCol, 0.7f, 6.0f, 0.7f, archFrameColor);
        drawCubeWires(leftCol, 0.72f, 6.02f, 0.72f, neonColor);
        drawCube(rightCol, 0.7f, 6.0f, 0.7f, archFrameColor);
        drawCubeWires(rightCol, 0.72f, 6.02f, 0.72f, neonColor);

        // Viga superior del arco
        Vector3 topBeam = new Vector3().x(0f).y(6.1f).z(doorZ);
        drawCube(topBeam, 6.0f, 0.6f, 0.8f, archFrameColor);
        drawCubeWires(topBeam, 6.02f, 0.62f, 0.82f, GOLD);

        // Umbral luminoso en el piso
        Vector3 floorStep = new Vector3().x(0f).y(0.08f).z(doorZ);
        drawCube(floorStep, 5.4f, 0.16f, 2.4f, fade(SKYBLUE, 0.35f));
        drawCubeWires(floorStep, 5.42f, 0.18f, 2.42f, WHITE);

        // Vórtice de energía giratorio en el centro del portal
        float pAngle = innerWorldTime * 55.0f;
        Vector3 portalCenter = new Vector3().x(0f).y(3.0f).z(doorZ);
        drawCircle3D(portalCenter, 2.2f, new Vector3().x(0).y(0).z(1), pAngle, fade(SKYBLUE, 0.5f));
        drawCircle3D(portalCenter, 1.5f, new Vector3().x(0).y(0).z(1), -pAngle * 1.4f, fade(MAGENTA, 0.65f));
        drawCircle3D(portalCenter, 0.8f, new Vector3().x(0).y(0).z(1), pAngle * 2.2f, fade(GOLD, 0.8f));

        // Letrero 3D flotante sobre la puerta
        drawBoundingBox(getDoorBoundingBox(), fade(neonColor, 0.25f));
    }

    private void drawInnerNodeWorldHUD() {
        if (expandedNode == null) return;
        int screenW = getScreenWidth();
        int screenH = getScreenHeight();
        boolean online = expandedNode.isOnline();

        // 1. Barra Superior con Identidad del Nodo y Botón para Atravesar la Puerta
        drawRectangle(0, 0, screenW, 56, fade(new Color().r((byte)10).g((byte)15).b((byte)30).a((byte)255), 0.92f));
        drawRectangleLines(0, 0, screenW, 56, online ? SKYBLUE : RED);

        drawText("🌐 MUNDO INTERIOR: SERVIDOR " + expandedNode.getId().toUpperCase(), 25, 12, 17, GOLD);
        drawLegibleText("Host: " + expandedNode.getHost() + ":" + expandedNode.getPort() + " | Rol: " + expandedNode.getRole() + " (" + expandedNode.getRaftState() + ")",
            25, 34, 11, SKYBLUE);

        // Botón destacado: Atravesar Puerta y Salir
        Rectangle exitBtnRec = new Rectangle().x(screenW - 410).y(10).width(390).height(36);
        boolean hoverExit = checkCollisionPointRec(getMousePosition(), exitBtnRec);
        drawRectangleRounded(exitBtnRec, 0.2f, 6, hoverExit ? GOLD : DARKBLUE);
        drawRectangleRoundedLines(exitBtnRec, 0.2f, 6, hoverExit ? WHITE : SKYBLUE);
        drawLegibleText("🚪 ATRAVESAR PUERTA - SALIR AL MUNDO PRINCIPAL [ESC]", screenW - 395, 20, 11, hoverExit ? BLACK : RAYWHITE);

        // 2. Panel Izquierdo: RECURSOS CONSUMIDOS EN ESTE NODO
        int px = 20, py = 70, pw = 380, ph = 490;
        Rectangle panelRec = new Rectangle().x(px).y(py).width(pw).height(ph);
        drawRectangleRounded(panelRec, 0.05f, 8, fade(new Color().r((byte)12).g((byte)16).b((byte)28).a((byte)255), 0.90f));
        drawRectangleRoundedLines(panelRec, 0.05f, 8, online ? SKYBLUE : RED);

        drawText("RECURSOS CONSUMIDOS EN EL NODO", px + 16, py + 14, 14, GOLD);
        drawLegibleText("Telemetría en Vivo de JettraStore Cluster", px + 16, py + 34, 11, DARKGRAY);

        int cy = py + 55;
        // Badge Estado
        Color badgeBg = online ? new Color().r((byte)20).g((byte)60).b((byte)30).a((byte)255) : new Color().r((byte)80).g((byte)20).b((byte)25).a((byte)255);
        Rectangle bRec = new Rectangle().x(px + 16).y(cy).width(pw - 32).height(26);
        drawRectangleRounded(bRec, 0.2f, 4, badgeBg);
        drawRectangleRoundedLines(bRec, 0.2f, 4, online ? LIME : RED);
        drawLegibleText(online ? "● EN LÍNEA - LATENCIA: " + expandedNode.getLatencyMs() + " ms" : "▲ FUERA DE SERVICIO (OFFLINE)",
            px + 26, cy + 6, 11, online ? LIME : RED);

        cy += 35;
        // Dictamen Policial
        drawRectangle(px + 16, cy, pw - 32, 42, fade(BLACK, 0.5f));
        drawRectangleLines(px + 16, cy, pw - 32, 42, online ? SKYBLUE : RED);
        drawLegibleText("🛡️ JettraStorePolice:", px + 22, cy + 4, 10, GOLD);
        drawLegibleText(expandedNode.getPoliceDiagnosis(), px + 22, cy + 20, 10, online ? RAYWHITE : RED);

        cy += 50;
        // Métricas de Memoria Heap
        long hUsed = expandedNode.getHeapUsedMb();
        long hMax = expandedNode.getHeapMaxMb();
        double satPct = expandedNode.getHeapSaturationPercent();
        drawLegibleText(String.format("• Memoria Heap JVM: %d MB / %d MB (%.1f%%)", hUsed, hMax, satPct), px + 16, cy, 11, RAYWHITE);
        cy += 16;
        int barW = pw - 32;
        int barH = 10;
        drawRectangle(px + 16, cy, barW, barH, DARKGRAY);
        Color barColor = satPct > 85 ? RED : (satPct > 70 ? YELLOW : LIME);
        int fillW = online ? (int)((satPct / 100.0) * barW) : 0;
        drawRectangle(px + 16, cy, Math.min(barW, Math.max(0, fillW)), barH, barColor);
        drawRectangleLines(px + 16, cy, barW, barH, WHITE);

        cy += 20;
        drawLegibleText("• Memoria Off-Heap (Panama FFM): " + expandedNode.getPanamaDirectMemMb() + " MB directos", px + 16, cy, 11, RAYWHITE);
        cy += 18;
        drawLegibleText("• Virtual Threads (Loom): " + expandedNode.getActiveVirtualThreads() + " hilos | " + expandedNode.getCpuCores() + " Cores CPU", px + 16, cy, 11, RAYWHITE);
        cy += 18;
        drawLegibleText(String.format("• Almacenamiento LSM: MemTable %d MB | SSTables %.1f MB",
            expandedNode.getMemTableMb(), (expandedNode.getDiskSSTablesBytes() / (1024.0 * 1024.0))), px + 16, cy, 11, RAYWHITE);
        cy += 18;
        drawLegibleText("• Quórum Raft: Sincronizado vía JettraDriver", px + 16, cy, 11, online ? SKYBLUE : RED);

        cy += 30;
        // Botón desconectar / reconectar
        String tText = online ? "DESCONECTAR (SIMULAR CAÍDA)" : "RECONECTAR (ACTIVAR SERVICIO)";
        Color tColor = online ? RED : LIME;
        Rectangle toggleBtn = new Rectangle().x(px + 16).y(cy).width(pw - 32).height(32);
        boolean hoverToggle = checkCollisionPointRec(getMousePosition(), toggleBtn);
        drawRectangleRounded(toggleBtn, 0.2f, 4, hoverToggle ? WHITE : tColor);
        drawLegibleText(tText, px + 35, cy + 8, 11, hoverToggle ? BLACK : WHITE);

        // 3. Resumen de Bases de Datos Flotantes (Proyectadas en 2D sobre los pedestales)
        for (DatabaseInfo3D db : expandedNode.getDatabases()) {
            Vector3 label3D = new Vector3().x(db.getX()).y(db.getY() + 3.4f).z(db.getZ());
            Vector2 screenPos = getWorldToScreen(label3D, camera);
            if (screenPos.x() > 0 && screenPos.x() < screenW && screenPos.y() > 0 && screenPos.y() < screenH) {
                int lx = (int)screenPos.x() - 100;
                int ly = (int)screenPos.y() - 25;
                drawRectangle(lx, ly, 200, 48, fade(BLACK, 0.82f));
                drawRectangleLines(lx, ly, 200, 48, new Color().r((byte)db.getR()).g((byte)db.getG()).b((byte)db.getB()).a((byte)255));
                drawLegibleText(db.getId(), lx + 8, ly + 5, 11, GOLD);
                drawLegibleText(db.getTotalObjects() + " objs | " + db.getSizeFormatted(), lx + 8, ly + 20, 10, RAYWHITE);
                drawLegibleText(online ? "● " + db.getStatus() : "▲ DESCONECTADA", lx + 8, ly + 33, 9, online ? LIME : RED);
            }
        }

        // 4. Puerta al Mundo Principal (Etiqueta 2D sobre la puerta 3D)
        Vector3 doorLabelPos = new Vector3().x(0f).y(6.8f).z(-11.0f);
        Vector2 dScreen = getWorldToScreen(doorLabelPos, camera);
        if (dScreen.x() > 0 && dScreen.x() < screenW && dScreen.y() > 0 && dScreen.y() < screenH) {
            int dx = (int)dScreen.x() - 150;
            int dy = (int)dScreen.y() - 15;
            drawRectangle(dx, dy, 300, 32, fade(DARKBLUE, 0.88f));
            drawRectangleLines(dx, dy, 300, 32, GOLD);
            drawLegibleText("🚪 PUERTA AL MUNDO PRINCIPAL (CLIC / ESC)", dx + 18, dy + 8, 11, GOLD);
        }

        // 5. Barra Inferior de Información y Ayuda
        drawRectangle(0, screenH - 42, screenW, 42, fade(BLACK, 0.85f));
        drawRectangleLines(0, screenH - 42, screenW, 42, DARKGRAY);
        drawLegibleText("ℹ️ Haz clic sobre cualquier Base de Datos 3D para inspeccionar sus Buckets | Haz clic en la Puerta 3D o presiona ESC para regresar",
            30, screenH - 28, 11, RAYWHITE);
    }

    private void drawDatabaseDetailModal() {
        if (selectedDatabase == null) return;
        int screenW = getScreenWidth();
        int screenH = getScreenHeight();

        int dw = 520, dh = 460;
        int dx = (screenW - dw) / 2 + 100;
        int dy = (screenH - dh) / 2;

        Rectangle dRec = new Rectangle().x(dx).y(dy).width(dw).height(dh);
        drawRectangleRounded(dRec, 0.06f, 8, fade(new Color().r((byte)14).g((byte)18).b((byte)32).a((byte)255), 0.95f));
        drawRectangleRoundedLines(dRec, 0.06f, 8, GOLD);

        drawText("FICHA TÉCNICA DE BASE DE DATOS", dx + 24, dy + 18, 16, GOLD);
        drawLegibleText(selectedDatabase.getTitle() + " (" + selectedDatabase.getId() + ")", dx + 24, dy + 42, 12, SKYBLUE);

        // Botón Cerrar
        if (guiButton(dx + dw - 42, dy + 15, 28, 25, "X", RED)) {
            selectedDatabase = null;
            return;
        }

        int cy = dy + 70;
        drawLegibleText("• Descripción: " + selectedDatabase.getDescription(), dx + 24, cy, 11, RAYWHITE);
        cy += 20;
        drawLegibleText("• Motor de Datos: " + selectedDatabase.getEngineType(), dx + 24, cy, 11, RAYWHITE);
        cy += 20;
        drawLegibleText("• Objetos Totales: " + String.format("%,d", selectedDatabase.getTotalObjects()) + " registros", dx + 24, cy, 11, RAYWHITE);
        cy += 20;
        drawLegibleText("• Tamaño en Almacenamiento: " + selectedDatabase.getSizeFormatted(), dx + 24, cy, 11, RAYWHITE);
        cy += 20;
        drawLegibleText("• Operaciones I/O: " + String.format("%,d", selectedDatabase.getIops()) + " IOPS en tiempo real", dx + 24, cy, 11, RAYWHITE);
        cy += 20;
        drawLegibleText("• Estado de Replicación: " + selectedDatabase.getStatus(), dx + 24, cy, 11, LIME);

        cy += 30;
        drawText("BUCKETS ESPECIALIZADOS (" + selectedDatabase.getBucketsCount() + "):", dx + 24, cy, 13, GOLD);
        cy += 22;

        int row = 0;
        for (String b : selectedDatabase.getBuckets()) {
            drawRectangle(dx + 24, cy + row * 22, dw - 48, 20, fade(BLACK, 0.4f));
            drawRectangleLines(dx + 24, cy + row * 22, dw - 48, 20, DARKBLUE);
            drawLegibleText("  ▸ " + b, dx + 30, cy + row * 22 + 4, 11, SKYBLUE);
            row++;
        }

        cy += row * 22 + 15;
        if (guiButton(dx + 150, cy, 220, 32, "CERRAR DETALLE [ESC]", DARKGRAY)) {
            selectedDatabase = null;
        }
    }

    private void drawNodeExpansionEffect3D() {
        if (expandedNode == null) return;
        float progress = 1.0f - (transitionTimer / 0.85f);
        float radius = progress * 14.0f;
        Vector3 center = new Vector3().x(expandedNode.getX()).y(expandedNode.getY() + 2.5f).z(expandedNode.getZ());
        drawSphereWires(center, radius, 12, 12, fade(SKYBLUE, 0.7f));
        drawCircle3D(center, radius * 1.2f, new Vector3().x(0).y(1).z(0), progress * 180.0f, fade(GOLD, 0.8f));
    }

    private void drawExitingPortalEffect3D() {
        float progress = 1.0f - (transitionTimer / 0.85f);
        float radius = progress * 15.0f;
        Vector3 portalCenter = new Vector3().x(0f).y(3.0f).z(-11.0f);
        drawSphereWires(portalCenter, radius, 14, 14, fade(GOLD, 0.75f));
        drawCircle3D(portalCenter, radius * 1.2f, new Vector3().x(0).y(0).z(1), progress * 360.0f, fade(SKYBLUE, 0.9f));
    }

    private void drawExpandingTransitionOverlay() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();
        float alpha = (1.0f - (transitionTimer / 0.85f));
        drawRectangle(0, 0, sw, sh, fade(BLACK, alpha * 0.75f));
        drawText("EXPANDIENDO MUNDO INTERIOR DEL NODO...", sw / 2 - 240, sh / 2 - 20, 22, GOLD);
        drawLegibleText("Iniciando espacio cuántico y bases de datos alojadas...", sw / 2 - 180, sh / 2 + 15, 13, SKYBLUE);
    }

    private void drawExitingTransitionOverlay() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();
        float alpha = (1.0f - (transitionTimer / 0.85f));
        drawRectangle(0, 0, sw, sh, fade(BLACK, alpha * 0.75f));
        drawText("ATRAVESANDO PUERTA DIMENSIONAL...", sw / 2 - 210, sh / 2 - 20, 22, SKYBLUE);
        drawLegibleText("Regresando a la vista macro del clúster...", sw / 2 - 140, sh / 2 + 15, 13, GOLD);
    }

    private void loadSelectedProfileIntoForm() {
        if (policeMonitor == null) return;
        ConnectionProfile cur = policeMonitor.getCurrentProfile();
        if (cur != null) {
            formConnId = cur.getId();
            formConnName = cur.getName();
            formConnUrl = cur.getUrl();
            formConnUsername = cur.getUsername();
            formConnPassword = cur.getPassword();
            formConnIsDefault = cur.isDefault();
            selectedProfileId = cur.getId();
        }
    }

    private HumanEntity findEntityByName(String name) {
        if (name == null) return null;
        for (HumanEntity e : entities) {
            if (name.equalsIgnoreCase(e.name)) return e;
        }
        return null;
    }

    private void drawInteractiveInput(int x, int y, int w, int h, String value, int fieldId, boolean isPassword) {
        Rectangle rec = new Rectangle().x(x).y(y).width(w).height(h);
        Vector2 mouse = getMousePosition();
        boolean hovered = checkCollisionPointRec(mouse, rec);
        boolean isActive = (activeConnField == fieldId);

        if (hovered && isMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            activeConnField = fieldId;
        }

        Color bg = isActive ? new Color().r((byte)30).g((byte)45).b((byte)75).a((byte)255)
                            : (hovered ? new Color().r((byte)25).g((byte)32).b((byte)52).a((byte)255)
                                       : new Color().r((byte)15).g((byte)20).b((byte)35).a((byte)255));

        drawRectangleRounded(rec, 0.15f, 4, bg);
        drawRectangleRoundedLines(rec, 0.15f, 4, isActive ? GOLD : (hovered ? SKYBLUE : DARKGRAY));

        String display = isPassword ? "•".repeat(value.length()) : value;
        if (isActive && ((int)(worldTime * 2) % 2 == 0)) {
            display += "_";
        }
        drawLegibleText(display, x + 8, y + 8, 12, RAYWHITE);
    }

    private void drawConnectionManagerModal() {
        int sw = getScreenWidth();
        int sh = getScreenHeight();

        // Fondo oscurecido semi-transparente
        drawRectangle(0, 0, sw, sh, fade(BLACK, 0.65f));

        int mw = 840;
        int mh = 530;
        int mx = (sw - mw) / 2;
        int my = (sh - mh) / 2;

        // Ventana principal con estilo ciberespacial y borde dorado
        drawRectangle(mx, my, mw, mh, new Color().r((byte)18).g((byte)22).b((byte)35).a((byte)250));
        drawRectangleLines(mx, my, mw, mh, GOLD);

        // Barra de Título
        drawRectangle(mx, my, mw, 45, new Color().r((byte)26).g((byte)32).b((byte)50).a((byte)255));
        drawLine(mx, my + 45, mx + mw, my + 45, GOLD);
        drawLegibleText("🔌 GESTIÓN DE CONEXIONES JETTRASTORE (TIEMPO REAL)", mx + 20, my + 14, 18, GOLD);

        ConnectionProfile activeProfile = (policeMonitor != null) ? policeMonitor.getCurrentProfile() : null;
        String activeBadge = (activeProfile != null) ? "[ACTIVA: " + activeProfile.getName() + "]" : "[DESCONECTADO]";
        drawLegibleText(activeBadge, mx + mw - measureLegibleText(activeBadge, 13) - 20, my + 16, 13, LIME);

        // Divisor vertical
        int col1W = 340;
        int col2X = mx + col1W + 25;
        int col2W = mw - col1W - 45;
        drawLine(mx + col1W + 10, my + 55, mx + col1W + 10, my + mh - 55, fade(GRAY, 0.4f));

        // --- COLUMNA IZQUIERDA: LISTA DE CONEXIONES REGISTRADAS ---
        drawLegibleText("LISTA DE CONEXIONES REGISTRADAS", mx + 20, my + 55, 13, SKYBLUE);
        drawLegibleText("Seleccione una conexión para editar o conectar:", mx + 20, my + 72, 10, LIGHTGRAY);

        List<ConnectionProfile> profiles = (policeMonitor != null) ? policeMonitor.getConnectionManager().getProfiles() : List.of();
        int listY = my + 92;
        int cardH = 68;

        for (int i = 0; i < Math.min(5, profiles.size()); i++) {
            ConnectionProfile p = profiles.get(i);
            boolean isSelected = p.getId().equals(selectedProfileId);
            boolean isCurrentActive = (activeProfile != null && p.getId().equals(activeProfile.getId()));

            Rectangle cardRec = new Rectangle().x(mx + 20).y(listY + (i * (cardH + 6))).width(col1W - 20).height(cardH);
            Vector2 mouse = getMousePosition();
            boolean hovered = checkCollisionPointRec(mouse, cardRec);

            Color cardBg = isSelected ? new Color().r((byte)35).g((byte)55).b((byte)90).a((byte)240)
                                      : (hovered ? new Color().r((byte)28).g((byte)36).b((byte)58).a((byte)220)
                                                 : new Color().r((byte)22).g((byte)28).b((byte)45).a((byte)200));

            drawRectangleRounded(cardRec, 0.15f, 6, cardBg);
            drawRectangleRoundedLines(cardRec, 0.15f, 6, isSelected ? GOLD : (hovered ? SKYBLUE : fade(GRAY, 0.4f)));

            // Nombre y URL
            drawLegibleText(p.getName(), (int)cardRec.x() + 10, (int)cardRec.y() + 8, 14, isSelected ? GOLD : RAYWHITE);
            drawLegibleText("URL: " + p.getUrl(), (int)cardRec.x() + 10, (int)cardRec.y() + 27, 11, SKYBLUE);
            drawLegibleText("User: " + p.getUsername(), (int)cardRec.x() + 10, (int)cardRec.y() + 45, 10, LIGHTGRAY);

            // Badges
            int badgeX = (int)(cardRec.x() + cardRec.width() - 95);
            if (p.isDefault()) {
                drawRectangle(badgeX, (int)cardRec.y() + 6, 85, 16, fade(GOLD, 0.25f));
                drawRectangleLines(badgeX, (int)cardRec.y() + 6, 85, 16, GOLD);
                drawLegibleText("★ DEFAULT", badgeX + 8, (int)cardRec.y() + 8, 10, GOLD);
            }
            if (isCurrentActive) {
                int activeY = (int)cardRec.y() + (p.isDefault() ? 26 : 6);
                drawRectangle(badgeX, activeY, 85, 16, fade(LIME, 0.25f));
                drawRectangleLines(badgeX, activeY, 85, 16, LIME);
                drawLegibleText("● EN LÍNEA", badgeX + 8, activeY + 2, 10, LIME);
            }

            // Clic para seleccionar
            if (hovered && isMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                selectedProfileId = p.getId();
                formConnId = p.getId();
                formConnName = p.getName();
                formConnUrl = p.getUrl();
                formConnUsername = p.getUsername();
                formConnPassword = p.getPassword();
                formConnIsDefault = p.isDefault();
                activeConnField = 0;
            }
        }

        // Botón Nueva Conexión
        if (guiButton(mx + 20, my + mh - 50, col1W - 20, 32, "+ NUEVA CONEXIÓN", new Color().r((byte)30).g((byte)120).b((byte)80).a((byte)255))) {
            formConnId = "";
            formConnName = "Nuevo Clúster JettraStore";
            formConnUrl = "tcp://127.0.0.1:8765";
            formConnUsername = "admin";
            formConnPassword = "";
            formConnIsDefault = profiles.isEmpty();
            selectedProfileId = "";
            activeConnField = 1;
            connStatusFeedback = "Formulario listo para nueva conexión.";
            connStatusFeedbackTimer = 3.0f;
        }

        // --- COLUMNA DERECHA: FORMULARIO DE DETALLES Y EDICIÓN ---
        drawLegibleText("DATOS DE LA CONEXIÓN (URL, CREDENCIALES)", col2X, my + 55, 13, GOLD);
        drawLegibleText("Haga clic en un campo o use TAB para editar:", col2X, my + 72, 10, LIGHTGRAY);

        int formY = my + 92;
        int inputH = 30;

        // Campo 1: Nombre
        drawLegibleText("Nombre Descriptivo:", col2X, formY, 11, RAYWHITE);
        drawInteractiveInput(col2X, formY + 16, col2W, inputH, formConnName, 1, false);

        // Campo 2: URL
        formY += 56;
        drawLegibleText("URL de la Base de Datos (ej. tcp://127.0.0.1:8765 o jettra://localhost:9091):", col2X, formY, 11, RAYWHITE);
        drawInteractiveInput(col2X, formY + 16, col2W, inputH, formConnUrl, 2, false);

        // Campo 3: Username
        formY += 56;
        drawLegibleText("Usuario (Username):", col2X, formY, 11, RAYWHITE);
        drawInteractiveInput(col2X, formY + 16, col2W, inputH, formConnUsername, 3, false);

        // Campo 4: Password
        formY += 56;
        drawLegibleText("Contraseña (Password):", col2X, formY, 11, RAYWHITE);
        drawInteractiveInput(col2X, formY + 16, col2W, inputH, formConnPassword, 4, true);

        // Checkbox: Default
        formY += 56;
        Rectangle chkRec = new Rectangle().x(col2X).y(formY).width(20).height(20);
        if (checkCollisionPointRec(getMousePosition(), chkRec) && isMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            formConnIsDefault = !formConnIsDefault;
        }
        drawRectangleRounded(chkRec, 0.2f, 4, formConnIsDefault ? GOLD : DARKGRAY);
        drawRectangleRoundedLines(chkRec, 0.2f, 4, WHITE);
        if (formConnIsDefault) {
            drawLegibleText("✓", col2X + 4, formY + 2, 14, BLACK);
        }
        drawLegibleText("Usar como conexión predeterminada al iniciar la aplicación", col2X + 28, formY + 3, 11, formConnIsDefault ? GOLD : RAYWHITE);

        // Botones de acción del formulario
        int btnY = my + mh - 50;

        // Botón 1: Guardar
        if (guiButton(col2X, btnY, 95, 32, "💾 GUARDAR", LIME)) {
            if (policeMonitor != null) {
                String idToSave = (formConnId != null && !formConnId.isEmpty()) ? formConnId : "conn_" + System.currentTimeMillis();
                ConnectionProfile toSave = new ConnectionProfile(
                    idToSave, formConnName, formConnUrl, formConnUsername, formConnPassword, formConnIsDefault
                );
                policeMonitor.getConnectionManager().saveOrUpdate(toSave);
                selectedProfileId = idToSave;
                formConnId = idToSave;
                connStatusFeedback = "¡Conexión '" + formConnName + "' guardada!";
                connStatusFeedbackTimer = 4.0f;
                triggerWorldEvent("Perfil de conexión guardado: " + formConnName, 50, 255, 100);
            }
        }

        // Botón 2: Conectar Ahora (Cambiar conexión activa en tiempo real)
        if (guiButton(col2X + 105, btnY, 115, 32, "⚡ CONECTAR", SKYBLUE)) {
            if (policeMonitor != null) {
                String idToSave = (formConnId != null && !formConnId.isEmpty()) ? formConnId : "conn_" + System.currentTimeMillis();
                ConnectionProfile toConn = new ConnectionProfile(
                    idToSave, formConnName, formConnUrl, formConnUsername, formConnPassword, formConnIsDefault
                );
                policeMonitor.getConnectionManager().saveOrUpdate(toConn);
                policeMonitor.switchConnection(toConn);
                selectedProfileId = idToSave;
                formConnId = idToSave;
                resetWorldWithJettraStore();
                connStatusFeedback = "¡Conectado a " + toConn.getName() + "!";
                connStatusFeedbackTimer = 4.0f;
                triggerWorldEvent("Conexión conmutada en tiempo real a " + toConn.getName() + " (" + toConn.getUrl() + ")", 0, 220, 255);
            }
        }

        // Botón 3: Predeterminada
        if (guiButton(col2X + 230, btnY, 120, 32, "★ DEFAULT", GOLD)) {
            if (policeMonitor != null && formConnId != null && !formConnId.isEmpty()) {
                policeMonitor.getConnectionManager().setDefault(formConnId);
                formConnIsDefault = true;
                connStatusFeedback = "Marcada como predeterminada.";
                connStatusFeedbackTimer = 4.0f;
                triggerWorldEvent("Conexión predeterminada establecida: " + formConnName, 255, 215, 0);
            }
        }

        // Botón 4: Eliminar
        if (guiButton(col2X + 360, btnY, 85, 32, "🗑️ BORRAR", RED)) {
            if (policeMonitor != null && formConnId != null && !formConnId.isEmpty()) {
                policeMonitor.getConnectionManager().delete(formConnId);
                loadSelectedProfileIntoForm();
                connStatusFeedback = "Conexión eliminada.";
                connStatusFeedbackTimer = 4.0f;
                triggerWorldEvent("Conexión eliminada del registro.", 255, 100, 100);
            }
        }

        // Botón Cerrar (Esquina superior derecha)
        Rectangle closeBtnRec = new Rectangle().x(mx + mw - 38).y(my + 10).width(26).height(26);
        boolean closeHover = checkCollisionPointRec(getMousePosition(), closeBtnRec);
        drawRectangleRounded(closeBtnRec, 0.2f, 4, closeHover ? RED : DARKGRAY);
        drawLegibleText("X", (int)closeBtnRec.x() + 8, (int)closeBtnRec.y() + 5, 14, RAYWHITE);
        if (closeHover && isMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            showConnectionModal = false;
        }

        // Mensaje de Feedback
        if (connStatusFeedbackTimer > 0) {
            connStatusFeedbackTimer -= getFrameTime();
            drawRectangle(col2X, my + mh - 90, col2W, 26, new Color().r((byte)20).g((byte)45).b((byte)30).a((byte)220));
            drawRectangleLines(col2X, my + mh - 90, col2W, 26, LIME);
            drawLegibleText("✔ " + connStatusFeedback, col2X + 10, my + mh - 84, 11, LIME);
        }
    }

}
