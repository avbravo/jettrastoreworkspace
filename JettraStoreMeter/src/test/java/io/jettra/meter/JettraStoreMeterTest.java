package io.jettra.meter;

import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;

import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraStoreMeterTest {

    @Test
    @DisplayName("Debe ejecutar prueba de estrés concurrente con Virtual Threads y ciclo de teardown")
    public void testStressTestAndTeardownLifecycle() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "test_meter_db");
        var result = runner.runStressTest(10, 20); // 10 hilos x 20 operaciones

        assertEquals(200, result.totalOperations());
        assertTrue(result.durationMs() > 0);
        assertTrue(result.opsPerSecond() > 0);
        assertTrue(result.teardownSuccess(), "El teardown automatizado debe limpiar la base de datos de prueba");
    }

    @Test
    @DisplayName("Debe simular múltiples usuarios concurrentes ejecutando operaciones multimodelo (Doc, SQL, KV, Vector, TS)")
    public void testMultiUserStressWorkloadAgainstJettraStore() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "test_multiuser_db");
        int users = 15;
        int opsPerUser = 25;
        var result = runner.runMultiUserDatabaseStressTest(users, opsPerUser);

        assertEquals(users * opsPerUser, result.totalOperations());
        assertTrue(result.opsPerSecond() > 0);
        assertTrue(result.avgLatencyMs() >= 0);
        assertTrue(result.p95LatencyMs() >= 0);
        assertTrue(result.teardownSuccess(), "El ciclo de teardown debe eliminar los datos generados por la prueba");
    }

    @Test
    @DisplayName("Debe ejecutar prueba de estrés en JettraStore usando JettraMemory como motor Off-Heap directo a disco")
    public void testJettraStoreWithJettraMemoryEngine() throws Exception {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "test_memory_engine_db");
        int users = 20;
        int opsPerUser = 30;

        var result = runner.runMemoryEngineStressTest(users, opsPerUser);

        assertEquals(users * opsPerUser, result.totalOperations());
        assertTrue(result.opsPerSecond() > 0);
        assertTrue(result.offHeapAllocatedBytes() > 0, "Debe registrar bytes asignados off-heap en el archivo .jettra");
        assertTrue(result.teardownSuccess(), "El almacenamiento físico .jettra debe ser purgado en el teardown");
    }

    @Test
    @DisplayName("Debe validar estrés con 25 usuarios concurrentes contra example_factura_db durante período sostenido")
    public void testFacturaWorkload25ConcurrentUsers() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "example_factura_db");
        var result = runner.runFacturaDurationWorkload(25, 1200);

        System.out.println("DEBUG [25 Users]: " + result.summary());
        assertEquals(0, result.failedOperations());
        assertTrue(result.totalOperations() > 100, "Debe procesar operaciones masivas con 25 usuarios");
        assertTrue(result.opsPerSecond() > 50.0);
    }

    @Test
    @DisplayName("Debe validar estrés con 50 usuarios concurrentes contra example_factura_db durante período sostenido")
    public void testFacturaWorkload50ConcurrentUsers() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "example_factura_db");
        var result = runner.runFacturaDurationWorkload(50, 1200);

        System.out.println("DEBUG [50 Users]: " + result.summary());
        assertEquals(0, result.failedOperations());
        assertTrue(result.totalOperations() > 200, "Debe procesar operaciones masivas con 50 usuarios");
        assertTrue(result.opsPerSecond() > 100.0);
    }

    @Test
    @DisplayName("Debe validar estrés con 100 usuarios concurrentes contra example_factura_db durante período sostenido")
    public void testFacturaWorkload100ConcurrentUsers() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "example_factura_db");
        var result = runner.runFacturaDurationWorkload(100, 1200);

        System.out.println("DEBUG [100 Users]: " + result.summary());
        assertEquals(0, result.failedOperations());
        assertTrue(result.totalOperations() > 300, "Debe procesar operaciones masivas con 100 usuarios");
        assertTrue(result.opsPerSecond() > 150.0);
    }

    @Test
    @DisplayName("Debe validar estrés con 500 usuarios concurrentes en Virtual Threads contra example_factura_db")
    public void testFacturaWorkload500ConcurrentUsers() throws InterruptedException {
        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "example_factura_db");
        var result = runner.runFacturaDurationWorkload(500, 1200);

        System.out.println("DEBUG [500 Users]: " + result.summary());
        assertEquals(0, result.failedOperations());
        assertTrue(result.totalOperations() > 500, "Debe procesar operaciones masivas con 500 usuarios concurrentes");
        assertTrue(result.opsPerSecond() > 200.0);
    }
}
