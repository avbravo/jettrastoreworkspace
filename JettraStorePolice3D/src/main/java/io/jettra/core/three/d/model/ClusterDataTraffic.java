package io.jettra.core.three.d.model;

/**
 * Representa el tráfico real de datos que ocurre entre los diferentes nodos analizando
 * el clúster de JettraStore en tiempo real.
 * Se refleja físicamente en el mundo 3D mediante los camiones que circulan entre nodos.
 */
public class ClusterDataTraffic {

    public enum TrafficType {
        RAFT_REPLICATION,      // Replicación de logs Raft entre Primary y Follower
        RING_OFFLOAD,          // Offloading de particiones en anillo dinámico
        SSTABLE_COMPACTION,    // Transferencia de bloques LSM/SSTables
        VECTOR_SYNC,           // Sincronización de índices vectoriales AI
        CLIENT_INGESTION       // Ingesta de lotes desde el balanceador/puerta de entrada
    }

    private final String id;
    private final String name;
    private final TrafficType trafficType;
    private String sourceNodeId;
    private String targetNodeId;
    private String payloadSummary;
    private long batchSizeBytes;
    private float transferSpeedMbps;
    private float progress; // 0.0 a 1.0 de source a target
    private boolean isReversing; // Retornando en vacío o completando viaje de ida

    public ClusterDataTraffic(String id, String name, TrafficType trafficType,
                              String sourceNodeId, String targetNodeId,
                              String payloadSummary, long batchSizeBytes, float transferSpeedMbps) {
        this.id = id;
        this.name = name;
        this.trafficType = trafficType;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.payloadSummary = payloadSummary;
        this.batchSizeBytes = batchSizeBytes;
        this.transferSpeedMbps = transferSpeedMbps;
        this.progress = 0.0f;
        this.isReversing = false;
    }

    public void updateBatch(long newBytes, float newSpeedMbps, String newSummary) {
        this.batchSizeBytes = newBytes;
        this.transferSpeedMbps = newSpeedMbps;
        this.payloadSummary = newSummary;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public TrafficType getTrafficType() { return trafficType; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
    public String getPayloadSummary() { return payloadSummary; }
    public void setPayloadSummary(String payloadSummary) { this.payloadSummary = payloadSummary; }
    public long getBatchSizeBytes() { return batchSizeBytes; }
    public void setBatchSizeBytes(long batchSizeBytes) { this.batchSizeBytes = batchSizeBytes; }
    public float getTransferSpeedMbps() { return transferSpeedMbps; }
    public void setTransferSpeedMbps(float transferSpeedMbps) { this.transferSpeedMbps = transferSpeedMbps; }
    public float getProgress() { return progress; }
    public void setProgress(float progress) { this.progress = progress; }
    public boolean isReversing() { return isReversing; }
    public void setReversing(boolean reversing) { isReversing = reversing; }

    public void advance(float dt) {
        float speed = 0.20f;
        if (isReversing) {
            progress -= speed * dt;
            if (progress <= 0.0f) {
                progress = 0.0f;
                isReversing = false;
            }
        } else {
            progress += speed * dt;
            if (progress >= 1.0f) {
                progress = 1.0f;
                isReversing = true;
            }
        }
    }

}
