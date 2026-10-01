package io.jettra.core.three.d.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un contenedor o bucket dentro de un engine multimodelo de JettraStore.
 */
public class EngineBucket {
    private final String engineType;
    private final String engineName;
    private final String bucketName;
    private final String description;
    private final long totalObjects;
    private final List<EngineRecord> sampleRecords = new ArrayList<>();

    public EngineBucket(String engineType, String engineName, String bucketName, String description, long totalObjects) {
        this.engineType = engineType;
        this.engineName = engineName;
        this.bucketName = bucketName;
        this.description = description;
        this.totalObjects = totalObjects;
    }

    public void addRecord(EngineRecord record) {
        sampleRecords.add(record);
    }

    public String getEngineType() { return engineType; }
    public String getEngineName() { return engineName; }
    public String getBucketName() { return bucketName; }
    public String getDescription() { return description; }
    public long getTotalObjects() { return totalObjects; }
    public List<EngineRecord> getSampleRecords() { return Collections.unmodifiableList(sampleRecords); }
}
