package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.interfaces.rest.controllers.InstallationDevicesController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstallationDevicesControllerTest {

    private static final String PATH = "/api/v1/installations/3/devices";
    private static final String BODY = "{\"deviceId\":7,\"roomKey\":\"sala\",\"function\":\"Luz principal\"}";
    private static final Authentication CAREGIVER = new TestingAuthenticationToken("9", "n/a", "ROLE_CAREGIVER");
    private static final Authentication ADMINISTRATOR = new TestingAuthenticationToken("8", "n/a", "ROLE_ADMINISTRATOR");

    private DeviceAssignmentCommandService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DeviceAssignmentCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new InstallationDevicesController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment assignment() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        var assignment = device.assignTo(3L, 5L, "sala", "Luz principal", 9L, LocalDateTime.of(2026, 11, 4, 10, 0));
        ReflectionTestUtils.setField(assignment, "id", 21L);
        return assignment;
    }

    @Test
    void assignsTheDeviceAndAnswers201WithTheDeviceInUse() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class))).thenReturn(assignment());

        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.installationId").value(3))
                .andExpect(jsonPath("$.householdId").value(5))
                .andExpect(jsonPath("$.roomKey").value("sala"))
                .andExpect(jsonPath("$.function").value("Luz principal"))
                .andExpect(jsonPath("$.device.id").value(7))
                .andExpect(jsonPath("$.device.alias").value("Luz de la sala"))
                .andExpect(jsonPath("$.device.deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.device.status").value(AssetStatus.IN_USE.name()));

        var captor = ArgumentCaptor.forClass(AssignDeviceToInstallationCommand.class);
        verify(service).handle(captor.capture());
        assertEquals(3L, captor.getValue().installationId());
        assertEquals(7L, captor.getValue().deviceId());
        assertEquals("sala", captor.getValue().roomKey());
        assertEquals(9L, captor.getValue().assignedBy());
        assertFalse(captor.getValue().assignedByBusinessAdministrator());
    }

    @Test
    void tellsTheServiceWhenTheCallerAdministersTheBusiness() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class))).thenReturn(assignment());

        mvc.perform(post(PATH).principal(ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(AssignDeviceToInstallationCommand.class);
        verify(service).handle(captor.capture());
        assertEquals(8L, captor.getValue().assignedBy());
        assertTrue(captor.getValue().assignedByBusinessAdministrator());
    }

    @Test
    void answers403WhenTheUserDoesNotManageTheHousehold() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class))).thenThrow(new InstallationAccessDenied());

        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("installation_access_denied"));
    }

    @Test
    void answers404WhenTheInstallationOrTheDeviceDoNotExist() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class)))
                .thenThrow(new InstallationNotFound(3L)).thenThrow(new DeviceNotFound(7L));

        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("installation_not_found"));
        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("device_not_found"));
    }

    @Test
    void answers409WhenTheDeviceIsTakenOrTheInstallationIsNotActive() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class)))
                .thenThrow(new DeviceNotAvailable(7L, AssetStatus.IN_USE))
                .thenThrow(new DeviceNotAvailable(7L, AssetStatus.BROKEN))
                .thenThrow(new InstallationNotActive(3L));

        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_already_assigned"));
        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_not_available"));
        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("installation_not_active"));
    }

    @Test
    void answers422WhenTheDeviceDoesNotFitThePlan() throws Exception {
        when(service.handle(any(AssignDeviceToInstallationCommand.class)))
                .thenThrow(new DeviceIncompatibleWithPlan("The plan does not include DOOR devices."));

        mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("device_incompatible_with_plan"));
    }

    @Test
    void rejectsAnIncompleteOrOversizedBodyWithoutCallingTheService() throws Exception {
        for (var body : new String[]{
                "{\"roomKey\":\"sala\",\"function\":\"Luz\"}",
                "{\"deviceId\":7,\"function\":\"Luz\"}",
                "{\"deviceId\":7,\"roomKey\":\"sala\"}",
                "{\"deviceId\":7,\"roomKey\":\" \",\"function\":\"Luz\"}",
                "{\"deviceId\":0,\"roomKey\":\"sala\",\"function\":\"Luz\"}",
                "{\"deviceId\":7,\"roomKey\":\"" + "a".repeat(61) + "\",\"function\":\"Luz\"}",
                "{\"deviceId\":7,\"roomKey\":\"sala\",\"function\":\"" + "a".repeat(81) + "\"}"}) {
            mvc.perform(post(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(service);
    }
}
