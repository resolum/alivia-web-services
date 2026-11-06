package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.exceptions.DeviceStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.InvalidMacAddress;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.ChangeDeviceStatusCommand;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceInventoryItemByIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetDevicesQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceInventoryItem;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import com.resolum.alivia.assets.domain.model.commands.RegisterDeviceCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateDeviceCommand;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.DevicesController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevicesControllerTest {

    private static final String BODY = """
            {"macAddress":"aa-bb-cc-dd-ee-ff","alias":"Luz de la sala","deviceType":"LIGHT"}
            """;

    private DeviceCommandService commands;
    private DeviceQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(DeviceCommandService.class);
        queries = mock(DeviceQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DevicesController(commands, queries, 1L))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static Device device() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    @Test
    void registersADeviceAndAnswers201WithTheAvailableStatus() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class))).thenReturn(7L);
        when(queries.handle(new GetDeviceByIdQuery(7L))).thenReturn(Optional.of(device()));

        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.businessId").value(1))
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.alias").value("Luz de la sala"))
                .andExpect(jsonPath("$.deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        var captor = ArgumentCaptor.forClass(RegisterDeviceCommand.class);
        verify(commands).handle(captor.capture());
        assertEquals(1L, captor.getValue().businessId());
        assertEquals("aa-bb-cc-dd-ee-ff", captor.getValue().macAddress());
        assertEquals("Luz de la sala", captor.getValue().alias());
        assertEquals("LIGHT", captor.getValue().deviceType());
    }

    @Test
    void alwaysRegistersTheDeviceUnderTheConfiguredBusinessNotTheRequestOne() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class))).thenReturn(7L);
        when(queries.handle(new GetDeviceByIdQuery(7L))).thenReturn(Optional.of(device()));

        mvc.perform(post("/api/v1/devices").param("businessId", "5")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(RegisterDeviceCommand.class);
        verify(commands).handle(captor.capture());
        assertEquals(1L, captor.getValue().businessId());
    }

    @Test
    void answers409WhenThePhysicalIdIsAlreadyRegistered() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class)))
                .thenThrow(new MacAddressAlreadyExists("AA:BB:CC:DD:EE:FF"));

        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_already_registered"));
    }

    @Test
    void answers400ForAMalformedMacAddress() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class))).thenThrow(new InvalidMacAddress("bad mac"));

        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"zz\",\"alias\":\"Luz\",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_mac_address"));
    }

    @Test
    void answers400WithoutCallingTheServiceWhenTheBodyIsIncomplete() throws Exception {
        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"alias\":\" \",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"alias\":\"" + "a".repeat(81)
                                + "\",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    @Test
    void answers400ForAnUnknownDeviceType() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class)))
                .thenThrow(new IllegalArgumentException("Unknown device type: TOASTER."));

        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"alias\":\"Luz\",\"deviceType\":\"TOASTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_argument"));
    }

    @Test
    void answers404WhenTheBusinessDoesNotExist() throws Exception {
        when(commands.handle(any(RegisterDeviceCommand.class)))
                .thenThrow(new BusinessNotFound("business with id 1 not found"));

        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("business_not_found"));
    }

    @Test
    void updatesADeviceAndReturnsItWithTheTypeAndStatusInTheirOwnFields() throws Exception {
        when(queries.handle(new GetDeviceByIdQuery(7L))).thenReturn(Optional.of(device()));

        mvc.perform(put("/api/v1/devices/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"deviceType\":\"light\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        var captor = ArgumentCaptor.forClass(UpdateDeviceCommand.class);
        verify(commands).handle(captor.capture());
        assertEquals("light", captor.getValue().deviceType());
    }

    @Test
    void answers404WhenUpdatingAMissingDevice() throws Exception {
        when(commands.handle(any(UpdateDeviceCommand.class))).thenThrow(new DeviceNotFound(8L));

        mvc.perform(put("/api/v1/devices/8").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("device_not_found"));
    }

    @Test
    void answers409WhenChangingTheTypeOfAnAssignedDevice() throws Exception {
        when(commands.handle(any(UpdateDeviceCommand.class))).thenThrow(DeviceStillAssigned.typeChange(7L));

        mvc.perform(put("/api/v1/devices/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"deviceType\":\"DOOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_still_assigned"));
    }

    @Test
    void answers409WhenAnotherRequestChangedTheSameDevice() throws Exception {
        when(commands.handle(any(UpdateDeviceCommand.class))).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Device.class, 7L));

        mvc.perform(put("/api/v1/devices/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("resource_modified_concurrently"));
    }

    @Test
    void answers409WhenUpdatingToAnExistingPhysicalId() throws Exception {
        when(commands.handle(any(UpdateDeviceCommand.class))).thenThrow(new MacAddressAlreadyExists("AA:BB:CC:DD:EE:00"));

        mvc.perform(put("/api/v1/devices/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:00\",\"deviceType\":\"LIGHT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAnUpdateThatLacksTheDeviceType() throws Exception {
        mvc.perform(put("/api/v1/devices/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"edgeType\":\"ACTUATOR_EDGE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    // --- changing the status of a device ---

    @Test
    void changesTheStatusAndReturnsTheDevice() throws Exception {
        var device = device();
        device.changeStatusTo(AssetStatus.MAINTENANCE);
        when(commands.handle(new ChangeDeviceStatusCommand(7L, "MAINTENANCE"))).thenReturn(7L);
        when(queries.handle(new GetDeviceByIdQuery(7L))).thenReturn(Optional.of(device));

        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void theStatusChangeAnswers400404And409() throws Exception {
        doThrow(new IllegalArgumentException("A device becomes IN_USE only by being assigned to an installation."))
                .when(commands).handle(new ChangeDeviceStatusCommand(7L, "IN_USE"));
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_USE\"}"))
                .andExpect(status().isBadRequest());

        doThrow(new DeviceNotFound(9L)).when(commands).handle(new ChangeDeviceStatusCommand(9L, "BROKEN"));
        mvc.perform(put("/api/v1/devices/9/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("device_not_found"));

        doThrow(new DeviceStillAssigned(7L, AssetStatus.RETIRED)).when(commands).handle(new ChangeDeviceStatusCommand(7L, "RETIRED"));
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RETIRED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("device_still_assigned"));

        doThrow(new InvalidAssetStatusTransition(AssetStatus.RETIRED, AssetStatus.AVAILABLE))
                .when(commands).handle(new ChangeDeviceStatusCommand(7L, "AVAILABLE"));
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_asset_status_transition"));

        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Device.class, 7L))
                .when(commands).handle(new ChangeDeviceStatusCommand(7L, "BROKEN"));
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("resource_modified_concurrently"));
    }

    @Test
    void anUnknownStatusIsRejectedWithTheMessageOfTheDomain() throws Exception {
        doThrow(new IllegalArgumentException("Unknown asset status: SLEEPING."))
                .when(commands).handle(new ChangeDeviceStatusCommand(7L, "SLEEPING"));

        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SLEEPING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown asset status: SLEEPING."));
    }

    @Test
    void theStatusChangeRejectsAMissingStatusWithoutCallingTheService() throws Exception {
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mvc.perform(put("/api/v1/devices/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    // --- reading the inventory ---

    private static PageRequest pageOf(int page, int size) {
        return PageRequest.of(page, size, Sort.by("id"));
    }

    @Test
    void listsTheInventoryWithItsDefaultsAndShowsWhereEachDeviceIs() throws Exception {
        var assigned = device();
        var free = device();
        ReflectionTestUtils.setField(free, "id", 8L);
        when(queries.handle(any(GetDevicesQuery.class))).thenReturn(new PageImpl<>(
                List.of(new DeviceInventoryItem(assigned, 30L), new DeviceInventoryItem(free, null)), pageOf(0, 20), 2));

        mvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].installationId").value(30))
                .andExpect(jsonPath("$.content[0].macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.content[0].deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].installationId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));

        var captor = ArgumentCaptor.forClass(GetDevicesQuery.class);
        verify(queries).handle(captor.capture());
        assertEquals(1L, captor.getValue().businessId());
        assertNull(captor.getValue().status());
        assertNull(captor.getValue().deviceType());
        assertEquals(pageOf(0, 20), captor.getValue().pageable());
    }

    @Test
    void theFiltersAndThePageAreReadIgnoringCase() throws Exception {
        when(queries.handle(any(GetDevicesQuery.class))).thenReturn(new PageImpl<>(List.of(), pageOf(2, 50), 0));

        mvc.perform(get("/api/v1/devices").param("status", " broken ").param("type", "Light").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        verify(queries).handle(new GetDevicesQuery(1L, AssetStatus.BROKEN, DeviceType.LIGHT, pageOf(2, 50)));
    }

    @Test
    void aBlankFilterIsNoFilter() throws Exception {
        when(queries.handle(any(GetDevicesQuery.class))).thenReturn(new PageImpl<>(List.of(), pageOf(0, 20), 0));

        mvc.perform(get("/api/v1/devices").param("status", "").param("type", "  ")).andExpect(status().isOk());

        verify(queries).handle(new GetDevicesQuery(1L, null, null, pageOf(0, 20)));
    }

    @Test
    void anUnknownFilterOrAPageOutOfRangeIsRejectedWithoutQueryingAnything() throws Exception {
        mvc.perform(get("/api/v1/devices").param("status", "SLEEPING"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Unknown asset status: SLEEPING."));
        mvc.perform(get("/api/v1/devices").param("type", "CAMERA"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Unknown device type: CAMERA."));
        for (var size : new String[]{"0", "-1", "201", "1000000"}) {
            mvc.perform(get("/api/v1/devices").param("size", size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/devices").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/devices").param("page", "abc")).andExpect(status().isBadRequest());

        verifyNoInteractions(queries);
    }

    @Test
    void theSortIsAlwaysByIdWhateverTheClientAsks() throws Exception {
        when(queries.handle(any(GetDevicesQuery.class))).thenReturn(new PageImpl<>(List.of(), pageOf(0, 20), 0));

        mvc.perform(get("/api/v1/devices").param("sort", "notAProperty,desc")).andExpect(status().isOk());

        verify(queries).handle(new GetDevicesQuery(1L, null, null, pageOf(0, 20)));
    }

    @Test
    void readsOneDeviceWithItsInstallationOrAnswers404() throws Exception {
        when(queries.handle(new GetDeviceInventoryItemByIdQuery(7L))).thenReturn(Optional.of(new DeviceInventoryItem(device(), 30L)));
        when(queries.handle(new GetDeviceInventoryItemByIdQuery(9L))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/devices/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.alias").value("Luz de la sala"))
                .andExpect(jsonPath("$.installationId").value(30));
        mvc.perform(get("/api/v1/devices/9"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("device_not_found"));
    }
}
