package io.jettra.core.three.d.explorer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un objeto o registro individual almacenado dentro de un engine especializado de JettraStore.
 * Incluye historial de auditoría de versiones y capacidad de restauración en caliente.
 */
public class EngineRecord {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String id;
    private final String engineType;
    private final String bucketName;
    private String summary;
    private String details;
    private String timestamp;
    private int currentVersion = 1;
    private final List<RecordVersion> versionHistory = new ArrayList<>();

    public EngineRecord(String id, String engineType, String bucketName, String summary, String details, String timestamp) {
        this.id = id;
        this.engineType = engineType;
        this.bucketName = bucketName;
        this.summary = summary;
        this.details = details;
        this.timestamp = (timestamp != null && !timestamp.isBlank()) ? timestamp : LocalDateTime.now().format(FMT);
        this.currentVersion = 1;
        this.versionHistory.add(new RecordVersion(1, this.summary, this.details, this.timestamp, "Versión inicial creada"));
    }

    public synchronized void update(String newSummary, String newDetails, String note) {
        this.currentVersion++;
        this.summary = newSummary;
        this.details = newDetails;
        this.timestamp = LocalDateTime.now().format(FMT);
        this.versionHistory.add(new RecordVersion(
            this.currentVersion,
            newSummary,
            newDetails,
            this.timestamp,
            (note != null && !note.isBlank()) ? note : "Edición de registro (v" + this.currentVersion + ")"
        ));
    }

    public synchronized boolean restoreVersion(int targetVersion) {
        for (RecordVersion rv : versionHistory) {
            if (rv.version() == targetVersion) {
                this.currentVersion++;
                this.summary = rv.summary();
                this.details = rv.details();
                this.timestamp = LocalDateTime.now().format(FMT);
                this.versionHistory.add(new RecordVersion(
                    this.currentVersion,
                    this.summary,
                    this.details,
                    this.timestamp,
                    "Restaurado desde versión v" + targetVersion
                ));
                return true;
            }
        }
        return false;
    }

    public synchronized RecordVersion getVersion(int v) {
        for (RecordVersion rv : versionHistory) {
            if (rv.version() == v) return rv;
        }
        return null;
    }

    public String getId() { return id; }
    public String getEngineType() { return engineType; }
    public String getBucketName() { return bucketName; }
    public synchronized String getSummary() { return summary; }
    public synchronized String getDetails() { return details; }
    public synchronized String getTimestamp() { return timestamp; }
    public synchronized int getCurrentVersion() { return currentVersion; }
    public synchronized List<RecordVersion> getVersionHistory() {
        return Collections.unmodifiableList(new ArrayList<>(versionHistory));
    }
}
