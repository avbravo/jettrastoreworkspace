package io.jettra.core.three.d.explorer;

/**
 * Representa un objeto o registro individual almacenado dentro de un engine especializado de JettraStore.
 */
public class EngineRecord {
    private final String id;
    private final String engineType;
    private final String bucketName;
    private final String summary;
    private final String details;
    private final String timestamp;

    public EngineRecord(String id, String engineType, String bucketName, String summary, String details, String timestamp) {
        this.id = id;
        this.engineType = engineType;
        this.bucketName = bucketName;
        this.summary = summary;
        this.details = details;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getEngineType() { return engineType; }
    public String getBucketName() { return bucketName; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public String getTimestamp() { return timestamp; }
}
