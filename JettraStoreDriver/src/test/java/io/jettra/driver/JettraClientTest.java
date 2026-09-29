package io.jettra.driver;

import io.jettra.driver.config.JettraClientConfig;
import io.jettra.store.core.JettraDatabase;
import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.jettra.test.core.JettraAssert.*;

public class JettraClientTest {

    @Test
    @DisplayName("Debe conectar cliente JettraClient y autenticar con JettraJWT")
    public void testClientConnectionAndAuth() {
        JettraClientConfig config = JettraClientConfig.builder()
            .addClusterNode("127.0.0.1", 9091)
            .credentials("admin", "admin-jettra")
            .build();

        try (JettraClient client = JettraClient.connect(config)) {
            assertNotNull(client.getSessionToken());
            assertTrue(client.getSessionToken().startsWith("JettraJWT."));

            // Probar ejecución SQL
            var result = client.sql("main_db", "SELECT * FROM products");
            assertNotNull(result);
            assertNotNull(result.columns());
        }
    }

    @Test
    @DisplayName("Debe ejecutar Backup y Restore programático a través del driver admin")
    public void testDriverBackupAndRestore() throws IOException {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraDatabase db = client.getDatabase("analytics_db");
            db.getDocumentEngine("metrics").insert("m1", Map.of("cpu", 15.4));

            Path backupPath = Files.createTempFile("driver_bak", ".jettra_bak");
            var backupRes = client.admin().backupDatabase(db, backupPath);
            assertTrue(backupRes.success());
            assertTrue(backupRes.durationMs() >= 0);

            var restoreRes = client.admin().restoreDatabase(backupPath, db);
            assertTrue(restoreRes.success());
            Files.deleteIfExists(backupPath);
        }
    }
}
