package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.DeviceStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.interfaces.rest.controllers.DeviceAssignmentController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceAssignmentControllerTest {

    private static final String PATH = "/api/v1/devices/7/assignment";
    private static final String BODY = "{\"reason\":\"Reemplazo por falla del equipo\"}";
    private static final Authentication CAREGIVER = new TestingAuthenticationToken("9", "n/a", "ROLE_CAREGIVER");
    private static final Authentication ADMINISTRATOR = new TestingAuthenticationToken("8", "n/a", "ROLE_ADMINISTRATOR");

    private DeviceAssignmentCommandService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DeviceAssignmentCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DeviceAssignmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void unassignsTheDeviceAndAnswers204WithoutBody() throws Exception {
        mvc.perform(delete(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        var captor = ArgumentCaptor.forClass(UnassignDeviceCommand.class);
        verify(service).handle(captor.capture());
        assertEquals(7L, captor.getValue().deviceId());
        assertEquals("Reemplazo por falla del equipo", captor.getValue().reason());
        assertEquals(9L, captor.getValue().unassignedBy());
        assertFalse(captor.getValue().unassignedByBusinessAdministrator());
    }

    @Test
    void tellsTheServiceWhenTheCallerAdministersTheBusiness() throws Exception {
        mvc.perform(delete(PATH).principal(ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent());

        var captor = ArgumentCaptor.forClass(UnassignDeviceCommand.class);
        verify(service).handle(captor.capture());
        assertEquals(8L, captor.getValue().unassignedBy());
        assertTrue(captor.getValue().unassignedByBusinessAdministrator());
    }

    @Test
    void answers403WhenTheUserDoesNotManageTheHousehold() throws Exception {
        when(service.handle(any(UnassignDeviceCommand.class))).thenThrow(new InstallationAccessDenied());

        mvc.perform(delete(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("installation_access_denied"));
    }

    @Test
    void answers404WhenTheDeviceDoesNotExist() throws Exception {
        when(service.handle(any(UnassignDeviceCommand.class))).thenThrow(new DeviceNotFound(7L));

        mvc.perform(delete(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("device_not_found"));
    }

    @Test
    void answers409WhenTheDeviceIsNotAssigned() throws Exception {
        when(service.handle(any(UnassignDeviceCommand.class))).thenThrow(new DeviceNotAssigned(7L));

        mvc.perform(delete(PATH).principal(ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_not_assigned"));
    }

    @Test
    void answers409WhenAStatusChangeWouldContradictTheAssignment() {
        var problem = new GlobalExceptionHandler()
                .handleDeviceStillAssigned(new DeviceStillAssigned(7L, AssetStatus.RETIRED));

        assertEquals(409, problem.getStatus());
        assertEquals("device_still_assigned", problem.getProperties().get("code"));
    }

    @Test
    void rejectsAMissingBlankOrOversizedReasonWithoutCallingTheService() throws Exception {
        mvc.perform(delete(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        for (var body : new String[]{"{}", "{\"reason\":\" \"}", "{\"reason\":\"" + "a".repeat(301) + "\"}"}) {
            mvc.perform(delete(PATH).principal(CAREGIVER).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(service);
    }
}
