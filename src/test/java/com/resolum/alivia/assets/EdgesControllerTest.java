package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeStillAssigned;
import com.resolum.alivia.assets.domain.exceptions.InvalidAssetStatusTransition;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgesController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EdgesControllerTest {

    private static final String BODY = "{\"macAddress\":\"AA:BB:CC:DD:EE:FF\",\"edgeType\":\"ACTUATOR_EDGE\"}";

    private EdgeCommandService commands;
    private EdgeQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(EdgeCommandService.class);
        queries = mock(EdgeQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EdgesController(commands, queries, 1L))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static Edge edge() {
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:FF", "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 3L);
        return edge;
    }

    @Test
    void updatesAnEdgeAndReturnsIt() throws Exception {
        when(queries.handle(new GetEdgeByIdQuery(3L))).thenReturn(Optional.of(edge()));

        mvc.perform(put("/api/v1/edges/3").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.edgeType").value("ACTUATOR_EDGE"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void answers404ForAMissingEdge() throws Exception {
        when(commands.handle(any(UpdateEdgeCommand.class))).thenThrow(new EdgeNotFound(9L));

        mvc.perform(put("/api/v1/edges/9").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_not_found"));
    }

    @Test
    void answers409ForADuplicatePhysicalId() throws Exception {
        when(commands.handle(any(UpdateEdgeCommand.class))).thenThrow(new MacAddressAlreadyExists("AA:BB:CC:DD:EE:FF"));

        mvc.perform(put("/api/v1/edges/3").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("device_already_registered"));
    }

    @Test
    void rejectsAnIncompleteBodyWithoutCallingTheService() throws Exception {
        mvc.perform(put("/api/v1/edges/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(commands);
    }

    // --- registering an edge in the inventory ---

    @Test
    void registersAnEdgeInTheInventoryOfTheDefaultBusiness() throws Exception {
        when(commands.handle(new RegisterEdgeCommand(1L, "AA:BB:CC:DD:EE:FF", "ACTUATOR_EDGE"))).thenReturn(3L);
        when(queries.handle(new GetEdgeByIdQuery(3L))).thenReturn(Optional.of(edge()));

        mvc.perform(post("/api/v1/edges").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.businessId").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.edgeType").value("ACTUATOR_EDGE"));
    }

    @Test
    void registrationAnswers404400And409() throws Exception {
        when(commands.handle(any(RegisterEdgeCommand.class))).thenThrow(new BusinessNotFound("business with id 1 not found"));
        mvc.perform(post("/api/v1/edges").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("business_not_found"));

        when(commands.handle(any(RegisterEdgeCommand.class))).thenThrow(new MacAddressAlreadyExists("AA:BB:CC:DD:EE:FF"));
        mvc.perform(post("/api/v1/edges").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("device_already_registered"));

        when(commands.handle(any(RegisterEdgeCommand.class))).thenThrow(new IllegalArgumentException("Unknown edge type: CAMERA."));
        mvc.perform(post("/api/v1/edges").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRejectsAnIncompleteBodyWithoutCallingTheService() throws Exception {
        mvc.perform(post("/api/v1/edges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB:CC:DD:EE:FF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(commands);
    }

    // --- changing the status of an edge ---

    @Test
    void changesTheStatusAndReturnsTheEdge() throws Exception {
        var edge = edge();
        edge.changeStatusTo(AssetStatus.MAINTENANCE);
        when(commands.handle(new ChangeEdgeStatusCommand(3L, "MAINTENANCE"))).thenReturn(3L);
        when(queries.handle(new GetEdgeByIdQuery(3L))).thenReturn(Optional.of(edge));

        mvc.perform(put("/api/v1/edges/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void theStatusChangeAnswers400404And409() throws Exception {
        doThrow(new IllegalArgumentException("An edge becomes IN_USE only by being placed in an installation."))
                .when(commands).handle(new ChangeEdgeStatusCommand(3L, "IN_USE"));
        mvc.perform(put("/api/v1/edges/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_USE\"}"))
                .andExpect(status().isBadRequest());

        doThrow(new EdgeNotFound(9L)).when(commands).handle(new ChangeEdgeStatusCommand(9L, "BROKEN"));
        mvc.perform(put("/api/v1/edges/9/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));

        doThrow(new EdgeStillAssigned(3L, AssetStatus.RETIRED)).when(commands).handle(new ChangeEdgeStatusCommand(3L, "RETIRED"));
        mvc.perform(put("/api/v1/edges/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RETIRED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_still_assigned"));

        doThrow(new InvalidAssetStatusTransition(AssetStatus.RETIRED, AssetStatus.AVAILABLE))
                .when(commands).handle(new ChangeEdgeStatusCommand(3L, "AVAILABLE"));
        mvc.perform(put("/api/v1/edges/3/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_asset_status_transition"));
    }

    @Test
    void theStatusChangeRejectsAMissingStatusWithoutCallingTheService() throws Exception {
        mvc.perform(put("/api/v1/edges/3/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(commands);
    }
}
