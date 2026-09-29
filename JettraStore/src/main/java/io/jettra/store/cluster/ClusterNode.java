package io.jettra.store.cluster;

import java.util.concurrent.atomic.AtomicLong;

public final class ClusterNode {
    public enum Role { PRIMARY, SECONDARY }
    public enum RaftState { LEADER, FOLLOWER, CANDIDATE }

    private final String id;
    private final String ip;
    private final int port;
    private Role role;
    private RaftState raftState;
    private final AtomicLong receivedOffloadedBytes = new AtomicLong(0);
    private final AtomicLong receivedRingSegments = new AtomicLong(0);

    public ClusterNode(String id, String ip, int port, Role role) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.role = role;
        this.raftState = (role == Role.PRIMARY) ? RaftState.LEADER : RaftState.FOLLOWER;
    }

    public void receiveOffloadedRingPayload(String sourceNodeId, long bytes) {
        receivedOffloadedBytes.addAndGet(bytes);
        receivedRingSegments.incrementAndGet();
    }

    public String getId() { return id; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public RaftState getRaftState() { return raftState; }
    public void setRaftState(RaftState raftState) { this.raftState = raftState; }
    public long getReceivedOffloadedBytes() { return receivedOffloadedBytes.get(); }
    public long getReceivedRingSegments() { return receivedRingSegments.get(); }
}
