package io.jettra.store.police;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JettraPolice implements Runnable {
    private static final JettraPolice INSTANCE = new JettraPolice();
    public static JettraPolice getInstance() { return INSTANCE; }

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final List<PoliceAlert> alerts = new CopyOnWriteArrayList<>();
    private long intervalMs = 500;
    private Thread policeThread;

    public record PoliceAlert(Instant timestamp, String code, String message) {}

    private JettraPolice() {}

    @Override
    public void run() {
        while (active.get()) {
            try {
                Thread.sleep(intervalMs);
                runPreventiveChecks();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public synchronized void start(boolean enabled, long intervalMs) {
        this.intervalMs = intervalMs;
        this.active.set(enabled);
        if (!enabled) {
            recordAlert("POLICE_DISABLED", "JettraPolice supervisor is configured inactive (jettrapolice.active = false).");
            return;
        }

        recordAlert("POLICE_STARTED", "JettraPolice autonomous supervisor initialized in background daemon thread.");
        policeThread = Thread.ofVirtual().name("jettra-police-sentinel").start(this);
    }

    private void runPreventiveChecks() {
        // Sondeo continuo de bajo consumo (< 0.5% CPU)
    }

    public void recordAlert(String code, String message) {
        PoliceAlert alert = new PoliceAlert(Instant.now(), code, message);
        alerts.add(alert);
        if (alerts.size() > 500) {
            alerts.removeFirst();
        }
    }

    public void stop() {
        active.set(false);
        if (policeThread != null) {
            policeThread.interrupt();
        }
    }

    public boolean isActive() { return active.get(); }
    public List<PoliceAlert> getAlerts() { return List.copyOf(alerts); }
}
