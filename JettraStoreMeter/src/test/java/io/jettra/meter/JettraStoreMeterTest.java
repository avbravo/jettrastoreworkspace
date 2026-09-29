package io.jettra.meter;

import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.Test;

import static io.jettra.test.core.JettraAssert.*;

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
}
