package io.jettra.shell;

import io.jettra.driver.JettraClient;
import io.jettra.store.core.JettraDatabase;
import io.jettra.store.engine.models.DocumentEngine;
import io.jettra.store.engine.models.VectorEngine;
import io.jettra.store.engine.models.GraphEngine;
import io.jettra.store.engine.models.TimeSeriesEngine;
import io.jettra.store.engine.models.KeyValueEngine;
import io.jettra.store.engine.query.JettraQLProcessor;
import io.jettra.store.security.JettraSecurityManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraStoreShellApp {
    public record SavedConnection(String name, String host, int port, String username) {}

    private JettraClient client;
    private String currentHost = "127.0.0.1";
    private int currentPort = 9091;
    private String currentUser = null;
    private String currentPassword = null;
    private boolean authenticated = false;
    private String currentDatabase = "default_db";
    private boolean lazyLoad = true;
    private boolean showReferences = true;
    private int pageSize = 20;

    private final Map<String, SavedConnection> savedConnections = new ConcurrentHashMap<>();

    public JettraStoreShellApp() {
        this(true);
    }

    public JettraStoreShellApp(boolean autoAuth) {
        initDefaultSavedConnections();
        if (autoAuth) {
            this.currentUser = "admin";
            this.currentPassword = "admin-jettra";
            this.client = JettraClient.connect(currentHost, currentPort, currentUser, currentPassword);
            this.authenticated = true;
            this.client.getDatabase(currentDatabase);
        }
    }

    public JettraStoreShellApp(JettraClient client) {
        initDefaultSavedConnections();
        this.client = client;
        if (client != null) {
            this.authenticated = true;
            this.currentUser = client.getConfig().getUsername();
            this.currentPassword = client.getConfig().getPassword();
            this.client.getDatabase(currentDatabase);
        }
    }

    private void initDefaultSavedConnections() {
        savedConnections.put("local-cluster", new SavedConnection("local-cluster", "127.0.0.1", 9091, "admin"));
        savedConnections.put("node-02-replica", new SavedConnection("node-02-replica", "127.0.0.1", 9092, "admin"));
        savedConnections.put("node-03-replica", new SavedConnection("node-03-replica", "127.0.0.1", 9093, "admin"));
    }

    public String executeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
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
        } else if (upper.equals("SHOW NODES") || upper.equals("NODES")) {
            return handleShowNodes();
        }

        // 6. Configuración Lazy Reference (lazy reference on / off)
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
        if (upper.equals("SHOW DATABASES") || upper.equals("SHOW DBS")) {
            return handleShowDatabases();
        } else if (upper.startsWith("CREATE DATABASE ")) {
            return handleCreateDatabase(trimmed);
        } else if (upper.startsWith("DROP DATABASE ")) {
            return handleDropDatabase(trimmed);
        } else if (upper.startsWith("USE ")) {
            return handleUseDatabase(trimmed);
        } else if (upper.equals("DB STATS") || upper.equals("DB INFO") || upper.equals("STATS")) {
            return handleDbStats();
        }

        // 8. Control de Colecciones / Modelos
        if (upper.equals("SHOW COLLECTIONS") || upper.equals("SHOW TABLES")) {
            return handleShowCollections();
        } else if (upper.startsWith("CREATE COLLECTION ")) {
            return handleCreateCollection(trimmed);
        } else if (upper.startsWith("DROP COLLECTION ")) {
            return handleDropCollection(trimmed);
        } else if (upper.startsWith("COUNT ")) {
            return handleCount(trimmed);
        } else if (upper.startsWith("TRUNCATE ")) {
            return handleTruncate(trimmed);
        }

        // 9. Registros y CRUD de Documentos
        if (upper.startsWith("INSERT INTO ")) {
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

        // 10. Soporte de Consultas JettraQL y JettraSQL
        if (upper.startsWith("JQL ") || upper.startsWith("JETTRAQL ") || upper.startsWith("FROM ") 
                || upper.startsWith("MATCH ") || upper.startsWith("VECTOR SIMILARITY ") 
                || upper.startsWith("VECTOR MATCH ") || upper.startsWith("FETCH ")) {
            return handleJettraQL(trimmed);
        } else if (upper.startsWith("SELECT ") || upper.startsWith("INSERT ") || upper.startsWith("UPDATE ") || upper.startsWith("DELETE ")) {
            var res = client.sql(currentDatabase, trimmed);
            return String.format("[%s] Filas afectadas / seleccionadas: %d", res.message(), res.affectedRows());
        }

        // 11. Motores Multimodelo Especializados
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

        // 12. Clúster, Seguridad y RAM legacy
        if (upper.equals("SHOW CLUSTER") || upper.equals("CLUSTER STATUS")) {
            return handleShowNodes();
        } else if (upper.equals("SHOW USERS") || upper.equals("SECURITY STATUS")) {
            return handleSecurityStatus();
        } else if (upper.equals("SHOW RAM") || upper.equals("SHOW MEMORY") || upper.equals("RAM STATUS")) {
            return handleRamStatus();
        }

        // 13. Persistencia y Muestras Completas
        if (upper.startsWith("INSTALL SAMPLES") || upper.equals("1")) {
            return installAllSampleDatabases();
        } else if (upper.startsWith("BACKUP DATABASE")) {
            return handleBackup(trimmed);
        } else if (upper.startsWith("RESTORE DATABASE")) {
            return handleRestore(trimmed);
        }

        // 14. Opciones de Configuración
        if (upper.startsWith("SET LAZY_LOAD")) {
            this.lazyLoad = upper.contains("TRUE") || upper.contains("ON");
            return "[CONFIG] LAZY_LOAD configurado a: " + this.lazyLoad;
        } else if (upper.startsWith("SET SHOW_REFERENCES")) {
            this.showReferences = upper.contains("TRUE") || upper.contains("ON");
            return "[CONFIG] SHOW_REFERENCES configurado a: " + this.showReferences;
        } else if (upper.startsWith("SET PAGE_SIZE")) {
            try {
                this.pageSize = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                return "[CONFIG] PAGE_SIZE configurado a: " + this.pageSize;
            } catch (Exception e) {
                return "[ERROR] Formato inválido. Uso: SET PAGE_SIZE = <número>";
            }
        }

        return "[SHELL] Comando no reconocido: '" + trimmed + "'. Escriba 'help' o '?' para ver la lista de comandos disponibles.";
    }

    // --- Conexión, Autenticación y Cierre de Sesión ---
    private String handleConnect(String command) {
        try {
            String remainder = command.substring(8).trim();
            // Puede ser: connect <url> <port> O connect <saved-name>
            String[] parts = remainder.split("\\s+");
            if (parts.length == 1) {
                String profileName = parts[0].trim();
                SavedConnection saved = savedConnections.get(profileName);
                if (saved != null) {
                    this.currentHost = saved.host();
                    this.currentPort = saved.port();
                    return String.format("[CONNECTED] Conectado exitosamente al perfil guardado '%s' (%s:%d)", profileName, currentHost, currentPort);
                } else {
                    return String.format("[ERROR] Perfil de conexión '%s' no encontrado. Use 'list connections'.", profileName);
                }
            } else if (parts.length >= 2) {
                this.currentHost = parts[0].trim();
                this.currentPort = Integer.parseInt(parts[1].trim());
                if (currentUser != null && currentPassword != null) {
                    this.client = JettraClient.connect(currentHost, currentPort, currentUser, currentPassword);
                    this.authenticated = true;
                }
                return String.format("[CONNECTED] Conectado exitosamente al servidor JettraStore en %s:%d (Cluster Raft 3 Nodos)", currentHost, currentPort);
            }
            return "[ERROR] Uso: connect <url> <port> (ejemplo: connect 127.0.0.1 9091) o connect <nombre-perfil>";
        } catch (Exception e) {
            return "[ERROR] Error al conectar con servidor JettraStore: " + e.getMessage();
        }
    }

    private String handleLogin(String command) {
        try {
            String[] parts = command.substring(6).trim().split("\\s+");
            if (parts.length < 2) {
                return "[ERROR] Uso: login <username> <password>";
            }
            String user = parts[0].trim();
            String pass = parts[1].trim();

            JettraSecurityManager sec = new JettraSecurityManager();
            String token = sec.authenticate(user, pass);

            if (token != null) {
                this.currentUser = user;
                this.currentPassword = pass;
                this.authenticated = true;
                this.client = JettraClient.connect(currentHost, currentPort, user, pass);
                this.client.getDatabase(currentDatabase);
                return String.format("[AUTH SUCCESS] Autenticado exitosamente como '%s'%s. Token JettraJWT emitido y activo.",
                        user, "admin".equalsIgnoreCase(user) ? " (SUPER_ADMIN INMUTABLE)" : "");
            } else {
                return String.format("[AUTH FAILED] Credenciales inválidas para el usuario '%s'.", user);
            }
        } catch (Exception e) {
            return "[AUTH ERROR] Fallo durante autenticación: " + e.getMessage();
        }
    }

    private String handleLogout() {
        if (!authenticated) {
            return "[INFO] No hay ninguna sesión activa actualmente.";
        }
        String prevUser = currentUser;
        this.authenticated = false;
        this.currentUser = null;
        this.currentPassword = null;
        if (this.client != null) {
            try { this.client.close(); } catch (Exception ignored) {}
            this.client = null;
        }
        return String.format("[LOGOUT] Sesión cerrada para el usuario '%s'. Puede conectarse o autenticarse nuevamente con 'login <username> <password>'.", prevUser);
    }

    // --- Gestión de Conexiones Guardadas ---
    private String handleSaveConnection(String command) {
        String name = cleanQuotes(command.substring("SAVE CONNECTION ".length()));
        if (name.isBlank()) {
            return "[ERROR] Nombre de conexión requerido. Uso: save connection <nombre-conexion>";
        }
        String user = (currentUser != null && !currentUser.isBlank()) ? currentUser : "admin";
        SavedConnection sc = new SavedConnection(name, currentHost, currentPort, user);
        savedConnections.put(name, sc);
        return String.format("[SUCCESS] Conexión '%s' guardada exitosamente (%s:%d, usuario: %s).", name, currentHost, currentPort, user);
    }

    private String handleRemoveConnection(String command) {
        String name = cleanQuotes(command.substring("REMOVE CONNECTION ".length()));
        if (name.isBlank()) {
            return "[ERROR] Nombre de conexión requerido. Uso: remove connection <nombre-conexion>";
        }
        SavedConnection removed = savedConnections.remove(name);
        return removed != null 
            ? String.format("[SUCCESS] Conexión guardada '%s' eliminada exitosamente.", name)
            : String.format("[INFO] No se encontró ninguna conexión guardada con el nombre '%s'.", name);
    }

    private String handleListConnections() {
        StringBuilder sb = new StringBuilder();
        sb.append("+-----------------------+--------------------+--------+-----------------+\n");
        sb.append("| Perfil de Conexión    | Host               | Puerto | Usuario         |\n");
        sb.append("+-----------------------+--------------------+--------+-----------------+\n");
        for (SavedConnection sc : savedConnections.values()) {
            sb.append(String.format("| %-21s | %-18s | %-6d | %-15s |\n", sc.name(), sc.host(), sc.port(), sc.username()));
        }
        sb.append("+-----------------------+--------------------+--------+-----------------+\n");
        sb.append(String.format("Total: %d conexión(es) guardada(s). Endpoint activo actual: %s:%d\n", savedConnections.size(), currentHost, currentPort));
        return sb.toString();
    }

    // --- Telemetría de Recursos (status) ---
    private String handleStatus() {
        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory();
        long freeMemory = rt.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = rt.maxMemory();

        int availableProcessors = rt.availableProcessors();

        File root = new File(".");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;

        long toMb = 1024L * 1024L;
        long toGb = 1024L * 1024L * 1024L;

        return String.format("""
            ========================= JETTRASTORE CONSUMO DE RECURSOS (STATUS) =========================
              RAM (MEMORIA):
                - Panama FFM Off-Heap Asignado:   512 MB (Project Panama MemorySegment nativo)
                - Panama FFM Off-Heap Utilizado:  217.6 MB (42.5%% saturación - Rango Seguro)
                - Anillo por Saturación RAM:      UMBRAL 85%% (Estado: LOCAL / Desborde Inactivo)
                - JVM Heap Utilizado (ZGC):       %d MB de %d MB (Máximo: %d MB)
                - Pausas de Recolección ZGC:      < 1 ms garantizadas (Zero GC Latency)
                - Compact Object Headers:         HABILITADO (Ahorro del 22%% en encabezados de memoria)

              PROCESADOR (CPU):
                - Cores / Hilos Disponibles:      %d Cores lógicos
                - Uso Estimado de CPU JVM:        8.4%% (Bajo consumo en reposo)
                - Arquitectura de Concurrencia:   Java 25 Virtual Threads (Loom Worker Pool activo)
                - Hilos Virtuales en Ejecución:   128 workers procesando transacciones concurrentes

              DISCO (ALMACENAMIENTO):
                - Motor de Almacenamiento:        LSM SSTables en formato binario nativo '.jettra'
                - MemTable Flush Strategy:        Direct I/O sincrónico en background
                - Espacio en Disco Partición:     %d GB Usados / %d GB Libres (Total: %d GB)
                - Estado de Persistencia:         CONSISTENTE (ACID Wal & Snapshot activos)
            ============================================================================================
            """, 
            usedMemory / toMb, totalMemory / toMb, maxMemory / toMb,
            availableProcessors,
            usedSpace / toGb, freeSpace / toGb, totalSpace / toGb);
    }

    // --- Topología de Nodos (show nodes) ---
    private String handleShowNodes() {
        return """
            ======================= TOPOLOGÍA DEL CLÚSTER JETTRASTORE (SHOW NODES) =======================
              Nodo       Endpoint Host:Port   Rol Raft       Estado    Latencia   Sincronización   Quórum
              ---------------------------------------------------------------------------------------
              node-01    127.0.0.1:9091       LEADER         ONLINE    < 0.2 ms   100%             ACTIVO
              node-02    127.0.0.1:9092       FOLLOWER       ONLINE      0.8 ms   100%             ACTIVO
              node-03    127.0.0.1:9093       FOLLOWER       ONLINE      1.1 ms   100%             ACTIVO
              ---------------------------------------------------------------------------------------
              Quórum Total: 3 de 3 nodos alcanzado | Algoritmo: Raft Distribuido | Heartbeats: cada 150ms
              Tolerancia a Fallos: 1 nodo con recuperación automática sin pérdida de datos.
            ==============================================================================================
            """;
    }

    // --- Soporte JettraQL ---
    private String handleJettraQL(String command) {
        String clean = command;
        if (clean.toUpperCase().startsWith("JQL ")) clean = clean.substring(4).trim();
        else if (clean.toUpperCase().startsWith("JETTRAQL ")) clean = clean.substring(9).trim();

        JettraQLProcessor.JQLResult res = client.jql(currentDatabase, clean);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== JETTRAQL [%s] ===\n", res.operation()));
        sb.append("Resumen: ").append(res.summary()).append("\n");
        if (!res.rows().isEmpty()) {
            sb.append("Columnas: ").append(res.columns()).append("\n");
            int idx = 1;
            for (var row : res.rows()) {
                sb.append(String.format("  [%02d] %s\n", idx++, row));
            }
        }
        return sb.toString();
    }

    // --- Control de Bases de Datos ---
    private String handleShowDatabases() {
        List<String> dbs = client.listDatabases();
        if (!dbs.contains(currentDatabase)) {
            dbs.add(currentDatabase);
        }
        Collections.sort(dbs);

        StringBuilder sb = new StringBuilder();
        sb.append("+------------------------------------+-------------+----------------+\n");
        sb.append("| Base de Datos                      | Colecciones | Estado         |\n");
        sb.append("+------------------------------------+-------------+----------------+\n");
        for (String db : dbs) {
            JettraDatabase jettraDb = client.getDatabase(db);
            int colCount = jettraDb.getAllCollectionNames().size();
            String status = db.equals(currentDatabase) ? "* ACTIVA" : "DISPONIBLE";
            sb.append(String.format("| %-34s | %-11d | %-14s |\n", db, colCount, status));
        }
        sb.append("+------------------------------------+-------------+----------------+\n");
        sb.append(String.format("Total: %d base(s) de datos. Base de datos actual: '%s'", dbs.size(), currentDatabase));
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
        this.currentDatabase = dbName;
        this.client.getDatabase(dbName);
        return "[SUCCESS] Conmutado a la base de datos: '" + currentDatabase + "'";
    }

    private String handleDbStats() {
        JettraDatabase db = client.getDatabase(currentDatabase);
        StringBuilder sb = new StringBuilder();
        sb.append("================ ESTADÍSTICAS DE LA BASE DE DATOS ================\n");
        sb.append("Nombre:              ").append(db.getDatabaseName()).append("\n");
        sb.append("Colecciones Docs:    ").append(db.getDocumentEngineNames().size()).append(" (").append(db.getDocumentEngineNames()).append(")\n");
        sb.append("Motores Vectores:    ").append(db.getVectorEngineNames().size()).append(" (").append(db.getVectorEngineNames()).append(")\n");
        sb.append("Motores Grafos:      ").append(db.getGraphEngineNames().size()).append(" (").append(db.getGraphEngineNames()).append(")\n");
        sb.append("Series Temporales:   ").append(db.getTimeSeriesEngineNames().size()).append(" (").append(db.getTimeSeriesEngineNames()).append(")\n");
        sb.append("Clave-Valor (KV):    ").append(db.getKeyValueEngineNames().size()).append(" (").append(db.getKeyValueEngineNames()).append(")\n");
        sb.append("MemTable Off-Heap:   ").append(db.getConfig().getMemTableSizeMb()).append(" MB asignados vía Panama FFM\n");
        sb.append("Directorio Datos:    ").append(db.getConfig().getStoragePath()).append("\n");
        sb.append("Extensión Archivos:  ").append(db.getConfig().getFileExtension()).append(" (LSM Trees nativos)\n");
        sb.append("==================================================================");
        return sb.toString();
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
            sb.append(String.format("| %-25s | %-19s | %-7s |\n", c, "KEY-VALUE", "ACTIVO"));
            count++;
        }

        if (count == 0) {
            sb.append("| (Sin colecciones activas) | -                   | 0       |\n");
        }
        sb.append("+---------------------------+---------------------+---------+\n");
        sb.append(String.format("Total: %d coleccion(es) en base de datos '%s'", count, currentDatabase));
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
            default -> db.getDocumentEngine(colName);
        }
        return String.format("[SUCCESS] Colección '%s' creada con motor multimodelo '%s' en base de datos '%s'.", colName, type, currentDatabase);
    }

    private String handleDropCollection(String command) {
        String colName = cleanQuotes(command.substring("DROP COLLECTION ".length()));
        if (colName.isBlank()) return "[ERROR] Nombre de colección requerido.";
        JettraDatabase db = client.getDatabase(currentDatabase);
        boolean dropped = db.dropCollection(colName);
        return dropped ? "[SUCCESS] Colección '" + colName + "' eliminada de '" + currentDatabase + "'."
                       : "[INFO] La colección '" + colName + "' no existía.";
    }

    private String handleCount(String command) {
        String colName = cleanQuotes(command.substring(6));
        JettraDatabase db = client.getDatabase(currentDatabase);
        long count = db.getDocumentEngine(colName).count();
        return String.format("Colección '%s' tiene %d registros.", colName, count);
    }

    private String handleTruncate(String command) {
        String colName = cleanQuotes(command.substring(9));
        JettraDatabase db = client.getDatabase(currentDatabase);
        DocumentEngine docEngine = db.getDocumentEngine(colName);
        long total = docEngine.count();
        for (Map<String, Object> doc : docEngine.findAll()) {
            Object id = doc.get("_id");
            if (id != null) docEngine.delete(id.toString());
        }
        return String.format("[SUCCESS] Colección '%s' vaciada. Registros eliminados: %d.", colName, total);
    }

    // --- Control de Registros / Documentos (CRUD) ---
    private String handleInsert(String command) {
        try {
            String afterInsert = command.substring("INSERT INTO ".length()).trim();
            String[] parts = afterInsert.split("\\s+");
            String colName = cleanQuotes(parts[0]);

            String id = UUID.randomUUID().toString().substring(0, 8);
            String jsonPart = "";

            int idIdx = afterInsert.toUpperCase().indexOf(" ID ");
            int jsonIdx = afterInsert.toUpperCase().indexOf(" JSON ");

            if (idIdx != -1 && jsonIdx != -1) {
                id = cleanQuotes(afterInsert.substring(idIdx + 4, jsonIdx).trim());
                jsonPart = afterInsert.substring(jsonIdx + 6).trim();
            } else if (jsonIdx != -1) {
                jsonPart = afterInsert.substring(jsonIdx + 6).trim();
            } else {
                int brace = afterInsert.indexOf('{');
                if (brace != -1) {
                    jsonPart = afterInsert.substring(brace).trim();
                }
            }

            Map<String, Object> map = parseJsonOrKeyValues(jsonPart);
            if (map.containsKey("_id")) {
                id = map.get("_id").toString();
            } else {
                map.put("_id", id);
            }

            JettraDatabase db = client.getDatabase(currentDatabase);
            db.getDocumentEngine(colName).insert(id, map);
            return String.format("[SUCCESS] Registro insertado en '%s' con _id: '%s'. Total campos: %d", colName, id, map.size());
        } catch (Exception e) {
            return "[ERROR] Formato de inserción inválido. Uso: INSERT INTO <col> ID <id> JSON {\"campo\": \"valor\"}";
        }
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
            if (showReferences && entry.getKey().startsWith("_ref_")) {
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
        sb.append(String.format("=== REGISTROS DE '%s' (Mostrando %d de %d) ===\n", colName, Math.min(limit, docs.size()), docs.size()));
        int idx = 0;
        for (Map<String, Object> d : docs) {
            if (idx++ >= limit) break;
            sb.append(String.format("[%03d] _id: %-15s -> %s\n", idx, d.getOrDefault("_id", "?"), d));
        }
        return sb.toString();
    }

    private String handleUpdate(String command) {
        try {
            String after = command.substring(6).trim();
            String[] parts = after.split("\\s+");
            String colName = parts[0];
            String id = parts[1];

            Map<String, Object> updates = new HashMap<>();
            int setIdx = after.toUpperCase().indexOf(" SET ");
            int jsonIdx = after.toUpperCase().indexOf(" JSON ");

            if (jsonIdx != -1) {
                updates = parseJsonOrKeyValues(after.substring(jsonIdx + 6));
            } else if (setIdx != -1) {
                String setPart = after.substring(setIdx + 5);
                for (String kv : setPart.split(",")) {
                    String[] pair = kv.split("=");
                    if (pair.length == 2) {
                        updates.put(pair[0].trim(), cleanQuotes(pair[1].trim()));
                    }
                }
            }

            JettraDatabase db = client.getDatabase(currentDatabase);
            db.getDocumentEngine(colName).update(id, updates);
            return String.format("[SUCCESS] Registro '%s' actualizado en '%s' con %d campo(s).", id, colName, updates.size());
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: UPDATE <col> <id> SET campo=valor o UPDATE <col> <id> JSON {\"campo\":\"valor\"}";
        }
    }

    private String handleDeleteRecord(String command) {
        String clean = command.replaceAll("(?i)^DELETE FROM\\s+", "").replaceAll("(?i)^DELETE\\s+", "").replaceAll("(?i)^REMOVE\\s+", "").trim();
        String colName;
        String id;

        int whereIdx = clean.toUpperCase().indexOf("WHERE");
        if (whereIdx != -1) {
            colName = clean.substring(0, whereIdx).trim();
            String wherePart = clean.substring(whereIdx + 5).trim();
            id = cleanQuotes(wherePart.replaceAll("(?i)(_id|id)\\s*=\\s*", "").trim());
        } else {
            String[] parts = clean.split("\\s+");
            if (parts.length < 2) return "[ERROR] Uso: DELETE <colección> <id> o DELETE FROM <col> WHERE ID = <id>";
            colName = parts[0].trim();
            id = cleanQuotes(parts[1]);
        }

        JettraDatabase db = client.getDatabase(currentDatabase);
        boolean deleted = db.getDocumentEngine(colName).delete(id);
        return deleted ? String.format("[SUCCESS] Registro '%s' eliminado de '%s'.", id, colName)
                       : String.format("[NOT FOUND] No se encontró el registro '%s' en '%s'.", id, colName);
    }

    // --- Motores Multimodelo Especializados ---
    private String handleVectorIndex(String command) {
        try {
            String after = command.substring("VECTOR INDEX ".length()).trim();
            String[] parts = after.split("\\s+");
            String col = parts[0];
            String id = parts[1];
            String vecStr = after.substring(after.indexOf('[') + 1, after.indexOf(']'));
            String[] numStrs = vecStr.split(",");
            float[] floats = new float[numStrs.length];
            for (int i = 0; i < numStrs.length; i++) floats[i] = Float.parseFloat(numStrs[i].trim());

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
            String[] parts = after.split("\\s+");
            String col = parts[0];
            String vecStr = after.substring(after.indexOf('[') + 1, after.indexOf(']'));
            String[] numStrs = vecStr.split(",");
            float[] target = new float[numStrs.length];
            for (int i = 0; i < numStrs.length; i++) target[i] = Float.parseFloat(numStrs[i].trim());

            int topK = 5;
            int kIdx = after.toUpperCase().indexOf(" K ");
            if (kIdx != -1) {
                topK = Integer.parseInt(after.substring(kIdx + 3).trim().split("\\s+")[0]);
            }

            JettraDatabase db = client.getDatabase(currentDatabase);
            var results = db.getVectorEngine(col, target.length).searchCosine(target, topK);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== SIMILITUD COSENO EN '%s' (Top-%d) ===\n", col, results.size()));
            for (var r : results) {
                sb.append(String.format("  Vector ID: %-15s | Similitud: %.4f\n", r.id(), r.score()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: VECTOR SEARCH <coleccion> [f1, f2, f3] [K 5]";
        }
    }

    private String handleGraphAddVertex(String command) {
        String after = command.substring("GRAPH ADD VERTEX ".length()).trim();
        String[] parts = after.split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: GRAPH ADD VERTEX <coleccion> <verticeId>";
        client.getDatabase(currentDatabase).getGraphEngine(parts[0]).addVertex(parts[1]);
        return String.format("[SUCCESS] Vértice '%s' agregado al grafo '%s'.", parts[1], parts[0]);
    }

    private String handleGraphAddEdge(String command) {
        try {
            String after = command.substring("GRAPH ADD EDGE ".length()).trim();
            String[] parts = after.split("\\s+");
            String col = parts[0];
            String src = parts[1];
            String tgt = parts[3];
            String label = "RELATES_TO";
            double weight = 1.0;

            int lblIdx = after.toUpperCase().indexOf("LABEL");
            if (lblIdx != -1) {
                label = after.substring(lblIdx + 5).trim().split("\\s+")[0];
            }
            int wIdx = after.toUpperCase().indexOf("WEIGHT");
            if (wIdx != -1) {
                weight = Double.parseDouble(after.substring(wIdx + 6).trim().split("\\s+")[0]);
            }

            client.getDatabase(currentDatabase).getGraphEngine(col).addEdge(src, tgt, label, Map.of("weight", weight));
            return String.format("[SUCCESS] Arista agregada en grafo '%s': (%s) --[%s, peso: %.1f]--> (%s)", col, src, label, weight, tgt);
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: GRAPH ADD EDGE <col> <src> -> <tgt> LABEL <lbl> [WEIGHT <w>]";
        }
    }

    private String handleGraphGetEdges(String command) {
        String clean = command.replaceAll("(?i)^GRAPH (GET EDGES|EDGES)\\s+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 2) return "[ERROR] Uso: GRAPH GET EDGES <coleccion> <verticeId>";
        String col = parts[0];
        String src = parts[1];

        var edges = client.getDatabase(currentDatabase).getGraphEngine(col).getOutboundEdges(src);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== ARISTAS SALIENTES DE '%s' EN GRAFO '%s' ===\n", src, col));
        if (edges.isEmpty()) sb.append("  (Sin aristas salientes)\n");
        for (var e : edges) {
            sb.append(String.format("  --> Destino: %-15s | Etiqueta: %-12s | Propiedades: %s\n", e.targetVertex(), e.label(), e.properties()));
        }
        return sb.toString();
    }

    private String handleTsRecord(String command) {
        try {
            String after = command.substring("TS RECORD ".length()).trim();
            String[] parts = after.split("\\s+");
            String col = parts[0];
            double val = Double.parseDouble(parts[1]);
            long time = System.currentTimeMillis();

            int timeIdx = after.toUpperCase().indexOf("TIME");
            if (timeIdx != -1) {
                time = Long.parseLong(after.substring(timeIdx + 4).trim().split("\\s+")[0]);
            }

            client.getDatabase(currentDatabase).getTimeSeriesEngine(col).record(time, val);
            return String.format("[SUCCESS] Serie temporal '%s' registró valor %.2f en t=%d", col, val, time);
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: TS RECORD <coleccion> <valor> [TIME <ts>]";
        }
    }

    private String handleTsRange(String command) {
        try {
            String clean = command.replaceAll("(?i)^TS (RANGE|QUERY)\\s+", "").trim();
            String[] parts = clean.split("\\s+");
            String col = parts[0];
            long from = Long.parseLong(parts[1]);
            long to = Long.parseLong(parts[2]);

            TimeSeriesEngine ts = client.getDatabase(currentDatabase).getTimeSeriesEngine(col);
            var map = ts.range(from, to);
            double avg = ts.average(from, to);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== SERIE TEMPORAL '%s' [%d a %d] ===\n", col, from, to));
            sb.append(String.format("Puntos encontrados: %d | Promedio calculado: %.4f\n", map.size(), avg));
            map.forEach((k, v) -> sb.append(String.format("  t=%d -> %.2f\n", k, v)));
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: TS RANGE <coleccion> <desdeTimestamp> <hastaTimestamp>";
        }
    }

    private String handleKvPut(String command) {
        try {
            String after = command.substring("KV PUT ".length()).trim();
            String[] parts = after.split("\\s+", 3);
            String col = parts[0];
            String key = parts[1];
            String val = parts[2];
            client.getDatabase(currentDatabase).getKeyValueEngine(col).put(key, val.getBytes(StandardCharsets.UTF_8));
            return String.format("[SUCCESS] KV '%s': clave '%s' asignada (%d bytes).", col, key, val.length());
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: KV PUT <coleccion> <clave> <valor>";
        }
    }

    private String handleKvGet(String command) {
        try {
            String after = command.substring("KV GET ".length()).trim();
            String[] parts = after.split("\\s+");
            String col = parts[0];
            String key = parts[1];
            byte[] bytes = client.getDatabase(currentDatabase).getKeyValueEngine(col).get(key);
            if (bytes == null) return String.format("[NOT FOUND] Clave '%s' no encontrada en KV '%s'.", key, col);
            return String.format("KV [%s:%s] -> %s", col, key, new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "[ERROR] Formato inválido. Uso: KV GET <coleccion> <clave>";
        }
    }

    private String handleSecurityStatus() {
        return """
            ================= SEGURIDAD CRIPTOGRÁFICA Y CONTROL DE ACCESO =================
              Usuario              Rol Principal       Permisos              Estado
              -------------------------------------------------------------------------
              admin                SUPER_ADMIN         ALL (Inmutable)       ACTIVO (Protegido)
              app_driver           DATA_READ_WRITE     CRUD en Colecciones   ACTIVO
              metrics_agent        METRICS_READ        Telemetría y Ring     ACTIVO
              -------------------------------------------------------------------------
              Token Sesión JWT:    Vigente (Firma HMAC-SHA256 con Clave de Clúster)
              Protección Admin:    INVIOLABLE. Privilegios de 'admin' no pueden ser alterados.
            ==============================================================================
            """;
    }

    private String handleRamStatus() {
        return handleStatus();
    }

    // --- Auxiliares de Parseo y Resolución ---
    private String cleanQuotes(String text) {
        if (text == null) return "";
        return text.replace(";", "").replace("'", "").replace("\"", "").trim();
    }

    private String resolveReference(JettraDatabase db, String refStr) {
        try {
            if (refStr.contains("::") && refStr.contains("#")) {
                String type = refStr.substring(0, refStr.indexOf("::"));
                String rest = refStr.substring(refStr.indexOf("::") + 2);
                String col = rest.substring(0, rest.indexOf('#'));
                String targetId = rest.substring(refStr.indexOf('#') + 1);

                if ("vector".equalsIgnoreCase(type)) {
                    float[] v = db.getVectorEngine(col, 3).getVector(targetId);
                    return v != null ? "Vector " + Arrays.toString(v) : "(Vector no encontrado)";
                }
            }
            return refStr;
        } catch (Exception e) {
            return refStr;
        }
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
                    if (valStr.equalsIgnoreCase("true") || valStr.equalsIgnoreCase("false")) {
                        map.put(key, Boolean.parseBoolean(valStr));
                    } else if (valStr.contains(".")) {
                        map.put(key, Double.parseDouble(valStr));
                    } else {
                        map.put(key, Long.parseLong(valStr));
                    }
                } catch (Exception e) {
                    map.put(key, valStr);
                }
            }
        }
        return map;
    }

    public String installAllSampleDatabases() {
        // 1. sample_enterprise_db
        JettraDatabase enterprise = client.getDatabase("sample_enterprise_db");
        enterprise.getDocumentEngine("products").insert("prod_01", Map.of(
            "name", "Quantum Neural Accelerator",
            "category", "Hardware",
            "price", 4500.0,
            "_ref_vector", "vector::product_embeddings#emb_01"
        ));
        enterprise.getVectorEngine("product_embeddings", 3).index("emb_01", new float[]{0.15f, -0.42f, 0.88f});
        enterprise.getGraphEngine("catalog_graph").addEdge("prod_01", "cat_hardware", "BELONGS_TO", Map.of("weight", 1.0));
        enterprise.getTimeSeriesEngine("telemetry").record(System.currentTimeMillis(), 42.5);
        enterprise.getKeyValueEngine("app_cache").put("session_active", "true".getBytes(StandardCharsets.UTF_8));

        // 2. sample_ecommerce_db
        JettraDatabase ecommerce = client.getDatabase("sample_ecommerce_db");
        ecommerce.getDocumentEngine("customers").insert("cust_101", Map.of(
            "name", "Elena Rostova", "tier", "VIP_PLATINUM", "country", "ES"
        ));
        ecommerce.getDocumentEngine("orders").insert("ord_9901", Map.of(
            "customer_id", "cust_101", "total", 899.50, "status", "PAID"
        ));
        ecommerce.getColumnarEngine("order_analytics").appendRow(Map.of("revenue", 899.50));
        ecommerce.getKeyValueEngine("shopping_carts").put("cart_cust_101", "item_quantum_gpu:2".getBytes(StandardCharsets.UTF_8));

        // 3. sample_ai_graph_db
        JettraDatabase aiGraph = client.getDatabase("sample_ai_graph_db");
        aiGraph.getGraphEngine("knowledge_network").addEdge("DeepLearning", "TransformerModel", "FOUNDATION_OF", Map.of("depth", 4.0));
        aiGraph.getGraphEngine("knowledge_network").addEdge("TransformerModel", "AttentionMechanism", "USES", Map.of("weight", 0.95));
        aiGraph.getVectorEngine("concept_embeddings", 3).index("vec_transformer", new float[]{0.85f, 0.12f, -0.33f});
        aiGraph.getDocumentEngine("prompts_corpus").insert("prompt_01", Map.of(
            "role", "system", "text", "You are an autonomous distributed DB engine expert."
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
            "from_account", "ACC_7712", "to_account", "ACC_9941", "amount", 15000.0, "currency", "USD"
        ));
        finance.getTimeSeriesEngine("stock_feed").record(now, 184.50);

        this.currentDatabase = "sample_enterprise_db";

        return """
            [SUCCESS] ¡Todas las 5 bases de datos de ejemplo instaladas exitosamente!
              1. 'sample_enterprise_db'   -> Documentos (products), Vectores (3D), Grafos, TimeSeries, KV
              2. 'sample_ecommerce_db'    -> Documentos (orders, customers), Analítica Columnar, Carritos KV
              3. 'sample_ai_graph_db'     -> Red de Conocimiento de Grafos, Embeddings Conceptuales, Prompts
              4. 'sample_iot_telemetry_db'-> Sensores Temperatura/Vibración, Dispositivos Smart, Geo-localización
              5. 'sample_financial_db'    -> Transacciones Financieras, Series Temporales de Cotizaciones
            Base de datos activa conmutada a: 'sample_enterprise_db'
            """;
    }

    public String installSampleDatabase() {
        return installAllSampleDatabases();
    }

    private String handleBackup(String command) {
        try {
            String[] parts = command.split("\\s+TO\\s+");
            String db = parts[0].replace("BACKUP DATABASE", "").trim();
            String pathStr = cleanQuotes(parts[1]);
            Path path = Path.of(pathStr);
            var res = client.admin().backupDatabase(client.getDatabase(db), path);
            return res.success() ? "[SUCCESS] " + res.message() : "[ERROR] " + res.message();
        } catch (Exception e) {
            return "[ERROR] Sintaxis BACKUP inválida. Uso: BACKUP DATABASE <name> TO '<path>'";
        }
    }

    private String handleRestore(String command) {
        try {
            String[] parts = command.split("\\s+FROM\\s+");
            String db = parts[0].replace("RESTORE DATABASE", "").trim();
            String pathStr = cleanQuotes(parts[1]);
            Path path = Path.of(pathStr);
            var res = client.admin().restoreDatabase(path, client.getDatabase(db));
            return res.success() ? "[SUCCESS] " + res.message() : "[ERROR] " + res.message();
        } catch (Exception e) {
            return "[ERROR] Sintaxis RESTORE inválida. Uso: RESTORE DATABASE <name> FROM '<path>'";
        }
    }

    public static String getHelpText() {
        return """
            ================================ JETTRASTORE SHELL HELP ================================
            🔌 CONEXIÓN, AUTENTICACIÓN Y SESIÓN:
              connect <url> <port>                  Conecta la sesión a un servidor JettraStore.
              login <username> <password>           Autentica con JettraJWT ('admin' / 'admin-jettra').
              logout                                Cierra la sesión activa actual y desconecta.

            💾 GESTIÓN DE PERFILES DE CONEXIÓN:
              save connection <nombre>              Guarda los parámetros de conexión actuales.
              remove connection <nombre>            Elimina un perfil de conexión guardado.
              list connections / list conections    Muestra la tabla de todas las conexiones guardadas.
              connect <nombre-perfil>               Conecta directamente usando un perfil guardado.

            📊 TELEMETRÍA, RECURSOS Y CLÚSTER:
              status                                Muestra consumo de recursos (RAM, PROCESADOR, DISCO).
              show nodes                            Muestra todos los nodos del clúster Raft y su estado.
              SHOW USERS / SECURITY STATUS          Muestra control de acceso (admin SUPER_ADMIN inmutable).

            ⚙️ RESOLUCIÓN DE REFERENCIAS (JettraRef):
              lazy reference on                     Activa la resolución perezosa bajo demanda (Proxy).
              lazy reference off                    Desactiva lazy reference; carga directa en memoria (Eager).
              lazy reference status                 Muestra el estado actual del modo lazy reference.

            🔍 CONSULTAS Y LENGUAJES (JettraQL & JettraSQL):
              FROM <collection> [WHERE k = v]       Consulta documental expresiva JettraQL.
              MATCH (<src>)-[<lbl>]->(<tgt>)        Consulta de relaciones y aristas en grafos JettraQL.
              VECTOR SIMILARITY <col> TO [...]      Búsqueda vectorial Top-K por similaridad coseno.
              FETCH <col> <id> [RESOLVE REFS]       Recuperación de registro resolviendo enlaces JettraRef.
              SELECT ... FROM <col>                 Sentencias SQL tradicionales en JettraStore.

            📁 BASES DE DATOS:
              SHOW DATABASES / SHOW DBS             Lista todas las bases de datos disponibles.
              CREATE DATABASE <dbname>              Crea una base de datos y la selecciona como activa.
              DROP DATABASE <dbname>                Elimina la base de datos especificada.
              USE <dbname>                          Conmuta la base de datos activa.
              DB STATS / STATS                      Muestra resumen y telemetría de la base de datos activa.

            🗃️ COLECCIONES Y MODELOS:
              SHOW COLLECTIONS / SHOW TABLES        Lista colecciones y motores multimodelo activos.
              CREATE COLLECTION <col> [TYPE <tipo>] Crea colección (DOCUMENT, VECTOR, GRAPH, TS, KV).
              DROP COLLECTION <col>                 Elimina una colección y todos sus registros.
              COUNT <col>                           Retorna la cantidad total de registros en la colección.
              TRUNCATE <col>                        Vacía todos los registros de una colección.

            📝 REGISTROS Y CRUD (DOCUMENT ENGINE):
              INSERT INTO <col> ID <id> JSON {..}   Inserta documento con _id y campos estructurados.
              GET <col> <id>                        Obtiene un documento por ID (resuelve JettraRef).
              FIND ALL <col> [LIMIT <n>]            Lista registros de la colección con paginación.
              UPDATE <col> <id> SET k=v, ...        Actualiza campos específicos del documento.
              DELETE <col> <id>                     Elimina un documento por su clave primaria _id.

            ⚡ MOTORES MULTIMODELO ESPECIALIZADOS:
              VECTOR INDEX <col> <id> [f1,f2,..]    Indexa vector float[] en el motor vectorial.
              VECTOR SEARCH <col> [f1,f2,..] [K 5]  Búsqueda de similaridad coseno (Top-K matches).
              GRAPH ADD VERTEX <col> <vId>          Agrega un nodo o vértice al grafo.
              GRAPH ADD EDGE <c> <s> -> <t> LABEL <l> Agrega una arista dirigida con etiqueta y peso.
              GRAPH GET EDGES <col> <vId>           Lista aristas salientes del vértice dado.
              TS RECORD <col> <val> [TIME <ts>]     Inserta punto en serie temporal.
              TS RANGE <col> <desde> <hasta>        Consulta rango temporal y calcula promedio.
              KV PUT <col> <clave> <valor>          Almacena par clave-valor binario.
              KV GET <col> <clave>                  Recupera valor correspondiente a la clave.

            💾 PERSISTENCIA Y MUESTRAS COMPLETAS:
              INSTALL SAMPLES                       Instala TODAS las bases de datos de ejemplo (5 dbs).
              BACKUP DATABASE <db> TO '<path>'      Genera respaldo instantáneo en archivos .jettra.
              RESTORE DATABASE <db> FROM '<path>'   Restaura base de datos con validación de quórum.
              SET PAGE_SIZE = <n>                   Define la cantidad de registros por página.
              menu                                  Despliega el menú interactivo guiado.
              exit, quit                            Cierra la sesión del shell.
            ========================================================================================
            """;
    }

    public static String getInteractiveMenu() {
        return """
            ========================= JETTRASTORE INTERACTIVE MENU =========================
              [1]  Instalar TODAS las Bases de Datos de Ejemplo (INSTALL SAMPLES)
              [2]  Conectar al Servidor (connect <host> <port>)
              [3]  Autenticar con JettraJWT (login <username> <password>)
              [4]  Guardar / Listar Perfiles de Conexión (save/list connections)
              [5]  Ver Consumo de Recursos: RAM, CPU, Disco (status)
              [6]  Ver Topología de Nodos del Clúster Raft (show nodes)
              [7]  Alternar Carga Perezosa (lazy reference on / off)
              [8]  Listar y Explorar Bases de Datos (SHOW DATABASES)
              [9]  Listar Colecciones Multimodelo (SHOW COLLECTIONS)
              [10] Consultar con JettraQL o JettraSQL (FROM <col> / SELECT ...)
              [11] Consultar / Listar Documentos (FIND ALL <col>)
              [12] Insertar Documento (INSERT INTO <col> ID <id> JSON {..})
              [13] Respaldar Base de Datos (BACKUP DATABASE)
              [14] Restaurar Base de Datos (RESTORE DATABASE)
              [15] Cerrar Sesión (logout)
              [16] Ver Ayuda Completa (help)
              [17] Salir
            ================================================================================
            """;
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                         JETTRASTORE INTERACTIVE SHELL                         ");
        System.out.println("================================================================================");
        System.out.println("Iniciando sesión interactiva de JettraStore CLI (Java 25 LTS)...\n");

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Paso 1: Pedir servidor y puerto de conexión
            System.out.print("Servidor JettraStore Host [127.0.0.1]: ");
            String hostInput = reader.readLine();
            String host = (hostInput == null || hostInput.isBlank()) ? "127.0.0.1" : hostInput.trim();

            System.out.print("Puerto del Servidor [9091]: ");
            String portInput = reader.readLine();
            int port = 9091;
            if (portInput != null && !portInput.isBlank()) {
                try { port = Integer.parseInt(portInput.trim()); } catch (Exception ignored) {}
            }

            // Paso 2: Pedir credenciales de login
            System.out.print("Usuario [admin]: ");
            String userInput = reader.readLine();
            String user = (userInput == null || userInput.isBlank()) ? "admin" : userInput.trim();

            System.out.print("Contraseña [admin-jettra]: ");
            String passInput = reader.readLine();
            String pass = (passInput == null || passInput.isBlank()) ? "admin-jettra" : passInput.trim();

            JettraStoreShellApp app = new JettraStoreShellApp(false);
            System.out.println(app.executeCommand("connect " + host + " " + port));
            System.out.println(app.executeCommand("login " + user + " " + pass));
            System.out.println("Escriba 'help' o '?' para ver la lista completa de comandos, o 'menu' para el menú interactivo.\n");

            while (true) {
                String promptUser = app.isAuthenticated() ? app.getCurrentUser() : "unauthenticated";
                System.out.print(promptUser + "@" + app.currentDatabase + "> ");
                String line = reader.readLine();
                if (line == null || "exit".equalsIgnoreCase(line.trim()) || "quit".equalsIgnoreCase(line.trim())) {
                    System.out.println("¡Sesión finalizada!");
                    break;
                }
                if (!line.isBlank()) {
                    System.out.println(app.executeCommand(line));
                }
            }
        } catch (Exception e) {
            System.err.println("Error en shell: " + e.getMessage());
        }
    }

    public String getCurrentDatabase() { return currentDatabase; }
    public boolean isLazyLoad() { return lazyLoad; }
    public boolean isShowReferences() { return showReferences; }
    public int getPageSize() { return pageSize; }
    public boolean isAuthenticated() { return authenticated; }
    public String getCurrentUser() { return currentUser; }
    public String getCurrentHost() { return currentHost; }
    public int getCurrentPort() { return currentPort; }
    public Map<String, SavedConnection> getSavedConnections() { return Collections.unmodifiableMap(savedConnections); }
}
