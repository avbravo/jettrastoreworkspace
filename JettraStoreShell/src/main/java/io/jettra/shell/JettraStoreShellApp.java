package io.jettra.shell;

import io.jettra.driver.JettraClient;
import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.backup.BackupManager;
import io.jettra.store.cluster.ClusterNode;
import io.jettra.store.cluster.DynamicRingEngine;
import io.jettra.store.core.JettraDatabase;
import io.jettra.store.core.JettraStoreConfig;
import io.jettra.store.engine.index.JettraIndexManager;
import io.jettra.store.engine.query.JettraQLProcessor;
import io.jettra.store.engine.query.JettraSQLProcessor;
import io.jettra.store.security.JettraSecurityManager;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class JettraStoreShellApp {
    private JettraClient client;
    private String currentDatabase = "default_db";
    private boolean authenticated = false;
    private String currentUser = "anonymous";
    private String currentRole = "NONE";
    private String currentHost = "127.0.0.1";
    private int currentPort = 9091;
    private boolean lazyLoad = true;
    private boolean showReferences = true;
    private int pageSize = 20;

    // Perfiles de Conexión Guardados
    public record SavedConnection(String name, String host, int port, String user) {}
    private final Map<String, SavedConnection> savedConnections = new ConcurrentHashMap<>();

    public JettraStoreShellApp(boolean autoConnect) {
        initDefaultConnections();
        if (autoConnect) {
            connectAndLogin("127.0.0.1", 9091, "admin", "admin-jettra");
        }
    }

    public JettraStoreShellApp(JettraClient client) {
        initDefaultConnections();
        this.client = client;
        this.authenticated = true;
        this.currentUser = "admin";
        this.currentRole = "SUPER_ADMIN";
        this.currentHost = "127.0.0.1";
        this.currentPort = 9091;
        if (this.client != null) {
            this.client.getDatabase(currentDatabase);
        }
    }

    private void initDefaultConnections() {
        savedConnections.put("local_master", new SavedConnection("local_master", "127.0.0.1", 9091, "admin"));
        savedConnections.put("node-02-replica", new SavedConnection("node-02-replica", "127.0.0.1", 9092, "admin"));
        savedConnections.put("node-03-replica", new SavedConnection("node-03-replica", "127.0.0.1", 9093, "admin"));
    }

    public String executeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String upper = trimmed.toUpperCase();

        // 1. Ayuda y Menú
        if (upper.equals("HELP") || upper.equals("?")) {
            return getHelpText();
        } else if (upper.equals("MENU")) {
            return getInteractiveMenu();
        }

        // 2. Conexión, Autenticación y Cierre de Sesión
        if (upper.startsWith("CONNECT ")) {
            return handleConnect(trimmed);
        } else if (upper.startsWith("LOGIN ")) {
            return handleLogin(trimmed);
        } else if (upper.equals("LOGOUT")) {
            return handleLogout();
        }

        // 3. Gestión de Conexiones Guardadas (save, remove, list)
        if (upper.startsWith("SAVE CONNECTION ")) {
            return handleSaveConnection(trimmed);
        } else if (upper.startsWith("REMOVE CONNECTION ")) {
            return handleRemoveConnection(trimmed);
        } else if (upper.equals("LIST CONNECTIONS") || upper.equals("LIST CONECTIONS")) {
            return handleListConnections();
        }

        // 4. Verificación de Seguridad y Autenticación para operaciones operativas
        if (!authenticated || client == null) {
            return "[AUTH REQUIRED] Debe iniciar sesión con 'login <username> <password>' antes de interactuar con bases de datos o telemetría.";
        }

        // 5. Telemetría de Recursos (status) y Topología (show nodes)
        if (upper.equals("STATUS")) {
            return handleStatus();
        } else if (upper.equals("SHOW NODES") || upper.equals("NODES") || upper.equals("LIST NODES")) {
            return handleShowNodes();
        } else if (upper.startsWith("ADD NODE ")) {
            return handleAddNode(trimmed);
        } else if (upper.startsWith("REMOVE NODE ")) {
            return handleRemoveNode(trimmed);
        } else if (upper.startsWith("START NODE ")) {
            return handleStartNode(trimmed);
        } else if (upper.startsWith("STOP NODE ")) {
            return handleStopNode(trimmed);
        }

        // 6. Configuración Lazy Reference (lazy reference on / off (lazy reference on / lazy reference off))
        if (upper.equals("LAZY REFERENCE ON") || upper.equals("SET LAZY_REFERENCE ON") || upper.equals("SET LAZY_REFERENCE = TRUE")) {
            this.lazyLoad = true;
            this.showReferences = true;
            return "[CONFIG] Lazy Reference ACTIVADO (ON). Las referencias JettraRef se resolverán bajo demanda (Proxy).";
        } else if (upper.equals("LAZY REFERENCE OFF") || upper.equals("SET LAZY_REFERENCE OFF") || upper.equals("SET LAZY_REFERENCE = FALSE")) {
            this.lazyLoad = false;
            this.showReferences = true;
            return "[CONFIG] Lazy Reference DESACTIVADO (OFF). Las referencias JettraRef se cargarán inmediatamente en memoria (Eager).";
        } else if (upper.equals("LAZY REFERENCE STATUS") || upper.equals("LAZY REFERENCE")) {
            return String.format("[CONFIG] Estado de Lazy Reference: %s (showReferences: %s)", lazyLoad ? "ON (Lazy)" : "OFF (Eager)", showReferences);
        }

        // 7. Control de Bases de Datos
        if (upper.equals("SHOW DATABASES") || upper.equals("SHOW DBS") || upper.equals("LIST DATABASES") || upper.equals("LIST DBS")) {
            return handleShowDatabases();
        } else if (upper.equals("SHOW SAMPLES") || upper.equals("SHOW SAMPLE DBS") || upper.equals("SHOW SAMPLE DATABASES") || upper.equals("SHOW DBS SAMPLES")) {
            return handleShowSampleDatabases();
        } else if (upper.startsWith("CREATE DATABASE ")) {
            return handleCreateDatabase(trimmed);
        } else if (upper.startsWith("DROP DATABASE ")) {
            return handleDropDatabase(trimmed);
        } else if (upper.startsWith("USE ")) {
            return handleUseDatabase(trimmed);
        } else if (upper.equals("DB STATS") || upper.equals("DB INFO") || upper.equals("STATS")) {
            return handleDbStats();
        }

        // 8. Control de Buckets / Units y Colecciones
        if (upper.equals("SHOW BUCKETS") || upper.equals("SHOW BUCKET") 
                || upper.equals("SHOW UNIT") || upper.equals("SHOW UNITS") 
                || upper.startsWith("SHOW BUCKETS ") || upper.startsWith("SHOW BUCKET ")
                || upper.startsWith("SHOW UNIT ") || upper.startsWith("SHOW UNITS ")) {
            return handleShowBuckets(trimmed);
        } else if (upper.equals("SHOW RECORDS") || upper.startsWith("SHOW RECORDS ")) {
            return handleShowRecords(trimmed);
        } else if (upper.equals("COUNT") || upper.startsWith("COUNT ") || upper.equals("COUNT *") || upper.equals("COUNT ALL")) {
            return handleCount(trimmed);
        } else if (upper.equals("SHOW COLLECTIONS") || upper.equals("SHOW TABLES")) {
            return handleShowCollections();
        } else if (upper.startsWith("CREATE COLLECTION ")) {
            return handleCreateCollection(trimmed);
        } else if (upper.startsWith("DROP COLLECTION ")) {
            return handleDropCollection(trimmed);
        }

        // 9. Administración de Índices
        if (upper.startsWith("CREATE INDEX ")) {
            return handleCreateIndex(trimmed);
        } else if (upper.startsWith("DROP INDEX ")) {
            return handleDropIndex(trimmed);
        } else if (upper.startsWith("ALTER INDEX ") && upper.contains("REBUILD") || upper.startsWith("REINDEX ")) {
            return handleRebuildIndex(trimmed);
        } else if (upper.equals("SHOW INDEXES") || upper.startsWith("SHOW INDEXES ON ") || upper.equals("LIST INDEXES")) {
            return handleShowIndexes(trimmed);
        }

        // 10. Administración de Usuarios y Roles de Base de Datos
        if (upper.equals("SHOW USERS") || upper.equals("LIST USERS")) {
            return handleShowUsers();
        } else if (upper.startsWith("CREATE USER ")) {
            return handleCreateUser(trimmed);
        } else if (upper.startsWith("DROP USER ")) {
            return handleDropUser(trimmed);
        } else if (upper.startsWith("ALTER USER ")) {
            return handleAlterUser(trimmed);
        } else if (upper.startsWith("GRANT ")) {
            return handleGrantRole(trimmed);
        } else if (upper.startsWith("REVOKE ")) {
            return handleRevokeRole(trimmed);
        } else if (upper.startsWith("SHOW GRANTS FOR ")) {
            return handleShowGrants(trimmed);
        }

        // 11. Registros Referenciados y CRUD
        if (upper.startsWith("INSERT REF ")) {
            return handleInsertRef(trimmed);
        } else if (upper.startsWith("RESOLVE REF ")) {
            return handleResolveRef(trimmed);
        } else if (upper.startsWith("SHOW REFS ")) {
            return handleShowRefs(trimmed);
        } else if (upper.startsWith("INSERT INTO ")) {
            return handleInsert(trimmed);
        } else if (upper.startsWith("GET ") || upper.startsWith("FIND ONE ") || upper.startsWith("FIND BY ID ")) {
            return handleGetRecord(trimmed);
        } else if (upper.startsWith("FIND ALL ") || upper.startsWith("SCAN ")) {
            return handleFindAll(trimmed);
        } else if (upper.startsWith("UPDATE ")) {
            return handleUpdate(trimmed);
        } else if (upper.startsWith("DELETE FROM ") || upper.startsWith("DELETE ") || upper.startsWith("REMOVE ")) {
            return handleDeleteRecord(trimmed);
        }

        // 12. Soporte Políglota: JettraQL y JettraSQL
        if (upper.startsWith("JQL ") || upper.startsWith("JETTRAQL ") || upper.startsWith("FROM ") 
                || upper.startsWith("MATCH ") || upper.startsWith("VECTOR SIMILARITY ") 
                || upper.startsWith("VECTOR MATCH ") || upper.startsWith("FETCH ")) {
            return handleJettraQL(trimmed);
        } else if (upper.startsWith("SQL ") || upper.startsWith("JETTRASQL ") || upper.startsWith("SELECT ")) {
            return handleJettraSQL(trimmed);
        }

        // 13. Motores Multimodelo Especializados
        if (upper.startsWith("VECTOR INDEX ")) {
            return handleVectorIndex(trimmed);
        } else if (upper.startsWith("VECTOR SEARCH ")) {
            return handleVectorSearch(trimmed);
        } else if (upper.startsWith("GRAPH ADD VERTEX ")) {
            return handleGraphAddVertex(trimmed);
        } else if (upper.startsWith("GRAPH ADD EDGE ")) {
            return handleGraphAddEdge(trimmed);
        } else if (upper.startsWith("GRAPH GET EDGES ") || upper.startsWith("GRAPH EDGES ")) {
            return handleGraphGetEdges(trimmed);
        } else if (upper.startsWith("TS RECORD ")) {
            return handleTsRecord(trimmed);
        } else if (upper.startsWith("TS RANGE ") || upper.startsWith("TS QUERY ")) {
            return handleTsRange(trimmed);
        } else if (upper.startsWith("KV PUT ")) {
            return handleKvPut(trimmed);
        } else if (upper.startsWith("KV GET ")) {
            return handleKvGet(trimmed);
        }

        // 14. Persistencia y Muestras Completas
        if (upper.equals("INSTALL SAMPLES FACTURA") || upper.equals("INSTALL SAMPLE FACTURA")
                || upper.equals("LOAD SAMPLE EXAMPLE_FACTURA_DB") || upper.equals("INSTALL SAMPLE EXAMPLE_FACTURA_DB")
                || upper.equals("LOAD SAMPLE FACTURA") || upper.equals("INSTALL FACTURA")) {
            return installFacturaSampleDatabase();
        } else if (upper.startsWith("INSTALL SAMPLES") || upper.equals("1")) {
            return installAllSampleDatabases();
        } else if (upper.startsWith("BACKUP DATABASE")) {
            return handleBackup(trimmed);
        } else if (upper.startsWith("RESTORE DATABASE")) {
            return handleRestore(trimmed);
        }

        return "[ERROR] Comando desconocido: '" + trimmed + "'. Escriba 'help' o 'menu' para ver los comandos disponibles.";
    }

    // --- 1. Conexión y Autenticación ---
    public boolean connectAndLogin(String host, int port, String user, String pass) {
        try {
            this.currentHost = host;
            this.currentPort = port;
            this.client = JettraClient.connect(host, port, user, pass);
            this.authenticated = true;
            this.currentUser = user;
            var claims = client.getSecurityManager().validateToken(client.getSessionToken());
            this.currentRole = claims.role();
            this.client.getDatabase(currentDatabase);
            return true;
        } catch (Exception e) {
            this.authenticated = false;
            this.currentUser = "anonymous";
            this.currentRole = "NONE";
            return false;
        }
    }

    private String handleConnect(String command) {
        String clean = command.substring(8).trim();
        if (savedConnections.containsKey(clean)) {
            SavedConnection sc = savedConnections.get(clean);
            this.currentHost = sc.host();
            this.currentPort = sc.port();
            return String.format("[CONNECTED] Servidor configurado a '%s:%d' desde perfil '%s'. Inicie sesión con: login %s <password>",
                currentHost, currentPort, sc.name(), sc.user());
        }

        String[] parts = clean.split("\\s+");
        if (parts.length >= 2) {
            this.currentHost = parts[0].trim();
            try {
                this.currentPort = Integer.parseInt(parts[1].trim());
            } catch (Exception e) {
                return "[ERROR] Puerto numérico inválido: " + parts[1];
            }
            return String.format("[CONNECTED] Servidor JettraStore configurado a %s:%d. Proceda con 'login <username> <password>'.", currentHost, currentPort);
        } else if (parts.length == 1 && !parts[0].isBlank()) {
            this.currentHost = parts[0].trim();
            return String.format("[CONNECTED] Host configurado a %s:%d. Proceda con 'login <username> <password>'.", currentHost, currentPort);
        }
        return "[ERROR] Uso: connect <host> <puerto> o connect <nombre-conexion>";
    }

    private String handleLogin(String command) {
        String[] parts = command.substring(6).trim().split("\\s+");
        if (parts.length < 2) {
            return "[ERROR] Uso: login <username> <password>";
        }
        String user = parts[0].trim();
        String pass = parts[1].trim();

        boolean ok = connectAndLogin(currentHost, currentPort, user, pass);
        if (ok) {
            return String.format("[AUTH SUCCESS] Sesión iniciada como '%s' (Rol Global: %s) en %s:%d.", currentUser, currentRole, currentHost, currentPort);
        } else {
            return "[AUTH ERROR] Credenciales inválidas para el usuario: " + user;
        }
    }

    private String handleLogout() {
        if (!authenticated) {
            return "[INFO] No hay ninguna sesión activa en este momento.";
        }
        String oldUser = currentUser;
        this.authenticated = false;
        this.currentUser = "anonymous";
        this.currentRole = "NONE";
        if (this.client != null) {
            this.client.close();
        }
        return String.format("[LOGOUT] Sesión del usuario '%s' finalizada exitosamente. Inicie sesión nuevamente con 'login <username> <password>'.", oldUser);
    }

    // --- 2. Gestión de Perfiles de Conexión ---
    private String handleSaveConnection(String command) {
        String clean = command.substring("SAVE CONNECTION ".length()).trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 1 || parts[0].isBlank()) {
            return "[ERROR] Uso: save connection <nombre-conexion> [host] [puerto] [user]";
        }
        String name = cleanQuotes(parts[0]);
        String host = parts.length > 1 ? parts[1].trim() : currentHost;
        int port = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : currentPort;
        String user = parts.length > 3 ? parts[3].trim() : (authenticated ? currentUser : "admin");

        savedConnections.put(name, new SavedConnection(name, host, port, user));
        return String.format("[SUCCESS] Conexión '%s' guardada (%s:%d, usuario: %s).", name, host, port, user);
    }

    private String handleRemoveConnection(String command) {
        String name = cleanQuotes(command.substring("REMOVE CONNECTION ".length()).trim());
        if (name.isBlank()) return "[ERROR] Uso: remove connection <nombre-conexion>";
        SavedConnection removed = savedConnections.remove(name);
        if (removed != null) {
            return String.format("[SUCCESS] Conexión guardada '%s' eliminada correctamente.", name);
        } else {
            return String.format("[WARN] No existe una conexión guardada con el nombre '%s'.", name);
        }
    }

    private String handleListConnections() {
        if (savedConnections.isEmpty()) {
            return "[INFO] No hay conexiones guardadas.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("+----------------------+----------------------+-------+-----------------+----------+\n");
        sb.append("| Nombre de Perfil     | Host / IP            | Puerto| Usuario         | Activo   |\n");
        sb.append("+----------------------+----------------------+-------+-----------------+----------+\n");
        for (SavedConnection sc : savedConnections.values()) {
            boolean isCur = sc.host().equals(currentHost) && sc.port() == currentPort;
            sb.append(String.format("| %-20s | %-20s | %-5d | %-15s | %-8s |\n",
                sc.name(), sc.host(), sc.port(), sc.user(), isCur ? "* SI" : "NO"));
        }
        sb.append("+----------------------+----------------------+-------+-----------------+----------+\n");
        sb.append(String.format("Total: %d perfil(es) de conexión guardado(s).", savedConnections.size()));
        return sb.toString();
    }

    // --- 3. Telemetría de Recursos (STATUS) ---
    private String handleStatus() {
        Runtime rt = Runtime.getRuntime();
        long totalRam = rt.totalMemory() / (1024 * 1024);
        long freeRam = rt.freeMemory() / (1024 * 1024);
        long usedRam = totalRam - freeRam;
        long maxRam = rt.maxMemory() / (1024 * 1024);
        int cpuCores = rt.availableProcessors();

        JettraStoreConfig cfg = JettraStoreConfig.load();
        String storagePath = cfg.getStoragePath();
        String rawConfigured = cfg.getConfiguredStoragePath();
        long diskBytes = 0;
        int filesCount = 0;
        try {
            Path p = Path.of(storagePath);
            if (Files.exists(p)) {
                try (var s = Files.walk(p)) {
                    for (Path f : (Iterable<Path>) s::iterator) {
                        if (Files.isRegularFile(f)) {
                            diskBytes += Files.size(f);
                            filesCount++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return String.format("""
            ==============================================================================================
                                  JETTRASTORE RESOURCE MONITOR & TELEMETRY (JAVA 25+)
            ==============================================================================================
            1. RAM (MEMORIA):
               - Panama FFM Off-Heap Direct: Habilitado (Arena Compartida Cero Copia)
               - Heap JVM (ZGC Generational): Ocupada %d MB / Total %d MB (Máx JVM: %d MB)
               - MemTable Tamaño Asignado:   %d MB
               - Dynamic Ring Saturation:    Umbral 85%% (Descarga automática a nodos secundarios)
               - Compact Object Headers:     Activo (--XX:+UseCompactObjectHeaders)

            2. PROCESADOR (CPU):
               - Núcleos Lógicos del Host:   %d Cores
               - Virtual Threads (Loom):     Activos (I/O Concurrente No Bloqueante en red gRPC/REST)
               - Hilos de Compaction LSM:    En segundo plano (Prioridad baja)

            3. DISCO (ALMACENAMIENTO):
               - Ruta Física Configurada:    %s
               - Directorio Activo de Datos: %s
               - Tamaño Ocupado por SSTables: %.2f KB (%d bytes)
               - Archivos de Datos (.jettra): %d archivo(s)
               - Formato de Almacenamiento:  Estructura LSM (.jettra) con Bloom Filters y Sparse Indexes
            ==============================================================================================
            """, usedRam, totalRam, maxRam, cfg.getMemTableSizeMb(), cpuCores, rawConfigured, storagePath, (diskBytes / 1024.0), diskBytes, filesCount);
    }

    // --- 4. Administración de Nodos del Clúster ---
    private String handleShowNodes() {
        DynamicRingEngine ring = client.getRingEngine();
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================================================================\n");
        sb.append("                          JETTRASTORE RAFT CLUSTER TOPOLOGY                                   \n");
        sb.append("==============================================================================================\n");
        sb.append("+----------+----------------------+-------+-----------+------------+----------+--------------+\n");
        sb.append("| Nodo ID  | Dirección IP         | Puerto| Rol       | Estado Raft| Estado   | Offload Bytes|\n");
        sb.append("+----------+----------------------+-------+-----------+------------+----------+--------------+\n");
        sb.append(String.format("| %-8s | %-20s | %-5d | %-9s | %-10s | %-8s | %-12d |\n",
            ring.getNodeId(), currentHost, currentPort, "PRIMARY", "LEADER", "RUNNING", 0));

        for (ClusterNode peer : ring.getPeers()) {
            sb.append(String.format("| %-8s | %-20s | %-5d | %-9s | %-10s | %-8s | %-12d |\n",
                peer.getId(), peer.getIp(), peer.getPort(), peer.getRole(), peer.getRaftState(),
                peer.getStatus(), peer.getReceivedOffloadedBytes()));
        }
        sb.append("+----------+----------------------+-------+-----------+------------+----------+--------------+\n");
        sb.append(String.format("Total: %d nodo(s) registrados en el anillo dinámico. Quórum: Activo.\n", ring.getPeers().size() + 1));
        return sb.toString();
    }

    private String handleAddNode(String command) {
        String clean = command.substring("ADD NODE ".length()).trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 3) {
            return "[ERROR] Uso: ADD NODE <nodeId> <host> <port> [PRIMARY|SECONDARY]";
        }
        String id = parts[0].trim();
        String host = parts[1].trim();
        int port;
        try {
            port = Integer.parseInt(parts[2].trim());
        } catch (Exception e) {
            return "[ERROR] Puerto inválido: " + parts[2];
        }
        ClusterNode.Role role = (parts.length > 3 && "PRIMARY".equalsIgnoreCase(parts[3])) 
            ? ClusterNode.Role.PRIMARY : ClusterNode.Role.SECONDARY;

        ClusterNode newNode = new ClusterNode(id, host, port, role);
        client.getRingEngine().registerPeer(newNode);
        return String.format("[SUCCESS] Nodo '%s' (%s:%d, %s) agregado exitosamente al clúster Raft.", id, host, port, role);
    }

    private String handleRemoveNode(String command) {
        String id = cleanQuotes(command.substring("REMOVE NODE ".length()).trim());
        if (id.isBlank()) return "[ERROR] Uso: REMOVE NODE <nodeId>";
        if ("node-01".equalsIgnoreCase(id) || client.getRingEngine().getNodeId().equalsIgnoreCase(id)) {
            return "[ERROR] No se puede remover el nodo primario activo del clúster.";
        }
        boolean ok = client.getRingEngine().removePeer(id);
        return ok ? "[SUCCESS] Nodo '" + id + "' removido del anillo de réplicas."
                  : "[WARN] El nodo '" + id + "' no existe en el registro del clúster.";
    }

    private String handleStartNode(String command) {
        String id = cleanQuotes(command.substring("START NODE ".length()).trim());
        if (id.isBlank()) return "[ERROR] Uso: START NODE <nodeId>";
        boolean ok = client.getRingEngine().startPeer(id);
        return ok ? "[SUCCESS] Nodo '" + id + "' iniciado (RUNNING)."
                  : "[ERROR] No se pudo iniciar el nodo '" + id + "' (nodo no encontrado).";
    }

    private String handleStopNode(String command) {
        String id = cleanQuotes(command.substring("STOP NODE ".length()).trim());
        if (id.isBlank()) return "[ERROR] Uso: STOP NODE <nodeId>";
        if ("node-01".equalsIgnoreCase(id) || client.getRingEngine().getNodeId().equalsIgnoreCase(id)) {
            return "[ERROR] No se puede detener el nodo primario local en ejecución.";
        }
        boolean ok = client.getRingEngine().stopPeer(id);
        return ok ? "[SUCCESS] Nodo '" + id + "' detenido (STOPPED). Tráfico de anillo pausado para este nodo."
                  : "[ERROR] No se pudo detener el nodo '" + id + "' (nodo no encontrado).";
    }

    // --- 5. Control de Bases de Datos & Detección en Disco ---
    private String handleShowDatabases() {
        List<String> dbs = client.listDatabases();
        if (!dbs.contains(currentDatabase)) {
            dbs.add(currentDatabase);
        }
        Collections.sort(dbs);

        JettraStoreConfig cfg = JettraStoreConfig.load();

        StringBuilder sb = new StringBuilder();
        sb.append("+------------------------------------+----------+-------------+----------------+\n");
        sb.append("| Base de Datos                      | Tipo     | Colecciones | Estado         |\n");
        sb.append("+------------------------------------+----------+-------------+----------------+\n");
        for (String db : dbs) {
            JettraDatabase jettraDb = client.getDatabase(db);
            int colCount = jettraDb.getAllCollectionNames().size();
            String tipo = db.startsWith("sample_") ? "SAMPLE" : (db.equals("default_db") ? "SYSTEM" : "USER");
            String status = db.equals(currentDatabase) ? "* ACTIVA" : "DISPONIBLE";
            sb.append(String.format("| %-34s | %-8s | %-11d | %-14s |\n", db, tipo, colCount, status));
        }
        sb.append("+------------------------------------+----------+-------------+----------------+\n");
        sb.append(String.format("Total: %d base(s) de datos detectadas. Base activa: '%s'\n", dbs.size(), currentDatabase));
        sb.append(String.format("Ruta física en database.properties: '%s' | Directorio de lectura/escritura: '%s'",
            cfg.getConfiguredStoragePath(), cfg.getStoragePath()));
        return sb.toString();
    }

    private String handleShowSampleDatabases() {
        List<String> allDbs = client.listDatabases();
        String[] samples = {
            "sample_enterprise_db",
            "sample_ecommerce_db",
            "sample_ai_graph_db",
            "sample_iot_telemetry_db",
            "sample_financial_db",
            "example_factura_db"
        };

        StringBuilder sb = new StringBuilder();
        sb.append("==============================================================================================\n");
        sb.append("                        BASES DE DATOS DE EJEMPLO (JettraStore Samples)                      \n");
        sb.append("==============================================================================================\n");
        sb.append("+-------------------------+--------------------+---------------------------------------------+\n");
        sb.append("| Base de Datos           | Estado en Disco    | Motores & Propósito                         |\n");
        sb.append("+-------------------------+--------------------+---------------------------------------------+\n");
        for (String s : samples) {
            boolean installed = allDbs.contains(s);
            String desc = switch (s) {
                case "sample_enterprise_db"    -> "Documentos, Vectores 3D, Grafos de Catálogo, KV, Series";
                case "sample_ecommerce_db"     -> "Clientes, Órdenes, Analítica Columnar, Carritos KV";
                case "sample_ai_graph_db"      -> "Red de Grafos de Conocimiento, Embeddings, Prompts";
                case "sample_iot_telemetry_db" -> "Sensores Temperatura/Vibración, Smart Devices, Geo";
                case "sample_financial_db"     -> "Transacciones de Cuentas, Ledger y Cotizaciones";
                case "example_factura_db"      -> "Facturación 3M Objetos (1M Fac, 1M Det, 200k Cli, KV, Vec, Graph)";
                default -> "Muestra Multimodelo";
            };
            sb.append(String.format("| %-23s | %-18s | %-43s |\n", s, installed ? "INSTALADA (Lista)" : "NO INSTALADA", desc));
        }
        sb.append("+-------------------------+--------------------+---------------------------------------------+\n");
        sb.append("Para instalar o re-inicializar todas las muestras completas, ejecute: INSTALL SAMPLES\n");
        return sb.toString();
    }

    private String handleCreateDatabase(String command) {
        String dbName = cleanQuotes(command.substring("CREATE DATABASE ".length()));
        if (dbName.isBlank()) return "[ERROR] Nombre de base de datos requerido.";
        this.client.getDatabase(dbName);
        this.currentDatabase = dbName;
        return "[SUCCESS] Base de datos '" + dbName + "' creada exitosamente y seleccionada como activa.";
    }

    private String handleDropDatabase(String command) {
        String dbName = cleanQuotes(command.substring("DROP DATABASE ".length()));
        if (dbName.isBlank()) return "[ERROR] Nombre de base de datos requerido.";
        boolean dropped = client.dropDatabase(dbName);
        if (dbName.equalsIgnoreCase(currentDatabase)) {
            this.currentDatabase = "default_db";
            this.client.getDatabase(currentDatabase);
        }
        return dropped ? "[SUCCESS] Base de datos '" + dbName + "' eliminada correctamente."
                       : "[INFO] La base de datos '" + dbName + "' no existía o ya fue eliminada.";
    }

    private String handleUseDatabase(String command) {
        String dbName = cleanQuotes(command.substring(4));
        if (dbName.isBlank()) return "[ERROR] Nombre de base de datos requerido.";

        // Verificación de RBAC granular para el usuario activo
        if (!client.getSecurityManager().hasDatabaseAccess(currentUser, dbName, "USE")) {
            return String.format("[ACCESS DENIED] El usuario '%s' no posee permisos asignados para la base de datos '%s'.", currentUser, dbName);
        }

        this.client.getDatabase(dbName);
        this.currentDatabase = dbName;
        return "[SUCCESS] Conmutado a base de datos activa: '" + dbName + "'.";
    }

    private String handleDbStats() {
        JettraDatabase db = client.getDatabase(currentDatabase);
        return String.format("""
            === ESTADÍSTICAS DE BASE DE DATOS: '%s' ===
            - Colecciones Totales: %d
            - Documentos:          %s
            - Vectores:            %s
            - Grafos:              %s
            - Series Temporales:   %s
            - Índices Secundarios: %d
            - MemTable Utilizada:  %.2f KB
            """, currentDatabase, db.getAllCollectionNames().size(),
            db.getDocumentEngineNames(), db.getVectorEngineNames(),
            db.getGraphEngineNames(), db.getTimeSeriesEngineNames(),
            db.getIndexManager().listIndexes(null).size(),
            (db.getMemTable().getUsedBytes() / 1024.0));
    }

    // --- 6. Administración de Índices ---
    private String handleCreateIndex(String command) {
        // CREATE INDEX <indexName> ON <collection> (<field>) [TYPE <BTREE|HASH|SPARSE>] [UNIQUE]
        try {
            String upper = command.toUpperCase();
            int onIdx = upper.indexOf(" ON ");
            if (onIdx == -1) return "[ERROR] Sintaxis inválida. Uso: CREATE INDEX <nombre> ON <coleccion> (<campo>) [TYPE BTREE|HASH|SPARSE]";

            String indexName = cleanQuotes(command.substring(13, onIdx).trim());
            String rest = command.substring(onIdx + 4).trim();
            int parenOpen = rest.indexOf('(');
            int parenClose = rest.indexOf(')');
            if (parenOpen == -1 || parenClose == -1) {
                return "[ERROR] Campo de índice debe estar entre paréntesis: (<campo>).";
            }

            String col = cleanQuotes(rest.substring(0, parenOpen).trim());
            String field = cleanQuotes(rest.substring(parenOpen + 1, parenClose).trim());
            String tail = rest.substring(parenClose + 1).toUpperCase();

            String type = "BTREE";
            if (tail.contains("HASH")) type = "HASH";
            else if (tail.contains("SPARSE")) type = "SPARSE";

            boolean unique = tail.contains("UNIQUE");

            JettraDatabase db = client.getDatabase(currentDatabase);
            var info = db.getIndexManager().createIndex(col, indexName, field, type, unique, db.getDocumentEngine(col));
            return String.format("[SUCCESS] Índice '%s' creado sobre '%s'(%s) tipo %s (Entradas indexadas: %d).",
                info.name(), info.collection(), info.field(), info.type(), info.entriesCount());
        } catch (Exception e) {
            return "[ERROR] Error al crear índice: " + e.getMessage();
        }
    }

    private String handleDropIndex(String command) {
        String clean = cleanQuotes(command.substring("DROP INDEX ".length()).trim());
        String indexName = clean.split("\\s+")[0];
        JettraDatabase db = client.getDatabase(currentDatabase);
        boolean ok = db.getIndexManager().dropIndex(indexName);
        return ok ? "[SUCCESS] Índice '" + indexName + "' eliminado correctamente."
                  : "[WARN] El índice '" + indexName + "' no existe en la base de datos '" + currentDatabase + "'.";
    }

    private String handleRebuildIndex(String command) {
        String clean = cleanQuotes(command.replaceAll("(?i)^(ALTER INDEX|REINDEX)\\s+", "").replaceAll("(?i)REBUILD", "").trim());
        String indexName = clean.split("\\s+")[0];
        JettraDatabase db = client.getDatabase(currentDatabase);
        var info = db.getIndexManager().getIndex(indexName);
        if (info == null) return "[ERROR] Índice '" + indexName + "' no encontrado.";

        var updated = db.getIndexManager().rebuildIndex(indexName, db.getDocumentEngine(info.collection()));
        return String.format("[SUCCESS] Índice '%s' reconstruido exitosamente. Total entradas indexadas: %d.",
            updated.name(), updated.entriesCount());
    }

    private String handleShowIndexes(String command) {
        String col = null;
        if (command.toUpperCase().contains(" ON ")) {
            col = cleanQuotes(command.substring(command.toUpperCase().indexOf(" ON ") + 4).trim());
        }
        JettraDatabase db = client.getDatabase(currentDatabase);
        List<JettraIndexManager.IndexInfo> list = db.getIndexManager().listIndexes(col);

        if (list.isEmpty()) {
            return "[INFO] No se encontraron índices en '" + currentDatabase + "'" + (col != null ? " para la colección '" + col + "'." : ".");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== ÍNDICES DE BASE DE DATOS: '%s' ===\n", currentDatabase));
        sb.append("+----------------------+----------------------+----------------------+--------+--------+----------+\n");
        sb.append("| Nombre de Índice     | Colección            | Campo Indexado       | Tipo   | Único  | Entradas |\n");
        sb.append("+----------------------+----------------------+----------------------+--------+--------+----------+\n");
        for (var idx : list) {
            sb.append(String.format("| %-20s | %-20s | %-20s | %-6s | %-6s | %-8d |\n",
                idx.name(), idx.collection(), idx.field(), idx.type(), idx.unique() ? "SI" : "NO", idx.entriesCount()));
        }
        sb.append("+----------------------+----------------------+----------------------+--------+--------+----------+\n");
        return sb.toString();
    }

    // --- 7. Administración de Usuarios y Roles de Base de Datos ---
    private String handleShowUsers() {
        var users = client.getSecurityManager().listUsers();
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================================================================\n");
        sb.append("                           USUARIOS Y ROLES DE BASE DE DATOS (RBAC)                           \n");
        sb.append("==============================================================================================\n");
        sb.append("+-----------------+-----------------+------------------------------------------+-------------+\n");
        sb.append("| Usuario         | Rol Global      | Roles de Base de Datos                   | Inmutable   |\n");
        sb.append("+-----------------+-----------------+------------------------------------------+-------------+\n");
        for (var u : users) {
            StringBuilder rolesSb = new StringBuilder();
            if (u.databaseRoles().isEmpty()) {
                rolesSb.append("(Sin asignación específica)");
            } else {
                u.databaseRoles().forEach((db, r) -> rolesSb.append(db).append(":").append(r).append(" "));
            }
            sb.append(String.format("| %-15s | %-15s | %-40s | %-11s |\n",
                u.username(), u.role(), rolesSb.toString().trim(), u.immutable() ? "SI (Protegido)" : "NO"));
        }
        sb.append("+-----------------+-----------------+------------------------------------------+-------------+\n");
        return sb.toString();
    }

    private String handleCreateUser(String command) {
        // CREATE USER <username> PASSWORD <password> [ROLE <globalRole>]
        try {
            String after = command.substring("CREATE USER ".length()).trim();
            int passIdx = after.toUpperCase().indexOf(" PASSWORD ");
            if (passIdx == -1) return "[ERROR] Uso: CREATE USER <username> PASSWORD <password> [ROLE <globalRole>]";

            String username = cleanQuotes(after.substring(0, passIdx).trim());
            String rest = after.substring(passIdx + 10).trim();
            String password;
            String role = "DEVELOPER";

            int roleIdx = rest.toUpperCase().indexOf(" ROLE ");
            if (roleIdx != -1) {
                password = cleanQuotes(rest.substring(0, roleIdx).trim());
                role = cleanQuotes(rest.substring(roleIdx + 6).trim()).toUpperCase();
            } else {
                password = cleanQuotes(rest);
            }

            client.getSecurityManager().createUser(client.getSessionToken(), username, password, role);
            return String.format("[SUCCESS] Usuario '%s' creado con Rol Global '%s'.", username, role);
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleDropUser(String command) {
        String username = cleanQuotes(command.substring("DROP USER ".length()).trim());
        try {
            client.getSecurityManager().dropUser(client.getSessionToken(), username);
            return String.format("[SUCCESS] Usuario '%s' eliminado correctamente.", username);
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleAlterUser(String command) {
        // ALTER USER <username> PASSWORD <newPass> | ALTER USER <username> ROLE <newRole>
        try {
            String after = command.substring("ALTER USER ".length()).trim();
            String[] parts = after.split("\\s+", 3);
            if (parts.length < 3) return "[ERROR] Uso: ALTER USER <username> PASSWORD <newPass> | ALTER USER <username> ROLE <newRole>";
            String username = cleanQuotes(parts[0]);
            String action = parts[1].toUpperCase();
            String val = cleanQuotes(parts[2]);

            if ("PASSWORD".equals(action)) {
                client.getSecurityManager().alterUserPassword(client.getSessionToken(), username, val);
                return "[SUCCESS] Contraseña actualizada para el usuario '" + username + "'.";
            } else if ("ROLE".equals(action)) {
                client.getSecurityManager().alterUserRole(client.getSessionToken(), username, val.toUpperCase());
                return "[SUCCESS] Rol global del usuario '" + username + "' actualizado a '" + val.toUpperCase() + "'.";
            }
            return "[ERROR] Acción desconocida en ALTER USER. Use PASSWORD o ROLE.";
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleGrantRole(String command) {
        // GRANT <DB_ROLE> ON <database> TO <username>
        try {
            String upper = command.toUpperCase();
            int onIdx = upper.indexOf(" ON ");
            int toIdx = upper.indexOf(" TO ");
            if (onIdx == -1 || toIdx == -1) {
                return "[ERROR] Uso: GRANT <DB_OWNER|READ_WRITE|READ_ONLY> ON <database> TO <username>";
            }

            String role = cleanQuotes(command.substring(6, onIdx).trim()).toUpperCase();
            String db = cleanQuotes(command.substring(onIdx + 4, toIdx).trim());
            String user = cleanQuotes(command.substring(toIdx + 4).trim());

            client.getSecurityManager().grantDatabaseRole(client.getSessionToken(), user, db, role);
            return String.format("[SUCCESS] Concedido rol '%s' sobre la base de datos '%s' al usuario '%s'.", role, db, user);
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleRevokeRole(String command) {
        // REVOKE <database> FROM <username> o REVOKE ROLE ON <database> FROM <username>
        try {
            String upper = command.toUpperCase();
            int fromIdx = upper.indexOf(" FROM ");
            if (fromIdx == -1) return "[ERROR] Uso: REVOKE <database> FROM <username>";

            String targetDb = cleanQuotes(command.substring(7, fromIdx).replaceAll("(?i)ROLE\\s+ON\\s+", "").trim());
            String user = cleanQuotes(command.substring(fromIdx + 6).trim());

            client.getSecurityManager().revokeDatabaseRole(client.getSessionToken(), user, targetDb);
            return String.format("[SUCCESS] Permisos sobre base de datos '%s' revocados para el usuario '%s'.", targetDb, user);
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleShowGrants(String command) {
        String username = cleanQuotes(command.substring("SHOW GRANTS FOR ".length()).trim());
        var user = client.getSecurityManager().getUser(username);
        if (user == null) return "[ERROR] Usuario '" + username + "' no encontrado.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== PRIVILEGIOS ASIGNADOS AL USUARIO: '%s' ===\n", username));
        sb.append(String.format("- Rol Global: %s\n", user.role()));
        sb.append("- Permisos por Base de Datos:\n");
        if (user.databaseRoles().isEmpty()) {
            sb.append("  (Ningún rol específico por base de datos asignado)\n");
        } else {
            user.databaseRoles().forEach((db, role) -> {
                sb.append(String.format("  * Base de datos: %-25s -> Rol: %s\n", db, role));
            });
        }
        return sb.toString();
    }

    // --- 8. Registros Referenciados y JettraRef ---
    private String resolveReference(JettraDatabase db, String refStr) {
        try {
            if (refStr.contains("::") && refStr.contains("#")) {
                String type = refStr.substring(0, refStr.indexOf("::")).toLowerCase();
                String rest = refStr.substring(refStr.indexOf("::") + 2);
                String col = rest.substring(0, rest.indexOf('#'));
                String targetId = rest.substring(rest.indexOf('#') + 1);

                return switch (type) {
                    case "vector" -> {
                        float[] v = db.getVectorEngine(col, 3).getVector(targetId);
                        yield v != null ? "Vector " + Arrays.toString(v) : "(Vector no encontrado)";
                    }
                    case "document" -> {
                        Map<String, Object> doc = db.getDocumentEngine(col).findById(targetId);
                        yield doc != null ? "Documento " + doc.toString() : "(Documento no encontrado)";
                    }
                    case "graph" -> {
                        var edges = db.getGraphEngine(col).getOutboundEdges(targetId);
                        yield "Vértice de Grafo '" + targetId + "' (" + edges.size() + " aristas conectadas)";
                    }
                    case "timeseries" -> {
                        yield "Métrica TimeSeries registrada para target '" + targetId + "'";
                    }
                    case "kv" -> {
                        byte[] val = db.getKeyValueEngine(col).get(targetId);
                        yield val != null ? "KV Valor: " + new String(val, StandardCharsets.UTF_8) : "(Clave KV no encontrada)";
                    }
                    default -> refStr;
                };
            }
            return refStr;
        } catch (Exception e) {
            return refStr;
        }
    }

    private String handleInsertRef(String command) {
        // INSERT REF <collection> <docId> KEY <refKey> TARGET <engine>::<targetCol>#<targetId>
        try {
            String upper = command.toUpperCase();
            int keyIdx = upper.indexOf(" KEY ");
            int targetIdx = upper.indexOf(" TARGET ");
            if (keyIdx == -1 || targetIdx == -1) {
                return "[ERROR] Uso: INSERT REF <coleccion> <id> KEY <campo_ref> TARGET <engine>::<targetCol>#<targetId>";
            }

            String beforeKey = command.substring(11, keyIdx).trim();
            String[] parts = beforeKey.split("\\s+");
            if (parts.length < 2) return "[ERROR] Debe especificar colección e ID de registro.";
            String col = cleanQuotes(parts[0]);
            String id = cleanQuotes(parts[1]);

            String refKey = cleanQuotes(command.substring(keyIdx + 5, targetIdx).trim());
            String targetRef = cleanQuotes(command.substring(targetIdx + 8).trim());

            JettraDatabase db = client.getDatabase(currentDatabase);
            var docEngine = db.getDocumentEngine(col);
            Map<String, Object> doc = docEngine.findById(id);
            if (doc == null) {
                doc = new LinkedHashMap<>();
                doc.put("_id", id);
            }
            doc.put(refKey, targetRef);
            docEngine.insert(id, doc);

            return String.format("[SUCCESS] Referencia JettraRef '%s' vinculada en '%s'[%s] -> '%s'.",
                refKey, col, id, targetRef);
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String handleResolveRef(String command) {
        String targetRef = cleanQuotes(command.substring("RESOLVE REF ".length()).trim());
        JettraDatabase db = client.getDatabase(currentDatabase);
        String resolved = resolveReference(db, targetRef);
        return String.format("[JettraRef Resolución]: %s -> %s", targetRef, resolved);
    }

    private String handleShowRefs(String command) {
        String clean = cleanQuotes(command.substring("SHOW REFS ".length()).trim());
        String[] parts = clean.split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: SHOW REFS <coleccion> <id>";
        String col = parts[0];
        String id = parts[1];

        JettraDatabase db = client.getDatabase(currentDatabase);
        var doc = db.getDocumentEngine(col).findById(id);
        if (doc == null) return "[ERROR] Documento no encontrado: " + id;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== REFERENCIAS CRUZADAS PARA [%s] EN '%s' ===\n", id, col));
        int count = 0;
        for (var entry : doc.entrySet()) {
            if (entry.getKey().startsWith("_ref_") || String.valueOf(entry.getValue()).contains("::")) {
                count++;
                String refVal = String.valueOf(entry.getValue());
                sb.append(String.format("  [%02d] Campo: %-18s -> %s\n", count, entry.getKey(), refVal));
                sb.append(String.format("       ↳ Resolución %s: %s\n",
                    lazyLoad ? "(Lazy On-Demand)" : "(Eager)", resolveReference(db, refVal)));
            }
        }
        if (count == 0) {
            sb.append("  (No se encontraron campos de referencia en este registro)\n");
        }
        return sb.toString();
    }

    // --- 9. Soporte JettraQL y JettraSQL ---
    private String handleJettraSQL(String command) {
        String clean = command;
        if (clean.toUpperCase().startsWith("SQL ")) clean = clean.substring(4).trim();
        else if (clean.toUpperCase().startsWith("JETTRASQL ")) clean = clean.substring(10).trim();

        long start = System.currentTimeMillis();
        JettraSQLProcessor.QueryResult res = client.sql(currentDatabase, clean);
        long elapsed = System.currentTimeMillis() - start;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== JETTRASQL RESULTADO (%d ms) ===\n", elapsed));
        sb.append("Mensaje: ").append(res.message()).append("\n");

        if (!res.rows().isEmpty()) {
            // Renderizar tabla ASCII con formato dinámico
            List<String> cols = res.columns();
            sb.append("+");
            for (String c : cols) sb.append("-".repeat(Math.max(c.length() + 2, 14))).append("+");
            sb.append("\n|");
            for (String c : cols) sb.append(String.format(" %-" + Math.max(c.length(), 12) + "s |", c));
            sb.append("\n+");
            for (String c : cols) sb.append("-".repeat(Math.max(c.length() + 2, 14))).append("+");
            sb.append("\n");

            for (List<Object> row : res.rows()) {
                sb.append("|");
                for (int i = 0; i < cols.size(); i++) {
                    String val = (i < row.size() && row.get(i) != null) ? row.get(i).toString() : "";
                    sb.append(String.format(" %-" + Math.max(cols.get(i).length(), 12) + "s |", val));
                }
                sb.append("\n");
            }
            sb.append("+");
            for (String c : cols) sb.append("-".repeat(Math.max(c.length() + 2, 14))).append("+");
            sb.append("\n");
        }
        sb.append(String.format("Total: %d fila(s) seleccionadas / afectadas.\n", res.affectedRows()));
        return sb.toString();
    }

    private String handleJettraQL(String command) {
        String clean = command;
        if (clean.toUpperCase().startsWith("JQL ")) clean = clean.substring(4).trim();
        else if (clean.toUpperCase().startsWith("JETTRAQL ")) clean = clean.substring(9).trim();

        long start = System.currentTimeMillis();
        JettraQLProcessor.JQLResult res = client.jql(currentDatabase, clean);
        long elapsed = System.currentTimeMillis() - start;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== JETTRAQL [%s] (%d ms) ===\n", res.operation(), elapsed));
        sb.append("Resumen: ").append(res.summary()).append("\n");

        if (!res.rows().isEmpty()) {
            List<String> cols = res.columns();
            sb.append("Columnas: ").append(cols).append("\n");
            int idx = 1;
            for (var row : res.rows()) {
                sb.append(String.format("  [%02d] %s\n", idx++, row));
            }
        }
        sb.append(String.format("Coincidencias encontradas: %d\n", res.totalMatches()));
        return sb.toString();
    }

    // --- 10. CRUD de Documentos ---
    private String handleInsert(String command) {
        try {
            String after = command.substring("INSERT INTO ".length()).trim();
            int idIdx = after.toUpperCase().indexOf(" ID ");
            int jsonIdx = after.toUpperCase().indexOf(" JSON ");
            if (idIdx != -1 && jsonIdx != -1) {
                String col = cleanQuotes(after.substring(0, idIdx).trim());
                String id = cleanQuotes(after.substring(idIdx + 4, jsonIdx).trim());
                String jsonPart = after.substring(jsonIdx + 6).trim();
                Map<String, Object> data = parseJsonOrKeyValues(jsonPart);
                JettraDatabase db = client.getDatabase(currentDatabase);
                db.getDocumentEngine(col).insert(id, data);
                db.getIndexManager().onDocumentInsert(col, id, data);
                return String.format("[SUCCESS] Registro con _id '%s' insertado en la colección '%s'.", id, col);
            }
            int valIdx = after.toUpperCase().indexOf(" VALUES");
            if (valIdx != -1) {
                String col = after.substring(0, valIdx).trim();
                String rawVals = after.substring(valIdx + 7).trim();
                if (rawVals.startsWith("(") && rawVals.endsWith(")")) {
                    rawVals = rawVals.substring(1, rawVals.length() - 1).trim();
                }
                String[] parts = rawVals.split(",", 2);
                String id = cleanQuotes(parts[0]);
                String jsonPart = parts.length > 1 ? parts[1].trim() : "{}";
                Map<String, Object> data = parseJsonOrKeyValues(jsonPart);

                JettraDatabase db = client.getDatabase(currentDatabase);
                db.getDocumentEngine(col).insert(id, data);
                db.getIndexManager().onDocumentInsert(col, id, data);
                return String.format("[SUCCESS] Registro con _id '%s' insertado en la colección '%s'.", id, col);
            }
        } catch (Exception ignored) {}
        return "[ERROR] Formato inválido. Uso: INSERT INTO <col> ID <id> JSON {...} o INSERT INTO <col> VALUES ('<id>', '{...}')";
    }

    private String handleGetRecord(String command) {
        String clean = command.replaceAll("(?i)^(GET|FIND ONE|FIND BY ID)\\s+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: GET <colección> <id>";
        String colName = parts[0].trim();
        String id = cleanQuotes(parts[1]);

        JettraDatabase db = client.getDatabase(currentDatabase);
        Map<String, Object> doc = db.getDocumentEngine(colName).findById(id);
        if (doc == null) {
            return String.format("[NOT FOUND] No se encontró el registro con _id '%s' en la colección '%s'.", id, colName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- REGISTRO [%s] EN '%s' ---\n", id, colName));
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            sb.append(String.format("  %-15s : %s\n", entry.getKey(), entry.getValue()));
            if (showReferences && (entry.getKey().startsWith("_ref_") || String.valueOf(entry.getValue()).contains("::"))) {
                String refVal = String.valueOf(entry.getValue());
                sb.append(String.format("    ↳ [JettraRef Resolución %s]: %s\n", 
                    lazyLoad ? "(Lazy Proxy On-Demand)" : "(Eager Carga Inmediata)", resolveReference(db, refVal)));
            }
        }
        return sb.toString();
    }

    private String handleFindAll(String command) {
        String clean = command.replaceAll("(?i)^(FIND ALL|SCAN)\\s+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 1 || parts[0].isBlank()) return "[ERROR] Uso: FIND ALL <colección> [LIMIT <n>]";
        String colName = cleanQuotes(parts[0]);

        int limit = pageSize;
        for (int i = 1; i < parts.length - 1; i++) {
            if ("LIMIT".equalsIgnoreCase(parts[i])) {
                try { limit = Integer.parseInt(parts[i + 1].replaceAll("[;]", "")); } catch (Exception ignored) {}
            }
        }

        JettraDatabase db = client.getDatabase(currentDatabase);
        List<Map<String, Object>> docs = db.getDocumentEngine(colName).findAll();
        if (docs.isEmpty()) {
            return String.format("[INFO] La colección '%s' está vacía.", colName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- COLECCIÓN '%s' (Mostrando %d de %d registros) ---\n", 
            colName, Math.min(docs.size(), limit), docs.size()));
        int count = 0;
        for (Map<String, Object> doc : docs) {
            if (++count > limit) break;
            sb.append(String.format("  [%02d] %s\n", count, doc));
        }
        return sb.toString();
    }

    private String handleUpdate(String command) {
        try {
            String after = command.substring("UPDATE ".length()).trim();
            int setIdx = after.toUpperCase().indexOf(" SET ");
            int whereIdx = after.toUpperCase().indexOf(" WHERE ");
            if (setIdx != -1) {
                String col = cleanQuotes(after.substring(0, setIdx).trim());
                String id = null;
                if (whereIdx != -1) {
                    String wherePart = after.substring(whereIdx + 7).trim();
                    id = cleanQuotes(wherePart.split("=")[1].trim());
                }
                String jsonPart = whereIdx != -1 ? after.substring(setIdx + 5, whereIdx).trim() : after.substring(setIdx + 5).trim();
                Map<String, Object> updates = parseJsonOrKeyValues(jsonPart);

                if (id != null) {
                    JettraDatabase db = client.getDatabase(currentDatabase);
                    db.getDocumentEngine(col).update(id, updates);
                    return String.format("[SUCCESS] Registro con _id '%s' actualizado en '%s'.", id, col);
                }
            }
        } catch (Exception ignored) {}
        return "[ERROR] Formato inválido. Uso: UPDATE <colección> SET {campo:valor} WHERE _id = '<id>'";
    }

    private String handleDeleteRecord(String command) {
        try {
            String clean = command.replaceAll("(?i)^(DELETE FROM|DELETE|REMOVE)\s+", "").trim();
            if (clean.toUpperCase().contains(" WHERE ")) {
                int whereIdx = clean.toUpperCase().indexOf(" WHERE ");
                String col = cleanQuotes(clean.substring(0, whereIdx).trim());
                String whereClause = clean.substring(whereIdx + 7).trim();
                String[] kv = whereClause.split("=");
                if (kv.length == 2) {
                    String id = cleanQuotes(kv[1].trim());
                    JettraDatabase db = client.getDatabase(currentDatabase);
                    boolean deleted = db.getDocumentEngine(col).delete(id);
                    db.getIndexManager().onDocumentDelete(col, id, null);
                    return deleted 
                        ? String.format("[SUCCESS] Registro con _id '%s' eliminado de '%s'.", id, col)
                        : String.format("[WARN] No se encontró el registro con _id '%s' para eliminar.", id);
                }
            } else {
                String[] parts = clean.split("\s+");
                if (parts.length >= 2) {
                    String col = cleanQuotes(parts[0]);
                    String id = cleanQuotes(parts[1]);
                    JettraDatabase db = client.getDatabase(currentDatabase);
                    boolean deleted = db.getDocumentEngine(col).delete(id);
                    db.getIndexManager().onDocumentDelete(col, id, null);
                    return deleted 
                        ? String.format("[SUCCESS] Registro con _id '%s' eliminado de '%s'.", id, col)
                        : String.format("[WARN] No se encontró el registro con _id '%s' para eliminar.", id);
                }
            }
        } catch (Exception ignored) {}
        return "[ERROR] Formato inválido. Uso: DELETE <col> <id> o DELETE FROM <col> WHERE _id = '<id>'";
    }

    // --- 11. Motores Multimodelo ---
    private String handleVectorIndex(String command) {
        try {
            String after = command.substring("VECTOR INDEX ".length()).trim();
            String[] parts = after.split("\\s+", 3);
            String col = parts[0].trim();
            String id = parts[1].trim();
            String vecStr = after.substring(after.indexOf('[') + 1, after.indexOf(']'));
            String[] floatsStr = vecStr.split(",");
            float[] floats = new float[floatsStr.length];
            for (int i = 0; i < floatsStr.length; i++) floats[i] = Float.parseFloat(floatsStr[i].trim());

            JettraDatabase db = client.getDatabase(currentDatabase);
            db.getVectorEngine(col, floats.length).index(id, floats);
            return String.format("[SUCCESS] Vector '%s' indexado en '%s' (%d dimensiones).", id, col, floats.length);
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: VECTOR INDEX <coleccion> <id> [f1, f2, f3]";
        }
    }

    private String handleVectorSearch(String command) {
        try {
            String after = command.substring("VECTOR SEARCH ".length()).trim();
            String col = after.split("\\s+")[0].trim();
            String vecStr = after.substring(after.indexOf('[') + 1, after.indexOf(']'));
            String[] floatsStr = vecStr.split(",");
            float[] floats = new float[floatsStr.length];
            for (int i = 0; i < floatsStr.length; i++) floats[i] = Float.parseFloat(floatsStr[i].trim());

            int k = 3;
            int kIdx = after.toUpperCase().indexOf(" K ");
            if (kIdx != -1) {
                k = Integer.parseInt(after.substring(kIdx + 3).replaceAll("[;]", "").trim());
            }

            JettraDatabase db = client.getDatabase(currentDatabase);
            var matches = db.getVectorEngine(col, floats.length).searchCosine(floats, k);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== VECTOR COSINE EN '%s' (k=%d) ===\n", col, k));
            int idx = 1;
            for (var match : matches) {
                sb.append(String.format("  [%02d] Vector ID: %-15s | Similaridad: %.4f\n", idx++, match.id(), match.score()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: VECTOR SEARCH <coleccion> [f1, f2] K <num>";
        }
    }

    private String handleGraphAddVertex(String command) {
        String[] parts = command.substring("GRAPH ADD VERTEX ".length()).trim().split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: GRAPH ADD VERTEX <grafo> <verticeId>";
        client.getDatabase(currentDatabase).getGraphEngine(parts[0]).addVertex(cleanQuotes(parts[1]));
        return String.format("[SUCCESS] Vértice '%s' agregado al grafo '%s'.", parts[1], parts[0]);
    }

    private String handleGraphAddEdge(String command) {
        try {
            String after = command.substring("GRAPH ADD EDGE ".length()).trim();
            String[] parts = after.split("\\s+");
            String graph = parts[0];
            String from = parts[1];
            String to = parts[2];
            String label = "RELATES_TO";
            int lblIdx = after.toUpperCase().indexOf("LABEL");
            if (lblIdx != -1) label = cleanQuotes(after.substring(lblIdx + 5).split("\\s+")[0]);
            double weight = 1.0;
            int wIdx = after.toUpperCase().indexOf("WEIGHT");
            if (wIdx != -1) weight = Double.parseDouble(after.substring(wIdx + 6).replaceAll("[;]", "").trim());

            client.getDatabase(currentDatabase).getGraphEngine(graph).addEdge(from, to, label, Map.of("weight", weight));
            return String.format("[SUCCESS] Arista (%s)-[%s, w=%.1f]->(%s) agregada en grafo '%s'.", from, label, weight, to, graph);
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: GRAPH ADD EDGE <grafo> <de> <a> [LABEL <nombre>] [WEIGHT <peso>]";
        }
    }

    private String handleGraphGetEdges(String command) {
        String clean = command.replaceAll("(?i)^(GRAPH GET EDGES|GRAPH EDGES)\\s+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: GRAPH GET EDGES <grafo> <verticeId>";
        var edges = client.getDatabase(currentDatabase).getGraphEngine(parts[0]).getOutboundEdges(cleanQuotes(parts[1]));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== ARISTAS SALIENTES DESDE '%s' EN '%s' ===\n", parts[1], parts[0]));
        int idx = 1;
        for (var edge : edges) {
            sb.append(String.format("  [%02d] -> Destino: %-15s | Relación: %-15s | Props: %s\n",
                idx++, edge.targetVertex(), edge.label(), edge.properties()));
        }
        return sb.toString();
    }

    private String handleTsRecord(String command) {
        try {
            String after = command.substring("TS RECORD ".length()).trim();
            String[] parts = after.split("\\s+");
            String series = parts[0];
            double val = Double.parseDouble(parts[1]);
            long timestamp = System.currentTimeMillis();
            int timeIdx = after.toUpperCase().indexOf("TIME");
            if (timeIdx != -1) timestamp = Long.parseLong(after.substring(timeIdx + 4).replaceAll("[;]", "").trim());

            client.getDatabase(currentDatabase).getTimeSeriesEngine(series).record(timestamp, val);
            return String.format("[SUCCESS] Métrica (timestamp=%d, valor=%.2f) registrada en '%s'.", timestamp, val, series);
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: TS RECORD <serie> <valor> [TIME <timestamp>]";
        }
    }

    private String handleTsRange(String command) {
        try {
            String clean = command.replaceAll("(?i)^(TS RANGE|TS QUERY)\\s+", "").trim();
            String[] parts = clean.split("\\s+");
            String series = parts[0];
            long start = Long.parseLong(parts[1]);
            long end = Long.parseLong(parts[2].replaceAll("[;]", ""));

            var points = client.getDatabase(currentDatabase).getTimeSeriesEngine(series).range(start, end);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== TIME SERIES EN '%s' [%d a %d] ===\n", series, start, end));
            int idx = 1;
            for (var entry : points.entrySet()) {
                sb.append(String.format("  [%02d] Timestamp: %d | Valor: %.4f\n", idx++, entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: TS RANGE <serie> <timestampInicio> <timestampFin>";
        }
    }

    private String handleKvPut(String command) {
        String[] parts = command.substring(7).trim().split("\\s+", 3);
        if (parts.length < 3) return "[ERROR] Uso: KV PUT <tabla> <clave> <valor>";
        client.getDatabase(currentDatabase).getKeyValueEngine(parts[0]).put(cleanQuotes(parts[1]), parts[2].getBytes(StandardCharsets.UTF_8));
        return String.format("[SUCCESS] Clave '%s' guardada en tabla KV '%s'.", parts[1], parts[0]);
    }

    private String handleKvGet(String command) {
        String[] parts = command.substring(7).trim().split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: KV GET <tabla> <clave>";
        byte[] val = client.getDatabase(currentDatabase).getKeyValueEngine(parts[0]).get(cleanQuotes(parts[1]));
        return val != null ? "[KV] " + new String(val, StandardCharsets.UTF_8) : "[NOT FOUND] Clave '" + parts[1] + "' no encontrada.";
    }

    // --- 12. Persistencia y Muestras ---
    public String installAllSampleDatabases() {
        // 1. sample_enterprise_db
        JettraDatabase enterprise = client.getDatabase("sample_enterprise_db");
        enterprise.getDocumentEngine("departments").insert("dep_rd", Map.of(
            "name", "Research & Advanced Computing", "budget", 15000000.0, "floor", 12
        ));
        enterprise.getDocumentEngine("employees").insert("emp_01", Map.of(
            "name", "Ada Lovelace", "title", "Lead Architect", "salary", 185000.0,
            "_ref_department", "document::departments#dep_rd",
            "_ref_vector", "vector::employee_biometrics#bio_01",
            "_ref_equipment", "kv::inventory_cache#laptop_mac_m3"
        ));
        enterprise.getDocumentEngine("products").insert("prod_01", Map.of(
            "name", "Quantum Neural Accelerator", "category", "Hardware", "price", 4500.0,
            "_ref_vector", "vector::product_embeddings#emb_01",
            "_ref_category", "graph::catalog_graph#cat_hardware"
        ));
        enterprise.getVectorEngine("product_embeddings", 3).index("emb_01", new float[]{0.15f, -0.42f, 0.88f});
        enterprise.getVectorEngine("employee_biometrics", 3).index("bio_01", new float[]{0.92f, 0.11f, -0.05f});
        enterprise.getGraphEngine("catalog_graph").addEdge("prod_01", "cat_hardware", "BELONGS_TO", Map.of("weight", 1.0));
        enterprise.getTimeSeriesEngine("telemetry").record(System.currentTimeMillis(), 42.5);
        enterprise.getKeyValueEngine("inventory_cache").put("laptop_mac_m3", "MacBook Pro M3 Max 64GB".getBytes(StandardCharsets.UTF_8));
        enterprise.getIndexManager().createIndex("employees", "idx_emp_name", "name", "BTREE", false, enterprise.getDocumentEngine("employees"));
        enterprise.getIndexManager().createIndex("products", "idx_prod_cat", "category", "HASH", false, enterprise.getDocumentEngine("products"));

        // 2. sample_ecommerce_db
        JettraDatabase ecommerce = client.getDatabase("sample_ecommerce_db");
        ecommerce.getDocumentEngine("customers").insert("cust_101", Map.of(
            "name", "Elena Rostova", "tier", "VIP_PLATINUM", "country", "ES", "email", "elena@quantum.io"
        ));
        ecommerce.getDocumentEngine("orders").insert("ord_9901", Map.of(
            "customer_id", "cust_101", "total", 899.50, "status", "PAID",
            "_ref_customer", "document::customers#cust_101",
            "_ref_product", "document::products#prod_01"
        ));
        ecommerce.getColumnarEngine("order_analytics").appendRow(Map.of("revenue", 899.50));
        ecommerce.getKeyValueEngine("shopping_carts").put("cart_cust_101", "item_quantum_gpu:2".getBytes(StandardCharsets.UTF_8));
        ecommerce.getIndexManager().createIndex("customers", "idx_cust_tier", "tier", "HASH", false, ecommerce.getDocumentEngine("customers"));

        // 3. sample_ai_graph_db
        JettraDatabase aiGraph = client.getDatabase("sample_ai_graph_db");
        aiGraph.getGraphEngine("knowledge_network").addEdge("DeepLearning", "TransformerModel", "FOUNDATION_OF", Map.of("depth", 4.0));
        aiGraph.getGraphEngine("knowledge_network").addEdge("TransformerModel", "AttentionMechanism", "USES", Map.of("weight", 0.95));
        aiGraph.getVectorEngine("concept_embeddings", 3).index("vec_transformer", new float[]{0.85f, 0.12f, -0.33f});
        aiGraph.getDocumentEngine("prompts_corpus").insert("prompt_01", Map.of(
            "role", "system", "text", "You are an autonomous distributed DB engine expert.",
            "_ref_concept", "graph::knowledge_network#TransformerModel",
            "_ref_embedding", "vector::concept_embeddings#vec_transformer"
        ));

        // 4. sample_iot_telemetry_db
        JettraDatabase iot = client.getDatabase("sample_iot_telemetry_db");
        long now = System.currentTimeMillis();
        iot.getTimeSeriesEngine("sensor_temperature").record(now - 2000, 24.5);
        iot.getTimeSeriesEngine("sensor_temperature").record(now - 1000, 25.1);
        iot.getTimeSeriesEngine("sensor_temperature").record(now, 24.8);
        iot.getTimeSeriesEngine("sensor_vibration").record(now, 0.042);
        iot.getDocumentEngine("smart_devices").insert("iot_gateway_01", Map.of(
            "model", "EdgeGate-X25", "firmware", "v2.5.0-LTS", "status", "ONLINE"
        ));
        iot.getGeospatialEngine("device_locations").insertPoint("iot_gateway_01", 40.4168, -3.7038);

        // 5. sample_financial_db
        JettraDatabase finance = client.getDatabase("sample_financial_db");
        finance.getDocumentEngine("transactions").insert("tx_001", Map.of(
            "from_account", "ACC_7712", "to_account", "ACC_9941", "amount", 15000.0, "currency", "USD",
            "_ref_client", "document::customers#cust_101"
        ));
        finance.getTimeSeriesEngine("stock_feed").record(now, 184.50);

        // Flush de persistencia física a disco
        try {
            enterprise.flushMemTable();
            ecommerce.flushMemTable();
            aiGraph.flushMemTable();
            iot.flushMemTable();
            finance.flushMemTable();
        } catch (Exception ignored) {}

        this.currentDatabase = "sample_enterprise_db";

        return """
            [SUCCESS] ¡Todas las 5 bases de datos de ejemplo instaladas y persistidas exitosamente!
              1. 'sample_enterprise_db'   -> Empleados, Departamentos, Índices B-Tree, Vectores Biométricos, Grafos
              2. 'sample_ecommerce_db'    -> Clientes VIP, Órdenes con JettraRef, Analítica Columnar, Carritos KV
              3. 'sample_ai_graph_db'     -> Red de Conocimiento de Grafos, Embeddings Conceptuales, Prompts
              4. 'sample_iot_telemetry_db'-> Sensores Temperatura/Vibración, Dispositivos Smart, Geo-localización
              5. 'sample_financial_db'    -> Transacciones Financieras con JettraRef a Clientes, Series Temporales
              * Para cargar la base de datos masiva de 3 millones de objetos ejecute: LOAD SAMPLE example_factura_db
            Base de datos activa conmutada a: 'sample_enterprise_db'
            """;
    }


    public String installFacturaSampleDatabase() {
        long start = System.currentTimeMillis();
        JettraDatabase db = client.getDatabase("example_factura_db");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Task 1: Clientes (200,000)
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("clientes");
                int total = 200_000;
                int chunkSize = 25_000;
                for (int base = 0; base < total; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, total);
                    for (int i = base; i < end; i++) {
                        String id = "cli_" + i;
                        batch.put(id, Map.of(
                            "_id", id,
                            "razon_social", "Corporación Comercial " + i + " S.A.",
                            "rfc_tax_id", "RFC-PAN-" + (1000000 + i),
                            "ciudad", (i % 2 == 0) ? "Ciudad de Panamá" : "Colón",
                            "limite_credito", 50000.0 + (i % 1000) * 100
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 2: Facturas (1,000,000 con referencias cruzadas)
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("facturas");
                int total = 1_000_000;
                int chunkSize = 50_000;
                for (int base = 0; base < total; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, total);
                    for (int i = base; i < end; i++) {
                        String id = "fac_" + i;
                        batch.put(id, Map.of(
                            "_id", id,
                            "fecha", "2026-09-29",
                            "total", 250.0 + (i % 2000),
                            "estado", "TIMBRADA",
                            "_ref_cliente", "document::clientes#cli_" + (i % 200_000),
                            "_ref_detalle", "document::detalles_factura#det_" + i,
                            "_ref_folio", "kv::cache_folios#fol_" + (i % 300_000),
                            "_ref_vector", "vector::factura_embeddings#emb_" + (i % 200_000),
                            "_ref_sucursal", "geospatial::sucursales_fiscales#suc_" + (i % 25_000)
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 3: Detalles de Factura (1,000,000)
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("detalles_factura");
                int total = 1_000_000;
                int chunkSize = 50_000;
                for (int base = 0; base < total; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, total);
                    for (int i = base; i < end; i++) {
                        String id = "det_" + i;
                        int qty = (i % 10) + 1;
                        double price = 50.0 + (i % 200);
                        batch.put(id, Map.of(
                            "_id", id,
                            "concepto", "Servicio Cloud / Licencia Empresarial #" + (i % 100),
                            "cantidad", qty,
                            "precio_unitario", price,
                            "subtotal", qty * price,
                            "_ref_factura", "document::facturas#fac_" + i
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 4: KV Cache de Folios (300,000)
            executor.submit(() -> {
                var kvEngine = db.getKeyValueEngine("cache_folios");
                int total = 300_000;
                int chunkSize = 30_000;
                byte[] rawVal = "TIMBRADO_OK_CFDI_2026_SAT".getBytes(StandardCharsets.UTF_8);
                for (int base = 0; base < total; base += chunkSize) {
                    Map<String, byte[]> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, total);
                    for (int i = base; i < end; i++) {
                        batch.put("fol_" + i, rawVal);
                    }
                    kvEngine.putBatch(batch);
                }
            });

            // Task 5: Factura Embeddings (200,000 vectores 3d)
            executor.submit(() -> {
                var vecEngine = db.getVectorEngine("factura_embeddings", 3);
                int total = 200_000;
                int chunkSize = 25_000;
                float[] emb = new float[]{0.75f, -0.20f, 0.60f};
                for (int base = 0; base < total; base += chunkSize) {
                    Map<String, float[]> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, total);
                    for (int i = base; i < end; i++) {
                        batch.put("emb_" + i, emb);
                    }
                    vecEngine.indexBatch(batch);
                }
            });

            // Task 6: Red Comercial Graph (200,000 vértices de clientes y facturas conectadas)
            executor.submit(() -> {
                var graphEngine = db.getGraphEngine("red_comercial");
                int totalEdges = 100_000; // 100k aristas conectando 100k clientes y 100k facturas = 200k vértices
                int chunkSize = 25_000;
                for (int base = 0; base < totalEdges; base += chunkSize) {
                    Map<String, List<io.jettra.store.engine.models.GraphEngine.Edge>> batch = new HashMap<>(chunkSize);
                    int end = Math.min(base + chunkSize, totalEdges);
                    for (int i = base; i < end; i++) {
                        String from = "cli_" + i;
                        String to = "fac_" + i;
                        batch.put(from, List.of(new io.jettra.store.engine.models.GraphEngine.Edge(
                            to, "FACTURA_EMITIDA", Map.of("weight", 1.0)
                        )));
                    }
                    graphEngine.addEdgesBatch(batch);
                }
            });

            // Task 7: TimeSeries Volumen Facturación (50,000)
            executor.submit(() -> {
                var tsEngine = db.getTimeSeriesEngine("volumen_facturacion");
                long now = System.currentTimeMillis();
                Map<Long, Double> batch = new HashMap<>(50_000);
                for (int i = 0; i < 50_000; i++) {
                    batch.put(now - (i * 1000L), 2500.0 + (i % 500));
                }
                tsEngine.recordBatch(batch);
            });

            // Task 8: Geospatial Sucursales (25,000)
            executor.submit(() -> {
                var geoEngine = db.getGeospatialEngine("sucursales_fiscales");
                Map<String, io.jettra.store.engine.models.GeospatialEngine.GeoPoint> batch = new HashMap<>(25_000);
                for (int i = 0; i < 25_000; i++) {
                    String id = "suc_" + i;
                    batch.put(id, new io.jettra.store.engine.models.GeospatialEngine.GeoPoint(
                        id, 8.9800 + (i % 100) * 0.001, -79.5200 + (i % 100) * 0.001
                    ));
                }
                geoEngine.insertBatch(batch);
            });

            // Task 9: Columnar Analítica Fiscal (25,000)
            executor.submit(() -> {
                var colEngine = db.getColumnarEngine("analitica_fiscal");
                List<Double> sub = new ArrayList<>(25_000);
                List<Double> iva = new ArrayList<>(25_000);
                List<Double> tot = new ArrayList<>(25_000);
                for (int i = 0; i < 25_000; i++) {
                    double s = 1000.0 + (i % 500);
                    double iv = s * 0.07;
                    sub.add(s);
                    iva.add(iv);
                    tot.add(s + iv);
                }
                colEngine.appendBatch(
                    Map.of("subtotal", sub, "iva", iva, "total", tot),
                    Map.of(),
                    25_000
                );
            });
        }

        // Crear índices secundarios
        try {
            db.getIndexManager().createIndex("facturas", "idx_fac_cliente", "_ref_cliente", "HASH", false, db.getDocumentEngine("facturas"));
            db.getIndexManager().createIndex("clientes", "idx_cli_rfc", "rfc_tax_id", "BTREE", false, db.getDocumentEngine("clientes"));
        } catch (Exception ignored) {}

        // Flush persistencia física
        try {
            db.flushMemTable();
        } catch (Exception ignored) {}

        this.currentDatabase = "example_factura_db";
        long duration = System.currentTimeMillis() - start;

        return String.format("""
            ==============================================================================================
                    CARGA MASIVA EXITOSA: BASE DE DATOS 'example_factura_db' (3,000,000 OBJETOS)
            ==============================================================================================
            [OK] Tiempo de Inserción y Timbrado Multimodelo: %d ms (Java 25 Virtual Threads)
            [OK] Objetos Repartidos en 9 Buckets Especializados:
              * [DOCUMENT]   'facturas'              : 1,000,000 facturas electrónicas timbradas
              * [DOCUMENT]   'detalles_factura'      : 1,000,000 renglones/items vinculados
              * [DOCUMENT]   'clientes'              :   200,000 clientes empresariales con RFC/RUC
              * [KEYVALUE]   'cache_folios'          :   300,000 folios fiscales en caché ultrarrápida
              * [VECTOR]     'factura_embeddings'    :   200,000 vectores 3D indexados para IA
              * [GRAPH]      'red_comercial'         :   200,000 vértices conectados (clientes -> facturas)
              * [TIMESERIES] 'volumen_facturacion'   :    50,000 métricas históricas de facturación
              * [GEOSPATIAL] 'sucursales_fiscales'   :    25,000 puntos GIS de sucursales emisoras
              * [COLUMNAR]   'analitica_fiscal'      :    25,000 filas de cálculo analítico de IVA/Totales
            ----------------------------------------------------------------------------------------------
            GRAN TOTAL EN 'example_factura_db': 3,000,000 objetos multimodelo conectados mediante JettraRef.
            Índices Creados: idx_fac_cliente (HASH), idx_cli_rfc (BTREE)
            Base de datos activa conmutada a: 'example_factura_db'
            ==============================================================================================
            """, duration);
    }

    private String handleBackup(String command) {
        try {
            String after = command.substring("BACKUP DATABASE".length()).trim();
            String dbName = currentDatabase;
            String dest = "./data/jettra/backup_" + dbName + "_" + System.currentTimeMillis() + ".snap";
            if (!after.isBlank()) {
                if (after.toUpperCase().contains(" TO ")) {
                    String[] parts = after.split("(?i)\s+TO\s+");
                    dbName = cleanQuotes(parts[0].trim());
                    dest = cleanQuotes(parts[1].trim());
                } else {
                    String[] parts = after.split("\s+");
                    dbName = cleanQuotes(parts[0]);
                    if (parts.length > 1) dest = cleanQuotes(parts[1]);
                }
            }
            JettraDatabase db = client.getDatabase(dbName);
            var meta = BackupManager.backupDatabase(db, Path.of(dest));
            return String.format("[SUCCESS] Respaldo de '%s' completado en '%s'. Tamaño: %d bytes (CRC32: %d).",
                meta.databaseName(), dest, meta.sizeBytes(), meta.checksum());
        } catch (Exception e) {
            return "[ERROR] Error al crear respaldo: " + e.getMessage();
        }
    }

    private String handleRestore(String command) {
        try {
            String after = command.substring("RESTORE DATABASE".length()).trim();
            Path snap;
            String targetDb;
            if (after.toUpperCase().contains(" FROM ")) {
                String[] parts = after.split("(?i)\s+FROM\s+");
                targetDb = cleanQuotes(parts[0].trim());
                snap = Path.of(cleanQuotes(parts[1].trim()));
            } else {
                String[] parts = after.split("\s+");
                if (parts.length < 2) return "[ERROR] Uso: RESTORE DATABASE <archivo.snap> <nombreBaseDatosDestino> o RESTORE DATABASE <db> FROM '<archivo.snap>'";
                snap = Path.of(cleanQuotes(parts[0]));
                targetDb = cleanQuotes(parts[1]);
            }
            JettraDatabase db = client.getDatabase(targetDb);
            boolean ok = BackupManager.restoreDatabase(snap, db);
            return ok ? String.format("[SUCCESS] Snapshot '%s' restaurado en base de datos '%s'.", snap, targetDb)
                      : "[ERROR] Falló la restauración del snapshot.";
        } catch (Exception e) {
            return "[ERROR] Error al restaurar respaldo: " + e.getMessage();
        }
    }

    // --- Control de Colecciones ---
    private String handleShowCollections() {
        JettraDatabase db = client.getDatabase(currentDatabase);
        StringBuilder sb = new StringBuilder();
        sb.append("+---------------------------+---------------------+---------+\n");
        sb.append("| Colección                 | Motor Multimodelo   | Registros|\n");
        sb.append("+---------------------------+---------------------+---------+\n");

        int count = 0;
        for (String c : db.getDocumentEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "DOCUMENT", db.getDocumentEngine(c).count()));
            count++;
        }
        for (String c : db.getVectorEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "VECTOR (Cosine)", db.getVectorEngine(c, 3).size()));
            count++;
        }
        for (String c : db.getGraphEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "GRAPH (Adjacency)", db.getGraphEngine(c).getVertices().size()));
            count++;
        }
        for (String c : db.getTimeSeriesEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "TIMESERIES", db.getTimeSeriesEngine(c).size()));
            count++;
        }
        for (String c : db.getKeyValueEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "KEY-VALUE", db.getKeyValueEngine(c).size()));
            count++;
        }
        for (String c : db.getColumnarEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "COLUMNAR", db.getColumnarEngine(c).size()));
            count++;
        }
        for (String c : db.getGeospatialEngineNames()) {
            sb.append(String.format("| %-25s | %-19s | %-7d |\n", c, "GEOSPATIAL", db.getGeospatialEngine(c).size()));
            count++;
        }

        if (count == 0) {
            sb.append("| (Sin colecciones activas) | -                   | 0       |\n");
        }
        sb.append("+---------------------------+---------------------+---------+\n");
        sb.append(String.format("Total: %d coleccion(es) en base de datos '%s'\n", count, currentDatabase));
        return sb.toString();
    }

    private String handleCreateCollection(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3) return "[ERROR] Uso: CREATE COLLECTION <nombre> [TYPE <tipo>]";
        String colName = cleanQuotes(parts[2]);
        String type = "DOCUMENT";
        for (int i = 3; i < parts.length - 1; i++) {
            if ("TYPE".equalsIgnoreCase(parts[i])) {
                type = parts[i + 1].toUpperCase();
            }
        }

        JettraDatabase db = client.getDatabase(currentDatabase);
        switch (type) {
            case "VECTOR" -> db.getVectorEngine(colName, 3);
            case "GRAPH" -> db.getGraphEngine(colName);
            case "TIMESERIES" -> db.getTimeSeriesEngine(colName);
            case "KEYVALUE", "KV" -> db.getKeyValueEngine(colName);
            case "COLUMNAR" -> db.getColumnarEngine(colName);
            case "GEOSPATIAL", "GEO" -> db.getGeospatialEngine(colName);
            default -> db.getDocumentEngine(colName);
        }
        return String.format("[SUCCESS] Colección '%s' creada con motor multimodelo '%s' en base de datos '%s'.", colName, type, currentDatabase);
    }

    // --- Control de Buckets / Units, Conteo y Visualización de Registros ---
    private String handleShowBuckets(String command) {
        JettraDatabase db = client.getDatabase(currentDatabase);
        record UnitRow(String engine, String unitType, String name, long count, String status) {}
        List<UnitRow> rows = new ArrayList<>();

        String clean = command.replaceAll("(?i)^SHOW\\s+(BUCKETS|BUCKET|UNITS|UNIT)\\s*", "").trim();
        String filterEngine = clean.isBlank() ? null : clean.toUpperCase();

        // 1. Documents
        if (filterEngine == null || filterEngine.contains("DOC")) {
            for (String col : db.getDocumentEngineNames()) {
                long c = db.getDocumentEngine(col).count();
                rows.add(new UnitRow("DOCUMENT", "Collection", col, c, "ACTIVE (In-Memory)"));
            }
        }
        // 2. Vectors
        if (filterEngine == null || filterEngine.contains("VEC")) {
            for (String col : db.getVectorEngineNames()) {
                var vEng = db.getVectorEngine(col, 3);
                rows.add(new UnitRow("VECTOR", "Vector Index [" + vEng.getDimensions() + "d]", col, vEng.size(), "INDEXED (HNSW)"));
            }
        }
        // 3. Graph
        if (filterEngine == null || filterEngine.contains("GRAPH")) {
            for (String col : db.getGraphEngineNames()) {
                var gEng = db.getGraphEngine(col);
                rows.add(new UnitRow("GRAPH", "Property Graph", col, gEng.size(), "TOPOLOGY (In-Memory)"));
            }
        }
        // 4. TimeSeries
        if (filterEngine == null || filterEngine.contains("TIME") || filterEngine.contains("SERIES")) {
            for (String col : db.getTimeSeriesEngineNames()) {
                var ts = db.getTimeSeriesEngine(col);
                rows.add(new UnitRow("TIMESERIES", "Metric Series", col, ts.size(), "APPEND-ONLY (Delta)"));
            }
        }
        // 5. Key-Value
        if (filterEngine == null || filterEngine.contains("KEY") || filterEngine.contains("KV")) {
            for (String col : db.getKeyValueEngineNames()) {
                var kv = db.getKeyValueEngine(col);
                rows.add(new UnitRow("KEYVALUE", "KV Store", col, kv.size(), "HASH-MAP (Persistent)"));
            }
        }
        // 6. Geospatial
        if (filterEngine == null || filterEngine.contains("GEO") || filterEngine.contains("GIS")) {
            for (String col : db.getGeospatialEngineNames()) {
                var geo = db.getGeospatialEngine(col);
                rows.add(new UnitRow("GEOSPATIAL", "Spatial Layer", col, geo.size(), "R-TREE (Spatial)"));
            }
        }
        // 7. Columnar
        if (filterEngine == null || filterEngine.contains("COL")) {
            for (String col : db.getColumnarEngineNames()) {
                var colEng = db.getColumnarEngine(col);
                rows.add(new UnitRow("COLUMNAR", "Column Family", col, colEng.size(), "ARROW/SLOT (Compressed)"));
            }
        }

        if (rows.isEmpty()) {
            return String.format("[INFO] No se encontraron buckets/units en la base de datos '%s'%s.",
                currentDatabase, filterEngine != null ? " para el motor " + filterEngine : "");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("==============================================================================================\n");
        sb.append(String.format("                BUCKETS / UNITS EN BASE DE DATOS: '%s'                                        \n", currentDatabase));
        sb.append("==============================================================================================\n");
        sb.append("+-------------+----------------------+--------------------+-----------+----------------------+\n");
        sb.append("| Motor       | Tipo de Unidad       | Nombre de Unidad   | Registros | Estado               |\n");
        sb.append("+-------------+----------------------+--------------------+-----------+----------------------+\n");
        for (UnitRow r : rows) {
            sb.append(String.format("| %-11s | %-20s | %-18s | %-9d | %-20s |\n",
                r.engine(), r.unitType(), r.name(), r.count(), r.status()));
        }
        sb.append("+-------------+----------------------+--------------------+-----------+----------------------+\n");
        sb.append(String.format("Total: %d bucket(s)/unit(s) registrados en la base de datos '%s'.\n", rows.size(), currentDatabase));
        return sb.toString();
    }

    private String handleShowRecords(String command) {
        String clean = command.replaceAll("(?i)^SHOW\\s+RECORDS(\\s+FROM)?\\s*", "").trim();
        JettraDatabase db = client.getDatabase(currentDatabase);
        if (clean.isBlank()) {
            var all = db.getAllCollectionNames();
            if (all.isEmpty()) {
                return String.format("[INFO] No hay buckets/units creados en la base de datos '%s'.", currentDatabase);
            }
            clean = all.iterator().next();
        }

        String[] parts = clean.split("\\s+");
        String unitName = cleanQuotes(parts[0]);
        int limit = pageSize;
        for (int i = 1; i < parts.length - 1; i++) {
            if ("LIMIT".equalsIgnoreCase(parts[i])) {
                try { limit = Integer.parseInt(parts[i + 1].replaceAll("[;]", "")); } catch (Exception ignored) {}
            }
        }

        // 1. DOCUMENT
        if (db.getDocumentEngineNames().contains(unitName)) {
            var docEngine = db.getDocumentEngine(unitName);
            List<Map<String, Object>> docs = docEngine.findAll();
            if (docs.isEmpty()) return String.format("[INFO] El bucket de documentos '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE DOCUMENT BUCKET '%s' (Mostrando %d de %d) ===\n",
                unitName, Math.min(docs.size(), limit), docs.size()));
            int idx = 1;
            for (Map<String, Object> doc : docs) {
                if (idx > limit) break;
                sb.append(String.format("  [%02d] _id: %-15s -> %s\n", idx++, doc.getOrDefault("_id", "?"), doc));
                if (showReferences) {
                    for (var entry : doc.entrySet()) {
                        if (entry.getKey().startsWith("_ref_") || String.valueOf(entry.getValue()).contains("::")) {
                            sb.append(String.format("       ↳ Ref [%s]: %s\n", entry.getKey(), resolveReference(db, String.valueOf(entry.getValue()))));
                        }
                    }
                }
            }
            return sb.toString();
        }

        // 2. VECTOR
        if (db.getVectorEngineNames().contains(unitName)) {
            var vecEngine = db.getVectorEngine(unitName, 3);
            var vecs = vecEngine.getAllVectors();
            if (vecs.isEmpty()) return String.format("[INFO] El bucket vectorial '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE VECTOR BUCKET '%s' (Dim: %d | Mostrando %d de %d) ===\n",
                unitName, vecEngine.getDimensions(), Math.min(vecs.size(), limit), vecs.size()));
            int idx = 1;
            for (var entry : vecs.entrySet()) {
                if (idx > limit) break;
                sb.append(String.format("  [%02d] Vector ID: %-15s -> %s\n", idx++, entry.getKey(), Arrays.toString(entry.getValue())));
            }
            return sb.toString();
        }

        // 3. GRAPH
        if (db.getGraphEngineNames().contains(unitName)) {
            var graph = db.getGraphEngine(unitName);
            if (graph.getVertices().isEmpty()) return String.format("[INFO] El bucket de grafos '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE GRAPH BUCKET '%s' (%d vértices) ===\n", unitName, graph.getVertices().size()));
            int idx = 1;
            for (String v : graph.getVertices()) {
                if (idx > limit) break;
                var out = graph.getOutboundEdges(v);
                sb.append(String.format("  [%02d] Vértice: %-15s (Aristas salientes: %d)\n", idx++, v, out.size()));
                for (var e : out) {
                    sb.append(String.format("       ↳ (%s)-[%s, props=%s]->(%s)\n", v, e.label(), e.properties(), e.targetVertex()));
                }
            }
            return sb.toString();
        }

        // 4. TIMESERIES
        if (db.getTimeSeriesEngineNames().contains(unitName)) {
            var ts = db.getTimeSeriesEngine(unitName);
            var pts = ts.getAll();
            if (pts.isEmpty()) return String.format("[INFO] El bucket de series temporales '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE TIMESERIES BUCKET '%s' (Mostrando %d de %d) ===\n",
                unitName, Math.min(pts.size(), limit), pts.size()));
            int idx = 1;
            for (var entry : pts.entrySet()) {
                if (idx > limit) break;
                sb.append(String.format("  [%02d] Timestamp: %-15d -> Valor: %.4f\n", idx++, entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }

        // 5. KEYVALUE
        if (db.getKeyValueEngineNames().contains(unitName)) {
            var kv = db.getKeyValueEngine(unitName);
            var store = kv.getAll();
            if (store.isEmpty()) return String.format("[INFO] El bucket clave-valor '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE KEYVALUE BUCKET '%s' (Mostrando %d de %d) ===\n",
                unitName, Math.min(store.size(), limit), store.size()));
            int idx = 1;
            for (var entry : store.entrySet()) {
                if (idx > limit) break;
                String valStr = new String(entry.getValue(), StandardCharsets.UTF_8);
                sb.append(String.format("  [%02d] Clave: %-20s -> Valor: %s\n", idx++, entry.getKey(), valStr));
            }
            return sb.toString();
        }

        // 6. GEOSPATIAL
        if (db.getGeospatialEngineNames().contains(unitName)) {
            var geo = db.getGeospatialEngine(unitName);
            var pts = geo.getAllPoints();
            if (pts.isEmpty()) return String.format("[INFO] El bucket geoespacial '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE GEOSPATIAL BUCKET '%s' (Mostrando %d de %d) ===\n",
                unitName, Math.min(pts.size(), limit), pts.size()));
            int idx = 1;
            for (var p : pts.values()) {
                if (idx > limit) break;
                sb.append(String.format("  [%02d] Feature ID: %-15s -> Lat: %.6f, Lon: %.6f\n", idx++, p.id(), p.latitude(), p.longitude()));
            }
            return sb.toString();
        }

        // 7. COLUMNAR
        if (db.getColumnarEngineNames().contains(unitName)) {
            var col = db.getColumnarEngine(unitName);
            if (col.getRowCount() == 0) return String.format("[INFO] El bucket columnar '%s' está vacío.", unitName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== REGISTROS DE COLUMNAR BUCKET '%s' (Total Filas: %d) ===\n", unitName, col.getRowCount()));
            sb.append("  Columnas Numéricas: ").append(col.getNumericColumns().keySet()).append("\n");
            sb.append("  Columnas Texto:     ").append(col.getTextColumns().keySet()).append("\n");
            return sb.toString();
        }

        return String.format("[NOT FOUND] No se encontró el bucket/unit '%s' en la base de datos '%s'. Use 'show buckets' para ver las unidades disponibles.",
            unitName, currentDatabase);
    }

    private String handleCount(String command) {
        String clean = command.replaceAll("(?i)^COUNT(\\s+FROM)?\\s*", "").trim();
        JettraDatabase db = client.getDatabase(currentDatabase);

        if (clean.isBlank() || clean.equalsIgnoreCase("ALL") || clean.equals("*")) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== CONTEO TOTAL DE REGISTROS EN BASE DE DATOS: '%s' ===\n", currentDatabase));
            long grandTotal = 0;
            for (String col : db.getDocumentEngineNames()) {
                long c = db.getDocumentEngine(col).count();
                grandTotal += c;
                sb.append(String.format("  * [DOCUMENT]   %-22s : %d registro(s)\n", col, c));
            }
            for (String col : db.getVectorEngineNames()) {
                long c = db.getVectorEngine(col, 3).size();
                grandTotal += c;
                sb.append(String.format("  * [VECTOR]     %-22s : %d vector(es)\n", col, c));
            }
            for (String col : db.getGraphEngineNames()) {
                long c = db.getGraphEngine(col).size();
                grandTotal += c;
                sb.append(String.format("  * [GRAPH]      %-22s : %d vértice(s)\n", col, c));
            }
            for (String col : db.getTimeSeriesEngineNames()) {
                long c = db.getTimeSeriesEngine(col).size();
                grandTotal += c;
                sb.append(String.format("  * [TIMESERIES] %-22s : %d punto(s)\n", col, c));
            }
            for (String col : db.getKeyValueEngineNames()) {
                long c = db.getKeyValueEngine(col).size();
                grandTotal += c;
                sb.append(String.format("  * [KEYVALUE]   %-22s : %d clave(s)\n", col, c));
            }
            for (String col : db.getColumnarEngineNames()) {
                long c = db.getColumnarEngine(col).size();
                grandTotal += c;
                sb.append(String.format("  * [COLUMNAR]   %-22s : %d fila(s)\n", col, c));
            }
            for (String col : db.getGeospatialEngineNames()) {
                long c = db.getGeospatialEngine(col).size();
                grandTotal += c;
                sb.append(String.format("  * [GEOSPATIAL] %-22s : %d punto(s) GIS\n", col, c));
            }
            sb.append(String.format("Gran Total en '%s': %d registro(s) multimodelo.\n", currentDatabase, grandTotal));
            return sb.toString();
        }

        String unitName = cleanQuotes(clean.split("\\s+")[0]);

        if (db.getDocumentEngineNames().contains(unitName)) {
            long c = db.getDocumentEngine(unitName).count();
            return String.format("[COUNT] [DOCUMENT] '%s': %d registro(s).", unitName, c);
        }
        if (db.getVectorEngineNames().contains(unitName)) {
            long c = db.getVectorEngine(unitName, 3).size();
            return String.format("[COUNT] [VECTOR] '%s': %d vector(es).", unitName, c);
        }
        if (db.getGraphEngineNames().contains(unitName)) {
            long c = db.getGraphEngine(unitName).size();
            return String.format("[COUNT] [GRAPH] '%s': %d vértice(s).", unitName, c);
        }
        if (db.getTimeSeriesEngineNames().contains(unitName)) {
            long c = db.getTimeSeriesEngine(unitName).size();
            return String.format("[COUNT] [TIMESERIES] '%s': %d punto(s) temporales.", unitName, c);
        }
        if (db.getKeyValueEngineNames().contains(unitName)) {
            long c = db.getKeyValueEngine(unitName).size();
            return String.format("[COUNT] [KEYVALUE] '%s': %d clave(s).", unitName, c);
        }
        if (db.getColumnarEngineNames().contains(unitName)) {
            long c = db.getColumnarEngine(unitName).size();
            return String.format("[COUNT] [COLUMNAR] '%s': %d fila(s).", unitName, c);
        }
        if (db.getGeospatialEngineNames().contains(unitName)) {
            long c = db.getGeospatialEngine(unitName).size();
            return String.format("[COUNT] [GEOSPATIAL] '%s': %d punto(s) espaciales.", unitName, c);
        }

        return String.format("[NOT FOUND] El bucket/unit '%s' no existe en la base de datos '%s'.", unitName, currentDatabase);
    }

    private String handleDropCollection(String command) {
        String col = cleanQuotes(command.substring("DROP COLLECTION ".length()).trim());
        boolean ok = client.getDatabase(currentDatabase).dropCollection(col);
        return ok ? String.format("[SUCCESS] Colección '%s' eliminada.", col)
                  : String.format("[WARN] La colección '%s' no existía.", col);
    }

    // --- Auxiliares de Parseo ---
    private String cleanQuotes(String text) {
        if (text == null) return "";
        return text.replace(";", "").replace("'", "").replace("\"", "").trim();
    }

    private Map<String, Object> parseJsonOrKeyValues(String raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;

        String clean = raw.trim();
        if (clean.startsWith("{") && clean.endsWith("}")) {
            clean = clean.substring(1, clean.length() - 1);
        }

        String[] pairs = clean.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("[:=]", 2);
            if (kv.length == 2) {
                String key = cleanQuotes(kv[0].trim());
                String valStr = cleanQuotes(kv[1].trim());
                try {
                    if (valStr.contains(".")) {
                        map.put(key, Double.parseDouble(valStr));
                    } else {
                        map.put(key, Long.parseLong(valStr));
                    }
                } catch (NumberFormatException e) {
                    if ("true".equalsIgnoreCase(valStr) || "false".equalsIgnoreCase(valStr)) {
                        map.put(key, Boolean.parseBoolean(valStr));
                    } else {
                        map.put(key, valStr);
                    }
                }
            }
        }
        return map;
    }

    public String getHelpText() {
        return """
            ==============================================================================================
                                     JETTRASTORE SHELL - GUÍA COMPLETA DE COMANDOS
            ==============================================================================================
            1. CONEXIÓN Y SESIÓN:
              connect <url> <port>                  Establece la dirección del nodo servidor JettraStore.
              connect <nombre-perfil>               Conecta utilizando un perfil previamente guardado.
              login <username> <password>            Autentica y obtiene un token de sesión criptográfico JettraJWT.
              logout                                Cierra la sesión activa y revoca el token JWT.
              save connection <nombre> Guarda el perfil de conexión actual con un alias.
              remove connection <nombre>            Elimina un perfil de conexión guardado.
              list connections / list conections    Lista todos los perfiles de conexión guardados.

            2. TELEMETRÍA Y CLÚSTER:
              status                                Monitorea RAM Panama FFM, CPU Loom y Disco LSM.
              show nodes / list nodes               Muestra la topología del clúster Raft y nodos del anillo.
              add node <id> <host> <port> [ROLE]    Agrega un nuevo nodo secundario al clúster Raft.
              remove node <id>                      Remueve un nodo réplica del anillo dinámico.
              start node <id>                       Inicia y activa el procesamiento para un nodo específico.
              stop node <id>                        Detiene un nodo réplica (pausa el tráfico de descarga).

            3. BASES DE DATOS, BUCKETS/UNITS Y PERSISTENCIA EN DISCO:
              show databases / show dbs             Lista todas las bases de datos detectadas en disco y memoria.
              show buckets / show unit              Muestra todos los buckets/units de cada motor con sus conteos.
              show records <bucket> [LIMIT n]       Muestra los registros que contiene el bucket indicado en cualquier motor.
              count <bucket> / count all            Cuenta los registros de un bucket o el total de la base de datos activa.
              show samples / show sample dbs        Muestra las 5 bases de datos de prueba preconfiguradas.
              create database <nombre>              Crea una nueva base de datos lógica.
              drop database <nombre>                Elimina la base de datos especificada.
              use <nombre>                          Conmuta la base de datos activa.
              db stats                              Muestra estadísticas de la base de datos activa.
              INSTALL SAMPLES                       Instala y persiste las 5 bases de datos de ejemplo.
  LOAD SAMPLE example_factura_db        Carga la base de datos de facturación con 3,000,000 objetos multimodelo.
              backup database [nombre] [destino]    Genera un snapshot físico .snap de la base de datos.
              restore database <archivo> <nombre>   Restaura un snapshot .snap en una base de datos.

            4. CONSULTAS POLÍGLOTAS (JETTRAQL Y JETTRASQL):
              JettraQL: Declarativo multimodelo (FROM, MATCH, VECTOR SIMILARITY, FETCH)
              JQL FROM <col> [WHERE campo = valor]  Consulta declarativa sobre documentos.
              JQL MATCH (a)-[r]->(b) IN <grafo>     Pattern matching sobre redes de grafos.
              JQL VECTOR SIMILARITY <col> TO [...]  Búsqueda de vecinos más cercanos por similaridad coseno.
              JQL FETCH <col> <id> [RESOLVE REFS]   Recupera un registro resolviendo referencias JettraRef.
              SQL SELECT * FROM <col> [WHERE k = v] Consulta relacional con tabla formateada de columnas y filas.
              SQL INSERT INTO <col> VALUES (id, json) Inserta registro en la colección activa.
              SQL UPDATE <col> SET k = v WHERE _id = id Actualiza campos de un registro.
              SQL DELETE FROM <col> WHERE _id = id  Elimina un registro mediante sintaxis SQL.

            5. REGISTROS REFERENCIADOS (JETTRAREF) Y LAZY LOADING:
              lazy reference on / off (lazy reference on / lazy reference off)               Alterna la resolución diferida (Lazy) o inmediata (Eager).
              insert ref <col> <id> KEY <k> TARGET <engine>::<col>#<id>  Vincula un puntero cruzado multimodelo.
              resolve ref <engine>::<col>#<id>      Resuelve manualmente el destino de una referencia.
              show refs <col> <id>                  Muestra todas las referencias de un registro y sus resoluciones.
              get <col> <id>                        Obtiene un documento y resuelve sus punteros _ref_*.

            6. ADMINISTRACIÓN DE ÍNDICES:
              create index <nombre> ON <col> (campo) [TYPE BTREE|HASH|SPARSE] [UNIQUE]  Crea índice secundario.
              drop index <nombre>                   Elimina el índice especificado.
              alter index <nombre> rebuild          Reconstruye el índice re-escaneando los documentos.
              show indexes [ON <col>]               Muestra la tabla de índices creados en la base de datos.

            7. ADMINISTRACIÓN DE USUARIOS Y ROLES (RBAC):
              show users / list users               Muestra todos los usuarios, rol global y roles por base de datos.
              create user <user> PASSWORD <pass> [ROLE <role>] Crea un nuevo usuario en el sistema.
              drop user <user>                      Elimina un usuario (superuser 'admin' inmutable).
              alter user <user> PASSWORD <newPass>  Actualiza la contraseña del usuario.
              alter user <user> ROLE <newRole>      Actualiza el rol global del usuario.
              grant <DB_OWNER|READ_WRITE|READ_ONLY> ON <db> TO <user>  Asigna privilegios sobre una base de datos.
              revoke <db> FROM <user>               Revoca el acceso sobre la base de datos indicada.
              show grants for <user>                Muestra los privilegios asignados al usuario especificado.

            8. MOTORES ESPECIALIZADOS (VECTORES, GRAFOS, TIME SERIES, KV):
              vector index <col> <id> [f1,f2,..]    Indexa vector float[] en el motor vectorial.
              vector search <col> [f1,f2] K <num>   Búsqueda k-NN por similaridad coseno.
              graph add vertex <grafo> <id>         Agrega un vértice a la red de grafos.
              graph add edge <g> <a> <b> [LABEL l]  Agrega arista dirigida ponderada.
              ts record <serie> <val> [TIME t]      Registra punto métrico en serie temporal.
              ts range <serie> <inicio> <fin>       Consulta métricas en rango de tiempo.
              kv put <tabla> <clave> <valor>        Almacena clave-valor en memoria de acceso ultra rápido.
              kv get <tabla> <clave>                Recupera el valor asociado a la clave.
            ==============================================================================================
            """;
    }

    public String getInteractiveMenu() {
        return """
            ================================================================================
                                   JETTRASTORE MENU INTERACTIVO
            ================================================================================
            [1] Instalar todas las Bases de Datos de Muestra (INSTALL SAMPLES)
            [2] Listar Bases de Datos (SHOW DATABASES)
            [3] Monitoreo de Recursos (STATUS)
            [4] Topología de Nodos del Clúster (SHOW NODES)
            [5] Administrar Índices de la Base de Datos (SHOW INDEXES)
            [6] Administrar Usuarios y Roles RBAC (SHOW USERS)
            [7] Ayuda Completa (HELP)
            ================================================================================
            """;
    }

    // Getters para Testing y Verificación
    public boolean isAuthenticated() { return authenticated; }
    public String getCurrentUser() { return currentUser; }
    public String getCurrentRole() { return currentRole; }
    public String getCurrentDatabase() { return currentDatabase; }
    public String getCurrentHost() { return currentHost; }
    public int getCurrentPort() { return currentPort; }
    public boolean isLazyLoad() { return lazyLoad; }
    public Map<String, SavedConnection> getSavedConnections() { return savedConnections; }
    public JettraClient getClient() { return client; }

    public static void main(String[] args) {
        Console console = System.console();
        Scanner scanner = new Scanner(System.in);

        System.out.println("================================================================================");
        System.out.println("               JETTRASTORE INTERACTIVE DISTRIBUTED SHELL (JAVA 25+)             ");
        System.out.println("================================================================================");

        String host = "127.0.0.1";
        int port = 9091;
        String user = "admin";
        String pass = "admin-jettra";

        if (console != null) {
            String inputHost = console.readLine(">> JettraStore Host [%s]: ", host);
            if (inputHost != null && !inputHost.isBlank()) host = inputHost.trim();

            String inputPort = console.readLine(">> JettraStore Port [%d]: ", port);
            if (inputPort != null && !inputPort.isBlank()) {
                try { port = Integer.parseInt(inputPort.trim()); } catch (Exception ignored) {}
            }

            String inputUser = console.readLine(">> Username [%s]: ", user);
            if (inputUser != null && !inputUser.isBlank()) user = inputUser.trim();

            char[] passArray = console.readPassword(">> Password [hidden]: ");
            if (passArray != null && passArray.length > 0) pass = new String(passArray);
        }

        JettraStoreShellApp shell = new JettraStoreShellApp(false);
        boolean ok = shell.connectAndLogin(host, port, user, pass);
        if (ok) {
            System.out.printf("[AUTH OK] Autenticado exitosamente como '%s' (%s:%d)%n", user, host, port);
        } else {
            System.out.printf("[WARN] No se pudo conectar a %s:%d. Inicie sesión manualmente en la consola.%n", host, port);
        }

        System.out.println("Escriba 'help' o '?' para ver los comandos disponibles, o 'exit' / 'quit' para salir.\n");

        while (true) {
            String prompt = String.format("jettra-shell [%s@%s:%d/%s]> ", 
                shell.currentUser, shell.currentHost, shell.currentPort, shell.currentDatabase);
            System.out.print(prompt);
            String line;
            if (console != null) {
                line = console.readLine();
            } else if (scanner.hasNextLine()) {
                line = scanner.nextLine();
            } else {
                break;
            }

            if (line == null) break;
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                System.out.println("Saliendo de JettraStore Shell...");
                break;
            }

            String result = shell.executeCommand(trimmed);
            if (!result.isBlank()) {
                System.out.println(result);
            }
        }
    }
}
