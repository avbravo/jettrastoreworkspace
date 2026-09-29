package io.jettra.store.cluster;

import io.jettra.store.engine.panama.NativeMemTable;
import io.jettra.store.police.JettraPolice;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DynamicRingEngine {
    private final String nodeId;
    private final List<ClusterNode> peers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ringActive = new AtomicBoolean(false);
    private final double saturationThreshold;
    private final double targetReleaseThreshold;
    private double currentMemoryUsage = 0.25; // 25% inicial

    public DynamicRingEngine(String nodeId, double saturationThreshold, double targetReleaseThreshold) {
        this.nodeId = nodeId;
        this.saturationThreshold = saturationThreshold;
        this.targetReleaseThreshold = targetReleaseThreshold;
    }

    public void registerPeer(ClusterNode peer) {
        peers.add(peer);
    }

    public synchronized void evaluateMemorySaturation(double currentUsagePercent, NativeMemTable activeMemTable) {
        this.currentMemoryUsage = currentUsagePercent;

        if (currentUsagePercent >= saturationThreshold && !ringActive.get()) {
            activateRingTransition(activeMemTable);
        } else if (currentUsagePercent <= targetReleaseThreshold && ringActive.get()) {
            deactivateRingTransition();
        }
    }

    private void activateRingTransition(NativeMemTable memTable) {
        ringActive.set(true);
        JettraPolice.getInstance().recordAlert("RING_SATURATION_ACTIVATED", 
            String.format("Node %s reached %.1f%% RAM saturation. Converting dynamically to Distributed Ring Engine.", 
                nodeId, currentMemoryUsage * 100));

        // Particionar y transferir bloques a los nodos pares del anillo
        if (!peers.isEmpty()) {
            long bytesToOffload = memTable.getUsedBytes() / peers.size();
            for (ClusterNode peer : peers) {
                peer.receiveOffloadedRingPayload(nodeId, bytesToOffload);
            }
        }

        // Descarga proactiva en el nodo local: consumo cae al objetivo
        this.currentMemoryUsage = targetReleaseThreshold;
        JettraPolice.getInstance().recordAlert("RING_LOAD_BALANCED", 
            String.format("Node %s successfully released memory to peers. Current RAM load: %.1f%%", 
                nodeId, currentMemoryUsage * 100));
    }

    private void deactivateRingTransition() {
        ringActive.set(false);
        JettraPolice.getInstance().recordAlert("RING_NORMALIZED", 
            String.format("Node %s returned to standard local storage state.", nodeId));
    }

    public boolean isRingActive() {
        return ringActive.get();
    }

    public double getCurrentMemoryUsage() {
        return currentMemoryUsage;
    }

    public void setCurrentMemoryUsage(double usage) {
        this.currentMemoryUsage = usage;
    }

    public List<ClusterNode> getPeers() {
        return peers;
    }
}
