package io.jettra.driver;

import io.jettra.driver.admin.JettraAdminClient;
import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.core.JettraDatabase;
import io.jettra.store.core.JettraStoreConfig;
import io.jettra.store.engine.query.JettraSQLProcessor;
import io.jettra.store.security.JettraSecurityManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraClient implements AutoCloseable {
    private final JettraClientConfig config;
    private final String sessionToken;
    private final JettraAdminClient adminClient;
    private final JettraSecurityManager securityManager;
    private final Map<String, JettraDatabase> databases = new ConcurrentHashMap<>();

    private JettraClient(JettraClientConfig config) {
        this.config = config;
        this.securityManager = new JettraSecurityManager();
        this.sessionToken = securityManager.authenticate(config.getUsername(), config.getPassword());
        this.adminClient = new JettraAdminClient(sessionToken);
    }

    public static JettraClient connect(JettraClientConfig config) {
        return new JettraClient(config);
    }

    public static JettraClient connect(String host, int port, String user, String pass) {
        JettraClientConfig cfg = JettraClientConfig.builder()
            .addClusterNode(host, port)
            .credentials(user, pass)
            .build();
        return new JettraClient(cfg);
    }

    public java.util.List<String> listDatabases() {
        return new java.util.ArrayList<>(databases.keySet());
    }

    public boolean dropDatabase(String name) {
        return databases.remove(name) != null;
    }

    public boolean databaseExists(String name) {
        return databases.containsKey(name);
    }

    public JettraDatabase getDatabase(String name) {
        return databases.computeIfAbsent(name, k -> new JettraDatabase(k, JettraStoreConfig.load()));
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
