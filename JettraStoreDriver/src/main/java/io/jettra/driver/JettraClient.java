package io.jettra.driver;

import io.jettra.driver.admin.JettraAdminClient;
import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.cluster.ClusterNode;
import io.jettra.store.cluster.DynamicRingEngine;
import io.jettra.store.core.JettraDatabase;
import io.jettra.store.core.StorageMode;
import com.jettra.memory.api.JettraMemoryEngine;
import com.jettra.memory.engine.StorageMetrics;
import io.jettra.store.police.JettraPolice;
import io.jettra.store.core.JettraStoreConfig;
import io.jettra.store.engine.query.JettraSQLProcessor;
import io.jettra.store.security.JettraSecurityManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraClient implements AutoCloseable {
    private final JettraClientConfig config;
    private final ConcurrentHashMap<String, JettraDatabase> databases = new ConcurrentHashMap<>();
    private final JettraAdminClient adminClient;
    private final String sessionToken;
    private final JettraSecurityManager securityManager = new JettraSecurityManager();
    private final DynamicRingEngine ringEngine = new DynamicRingEngine("node-01", 0.85, 0.45);

    public JettraClient(JettraClientConfig config) {
        this.config = config;
        this.sessionToken = securityManager.authenticate(config.getUsername(), config.getPassword());
        this.adminClient = new JettraAdminClient(sessionToken);

        // Registrar nodos pares iniciales del cluster Raft
        this.ringEngine.registerPeer(new ClusterNode("node-02", "192.168.1.102", 9091, ClusterNode.Role.SECONDARY));
        this.ringEngine.registerPeer(new ClusterNode("node-03", "192.168.1.103", 9091, ClusterNode.Role.SECONDARY));
    }

    public static JettraClient connect(String host, int port, String user, String pass) {
        JettraClientConfig cfg = JettraClientConfig.builder()
            .addClusterNode(host, port)
            .credentials(user, pass)
            .build();
        return new JettraClient(cfg);
    }

    public static JettraClient connect(JettraClientConfig config) {
        return new JettraClient(config);
    }

    public java.util.List<String> listDatabases() {
        Set<String> result = new TreeSet<>(databases.keySet());

        // 1. Escanear rutas físicas configuradas en database.properties y locales
        JettraStoreConfig cfg = JettraStoreConfig.load();
        scanDatabasesFromPath(cfg.getStoragePath(), result);
        scanDatabasesFromPath(cfg.getConfiguredStoragePath(), result);
        scanDatabasesFromPath("./data/jettra", result);
        scanDatabasesFromPath("data/jettra", result);
        scanDatabasesFromPath("../data/jettra", result);
        scanDatabasesFromPath("/jettra/data", result);

        return new ArrayList<>(result);
    }

    private void scanDatabasesFromPath(String pathStr, Set<String> target) {
        if (pathStr == null || pathStr.isBlank()) return;
        try {
            Path p = Path.of(pathStr);
            if (Files.exists(p) && Files.isDirectory(p)) {
                try (var stream = Files.list(p)) {
                    stream.forEach(entry -> {
                        String name = entry.getFileName().toString();
                        if (Files.isDirectory(entry)) {
                            if (!name.startsWith(".")) {
                                target.add(name);
                            }
                        } else if (name.endsWith("_sstable.jettra")) {
                            target.add(name.substring(0, name.indexOf("_sstable.jettra")));
                        } else if (name.endsWith(".jettra") && !name.contains("_wal")) {
                            target.add(name.substring(0, name.indexOf(".jettra")));
                        } else if (name.endsWith("_meta.json")) {
                            target.add(name.substring(0, name.indexOf("_meta.json")));
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    public boolean dropDatabase(String name) {
        if (name == null || name.isBlank()) return false;
        boolean inMemory = false;
        JettraDatabase db = databases.remove(name);
        if (db != null) {
            inMemory = true;
            try {
                db.drop();
            } catch (Exception ignored) {}
        }

        boolean onDisk = deletePhysicalDatabase(name.trim());
        return inMemory || onDisk;
    }

    private boolean deletePhysicalDatabase(String name) {
        JettraStoreConfig cfg = JettraStoreConfig.load();
        Set<String> searchPaths = new LinkedHashSet<>();
        if (cfg.getStoragePath() != null) searchPaths.add(cfg.getStoragePath());
        if (cfg.getConfiguredStoragePath() != null) searchPaths.add(cfg.getConfiguredStoragePath());
        searchPaths.add("./data/jettra");
        searchPaths.add("data/jettra");
        searchPaths.add("../data/jettra");
        searchPaths.add("../../data/jettra");
        searchPaths.add("/jettra/data");
        searchPaths.add(System.getProperty("user.home") + "/jettra/data");

        boolean deleted = false;
        for (String pathStr : searchPaths) {
            try {
                Path p = Path.of(pathStr);
                if (!Files.exists(p)) continue;

                // 1. Si es directorio con el nombre de la BD
                Path dbDir = p.resolve(name);
                if (Files.exists(dbDir)) {
                    if (Files.isDirectory(dbDir)) {
                        try (var stream = Files.walk(dbDir)) {
                            stream.sorted(Comparator.reverseOrder())
                                  .forEach(f -> {
                                      try { Files.deleteIfExists(f); } catch (Exception ignored) {}
                                  });
                        }
                        deleted = true;
                    } else {
                        deleted |= Files.deleteIfExists(dbDir);
                    }
                }

                // 2. Archivos asociados
                deleted |= Files.deleteIfExists(p.resolve(name + "_sstable.jettra"));
                deleted |= Files.deleteIfExists(p.resolve(name + ".jettra"));
                deleted |= Files.deleteIfExists(p.resolve(name + "_wal.jettra"));
                deleted |= Files.deleteIfExists(p.resolve(name + "_meta.json"));
                deleted |= Files.deleteIfExists(p.resolve(name + "_sstable" + cfg.getFileExtension()));
                deleted |= Files.deleteIfExists(p.resolve(name + cfg.getFileExtension()));
                deleted |= Files.deleteIfExists(p.resolve(name + ".snap"));
                deleted |= Files.deleteIfExists(p.resolve(name + "_backup.snap"));

            } catch (Exception ignored) {}
        }
        return deleted;
    }

    public boolean isDatabaseLoaded(String name) {
        return name != null && databases.containsKey(name);
    }

    public int getLightweightCollectionCount(String dbName) {
        if (dbName == null) return 0;
        if (databases.containsKey(dbName)) {
            return databases.get(dbName).getAllCollectionNames().size();
        }
        return io.jettra.store.core.JettraDatabase.getLightweightCollectionCount(dbName, JettraStoreConfig.load());
    }

    public boolean databaseExists(String name) {
        return databases.containsKey(name) || listDatabases().contains(name);
    }

    public JettraDatabase getDatabase(String name) {
        return databases.computeIfAbsent(name, k -> new JettraDatabase(k, JettraStoreConfig.load(), ringEngine));
    }

    public io.jettra.store.engine.query.JettraQLProcessor.JQLResult jql(String databaseName, String query) {
        JettraDatabase db = getDatabase(databaseName);
        io.jettra.store.engine.query.JettraQLProcessor processor = new io.jettra.store.engine.query.JettraQLProcessor(db);
        return processor.execute(query);
    }

    /**
     * Ejecuta una consulta SQL paginada de forma segura garantizando control de memoria Heap.
     *
     * @param databaseName Nombre de la base de datos
     * @param query Sentencia SQL (ej. SELECT * FROM clientes)
     * @param page Número de página (1-based)
     * @param pageSize Tamaño del lote por página
     * @return Resultado de la consulta con filas del lote actual y resumen
     */
    public JettraSQLProcessor.QueryResult sqlPaged(String databaseName, String query, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 5000));
        int offset = (safePage - 1) * safeSize;
        String clean = query.replaceAll("(?i)\\s+LIMIT\\s+\\d+(\\s+OFFSET\\s+\\d+)?", "").trim();
        String pagedQuery = clean + " LIMIT " + safeSize + " OFFSET " + offset;
        return sql(databaseName, pagedQuery);
    }

    /**
     * Crea un cursor de carga perezosa distribuida (Lazy Paged Cursor) para iterar colecciones
     * masivas página por página sin sobrecargar el Heap y permitiendo recolección de basura O(1).
     *
     * @param databaseName Nombre de la base de datos
     * @param collection Nombre de la colección o bucket
     * @param pageSize Tamaño de página
     * @return Cursor perezoso autónomo
     */
    public io.jettra.store.police.JettraPolice.LazyPagedCursor<Map<String, Object>> cursor(String databaseName, String collection, int pageSize) {
        JettraDatabase db = getDatabase(databaseName);
        var engine = db.getDocumentEngine(collection);
        int safeSize = Math.max(1, Math.min(pageSize, 5000));
        return new io.jettra.store.police.JettraPolice.LazyPagedCursor<>(safeSize, (offset, limit) -> {
            List<Map<String, Object>> batch = new ArrayList<>(limit);
            if (engine == null || engine.isEmpty()) return batch;
            int current = 0;
            for (Map<String, Object> doc : engine) {
                if (current >= offset && batch.size() < limit) {
                    batch.add(doc);
                }
                current++;
                if (batch.size() >= limit) break;
            }
            return batch;
        });
    }

    /**
     * Acceso al centinela supervisor de estabilidad y telemetría de memoria.
     */
    public io.jettra.store.police.JettraPolice getPolice() {
        return io.jettra.store.police.JettraPolice.getInstance();
    }

    /**
     * Evalúa de forma predictiva si una consulta sobre una colección causaría riesgo de OOM en el Heap.
     */
    public io.jettra.store.police.JettraPolice.PoliceDecision evaluateQuerySafety(String databaseName, String collection, int requestedLimit) {
        JettraDatabase db = getDatabase(databaseName);
        long count = db.getDocumentEngine(collection) != null ? db.getDocumentEngine(collection).count() : 0;
        return io.jettra.store.police.JettraPolice.getInstance().evaluateHeapSafety("DRIVER_EVALUATE", collection, count, requestedLimit, 512L);
    }

    public JettraSQLProcessor.QueryResult sql(String databaseName, String query) {
        JettraDatabase db = getDatabase(databaseName);
        JettraSQLProcessor processor = new JettraSQLProcessor(db);
        return processor.execute(query);
    }

    public JettraAdminClient admin() {
        return adminClient;
    }

    public JettraSecurityManager getSecurityManager() {
        return securityManager;
    }

    public DynamicRingEngine getRingEngine() {
        return ringEngine;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public JettraClientConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        databases.clear();
    }

    public JettraMemoryEngine getMemoryEngine(String databaseName) {
        return getDatabase(databaseName).getMemoryEngine();
    }

    public void putBinary(String databaseName, String key, byte[] data) throws java.io.IOException {
        getDatabase(databaseName).putOffHeapBinary(key, data);
    }

    public byte[] getBinary(String databaseName, String key) throws java.io.IOException {
        return getDatabase(databaseName).getOffHeapBinary(key);
    }

    public StorageMetrics getMemoryMetrics(String databaseName) {
        return getDatabase(databaseName).getMemoryMetrics();
    }

    public boolean compactMemory(String databaseName) throws Exception {
        var engine = getDatabase(databaseName).getMemoryEngine();
        if (engine != null) {
            engine.compact();
            return true;
        }
        return false;
    }

    public List<JettraPolice.PoliceAlert> getPoliceAlerts() {
        return JettraPolice.getInstance().getAlerts();
    }

    public boolean isPoliceActive() {
        return JettraPolice.getInstance().isActive();
    }


    public StorageMode getStorageMode(String databaseName) {
        return getDatabase(databaseName).getStorageMode();
    }

    public void setStorageMode(String databaseName, StorageMode mode) {
        getDatabase(databaseName).setStorageMode(mode);
    }

    public void setGlobalStorageMode(StorageMode mode) {
        for (JettraDatabase db : databases.values()) {
            db.setStorageMode(mode);
        }
    }

    public Set<String> getCollectionNames(String databaseName) {
        return getDatabase(databaseName).getAllCollectionNames();
    }

    public Set<String> getDocumentEngineNames(String databaseName) {
        return getDatabase(databaseName).getDocumentEngineNames();
    }

    public long count(String databaseName, String bucketName) {
        var db = getDatabase(databaseName);
        if (db.getDocumentEngineNames().contains(bucketName)) {
            return db.getDocumentEngine(bucketName).count();
        }
        return 0;
    }

    public List<Map<String, Object>> getDocuments(String databaseName, String bucketName, int offset, int limit) {
        var db = getDatabase(databaseName);
        List<Map<String, Object>> result = new ArrayList<>();
        if (db.getDocumentEngineNames().contains(bucketName)) {
            var engine = db.getDocumentEngine(bucketName);
            int current = 0;
            for (Map<String, Object> doc : engine) {
                if (current >= offset && result.size() < limit) {
                    result.add(doc);
                }
                current++;
                if (result.size() >= limit) break;
            }
        }
        return result;
    }

    public void insertDocument(String databaseName, String bucketName, String id, Map<String, Object> data) {
        getDatabase(databaseName).getDocumentEngine(bucketName).insert(id, data);
    }

    public void createBucket(String databaseName, String bucketName, String engineType) {
        var db = getDatabase(databaseName);
        switch (engineType != null ? engineType.toUpperCase() : "DOCUMENT") {
            case "VECTOR" -> db.getVectorEngine(bucketName, 3);
            case "GRAPH" -> db.getGraphEngine(bucketName);
            case "TIMESERIES" -> db.getTimeSeriesEngine(bucketName);
            case "KEYVALUE" -> db.getKeyValueEngine(bucketName);
            default -> db.getDocumentEngine(bucketName);
        }
    }

    public boolean dropBucket(String databaseName, String bucketName) {
        return getDatabase(databaseName).dropCollection(bucketName);
    }

    public boolean deleteDocument(String databaseName, String bucketName, String id) {
        var db = getDatabase(databaseName);
        if (db.getDocumentEngineNames().contains(bucketName)) {
            return db.getDocumentEngine(bucketName).delete(id);
        } else if (db.getKeyValueEngineNames().contains(bucketName)) {
            return db.getKeyValueEngine(bucketName).remove(id);
        }
        return false;
    }

    public boolean createDatabase(String name) {
        if (name == null || name.isBlank()) return false;
        getDatabase(name.trim());
        return true;
    }

    public record BucketRecord(String id, String summary, String references) {}

    public long getBucketCount(String databaseName, String bucketName) {
        var db = getDatabase(databaseName);
        if (db.getDocumentEngineNames().contains(bucketName)) {
            return db.getDocumentEngine(bucketName).count();
        } else if (db.getVectorEngineNames().contains(bucketName)) {
            return db.getVectorEngine(bucketName, 3).size();
        } else if (db.getGraphEngineNames().contains(bucketName)) {
            return db.getGraphEngine(bucketName).size();
        } else if (db.getKeyValueEngineNames().contains(bucketName)) {
            return db.getKeyValueEngine(bucketName).size();
        } else if (db.getTimeSeriesEngineNames().contains(bucketName)) {
            return db.getTimeSeriesEngine(bucketName).size();
        } else if (db.getGeospatialEngineNames().contains(bucketName)) {
            return db.getGeospatialEngine(bucketName).size();
        } else if (db.getColumnarEngineNames().contains(bucketName)) {
            return db.getColumnarEngine(bucketName).size();
        }
        return 0;
    }

    public List<BucketRecord> getBucketRecords(String databaseName, String bucketName, int offset, int limit) {
        var db = getDatabase(databaseName);
        List<BucketRecord> items = new ArrayList<>();
        if (db.getDocumentEngineNames().contains(bucketName)) {
            var engine = db.getDocumentEngine(bucketName);
            int current = 0;
            for (Map<String, Object> doc : engine) {
                if (current >= offset && items.size() < limit) {
                    String id = String.valueOf(doc.getOrDefault("_id", ""));
                    String refs = doc.keySet().stream().filter(k -> k.startsWith("_ref")).map(k -> k + "->" + doc.get(k)).reduce("", (a, b) -> a + " " + b);
                    items.add(new BucketRecord(id, doc.toString(), refs.isBlank() ? "(Sin Ref)" : refs.trim()));
                }
                current++;
                if (items.size() >= limit) break;
            }
        } else if (db.getVectorEngineNames().contains(bucketName)) {
            var vecEngine = db.getVectorEngine(bucketName, 3);
            for (var entry : vecEngine.getAllVectors().entrySet()) {
                items.add(new BucketRecord(entry.getKey(), Arrays.toString(entry.getValue()), "Vector [" + vecEngine.getDimensions() + "D]"));
            }
        } else if (db.getGraphEngineNames().contains(bucketName)) {
            var graphEngine = db.getGraphEngine(bucketName);
            var edgesMap = graphEngine.getAllEdges();
            for (String v : graphEngine.getVertices()) {
                var out = edgesMap.getOrDefault(v, List.of());
                String edgeDesc = out.isEmpty() ? "(Vértice aislado)" : out.stream().map(e -> e.label() + " -> " + e.targetVertex()).reduce("", (a, b) -> a + "; " + b);
                items.add(new BucketRecord(v, edgeDesc.startsWith("; ") ? edgeDesc.substring(2) : edgeDesc, "Graph (" + out.size() + " aristas)"));
            }
        } else if (db.getKeyValueEngineNames().contains(bucketName)) {
            var kvEngine = db.getKeyValueEngine(bucketName);
            for (var entry : kvEngine.getAll().entrySet()) {
                String valStr = new String(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                items.add(new BucketRecord(entry.getKey(), valStr, "KeyValue"));
            }
        } else if (db.getTimeSeriesEngineNames().contains(bucketName)) {
            var tsEngine = db.getTimeSeriesEngine(bucketName);
            for (var entry : tsEngine.getAll().entrySet()) {
                String timeStr = java.time.Instant.ofEpochMilli(entry.getKey()).toString();
                items.add(new BucketRecord(String.valueOf(entry.getKey()), "Valor: " + entry.getValue() + " (" + timeStr + ")", "TimeSeries"));
            }
        } else if (db.getGeospatialEngineNames().contains(bucketName)) {
            var geoEngine = db.getGeospatialEngine(bucketName);
            for (var entry : geoEngine.getAllPoints().entrySet()) {
                items.add(new BucketRecord(entry.getKey(), "Lat: " + entry.getValue().latitude() + ", Lon: " + entry.getValue().longitude(), "Geospatial"));
            }
        } else if (db.getColumnarEngineNames().contains(bucketName)) {
            var colEngine = db.getColumnarEngine(bucketName);
            int count = colEngine.size();
            var numCols = colEngine.getNumericColumns();
            var txtCols = colEngine.getTextColumns();
            for (int i = 0; i < count && items.size() < limit; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (var e : numCols.entrySet()) {
                    if (i < e.getValue().size()) row.put(e.getKey(), e.getValue().get(i));
                }
                for (var e : txtCols.entrySet()) {
                    if (i < e.getValue().size()) row.put(e.getKey(), e.getValue().get(i));
                }
                items.add(new BucketRecord("row_" + (i + 1), row.toString(), "Columnar"));
            }
        }
        return items;
    }

    public void insertRecord(String databaseName, String bucketName, String id, String rawData) {
        var db = getDatabase(databaseName);
        if (db.getDocumentEngineNames().contains(bucketName)) {
            Map<String, Object> map = new HashMap<>();
            map.put("_id", id);
            map.put("raw_data", rawData);
            db.getDocumentEngine(bucketName).insert(id, map);
        } else if (db.getKeyValueEngineNames().contains(bucketName)) {
            db.getKeyValueEngine(bucketName).put(id, rawData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else if (db.getVectorEngineNames().contains(bucketName)) {
            String[] parts = rawData.replace("[", "").replace("]", "").split(",");
            float[] floats = new float[parts.length];
            for (int i = 0; i < parts.length; i++) floats[i] = Float.parseFloat(parts[i].trim());
            db.getVectorEngine(bucketName, floats.length).index(id, floats);
        } else if (db.getGraphEngineNames().contains(bucketName)) {
            db.getGraphEngine(bucketName).addVertex(id);
        } else if (db.getTimeSeriesEngineNames().contains(bucketName)) {
            db.getTimeSeriesEngine(bucketName).record(System.currentTimeMillis(), Double.parseDouble(rawData.trim()));
        } else if (db.getGeospatialEngineNames().contains(bucketName)) {
            String[] parts = rawData.split(",");
            db.getGeospatialEngine(bucketName).insertPoint(id, Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put("_id", id);
            map.put("raw_data", rawData);
            db.getDocumentEngine(bucketName).insert(id, map);
        }
    }

}
