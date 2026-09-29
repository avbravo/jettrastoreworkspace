package io.jettra.driver.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class JettraClientConfig {
    private final List<String> clusterEndpoints;
    private final String username;
    private final String password;
    private final Duration connectionTimeout;
    private final boolean enableVirtualThreads;

    private JettraClientConfig(Builder builder) {
        this.clusterEndpoints = List.copyOf(builder.clusterEndpoints);
        this.username = builder.username;
        this.password = builder.password;
        this.connectionTimeout = builder.connectionTimeout;
        this.enableVirtualThreads = builder.enableVirtualThreads;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getClusterEndpoints() { return clusterEndpoints; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public boolean isEnableVirtualThreads() { return enableVirtualThreads; }

    public static class Builder {
        private final List<String> clusterEndpoints = new ArrayList<>();
        private String username = "admin";
        private String password = "admin-jettra";
        private Duration connectionTimeout = Duration.ofMillis(1000);
        private boolean enableVirtualThreads = true;

        public Builder addClusterNode(String host, int port) {
            clusterEndpoints.add(host + ":" + port);
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = timeout;
            return this;
        }

        public Builder enableVirtualThreads(boolean enable) {
            this.enableVirtualThreads = enable;
            return this;
        }

        public JettraClientConfig build() {
            if (clusterEndpoints.isEmpty()) {
                clusterEndpoints.add("localhost:9091");
            }
            return new JettraClientConfig(this);
        }
    }
}
