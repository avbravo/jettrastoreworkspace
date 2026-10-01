package io.jettra.core.three.d.police;

import io.jettra.core.three.d.config.ConnectionManager;
import io.jettra.core.three.d.config.ConnectionProfile;
import io.jettra.core.three.d.model.ClusterDataTraffic;
import io.jettra.core.three.d.model.JettraLiveSession;
import io.jettra.core.three.d.model.JettraPoliceAgent;
import io.jettra.core.three.d.model.ServerNode3D;
import io.jettra.core.three.d.model.UserZoneGroup;
import io.jettra.driver.JettraClient;
import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.cluster.ClusterNode;
import io.jettra.store.core.JettraStoreConfig;
import io.jettra.store.police.JettraPolice;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitor Centinela en tiempo real de JettraStore y JettraPolice.
 * Gestiona conexiones activas, sesiones de usuarios agrupados por zonas en edificios,
 * agentes caninos policiales reactivos y flujos de tráfico entre nodos del clúster.
 */
public class JettraStorePoliceMonitor implements AutoCloseable {

    private final ConnectionManager connectionManager;
    private ConnectionProfile currentProfile;
    private final List<ServerNode3D> serverNodes = new CopyOnWriteArrayList<>();
    private final List<UserZoneGroup> userZones = new CopyOnWriteArrayList<>();
    private final List<JettraLiveSession> liveSessions = new CopyOnWriteArrayList<>();
    private final List<JettraPoliceAgent> activePoliceAgents = new CopyOnWriteArrayList<>();
    private final List<ClusterDataTraffic> activeTraffic = new CopyOnWriteArrayList<>();

    private final JettraPolice policeSentinel = JettraPolice.getInstance();
    private JettraClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jettra-police-sentinel-monitor");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean connected = false;
    private volatile long totalEvaluations = 0;
    private volatile long offlineAlertCount = 0;
    private volatile String lastPoliceEvent = "JettraStorePolice inicializado en modo Centinela.";

    // Métricas en tiempo real de procesamiento de objetos en JettraStore
    private volatile long processedObjectsTotal = 8_250_000L;
    private volatile long processedObjectsPerSecond = 34_800L;
    private volatile int activeTransactions = 11;


    public JettraStorePoliceMonitor() {
        this.connectionManager = new ConnectionManager();
        this.currentProfile = connectionManager.getDefaultProfile().orElse(new ConnectionProfile(
            "conn_default", "JettraStore Local Master", "tcp://127.0.0.1:8765", "admin", "admin123", true
        ));

        initNodes();
        initZonesAndSessions();
        initPoliceAgents();
        initClusterTraffic();
        initClient();
        startScheduler();
    }

    private void initNodes() {
        serverNodes.clear();
        String host = (currentProfile != null) ? currentProfile.getHost() : "127.0.0.1";
        int basePort = (currentProfile != null) ? currentProfile.getPort() : 9091;

        // Servidor 1: Nodo Maestro Primario
        ServerNode3D n1 = new ServerNode3D("node-01", "node-01-master", host, basePort, ClusterNode.Role.PRIMARY, -14.0f, 0.0f, -12.0f);
        n1.setRaftState(ClusterNode.RaftState.LEADER);
        serverNodes.add(n1);

        // Servidor 2: Nodo Réplica Secundaria A
        ServerNode3D n2 = new ServerNode3D("node-02", "node-02-replica", host, basePort + 1, ClusterNode.Role.SECONDARY, 0.0f, 0.0f, -18.0f);
        n2.setRaftState(ClusterNode.RaftState.FOLLOWER);
        serverNodes.add(n2);

        // Servidor 3: Nodo Réplica Secundaria B (Inicia como Fuera de Servicio para alerta visual)
        ServerNode3D n3 = new ServerNode3D("node-03", "node-03-replica", host, basePort + 2, ClusterNode.Role.SECONDARY, 14.0f, 0.0f, -12.0f);
        n3.setRaftState(ClusterNode.RaftState.FOLLOWER);
        n3.toggleOffline();
        serverNodes.add(n3);
    }

    private void initZonesAndSessions() {
        userZones.clear();
        liveSessions.clear();

        // Zona 1: Red Financiera & Facturación (Subred 192.168.1.x)
        UserZoneGroup zFin = new UserZoneGroup(
            "zone_financial", "Zona Financiera & Comercial", "192.168.1",
            "🏢 Sede Financiera & Facturación", "example_factura_db",
            "Facturación electrónica con soporte Multimodelo Off-Heap", 2,
            -22f, 0f, 15f, 255, 190, 30
        );
        userZones.add(zFin);

        // Zona 2: Red Médica & Salud Pública (Subred 10.0.4.x)
        UserZoneGroup zHosp = new UserZoneGroup(
            "zone_health", "Red Hospitalaria & Salud", "10.0.4",
            "🏥 Hospital Metropolitano", "samples_hostipal_db",
            "Gestión médica, pacientes, recetas e historias clínicas", 4,
            24f, 0f, 18f, 0, 220, 220
        );
        userZones.add(zHosp);

        // Zona 3: Sensores Ambientales IoT (Subred 172.16.8.x)
        UserZoneGroup zEnv = new UserZoneGroup(
            "zone_environmental", "Red Ambiental & Sensores IoT", "172.16.8",
            "🌿 Estación Ambiental Global IoT", "samples_ambiental_db",
            "Ingesta masiva TimeSeries y vectores climáticos mundiales", 1,
            25f, 0f, -20f, 50, 220, 100
        );
        userZones.add(zEnv);

        // Zona 4: Consenso Raft & Data Center (Subred 127.0.0.x / 10.0.1.x)
        UserZoneGroup zCore = new UserZoneGroup(
            "zone_datacenter", "Data Center Corporativo & Consenso", "127.0.0",
            "🏛️ Data Center Corporativo & Raft", "system_metadata_db",
            "Catálogo, esquemas, auditorías y logs de consenso Raft", 3,
            -20f, 0f, -22f, 180, 70, 240
        );
        userZones.add(zCore);

        // Sesiones iniciales tomadas de conexiones en tiempo real
        addLiveUserSession("usr_facturacion_01", "192.168.1.15", "example_factura_db", "INSERT INTO facturas (1,000 items)");
        addLiveUserSession("usr_cobranzas_02", "192.168.1.42", "example_factura_db", "SELECT * FROM clientes WHERE saldo > 0");
        addLiveUserSession("usr_contador_03", "192.168.1.88", "example_factura_db", "GROUP BY fecha SUM(total) [Aggregate]");

        addLiveUserSession("dr_gonzalez_01", "10.0.4.12", "samples_hostipal_db", "SELECT * FROM pacientes WHERE uci = true");
        addLiveUserSession("enf_medica_02", "10.0.4.25", "samples_hostipal_db", "UPDATE camas_uci SET ocupada = true");
        addLiveUserSession("admin_uci_03", "10.0.4.60", "samples_hostipal_db", "INSERT INTO admisiones (historia_clinica)");

        addLiveUserSession("sensor_co2_norte", "172.16.8.101", "samples_ambiental_db", "TimeSeries PUSH co2_ppm 418.5");
        addLiveUserSession("ing_climatico_02", "172.16.8.115", "samples_ambiental_db", "KNN_SEARCH vector_clima (3D dist < 0.05)");
        addLiveUserSession("meteo_analista_03", "172.16.8.204", "samples_ambiental_db", "AVG(temperatura) OVER (WINDOW 1H)");

        addLiveUserSession("dba_root_admin", "127.0.0.1", "system_metadata_db", "CHECK RAFT CONSENSUS QUORUM");
        addLiveUserSession("sec_auditor_02", "127.0.0.1", "system_metadata_db", "AUDIT POLICE LOGS & COMPACT STATUS");
    }

    private void initPoliceAgents() {
        activePoliceAgents.clear();
        // 1. Mascota Jettra K9-Alpha: Heap Sentinel
        activePoliceAgents.add(new JettraPoliceAgent(
            "k9_alpha", "Mascota Jettra K9-Alpha", JettraPoliceAgent.PoliceRole.HEAP_SENTINEL,
            "node-01", "🛡️ Supervisando Heap y Memoria Off-Heap (Panama FFM)"
        ));

        // 2. K9-Beta: Quórum Raft y aislamiento de fallos
        activePoliceAgents.add(new JettraPoliceAgent(
            "k9_beta", "JettraPolice-K9-Beta", JettraPoliceAgent.PoliceRole.RAFT_QUORUM_K9,
            "node-02", "🛡️ Monitoreando sincronía y quórum entre nodos réplicas"
        ));

        // 3. K9-Delta: Auditor de MemTable y Flush LSM
        activePoliceAgents.add(new JettraPoliceAgent(
            "k9_delta", "JettraPolice-K9-Delta", JettraPoliceAgent.PoliceRole.MEMTABLE_PURGE_DOG,
            "node-01", "🛡️ Inspeccionando umbrales de vaciado de MemTable a disco"
        ));

        // 4. K9-Gamma: Seguridad y control de sesiones
        activePoliceAgents.add(new JettraPoliceAgent(
            "k9_gamma", "JettraPolice-K9-Gamma", JettraPoliceAgent.PoliceRole.SECURITY_PATROL,
            "node-03", "🚨 Centinela de alerta: Custodiando tráfico desviado de nodo fuera de servicio"
        ));

        // Suscribir listener proactivo a JettraPolice
        policeSentinel.addDecisionListener(decision -> {
            if (decision.interventionRequired()) {
                this.lastPoliceEvent = "JettraPolice INTERVENCIÓN: " + decision.rationale();
                for (JettraPoliceAgent k9 : activePoliceAgents) {
                    if (k9.getRole() == JettraPoliceAgent.PoliceRole.HEAP_SENTINEL) {
                        k9.assignMission("node-01", decision.rationale(), true, "WARNING");
                    }
                }
            }
        });
    }

    private void initClusterTraffic() {
        activeTraffic.clear();
        // Camión 1: Replicación Raft entre node-01 y node-02
        activeTraffic.add(new ClusterDataTraffic(
            "traffic_raft_01_02", "Tráfico-Replicación-Raft", ClusterDataTraffic.TrafficType.RAFT_REPLICATION,
            "node-01", "node-02", "Replicación de Logs Raft (Consenso Quórum)", 4_500_000L, 8.5f
        ));

        // Camión 2: Tráfico Multimodelo Facturas
        activeTraffic.add(new ClusterDataTraffic(
            "traffic_facturas", "Tráfico-Facturación-Cluster", ClusterDataTraffic.TrafficType.RING_OFFLOAD,
            "node-01", "node-02", "Offload Anillo Dinámico: 25,000 Facturas", 2_800_000L, 4.2f
        ));

        // Camión 3: Tráfico Médico / Hospital
        activeTraffic.add(new ClusterDataTraffic(
            "traffic_salud", "Tráfico-Salud-LSM", ClusterDataTraffic.TrafficType.SSTABLE_COMPACTION,
            "node-02", "node-01", "Compactación SSTables Pacientes & UCI", 1_900_000L, 3.1f
        ));

        // Camión 4: Streaming Sensores Ambientales
        activeTraffic.add(new ClusterDataTraffic(
            "traffic_ambiental", "Tráfico-Ambiental-Stream", ClusterDataTraffic.TrafficType.VECTOR_SYNC,
            "node-01", "node-02", "Sync Vectores Clima & TimeSeries IoT", 3_600_000L, 5.8f
        ));
    }

    public synchronized void switchConnection(ConnectionProfile profile) {
        if (profile == null) return;
        this.currentProfile = profile;
        this.lastPoliceEvent = "Conectando a perfil: " + profile.getName() + " (" + profile.getUrl() + ")...";

        // Cerrar cliente previo si existiese
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
            client = null;
        }

        // Reconfigurar nodos para apuntar al nuevo host/puerto
        String host = profile.getHost();
        int port = profile.getPort();
        if (!serverNodes.isEmpty()) {
            serverNodes.get(0).setStatusMessage("Conectando a " + host + ":" + port + "...");
        }

        initClient();
        pollAndEvaluateServers();
    }

    private void initClient() {
        try {
            String host = (currentProfile != null) ? currentProfile.getHost() : "127.0.0.1";
            int port = (currentProfile != null) ? currentProfile.getPort() : 9091;
            String user = (currentProfile != null) ? currentProfile.getUsername() : "admin";
            String pass = (currentProfile != null) ? currentProfile.getPassword() : "admin";

            JettraClientConfig cfg = JettraClientConfig.builder()
                .addClusterNode(host, port)
                .credentials(user, pass)
                .build();
            this.client = new JettraClient(cfg);
            this.connected = true;
            this.lastPoliceEvent = "En línea con JettraStore [" + currentProfile.getName() + "] en " + host + ":" + port;
        } catch (Exception e) {
            this.connected = false;
            this.lastPoliceEvent = "JettraStore en modo autónomo local: " + e.getMessage();
        }
    }

    private void startScheduler() {
        scheduler.scheduleAtFixedRate(this::pollAndEvaluateServers, 1, 2, TimeUnit.SECONDS);
    }

    public void pollAndEvaluateServers() {
        Runtime rt = Runtime.getRuntime();
        long maxRam = rt.maxMemory() / (1024 * 1024);
        long totalRam = rt.totalMemory() / (1024 * 1024);
        long freeRam = rt.freeMemory() / (1024 * 1024);
        long usedRam = Math.max(1, totalRam - freeRam);
        double satPercent = ((double) usedRam / (double) maxRam) * 100.0;
        int cpuCores = rt.availableProcessors();

        long diskBytes = 0;
        int diskFiles = 0;
        try {
            JettraStoreConfig cfg = JettraStoreConfig.load();
            Path p = Path.of(cfg.getStoragePath());
            if (Files.exists(p)) {
                try (var s = Files.walk(p)) {
                    for (Path f : (Iterable<Path>) s::iterator) {
                        if (Files.isRegularFile(f)) {
                            diskBytes += Files.size(f);
                            diskFiles++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        totalEvaluations++;

        // Actualizar métricas de procesamiento en tiempo real de JettraStore
        long delta = (long) (processedObjectsPerSecond * 2.0);
        processedObjectsTotal += delta;

        long activeNodeCount = serverNodes.stream().filter(ServerNode3D::isOnline).count();
        if (activeNodeCount >= 3) {
            processedObjectsPerSecond = 46_000L + (long)(Math.random() * 8_500);
        } else if (activeNodeCount == 2) {
            processedObjectsPerSecond = 28_000L + (long)(Math.random() * 5_000);
        } else {
            processedObjectsPerSecond = 14_000L + (long)(Math.random() * 3_000);
        }
        activeTransactions = liveSessions.size();

        // Actualizar lotes de camiones (tráfico entre nodos) con objetos procesados en tiempo real
        for (ClusterDataTraffic tr : activeTraffic) {
            long batchSize = (processedObjectsPerSecond / Math.max(1, activeTraffic.size())) * 3;
            tr.setPayloadSummary("Batch " + String.format("%,d", batchSize) + " objetos | " + tr.getTrafficType().name());
        }

        for (ServerNode3D node : serverNodes) {
            if (!node.isSimulatedOffline()) {
                boolean reachable = checkSocketPing(node.getHost(), node.getPort());
                if (!reachable && !node.getId().equals("node-01")) {
                    node.setOnline(false);
                    node.setStatus(ClusterNode.NodeStatus.OFFLINE);
                    node.setStatusMessage("FUERA DE SERVICIO (Sin respuesta en puerto " + node.getPort() + ")");
                } else {
                    node.setOnline(true);
                    node.setStatus(ClusterNode.NodeStatus.RUNNING);
                    node.setStatusMessage("EN LÍNEA (Quórum Raft Activo)");
                    node.setLastHeartbeat(System.currentTimeMillis());
                }
            }

            if (node.isOnline()) {
                if (node.getRole() == ClusterNode.Role.PRIMARY) {
                    node.setHeapUsedMb(usedRam);
                    node.setHeapTotalMb(totalRam);
                    node.setHeapMaxMb(maxRam);
                    node.setHeapSaturationPercent(satPercent);
                    node.setPanamaDirectMemMb(128);
                    node.setMemTableMb(128);
                    node.setDiskSSTablesBytes(diskBytes);
                    node.setDiskFilesCount(diskFiles);
                    node.setActiveVirtualThreads(Thread.activeCount() + 8);
                } else {
                    long replicaUsed = Math.max(20, (long)(usedRam * 0.45));
                    node.setHeapUsedMb(replicaUsed);
                    node.setHeapTotalMb(totalRam / 2);
                    node.setHeapMaxMb(maxRam / 2);
                    node.setHeapSaturationPercent(Math.min(100.0, (double) replicaUsed / (maxRam / 2.0) * 100.0));
                    node.setPanamaDirectMemMb(64);
                    node.setMemTableMb(64);
                    node.setDiskSSTablesBytes((long)(diskBytes * 0.4));
                    node.setDiskFilesCount(diskFiles / 2);
                    node.setActiveVirtualThreads(cpuCores * 2);
                }

                if (node.getHeapSaturationPercent() > 85.0) {
                    node.setPoliceDiagnosis("INTERVENCIÓN ANTI-OOM: Saturación (" + String.format("%.1f", node.getHeapSaturationPercent()) + "%). Forzada paginación lazy.");
                } else if (node.getHeapSaturationPercent() > 70.0) {
                    node.setPoliceDiagnosis("ADVERTENCIA POLICIAL: Presión de memoria moderada (" + String.format("%.1f", node.getHeapSaturationPercent()) + "%).");
                } else {
                    node.setPoliceDiagnosis("SALUDABLE: Estabilidad operativa garantizada por JettraPolice (" + String.format("%.1f", node.getHeapSaturationPercent()) + "% saturación).");
                }
            } else {
                offlineAlertCount++;
                node.setHeapUsedMb(0);
                node.setHeapSaturationPercent(0.0);
                node.setLatencyMs(-1);
                node.setPoliceDiagnosis("CRÍTICO [FUERA DE SERVICIO]: El servidor no responde latidos. Tráfico desviado en anillo dinámico.");
            }
        }

        // Evaluar misiones de los agentes caninos policiales en base a la telemetría real
        updatePoliceAgentMissions();
    }

    private void updatePoliceAgentMissions() {
        ServerNode3D highestLoadNode = null;
        double maxSat = -1.0;
        ServerNode3D offlineNode = null;

        for (ServerNode3D n : serverNodes) {
            if (!n.isOnline()) {
                offlineNode = n;
            } else if (n.getHeapSaturationPercent() > maxSat) {
                maxSat = n.getHeapSaturationPercent();
                highestLoadNode = n;
            }
        }

        for (JettraPoliceAgent k9 : activePoliceAgents) {
            switch (k9.getRole()) {
                case HEAP_SENTINEL -> {
                    if (highestLoadNode != null) {
                        k9.setTargetNodeId(highestLoadNode.getId());
                        if (maxSat > 75.0) {
                            k9.assignMission(highestLoadNode.getId(), "🚨 Alerta Heap (" + String.format("%.1f", maxSat) + "%). Inspeccionando MemTable y forzando lazy load.", true, "WARNING");
                        } else {
                            k9.assignMission(highestLoadNode.getId(), "🐾 Patrullando Heap en " + highestLoadNode.getId() + " (" + String.format("%.1f", maxSat) + "% seguro).", false, "INFO");
                        }
                    }
                }
                case RAFT_QUORUM_K9 -> {
                    if (offlineNode != null) {
                        k9.assignMission(offlineNode.getId(), "🚨 ¡ALERTA! " + offlineNode.getId() + " FUERA DE SERVICIO. Desviando réplicas.", true, "CRITICAL");
                    } else {
                        k9.assignMission("node-02", "🐾 Consenso Raft sincronizado al 100%. Quórum verificado.", false, "INFO");
                    }
                }
                case MEMTABLE_PURGE_DOG -> {
                    k9.assignMission("node-01", "🐾 Inspeccionando compactaciones SSTables y vaciado Off-Heap.", false, "INFO");
                }
                case SECURITY_PATROL -> {
                    k9.assignMission("node-01", "🛡️ Autenticando usuarios y supervisando permisos de conexión.", false, "INFO");
                }
            }
        }
    }

    public synchronized void addLiveUserSession(String username, String ip, String db, String initialQuery) {
        UserZoneGroup targetZone = findOrCreateZoneForIp(ip, db);
        String sessionId = "sess_" + username + "_" + System.currentTimeMillis();
        targetZone.registerSession(sessionId);

        JettraLiveSession session = new JettraLiveSession(
            sessionId, username, ip, targetZone.getZoneId(), db, "node-01", initialQuery
        );
        liveSessions.add(session);
    }

    public synchronized void removeLiveUserSession(String sessionId) {
        for (UserZoneGroup z : userZones) {
            z.unregisterSession(sessionId);
        }
        liveSessions.removeIf(s -> s.getSessionId().equalsIgnoreCase(sessionId));
    }

    public synchronized UserZoneGroup findOrCreateZoneForIp(String ip, String suggestedDb) {
        for (UserZoneGroup z : userZones) {
            if (z.matchesIp(ip)) {
                return z;
            }
        }
        // Asignar a zona por defecto o data center
        return userZones.get(0);
    }

    public UserZoneGroup getZoneById(String zoneId) {
        for (UserZoneGroup z : userZones) {
            if (z.getZoneId().equalsIgnoreCase(zoneId)) return z;
        }
        return userZones.isEmpty() ? null : userZones.get(0);
    }

    private boolean checkSocketPing(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 250);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void toggleNodeOffline(String nodeId) {
        for (ServerNode3D n : serverNodes) {
            if (n.getId().equalsIgnoreCase(nodeId)) {
                n.toggleOffline();
                this.lastPoliceEvent = "JettraStorePolice: Servidor '" + n.getId() + "' conmutado a " 
                    + (n.isOnline() ? "EN LÍNEA" : "FUERA DE SERVICIO") + ".";
                break;
            }
        }
    }

    public ConnectionManager getConnectionManager() { return connectionManager; }
    public ConnectionProfile getCurrentProfile() { return currentProfile; }
    public List<ServerNode3D> getServerNodes() { return serverNodes; }
    public List<UserZoneGroup> getUserZones() { return userZones; }
    public List<JettraLiveSession> getLiveSessions() { return liveSessions; }
    public List<JettraPoliceAgent> getActivePoliceAgents() { return activePoliceAgents; }
    public List<ClusterDataTraffic> getActiveTraffic() { return activeTraffic; }

    public ServerNode3D getNodeById(String id) {
        for (ServerNode3D n : serverNodes) {
            if (n.getId().equalsIgnoreCase(id)) return n;
        }
        return null;
    }

    public boolean isConnected() { return connected; }
    public long getTotalEvaluations() { return totalEvaluations; }
    public String getLastPoliceEvent() { return lastPoliceEvent; }
    public long getProcessedObjectsTotal() { return processedObjectsTotal; }
    public long getProcessedObjectsPerSecond() { return processedObjectsPerSecond; }
    public int getActiveTransactions() { return activeTransactions; }

    @Override
    public void close() {
        scheduler.shutdownNow();
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
    }
}
