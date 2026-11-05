package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.HouseholdDeviceRegistryCommandServiceImpl;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.application.internal.queryservices.HouseholdDeviceRegistryQueryServiceImpl;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HouseholdDeviceRegistryServicesTest {

    private static final RegisterInstallationCommand COMMAND = new RegisterInstallationCommand(3L, 5L, 1L);

    private HouseholdDeviceRegistryRepository registries;
    private ExternalBusinessService businesses;
    private HouseholdDeviceRegistryCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        registries = mock(HouseholdDeviceRegistryRepository.class);
        businesses = mock(ExternalBusinessService.class);
        service = new HouseholdDeviceRegistryCommandServiceImpl(registries, businesses,
                mock(com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository.class),
                mock(com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService.class));
        when(businesses.existsBusiness(1L)).thenReturn(true);
        when(registries.saveAndFlush(any(HouseholdDeviceRegistry.class))).thenAnswer(invocation -> {
            var registry = invocation.<HouseholdDeviceRegistry>getArgument(0);
            ReflectionTestUtils.setField(registry, "id", 11L);
            return registry;
        });
    }

    @Test
    void registeringAnInstallationOpensTheRegistryOfItsHousehold() {
        assertEquals(11L, service.handle(COMMAND));

        verify(registries).saveAndFlush(any(HouseholdDeviceRegistry.class));
    }

    @Test
    void rejectsAnUnknownBusiness() {
        when(businesses.existsBusiness(1L)).thenReturn(false);

        assertThrows(BusinessNotFound.class, () -> service.handle(COMMAND));

        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnInstallationThatIsAlreadyRegistered() {
        when(registries.existsByInstallationId(new InstallationId(3L))).thenReturn(true);

        var exception = assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("installation with id 3"));
        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void rejectsASecondInstallationForTheSameHousehold() {
        when(registries.existsByHouseholdId(5L)).thenReturn(true);

        var exception = assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));

        assertTrue(exception.getMessage().contains("household with id 5"));
        verify(registries, never()).saveAndFlush(any());
    }

    @Test
    void translatesAConcurrentRegistrationDetectedByTheDatabase() {
        when(registries.saveAndFlush(any(HouseholdDeviceRegistry.class)))
                .thenThrow(new DataIntegrityViolationException("uk_household_device_registries_household"));

        assertThrows(InstallationAlreadyRegistered.class, () -> service.handle(COMMAND));
    }

    @Test
    void validatesTheCommand() {
        assertDoesNotThrow(() -> new RegisterInstallationCommand(3L, 5L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RegisterInstallationCommand(null, 5L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RegisterInstallationCommand(3L, 0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RegisterInstallationCommand(3L, 5L, null));
    }

    @Test
    void theQueryAnswersTheTableOfAKnownInstallationWithItsVersion() {
        var registry = HouseholdDeviceRegistry.open(3L, 5L, 1L);
        var light = new Device("AA:BB:CC:DD:EE:01", "Luz", "LIGHT", 1L);
        ReflectionTestUtils.setField(light, "id", 7L);
        registry.register(light, "sala", "Luz", 9L, LocalDateTime.of(2026, 11, 5, 10, 0),
                new ContractedActuators(Map.of(DeviceType.LIGHT, 1)));
        when(registries.findByInstallationId(new InstallationId(3L))).thenReturn(Optional.of(registry));
        var queries = new HouseholdDeviceRegistryQueryServiceImpl(registries);

        var table = queries.handle(new GetEdgeAccessTableByInstallationIdQuery(3L)).orElseThrow();

        assertEquals(1, table.version());
        assertEquals(5L, table.householdId());
        assertEquals(1, table.nodes().size());
    }

    @Test
    void theQueryAnswersEmptyForAnUnknownInstallation() {
        when(registries.findByInstallationId(new InstallationId(9L))).thenReturn(Optional.empty());

        assertTrue(new HouseholdDeviceRegistryQueryServiceImpl(registries)
                .handle(new GetEdgeAccessTableByInstallationIdQuery(9L)).isEmpty());
    }
}
