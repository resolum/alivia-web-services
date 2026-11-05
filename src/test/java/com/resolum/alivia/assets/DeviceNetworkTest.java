package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.DeviceNetworkCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.queryservices.DeviceNetworkQueryServiceImpl;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceNetworkCommand;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceNetworkByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.NetworkStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceNetworkTest {

    private static final RegisterDeviceNetworkCommand COMMAND = new RegisterDeviceNetworkCommand(3L, 5L, 1L);

    private DeviceNetworkRepository networks;
    private ExternalBusinessService businesses;
    private DeviceNetworkCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        networks = mock(DeviceNetworkRepository.class);
        businesses = mock(ExternalBusinessService.class);
        service = new DeviceNetworkCommandServiceImpl(networks, businesses);
        when(businesses.existsBusiness(1L)).thenReturn(true);
        when(networks.saveAndFlush(any(DeviceNetwork.class))).thenAnswer(invocation -> {
            var network = invocation.<DeviceNetwork>getArgument(0);
            ReflectionTestUtils.setField(network, "id", 11L);
            return network;
        });
    }

    @Test
    void startsActiveWithTheHouseholdAndTheBusiness() {
        var network = new DeviceNetwork(3L, 5L, 1L);

        assertTrue(network.isActive());
        assertEquals(NetworkStatus.ACTIVE, network.getNetworkStatus());
        assertEquals(3L, network.getInstallationId().installationId());
        assertEquals(5L, network.getHouseholdId());
        assertEquals(1L, network.getBusinessId().businessId());
    }

    @Test
    void requiresTheHouseholdAndPositiveIds() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceNetwork(3L, null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new DeviceNetwork(3L, 0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new DeviceNetwork(0L, 5L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new DeviceNetwork(3L, 5L, null));
    }

    @Test
    void registersTheNetworkOfAnInstallation() {
        assertEquals(11L, service.handle(COMMAND));

        verify(networks).saveAndFlush(any(DeviceNetwork.class));
    }

    @Test
    void rejectsAnUnknownBusiness() {
        when(businesses.existsBusiness(1L)).thenReturn(false);

        assertThrows(BusinessNotFound.class, () -> service.handle(COMMAND));

        verify(networks, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnInstallationThatIsAlreadyRegistered() {
        when(networks.existsByInstallationId(new InstallationId(3L))).thenReturn(true);

        var exception = assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("installation with id 3"));
        verify(networks, never()).saveAndFlush(any());
    }

    @Test
    void rejectsASecondActiveInstallationForTheSameHousehold() {
        when(networks.existsByHouseholdIdAndNetworkStatus(5L, NetworkStatus.ACTIVE)).thenReturn(true);

        var exception = assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("household with id 5"));
        verify(networks, never()).saveAndFlush(any());
    }

    @Test
    void translatesAConcurrentRegistrationDetectedByTheDatabase() {
        when(networks.saveAndFlush(any(DeviceNetwork.class))).thenThrow(new DataIntegrityViolationException("uk_device_networks_installation"));

        assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));
    }

    @Test
    void findsTheNetworkByInstallation() {
        var network = new DeviceNetwork(3L, 5L, 1L);
        when(networks.findByInstallationId(new InstallationId(3L))).thenReturn(Optional.of(network));
        var queries = new DeviceNetworkQueryServiceImpl(networks);

        assertSame(network, queries.handle(new GetDeviceNetworkByInstallationIdQuery(3L)).orElseThrow());
        assertTrue(queries.handle(new GetDeviceNetworkByInstallationIdQuery(4L)).isEmpty());
    }
}
