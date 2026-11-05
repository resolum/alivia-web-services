package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.MacAddressAlreadyExists;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;
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
        mvc = MockMvcBuilders.standaloneSetup(new EdgesController(commands, queries))
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
}
