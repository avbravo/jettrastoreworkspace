package io.jettra.core.three.d.police;

import io.jettra.core.three.d.model.ServerNode3D;
import io.jettra.store.cluster.ClusterNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JettraStorePoliceMonitorTest {

    @Test
    @DisplayName("Debe inicializar nodos de cluster JettraStore y evaluarlos con JettraStorePolice")
    public void testMonitorInitializationAndNodes() {
        try (JettraStorePoliceMonitor monitor = new JettraStorePoliceMonitor()) {
            List<ServerNode3D> nodes = monitor.getServerNodes();
            assertEquals(3, nodes.size(), "Deben existir 3 nodos de cluster preconfigurados");

            // Nodo 1: Master Primario
            ServerNode3D n1 = monitor.getNodeById("node-01");
            assertNotNull(n1);
            assertEquals("node-01-master", n1.getName());
            assertEquals(ClusterNode.Role.PRIMARY, n1.getRole());
            assertEquals(ClusterNode.RaftState.LEADER, n1.getRaftState());
            assertEquals(monitor.getCurrentProfile().getPort(), n1.getPort());

            // Nodo 2: Réplica Secundaria
            ServerNode3D n2 = monitor.getNodeById("node-02");
            assertNotNull(n2);
            assertEquals(ClusterNode.Role.SECONDARY, n2.getRole());
            assertEquals(ClusterNode.RaftState.FOLLOWER, n2.getRaftState());

            // Nodo 3: Configurado inicialmente Fuera de Servicio para verificación visual
            ServerNode3D n3 = monitor.getNodeById("node-03");
            assertNotNull(n3);
            assertFalse(n3.isOnline(), "node-03 debe estar inicialmente fuera de servicio");
            assertEquals(ClusterNode.NodeStatus.OFFLINE, n3.getStatus());
            assertTrue(n3.getStatusMessage().contains("FUERA DE SERVICIO"));
            assertTrue(n3.getPoliceDiagnosis().contains("CRÍTICO"));
        }
    }

    @Test
    @DisplayName("Debe permitir alternar estado de un nodo entre En Línea y Fuera de Servicio")
    public void testToggleNodeOfflineState() {
        try (JettraStorePoliceMonitor monitor = new JettraStorePoliceMonitor()) {
            ServerNode3D n1 = monitor.getNodeById("node-01");
            assertTrue(n1.isOnline());

            // Simular caída de servicio
            monitor.toggleNodeOffline("node-01");
            assertFalse(n1.isOnline(), "Nodo debe pasar a Fuera de Servicio");
            assertEquals(ClusterNode.NodeStatus.OFFLINE, n1.getStatus());
            assertTrue(n1.getStatusMessage().contains("FUERA DE SERVICIO"));
            assertTrue(n1.getPoliceDiagnosis().contains("CRÍTICO"));

            // Restaurar servicio
            monitor.toggleNodeOffline("node-01");
            assertTrue(n1.isOnline(), "Nodo debe volver a estar En Línea");
            assertEquals(ClusterNode.NodeStatus.RUNNING, n1.getStatus());
            assertTrue(n1.getStatusMessage().contains("EN LÍNEA"));
            assertTrue(n1.getPoliceDiagnosis().contains("SALUDABLE"));
        }
    }

    @Test
    @DisplayName("Debe calcular métricas de recursos y bounding box para picking 3D con clic derecho")
    public void testResourceMetricsAndBoundingBox() {
        try (JettraStorePoliceMonitor monitor = new JettraStorePoliceMonitor()) {
            monitor.pollAndEvaluateServers();

            ServerNode3D n1 = monitor.getNodeById("node-01");
            assertTrue(n1.getHeapMaxMb() > 0, "Heap Max debe ser positivo");
            assertTrue(n1.getCpuCores() > 0, "Cores CPU deben ser mayores a 0");
            assertTrue(n1.getPanamaDirectMemMb() > 0, "Memoria Panama FFM debe estar registrada");

            // Bounding box para clic derecho (Ray collision)
            var box = n1.getBoundingBox();
            assertNotNull(box);
            assertTrue(box.max().y() > box.min().y(), "Box Max Y debe ser mayor que Box Min Y");
            assertTrue(box.max().x() > box.min().x(), "Box Max X debe ser mayor que Box Min X");
        }
    }
}
