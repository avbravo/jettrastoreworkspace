package io.jettra.shell;

import io.jettra.driver.JettraClient;
import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.jettra.test.core.JettraAssert.*;

public class JettraStoreShellTest {

    @Test
    @DisplayName("Debe procesar connect, login, logout, save/remove/list connections")
    public void testConnectionLifecycleAndProfiles() {
        JettraStoreShellApp shell = new JettraStoreShellApp(false);

        // 1. Estado inicial sin autenticación
        assertFalse(shell.isAuthenticated());
        String unauthBlocked = shell.executeCommand("SHOW DATABASES");
        assertTrue(unauthBlocked.contains("[AUTH REQUIRED]"));

        // 2. Conexión y login explícito
        String conn = shell.executeCommand("connect 127.0.0.1 9091");
        assertTrue(conn.contains("[CONNECTED]"));

        String login = shell.executeCommand("login admin admin-jettra");
        assertTrue(login.contains("[AUTH SUCCESS]"));
        assertTrue(shell.isAuthenticated());
        assertEquals("admin", shell.getCurrentUser());

        // 3. Guardar conexión actual con save connection
        String save = shell.executeCommand("save connection prod_node_master");
        assertTrue(save.contains("[SUCCESS]"));
        assertTrue(shell.getSavedConnections().containsKey("prod_node_master"));

        // 4. Listar conexiones con list connections y list conections
        String list1 = shell.executeCommand("list connections");
        assertTrue(list1.contains("prod_node_master"));
        assertTrue(list1.contains("127.0.0.1"));

        String list2 = shell.executeCommand("list conections");
        assertTrue(list2.contains("prod_node_master"));

        // 5. Remover conexión con remove connection
        String rem = shell.executeCommand("remove connection prod_node_master");
        assertTrue(rem.contains("[SUCCESS]"));
        assertFalse(shell.getSavedConnections().containsKey("prod_node_master"));

        // 6. Cierre de sesión con logout
        String logout = shell.executeCommand("logout");
        assertTrue(logout.contains("[LOGOUT]"));
        assertFalse(shell.isAuthenticated());

        // 7. Comando bloqueado tras logout
        String blockedAfterLogout = shell.executeCommand("SHOW DATABASES");
        assertTrue(blockedAfterLogout.contains("[AUTH REQUIRED]"));

        // 8. Re-autenticación
        String relogin = shell.executeCommand("login admin admin-jettra");
        assertTrue(relogin.contains("[AUTH SUCCESS]"));
        assertTrue(shell.isAuthenticated());
    }

    @Test
    @DisplayName("Debe procesar status, show nodes y lazy reference")
    public void testStatusNodesAndLazyReference() {
        JettraStoreShellApp shell = new JettraStoreShellApp(true);

        // 1. Telemetría de recursos con status
        String status = shell.executeCommand("status");
        assertTrue(status.contains("RAM (MEMORIA)"));
        assertTrue(status.contains("PROCESADOR (CPU)"));
        assertTrue(status.contains("DISCO (ALMACENAMIENTO)"));
        assertTrue(status.contains("Panama FFM"));

        // 2. Mostrar nodos del clúster con show nodes
        String nodes = shell.executeCommand("show nodes");
        assertTrue(nodes.contains("node-01"));
        assertTrue(nodes.contains("node-02"));
        assertTrue(nodes.contains("node-03"));
        assertTrue(nodes.contains("LEADER"));
        assertTrue(nodes.contains("FOLLOWER"));

        // 3. Configuración de lazy reference on / off
        String lazyOff = shell.executeCommand("lazy reference off");
        assertTrue(lazyOff.contains("DESACTIVADO (OFF)"));
        assertFalse(shell.isLazyLoad());

        String lazyOn = shell.executeCommand("lazy reference on");
        assertTrue(lazyOn.contains("ACTIVADO (ON)"));
        assertTrue(shell.isLazyLoad());
    }

    @Test
    @DisplayName("Debe instalar todas las bases de datos con INSTALL SAMPLES y ejecutar JettraQL")
    public void testInstallAllSamplesAndJettraQL() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            // 1. INSTALL SAMPLES debe instalar todas las 5 bases de datos de ejemplo
            String samples = shell.executeCommand("INSTALL SAMPLES");
            assertTrue(samples.contains("[SUCCESS]"));
            assertTrue(samples.contains("sample_enterprise_db"));
            assertTrue(samples.contains("sample_ecommerce_db"));
            assertTrue(samples.contains("sample_ai_graph_db"));
            assertTrue(samples.contains("sample_iot_telemetry_db"));
            assertTrue(samples.contains("sample_financial_db"));

            // 2. Verificar que las bases de datos están registradas
            String showDbs = shell.executeCommand("SHOW DATABASES");
            assertTrue(showDbs.contains("sample_enterprise_db"));
            assertTrue(showDbs.contains("sample_ecommerce_db"));
            assertTrue(showDbs.contains("sample_ai_graph_db"));

            // 3. Consultas expresivas con JettraQL
            String jqlFrom = shell.executeCommand("FROM products");
            assertTrue(jqlFrom.contains("JETTRAQL [FROM]"));
            assertTrue(jqlFrom.contains("prod_01"));

            String jqlFetch = shell.executeCommand("FETCH products prod_01 RESOLVE REFS");
            assertTrue(jqlFetch.contains("JETTRAQL [FETCH]"));

            String jqlMatch = shell.executeCommand("MATCH (prod_01)-[BELONGS_TO]->(cat_hardware) IN catalog_graph");
            assertTrue(jqlMatch.contains("JETTRAQL [MATCH]"));
        }
    }

    @Test
    @DisplayName("Debe verificar comandos en lista help e interactuar con registros")
    public void testHelpAndCrudOperations() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            // Verificar help exhaustivo con nuevos comandos
            String help = shell.executeCommand("help");
            assertTrue(help.contains("connect <url> <port>"));
            assertTrue(help.contains("login <username> <password>"));
            assertTrue(help.contains("logout"));
            assertTrue(help.contains("save connection <nombre>"));
            assertTrue(help.contains("remove connection <nombre>"));
            assertTrue(help.contains("list connections"));
            assertTrue(help.contains("status"));
            assertTrue(help.contains("show nodes"));
            assertTrue(help.contains("lazy reference on"));
            assertTrue(help.contains("lazy reference off"));
            assertTrue(help.contains("JettraQL"));
            assertTrue(help.contains("INSTALL SAMPLES"));

            // CRUD en colección
            shell.executeCommand("CREATE DATABASE crud_db");
            shell.executeCommand("CREATE COLLECTION users TYPE DOCUMENT");
            String ins = shell.executeCommand("INSERT INTO users ID u1 JSON {\"name\": \"Carlos\", \"role\": \"developer\"}");
            assertTrue(ins.contains("[SUCCESS]"));

            String get = shell.executeCommand("GET users u1");
            assertTrue(get.contains("Carlos"));

            String del = shell.executeCommand("DELETE users u1");
            assertTrue(del.contains("[SUCCESS]"));
        }
    }

    @Test
    @DisplayName("Debe ejecutar comando BACKUP DATABASE y RESTORE DATABASE desde el shell")
    public void testShellBackupRestore() throws IOException {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("INSTALL SAMPLES");

            Path tempBackup = Files.createTempFile("shell_backup", ".jettra_bak");
            String backupOutput = shell.executeCommand("BACKUP DATABASE sample_enterprise_db TO '" + tempBackup.toAbsolutePath() + "'");
            assertTrue(backupOutput.contains("[SUCCESS]"));

            String restoreOutput = shell.executeCommand("RESTORE DATABASE sample_enterprise_db FROM '" + tempBackup.toAbsolutePath() + "'");
            assertTrue(restoreOutput.contains("[SUCCESS]"));

            Files.deleteIfExists(tempBackup);
        }
    }
}
