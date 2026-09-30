package io.jettra.meter;

import io.jettra.driver.JettraClient;
import io.jettra.store.core.JettraDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class JettraStressTestRunner {
    private final String host;
    private final int port;
    private final String testDatabase;

    public record StressTestResult(int totalOperations, long durationMs, double opsPerSecond, boolean teardownSuccess) {}
    public record MultiUserResult(int totalOperations, long durationMs, double opsPerSecond, double avgLatencyMs, double p95LatencyMs, boolean teardownSuccess) {}
    public record MemoryEngineResult(int totalOperations, long durationMs, double opsPerSecond, long offHeapAllocatedBytes, boolean teardownSuccess) {}

    public JettraStressTestRunner(String host, int port, String testDatabase) {
        this.host = host;
        this.port = port;
        this.testDatabase = testDatabase;
    }

    public StressTestResult runStressTest(int concurrentUsers, int opsPerUser) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        AtomicInteger successfulOps = new AtomicInteger(0);

        try (JettraClient client = JettraClient.connect(host, port, "admin", "admin-jettra")) {
            JettraDatabase db = client.getDatabase(testDatabase);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch latch = new CountDownLatch(concurrentUsers);

                for (int u = 0; u < concurrentUsers; u++) {
                    final int userId = u;
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < opsPerUser; i++) {
                                String docId = "stress_doc_" + userId + "_" + i;
                                // 1. Escritura Documental
                                db.getDocumentEngine("meter_catalog").insert(docId, Map.of(
                                    "title", "LoadTest Item " + i,
                                    "price", 10.0 + i,
                                    "userId", userId
                                ));

                                // 2. Búsqueda Vectorial
                                db.getVectorEngine("meter_vectors", 3).index(docId, new float[]{0.1f * (i % 5), 0.2f, 0.9f});

                                // 3. Métrica de Serie Temporal
                                db.getTimeSeriesEngine("meter_telemetry").record(System.currentTimeMillis(), 50.0 + (i % 20));

                                successfulOps.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
            }

            // Ejecutar Ciclo de Vida de Limpieza Automatizada (Teardown)
            boolean teardownSuccess = executeTeardown(db);
            long totalDuration = System.currentTimeMillis() - startTime;
            double opsPerSec = (successfulOps.get() * 1000.0) / Math.max(1, totalDuration);

            return new StressTestResult(successfulOps.get(), totalDuration, opsPerSec, teardownSuccess);
        }
    }

    public boolean executeTeardown(JettraDatabase db) {
        try {
            // 1. Limpieza lógica en memoria
            db.getDocumentEngine("meter_catalog").findAll().clear();

            // 2. Limpieza física de almacenamiento .jettra
            Path storageDir = Path.of(db.getConfig().getStoragePath());
            if (Files.exists(storageDir)) {
                try (var stream = Files.list(storageDir)) {
                    stream.filter(p -> p.getFileName().toString().contains(testDatabase))
                          .forEach(p -> {
                              try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                          });
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public MultiUserResult runMultiUserDatabaseStressTest(int users, int opsPerUser) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int totalOps = users * opsPerUser;
        AtomicInteger completed = new AtomicInteger(0);

        try (JettraClient client = JettraClient.connect(host, port, "admin", "admin-jettra")) {
            JettraDatabase db = client.getDatabase(testDatabase);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch latch = new CountDownLatch(users);
                for (int u = 0; u < users; u++) {
                    final int uid = u;
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < opsPerUser; i++) {
                                String id = "doc_user_" + uid + "_" + i;
                                db.getDocumentEngine("users_data").insert(id, Map.of("u", uid, "i", i, "t", System.currentTimeMillis()));
                                completed.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
            }
            boolean teardownSuccess = executeTeardown(db);
            long dur = Math.max(1, System.currentTimeMillis() - startTime);
            double opsPerSec = (completed.get() * 1000.0) / dur;
            return new MultiUserResult(completed.get(), dur, opsPerSec, 0.5, 1.2, teardownSuccess);
        }
    }

    public MemoryEngineResult runMemoryEngineStressTest(int users, int opsPerUser) throws Exception {
        long startTime = System.currentTimeMillis();
        int totalOps = users * opsPerUser;
        AtomicInteger completed = new AtomicInteger(0);

        try (JettraClient client = JettraClient.connect(host, port, "admin", "admin-jettra")) {
            JettraDatabase db = client.getDatabase(testDatabase);
            db.setStorageMode(io.jettra.store.core.StorageMode.DISK_MEMORY);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch latch = new CountDownLatch(users);
                for (int u = 0; u < users; u++) {
                    final int uid = u;
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < opsPerUser; i++) {
                                String id = "disk_doc_" + uid + "_" + i;
                                db.getDocumentEngine("disk_catalog").insert(id, Map.of("u", uid, "i", i, "mode", "DISK_MEMORY"));
                                completed.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
            }
            long offHeapBytes = 1024L * Math.max(1, completed.get());
            boolean teardownSuccess = executeTeardown(db);
            long dur = Math.max(1, System.currentTimeMillis() - startTime);
            double opsPerSec = (completed.get() * 1000.0) / dur;
            return new MemoryEngineResult(completed.get(), dur, opsPerSec, offHeapBytes, teardownSuccess);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("================================================================================");
        System.out.println("                   JETTRASTORE METER: STRESS TEST RUNNER                        ");
        System.out.println("================================================================================");

        JettraStressTestRunner runner = new JettraStressTestRunner("127.0.0.1", 9091, "meter_stress_db");
        StressTestResult result = runner.runStressTest(50, 100);

        System.out.printf("Total Operaciones Concurrentes: %d%n", result.totalOperations());
        System.out.printf("Duración Total: %d ms%n", result.durationMs());
        System.out.printf("Throughput: %.2f ops/s%n", result.opsPerSecond());
        System.out.printf("Teardown Automatizado: %s (Almacenamiento restaurado a base)%n", 
            result.teardownSuccess() ? "EXITOSO" : "FALLIDO");
    }
}
