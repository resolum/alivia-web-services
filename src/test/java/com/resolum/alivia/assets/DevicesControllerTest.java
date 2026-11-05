package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InvalidMacAddress;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
}
