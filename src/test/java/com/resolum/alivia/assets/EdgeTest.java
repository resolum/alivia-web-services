package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Test
    void registersAnAvailableEdgeWithTheCanonicalMac() {
        var edge = new Edge(1L, "aa-bb-cc-dd-ee-ff", "actuator_edge");

        assertEquals(MAC, edge.getMacAddress().macAddress());
        assertEquals(EdgeType.ACTUATOR_EDGE, edge.getEdgeType());
        assertEquals(AssetStatus.AVAILABLE, edge.getAssetStatus());
        assertEquals(1L, edge.getBusinessId().businessId());
    }

    @Test
    void rejectsAMissingOrUnknownEdgeType() {
        assertThrows(IllegalArgumentException.class, () -> new Edge(1L, MAC, null));
        assertThrows(IllegalArgumentException.class, () -> new Edge(1L, MAC, " "));
        var unknown = assertThrows(IllegalArgumentException.class, () -> new Edge(1L, MAC, "ROUTER"));

        assertEquals("Unknown edge type: ROUTER.", unknown.getMessage());
    }

    @Test
    void updateKeepsTheEdgeUnchangedWhenTheNewMacIsInvalid() {
        var edge = new Edge(1L, MAC, "ACTUATOR_EDGE");

        assertThrows(IllegalArgumentException.class, () -> edge.update("not-a-mac", "MICROPHONE_EDGE"));
        assertThrows(IllegalArgumentException.class, () -> edge.update("AA:BB:CC:DD:EE:00", "ROUTER"));

        assertEquals(MAC, edge.getMacAddress().macAddress());
        assertEquals(EdgeType.ACTUATOR_EDGE, edge.getEdgeType());
        edge.update("aa:bb:cc:dd:ee:00", "microphone_edge");
        assertEquals("AA:BB:CC:DD:EE:00", edge.getMacAddress().macAddress());
        assertEquals(EdgeType.MICROPHONE_EDGE, edge.getEdgeType());
    }

    @Test
    void followsTheSameStatusRulesAsAnyAsset() {
        var edge = new Edge(1L, MAC, "ACTUATOR_EDGE");

        edge.updateAssetStatus("RETIRED");

        assertEquals(AssetStatus.RETIRED, edge.getAssetStatus());
        assertThrows(RuntimeException.class, () -> edge.updateAssetStatus("AVAILABLE"));
    }

    @Test
    void validatesTheRegisterCommand() {
        assertDoesNotThrow(() -> new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterEdgeCommand(null, MAC, "ACTUATOR_EDGE"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterEdgeCommand(1L, "", "ACTUATOR_EDGE"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterEdgeCommand(1L, MAC, null));
    }
}
