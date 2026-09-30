package io.jettra.driver;

import io.jettra.driver.admin.JettraAdminClient;
import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.cluster.ClusterNode;
import io.jettra.store.cluster.DynamicRingEngine;
import io.jettra.store.core.JettraDatabase;
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
}
