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
}
