package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EdgeCommandServiceTest {

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    private EdgeRepository edges;
    private DeviceRepository devices;
    private EdgeCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        edges = mock(EdgeRepository.class);
        devices = mock(DeviceRepository.class);
        service = new EdgeCommandServiceImpl(edges, devices, mock(ExternalBusinessService.class));
        when(edges.save(any(Edge.class))).thenAnswer(invocation -> {
            var edge = invocation.<Edge>getArgument(0);
            ReflectionTestUtils.setField(edge, "id", 3L);
            return edge;
        });
    }

    private static Edge storedEdge() {
        var edge = new Edge(1L, MAC, "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 3L);
        return edge;
    }

    @Test
    void registersAnEdgeAndReturnsItsId() {
        assertEquals(3L, service.handle(new RegisterEdgeCommand(1L, "aa-bb-cc-dd-ee-ff", "MICROPHONE_EDGE")));

        verify(edges).save(any(Edge.class));
    }

    @Test
    void rejectsAMacAddressUsedByAnotherEdgeOrByADevice() {
        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(true);
        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));

        when(edges.existsEdgeByMacAddress(new MacAddress(MAC))).thenReturn(false);
        when(devices.existsDeviceByMacAddress(new MacAddress(MAC))).thenReturn(true);
        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));

        verify(edges, never()).save(any());
    }

    @Test
    void translatesAConcurrentDuplicateAndDoesNotReturnAnEmptyId() {
        when(edges.save(any(Edge.class))).thenThrow(new DataIntegrityViolationException("uk_edges_mac_address"));

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void doesNotHideOtherPersistenceFailures() {
        when(edges.save(any(Edge.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> service.handle(new RegisterEdgeCommand(1L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void updatesTheTypeKeepingTheSameMacWithoutCheckingDuplicates() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        assertEquals(3L, service.handle(new UpdateEdgeCommand(3L, "aa-bb-cc-dd-ee-ff", "microphone_edge")));

        assertEquals(EdgeType.MICROPHONE_EDGE, edge.getEdgeType());
        verify(edges, never()).existsEdgeByMacAddress(any());
        verify(edges).save(edge);
    }

    @Test
    void rejectsUpdatingToAMacAddressOfAnotherAsset() {
        when(edges.findById(3L)).thenReturn(Optional.of(storedEdge()));
        when(devices.existsDeviceByMacAddress(new MacAddress("AA:BB:CC:DD:EE:00"))).thenReturn(true);

        assertThrows(MacAddressAlreadyExists.class,
                () -> service.handle(new UpdateEdgeCommand(3L, "aa:bb:cc:dd:ee:00", "ACTUATOR_EDGE")));

        verify(edges, never()).save(any());
    }

    @Test
    void failsToUpdateAMissingEdge() {
        when(edges.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EdgeNotFound.class, () -> service.handle(new UpdateEdgeCommand(9L, MAC, "ACTUATOR_EDGE")));
    }

    @Test
    void changesStatusAlongAValidTransition() {
        var edge = storedEdge();
        when(edges.findById(3L)).thenReturn(Optional.of(edge));

        service.handle(new ChangeEdgeStatusCommand(3L, "MAINTENANCE"));

        assertEquals(AssetStatus.MAINTENANCE, edge.getAssetStatus());
    }
}
