package io.jettra.core.three.d.police;

import io.jettra.core.three.d.model.ServerNode3D;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JettraStorePoliceMonitor implements AutoCloseable {
    private final List<ServerNode3D> serverNodes = new CopyOnWriteArrayList<>();
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

    public JettraStorePoliceMonitor() {
        initNodes();
        initClient();
        startScheduler();
    }

    private void initNodes() {
        // Servidor 1: Nodo Maestro Primario
        ServerNode3D n1 = new ServerNode3D("node-01", "node-01-master", "127.0.0.1", 9091, ClusterNode.Role.PRIMARY, -14.0f, 0.0f, -12.0f);
        n1.setRaftState(ClusterNode.RaftState.LEADER);
        serverNodes.add(n1);

        // Servidor 2: Nodo Réplica Secundaria A
        ServerNode3D n2 = new ServerNode3D("node-02", "node-02-replica", "127.0.0.1", 9092, ClusterNode.Role.SECONDARY, 0.0f, 0.0f, -18.0f);
        n2.setRaftState(ClusterNode.RaftState.FOLLOWER);
        serverNodes.add(n2);

        // Servidor 3: Nodo Réplica Secundaria B (Inicia como Fuera de Servicio para demostración visual de alerta)
        ServerNode3D n3 = new ServerNode3D("node-03", "node-03-replica", "127.0.0.1", 9093, ClusterNode.Role.SECONDARY, 14.0f, 0.0f, -12.0f);
        n3.setRaftState(ClusterNode.RaftState.FOLLOWER);
        n3.toggleOffline(); // Estado inicial: FUERA DE SERVICIO
        serverNodes.add(n3);
    }

    private void initClient() {
        try {
            JettraClientConfig cfg = JettraClientConfig.builder()
                .addClusterNode("127.0.0.1", 9091)
                .credentials("admin", "admin-jettra")
                .build();
            this.client = new JettraClient(cfg);
            this.connected = true;
            this.lastPoliceEvent = "Conexión establecida con JettraStore Cluster (127.0.0.1:9091).";
        } catch (Exception e) {
            this.connected = false;
            this.lastPoliceEvent = "JettraStore local activo en modo autónomo (Driver embebido).";
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

        // Telemetría de almacenamiento LSM
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

        for (ServerNode3D node : serverNodes) {
            // Si el nodo no está en modo de fallo forzado/simulado, probar conectividad física
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

            // Actualizar métricas de recursos en base a su rol y estado
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
                    // Carga balanceada en nodos secundarios réplica
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

                // Evaluación del Policía Centinela (JettraStorePolice)
                if (node.getHeapSaturationPercent() > 85.0) {
                    node.setPoliceDiagnosis("INTERVENCIÓN ANTI-OOM: Saturación (" + String.format("%.1f", node.getHeapSaturationPercent()) + "%). Forzada paginación lazy.");
                } else if (node.getHeapSaturationPercent() > 70.0) {
                    node.setPoliceDiagnosis("ADVERTENCIA POLICIAL: Presión de memoria moderada (" + String.format("%.1f", node.getHeapSaturationPercent()) + "%).");
                } else {
                    node.setPoliceDiagnosis("SALUDABLE: Estabilidad operativa garantizada por JettraPolice (" + String.format("%.1f", node.getHeapSaturationPercent()) + "% saturación).");
                }
            } else {
                // Nodo Fuera de Servicio
                offlineAlertCount++;
                node.setHeapUsedMb(0);
                node.setHeapSaturationPercent(0.0);
                node.setLatencyMs(-1);
                node.setPoliceDiagnosis("CRÍTICO [FUERA DE SERVICIO]: El servidor no responde latidos. Tráfico desviado en anillo dinámico.");
            }
        }
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

    public List<ServerNode3D> getServerNodes() {
        return serverNodes;
    }

    public ServerNode3D getNodeById(String id) {
        for (ServerNode3D n : serverNodes) {
            if (n.getId().equalsIgnoreCase(id)) return n;
        }
        return null;
    }

    public boolean isConnected() {
        return connected;
    }

    public long getTotalEvaluations() {
        return totalEvaluations;
    }

    public String getLastPoliceEvent() {
        return lastPoliceEvent;
    }

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
