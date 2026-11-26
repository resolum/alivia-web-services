package com.resolum.alivia.integration;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope of an edge over a real PostgreSQL, through the real facade: the entries and the devices are lazy relations, so
 * this is what shows that they are read inside the transaction and that an installation never sees another's devices.
 */
class EdgeScopeOnPostgresTest extends EdgeTransportTestSupport {

    @Autowired
    private AssetsContextFacade facade;

    @Test
    void bothEdgesOfAnInstallationKnowItsHomeAndItsDevicesAndNothingOfAnotherInstallation() throws Exception {
        var mine = installationWithEdges();
        var other = installationWithEdges();
        long myLight = registerLight(mine.installation());
        long otherLight = registerLight(other.installation());

        var microphone = facade.fetchEdgeScope(mine.mic()).orElseThrow();
        var actuators = facade.fetchEdgeScope(mine.act()).orElseThrow();

        assertEquals("MICROPHONE", microphone.edgeKind());
        assertEquals("ACTUATOR", actuators.edgeKind());
        assertEquals(mine.installation(), microphone.installationId());
        assertEquals(microphone.householdId(), actuators.householdId());
        assertEquals(microphone.devices(), actuators.devices());
        assertEquals(1, microphone.devices().size());
        var light = microphone.devices().get(0);
        assertEquals(myLight, light.deviceId());
        assertEquals("Luz", light.alias());
        assertEquals("LIGHT", light.type());
        assertEquals("IN_USE", light.status());
        assertEquals("sala", light.roomKey());
        assertTrue(facade.fetchEdgeScope(other.mic()).orElseThrow().devices().stream()
                .allMatch(device -> device.deviceId() == otherLight));
    }

    @Test
    void anEdgeThatIsNotAssignedOrWasRemovedHasNoScope() throws Exception {
        var edges = installationWithEdges();
        long loose = newEdge("MICROPHONE_EDGE");

        assertTrue(facade.fetchEdgeScope(loose).isEmpty());
        assertTrue(facade.fetchEdgeScope(987_654_321L).isEmpty());

        assertEquals(200, call("DELETE", "/api/v1/installations/" + edges.installation() + "/edges/" + edges.act(), admin).status());
        assertTrue(facade.fetchEdgeScope(edges.act()).isEmpty());
        assertTrue(facade.fetchEdgeScope(edges.mic()).isPresent());
    }
}
