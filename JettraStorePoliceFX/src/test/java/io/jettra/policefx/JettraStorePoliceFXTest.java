package io.jettra.policefx;

import io.jettra.policefx.world3d.ImmersiveWorld3D;
import io.jettra.policefx.world3d.JettraPoliceAgentMesh;
import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.Test;

import static io.jettra.test.core.JettraAssert.*;

public class JettraStorePoliceFXTest {

    @Test
    @DisplayName("Debe gestionar estados visuales y posición del agente JettraPolice 3D")
    public void testAgentMeshStates() {
        JettraPoliceAgentMesh mesh = new JettraPoliceAgentMesh();
        assertEquals(JettraPoliceAgentMesh.AgentStatus.NORMAL, mesh.getCurrentStatus());

        mesh.setStatus(JettraPoliceAgentMesh.AgentStatus.WARNING_RAM);
        assertEquals(JettraPoliceAgentMesh.AgentStatus.WARNING_RAM, mesh.getCurrentStatus());

        mesh.moveTo(10, 20, 30);
        assertEquals(10.0, mesh.getTranslateX());
        assertEquals(20.0, mesh.getTranslateY());
        assertEquals(30.0, mesh.getTranslateZ());

        // Verificar pensamientos y metas estilo JettraICore
        mesh.setThought("Vigilando 3M facturas");
        assertEquals("Vigilando 3M facturas", mesh.getThought());

        mesh.setGoal("Patrulla Raft");
        assertEquals("Patrulla Raft", mesh.getGoal());
    }

    @Test
    @DisplayName("Debe inicializar la escena 3D y ejecutar ciclo de animación")
    public void testImmersiveWorldAnimation() {
        ImmersiveWorld3D world = new ImmersiveWorld3D();
        assertNotNull(world.getAgentMesh());
        assertDoesNotThrow(world::tickAnimation);
    }
}
