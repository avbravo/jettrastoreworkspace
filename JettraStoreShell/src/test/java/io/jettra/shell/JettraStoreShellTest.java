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
    @DisplayName("Debe procesar status, administración de nodos y lazy reference")
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

        // 3. Administración de nodos (ADD, STOP, START, REMOVE)
        String addNode = shell.executeCommand("ADD NODE node-04 192.168.1.104 9091 SECONDARY");
        assertTrue(addNode.contains("[SUCCESS]"));

        String stopNode = shell.executeCommand("STOP NODE node-04");
        assertTrue(stopNode.contains("[SUCCESS]"));
        assertTrue(shell.executeCommand("show nodes").contains("STOPPED"));

        String startNode = shell.executeCommand("START NODE node-04");
        assertTrue(startNode.contains("[SUCCESS]"));
        assertTrue(shell.executeCommand("show nodes").contains("RUNNING"));

        String remNode = shell.executeCommand("REMOVE NODE node-04");
        assertTrue(remNode.contains("[SUCCESS]"));

        // 4. Configuración de lazy reference on / off
        String lazyOff = shell.executeCommand("lazy reference off");
        assertTrue(lazyOff.contains("DESACTIVADO (OFF)"));
        assertFalse(shell.isLazyLoad());

        String lazyOn = shell.executeCommand("lazy reference on");
        assertTrue(lazyOn.contains("ACTIVADO (ON)"));
        assertTrue(shell.isLazyLoad());

        // 5. Configuración de STORAGE_MODE (JVM_RAM vs DISK_MEMORY / JettraMemory)
        String smStatus = shell.executeCommand("STORAGE_MODE");
        assertTrue(smStatus.contains("MODO DE ALMACENAMIENTO"));

        String smDisk = shell.executeCommand("STORAGE_MODE DISK_MEMORY");
        assertTrue(smDisk.contains("DISK-MEMORY"));

        String smRam = shell.executeCommand("STORAGE_MODE JVM_RAM");
        assertTrue(smRam.contains("JVM-RAM"));
    }

    @Test
    @DisplayName("Debe instalar todas las bases de datos con INSTALL SAMPLES, mostrar show samples y ejecutar JettraQL y JettraSQL")
    public void testInstallAllSamplesAndQueries() {
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

            // 2. Verificar que las bases de datos están registradas en show dbs y show samples
            String showDbs = shell.executeCommand("SHOW DATABASES");
            assertTrue(showDbs.contains("sample_enterprise_db"));
            assertTrue(showDbs.contains("SAMPLE"));

            String showSamples = shell.executeCommand("SHOW SAMPLES");
            assertTrue(showSamples.contains("sample_enterprise_db"));
            assertTrue(showSamples.contains("samples_hostipal_db"));
            assertTrue(showSamples.contains("samples_ambiental_db"));
            assertTrue(showSamples.contains("INSTALADA"));

            // 3. Consultas expresivas con JettraQL
            String jqlFrom = shell.executeCommand("FROM products");
            assertTrue(jqlFrom.contains("JETTRAQL [FROM]"));
            assertTrue(jqlFrom.contains("prod_01"));

            String jqlFetch = shell.executeCommand("FETCH products prod_01 RESOLVE REFS");
            assertTrue(jqlFetch.contains("JETTRAQL [FETCH]"));

            String jqlMatch = shell.executeCommand("MATCH (prod_01)-[BELONGS_TO]->(cat_hardware) IN catalog_graph");
            assertTrue(jqlMatch.contains("JETTRAQL [MATCH]"));

            // 4. Consultas con JettraSQL
            String sqlSelect = shell.executeCommand("SQL SELECT * FROM products");
            assertTrue(sqlSelect.contains("JETTRASQL RESULTADO"));
            assertTrue(sqlSelect.contains("prod_01"));
        }
    }

    @Test
    @DisplayName("Debe administrar índices: creación, listado, reconstrucción y eliminación")
    public void testIndexManagement() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("CREATE DATABASE idx_db");
            shell.executeCommand("USE idx_db");
            shell.executeCommand("INSERT INTO articles ID art1 JSON {\"title\": \"Java 25 Vectors\", \"category\": \"Tech\"}");

            // 1. Crear índice
            String createIdx = shell.executeCommand("CREATE INDEX idx_cat ON articles (category) TYPE BTREE");
            assertTrue(createIdx.contains("[SUCCESS]"));
            assertTrue(createIdx.contains("idx_cat"));

            // 2. Listar índices
            String showIdx = shell.executeCommand("SHOW INDEXES");
            assertTrue(showIdx.contains("idx_cat"));
            assertTrue(showIdx.contains("articles"));

            // 3. Reconstruir índice
            String rebuild = shell.executeCommand("ALTER INDEX idx_cat REBUILD");
            assertTrue(rebuild.contains("[SUCCESS]"));

            // 4. Eliminar índice
            String dropIdx = shell.executeCommand("DROP INDEX idx_cat");
            assertTrue(dropIdx.contains("[SUCCESS]"));
        }
    }

    @Test
    @DisplayName("Debe administrar usuarios y roles RBAC por base de datos")
    public void testUserAndRoleManagement() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            // 1. Listar usuarios
            String showUsers = shell.executeCommand("SHOW USERS");
            assertTrue(showUsers.contains("admin"));
            assertTrue(showUsers.contains("SUPER_ADMIN"));

            // 2. Crear usuario
            String createUsr = shell.executeCommand("CREATE USER dev_user PASSWORD secret-pass ROLE DEVELOPER");
            assertTrue(createUsr.contains("[SUCCESS]"));

            // 3. Asignar rol de base de datos
            String grant = shell.executeCommand("GRANT READ_WRITE ON sample_ecommerce_db TO dev_user");
            assertTrue(grant.contains("[SUCCESS]"));

            // 4. Mostrar privilegios
            String grants = shell.executeCommand("SHOW GRANTS FOR dev_user");
            assertTrue(grants.contains("sample_ecommerce_db"));
            assertTrue(grants.contains("READ_WRITE"));

            // 5. Revocar rol
            String revoke = shell.executeCommand("REVOKE sample_ecommerce_db FROM dev_user");
            assertTrue(revoke.contains("[SUCCESS]"));

            // 6. Eliminar usuario
            String dropUser = shell.executeCommand("DROP USER dev_user");
            assertTrue(dropUser.contains("[SUCCESS]"));
        }
    }

    @Test
    @DisplayName("Debe procesar registros referenciados y operaciones CRUD")
    public void testReferencedRecordsAndCrud() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("CREATE DATABASE ref_db");
            shell.executeCommand("USE ref_db");

            // 1. Inserción de documentos
            shell.executeCommand("INSERT INTO customers ID c100 JSON {\"name\": \"Maria Silva\", \"city\": \"Panama\"}");
            shell.executeCommand("INSERT INTO orders ID o500 JSON {\"amount\": 350.0, \"status\": \"NEW\"}");

            // 2. Vincular referencia cruzada JettraRef
            String insRef = shell.executeCommand("INSERT REF orders o500 KEY _ref_customer TARGET document::customers#c100");
            assertTrue(insRef.contains("[SUCCESS]"));

            // 3. Obtener registro y verificar resolución
            String get = shell.executeCommand("GET orders o500");
            assertTrue(get.contains("o500"));
            assertTrue(get.contains("JettraRef Resolución"));
            assertTrue(get.contains("Maria Silva"));

            // 4. Resolver referencia explícita
            String res = shell.executeCommand("RESOLVE REF document::customers#c100");
            assertTrue(res.contains("Maria Silva"));

            // 5. Mostrar referencias del registro
            String showRefs = shell.executeCommand("SHOW REFS orders o500");
            assertTrue(showRefs.contains("_ref_customer"));

            // 6. Eliminar registro
            String del = shell.executeCommand("DELETE orders o500");
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

    
    
    @Test
    @DisplayName("Debe listar buckets/units, mostrar registros multimodelo y contar registros con show buckets, show records y count")
    public void testShowBucketsShowRecordsAndCount() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("INSTALL SAMPLES");

            // 1. SHOW BUCKETS / SHOW UNIT en sample_enterprise_db
            shell.executeCommand("USE sample_enterprise_db");
            String buckets = shell.executeCommand("SHOW BUCKETS");
            assertTrue(buckets.contains("sample_enterprise_db"));
            assertTrue(buckets.contains("employees"));
            assertTrue(buckets.contains("DOCUMENT"));

            String units = shell.executeCommand("SHOW UNIT");
            assertTrue(units.contains("employees"));

            // 2. Filtro por motor
            String docBuckets = shell.executeCommand("SHOW BUCKETS DOCUMENT");
            assertTrue(docBuckets.contains("employees"));

            // 3. SHOW RECORDS con paginación
            String recs = shell.executeCommand("SHOW RECORDS employees LIMIT 3");
            assertTrue(recs.contains("REGISTROS DE DOCUMENT BUCKET 'employees'"));
            assertTrue(recs.contains("emp_01"));

            // 4. COUNT para una unidad específica y COUNT ALL
            String cntEmp = shell.executeCommand("COUNT employees");
            assertTrue(cntEmp.contains("[COUNT] [DOCUMENT] 'employees':"));

            String cntAll = shell.executeCommand("COUNT ALL");
            assertTrue(cntAll.contains("CONTEO TOTAL DE REGISTROS EN BASE DE DATOS: 'sample_enterprise_db'"));
            assertTrue(cntAll.contains("Gran Total"));

            // 5. Verificar Motores Vectorial y Grafo en sample_ai_graph_db
            shell.executeCommand("USE sample_ai_graph_db");
            String aiBuckets = shell.executeCommand("SHOW BUCKETS");
            assertTrue(aiBuckets.contains("VECTOR"));
            assertTrue(aiBuckets.contains("GRAPH"));

            String vecRecs = shell.executeCommand("SHOW RECORDS concept_embeddings LIMIT 2");
            assertTrue(vecRecs.contains("REGISTROS DE VECTOR BUCKET 'concept_embeddings'"));

            String graphRecs = shell.executeCommand("SHOW RECORDS knowledge_network LIMIT 2");
            assertTrue(graphRecs.contains("REGISTROS DE GRAPH BUCKET 'knowledge_network'"));

            String cntVec = shell.executeCommand("COUNT concept_embeddings");
            assertTrue(cntVec.contains("[COUNT] [VECTOR] 'concept_embeddings':"));

            String cntGraph = shell.executeCommand("COUNT knowledge_network");
            assertTrue(cntGraph.contains("[COUNT] [GRAPH] 'knowledge_network':"));
        }
    }

    
    @Test
    @DisplayName("Debe cargar la base de datos example_factura_db con 3 millones de objetos multimodelo y referencias cruzadas")
    public void testLoadFacturaSampleDatabase3MObjects() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            String samples = shell.executeCommand("SHOW SAMPLES");
            System.out.println("DEBUG samples: " + samples);
            assertTrue(samples.contains("example_factura_db"));

            String loadResult = shell.executeCommand("LOAD SAMPLE example_factura_db");
            System.out.println("DEBUG loadResult: " + loadResult);
            assertTrue(loadResult.contains("CARGA MASIVA EXITOSA"));

            String buckets = shell.executeCommand("SHOW BUCKETS");
            System.out.println("DEBUG buckets: " + buckets);

            String countAll = shell.executeCommand("COUNT ALL");
            System.out.println("DEBUG countAll: " + countAll);
            assertTrue(countAll.contains("3000000 registro(s) multimodelo"));

            String showRecs = shell.executeCommand("SHOW RECORDS facturas LIMIT 2");
            System.out.println("DEBUG showRecs: " + showRecs);
            assertTrue(showRecs.contains("REGISTROS DE DOCUMENT BUCKET 'facturas'"));
        }
    }

    @Test
    @DisplayName("Debe eliminar fisicamente la base de datos con DROP DATABASE y DROP DB")
    public void testDropDatabaseCommand() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            // 1. Crear BD 'test_drop_db' y verificar existencia
            String created = shell.executeCommand("CREATE DATABASE test_drop_db");
            assertTrue(created.contains("[SUCCESS]"));
            shell.executeCommand("USE test_drop_db");
            shell.executeCommand("CREATE COLLECTION users");
            shell.executeCommand("INSERT INTO users (id, name) VALUES ('u1', 'Alice')");

            String dbsBefore = shell.executeCommand("SHOW DATABASES");
            assertTrue(dbsBefore.contains("test_drop_db"));

            // 2. Eliminar con DROP DATABASE
            String dropResult = shell.executeCommand("DROP DATABASE test_drop_db");
            assertTrue(dropResult.contains("[SUCCESS]"));

            String dbsAfter = shell.executeCommand("SHOW DATABASES");
            assertFalse(dbsAfter.contains("test_drop_db"));

            // 3. Crear con CREATE DB y eliminar con DROP DB
            shell.executeCommand("CREATE DB test_drop_db2");
            String dbs2 = shell.executeCommand("SHOW DATABASES");
            assertTrue(dbs2.contains("test_drop_db2"));

            String dropResult2 = shell.executeCommand("DROP DB test_drop_db2");
            assertTrue(dropResult2.contains("[SUCCESS]"));

            String dbsAfter2 = shell.executeCommand("SHOW DATABASES");
            assertFalse(dbsAfter2.contains("test_drop_db2"));
        }
    }

    @Test
    @DisplayName("show dbs debe ejecutarse de forma perezosa (lazy) sin cargar todo el contenido a RAM")
    public void testShowDatabasesLazyLoadingAndNoOOM() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);

            // 1. show dbs y show databases deben listar sin error de memoria
            String showDbs = shell.executeCommand("show dbs");
            assertTrue(showDbs.contains("default_db"));
            assertTrue(showDbs.contains("Base de Datos"));

            String showDatabases = shell.executeCommand("show databases");
            assertTrue(showDatabases.contains("default_db"));
        }
    }

    @Test
    @DisplayName("select * from clientes debe ejecutarse de forma streaming acotada sin OutOfMemoryError")
    public void testSelectClientesQueryStreamingAndLimit() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("use example_factura_db");

            // 1. select * from clientes sin limit explicito aplica default limit acotado
            String resDefault = shell.executeCommand("select * from clientes");
            System.out.println("DEBUG select * from clientes:\n" + resDefault.substring(0, Math.min(resDefault.length(), 600)));
            assertTrue(resDefault.contains("JETTRASQL RESULTADO"));
            assertTrue(resDefault.contains("clientes"));
            assertTrue(resDefault.contains("fila(s)"));

            // 2. select con LIMIT explicito
            String resLimit5 = shell.executeCommand("select * from clientes limit 5");
            assertTrue(resLimit5.contains("5 fila(s) retornada(s)"));

            // 3. select con WHERE usando índice
            String resWhere = shell.executeCommand("select * from clientes where _id = cli_100");
            assertTrue(resWhere.contains("JETTRASQL RESULTADO"));
        }
    }

    @Test
    @DisplayName("Debe gestionar paginación interactiva con FIRST, PREV, NEXT, LAST y PAGE_SIZE")
    public void testInteractivePaginationCommands() {
        try (JettraClient client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra")) {
            JettraStoreShellApp shell = new JettraStoreShellApp(client);
            shell.executeCommand("use example_factura_db");

            // 1. Configurar tamaño de página
            String sizeRes = shell.executeCommand("PAGE_SIZE 15");
            assertTrue(sizeRes.contains("15 registros"));

            // 2. Ejecutar consulta base que activa paginación
            String p1 = shell.executeCommand("select * from clientes");
            assertTrue(p1.contains("PÁGINA [ 1 /"));
            assertTrue(p1.contains("Tamaño de página: 15"));

            // 3. Desplazarse a la siguiente página (NEXT o SIGUIENTE)
            String p2 = shell.executeCommand("NEXT");
            assertTrue(p2.contains("PÁGINA [ 2 /"));

            // 4. Desplazarse a la página anterior (PREV o ANTERIOR)
            String prev = shell.executeCommand("PREV");
            assertTrue(prev.contains("PÁGINA [ 1 /"));

            // 5. Ir a la última página (LAST o ULTIMO)
            String pLast = shell.executeCommand("LAST");
            assertTrue(pLast.contains("PÁGINA ["));

            // 6. Ir a la primera página (FIRST o PRIMERO)
            String pFirst = shell.executeCommand("FIRST");
            assertTrue(pFirst.contains("PÁGINA [ 1 /"));

            // 7. Salto directo a página específica (PAGE 3)
            String p3 = shell.executeCommand("PAGE 3");
            assertTrue(p3.contains("PÁGINA [ 3 /"));
        }
    }
}
