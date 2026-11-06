package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.interfaces.rest.controllers.InstallationEdgesController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstallationEdgesControllerTest {

    private static final String PATH = "/api/v1/installations/3/edges/7";
    private static final PlaceEdgeInInstallationCommand PLACE = new PlaceEdgeInInstallationCommand(3L, 7L);
    private static final RemoveEdgeFromInstallationCommand REMOVE = new RemoveEdgeFromInstallationCommand(3L, 7L);

    private HouseholdDeviceRegistryCommandService commands;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(HouseholdDeviceRegistryCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new InstallationEdgesController(commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void placesAnEdgeAndAnswersWithTheEdgesOfTheInstallation() throws Exception {
        when(commands.handle(PLACE)).thenReturn(new InstallationEdges(3L, 1L, 7L, 4L));

        mvc.perform(put(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value(3))
                .andExpect(jsonPath("$.microphoneEdgeId").value(1))
                .andExpect(jsonPath("$.actuatorEdgeId").value(7))
                .andExpect(jsonPath("$.replacedEdgeId").value(4));
    }

    @Test
    void aPlaceWithoutEdgeIsShownAsNull() throws Exception {
        when(commands.handle(PLACE)).thenReturn(new InstallationEdges(3L, null, 7L, null));

        mvc.perform(put(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.microphoneEdgeId").doesNotExist())
                .andExpect(jsonPath("$.replacedEdgeId").doesNotExist());
    }

    @Test
    void answers404WhenTheInstallationOrTheEdgeDoNotExist() throws Exception {
        doThrow(new InstallationNotFound(3L)).when(commands).handle(PLACE);
        mvc.perform(put(PATH)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("installation_not_found"));

        doThrow(new EdgeNotFound(7L)).when(commands).handle(PLACE);
        mvc.perform(put(PATH)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));
    }

    @Test
    void answers409WithADistinctCodeForEachConflict() throws Exception {
        doThrow(new EdgeNotAvailable(7L, AssetStatus.IN_USE)).when(commands).handle(PLACE);
        mvc.perform(put(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_already_assigned"));

        doThrow(new EdgeNotAvailable(7L, AssetStatus.BROKEN)).when(commands).handle(PLACE);
        mvc.perform(put(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_not_available"));

        doThrow(new InstallationNotActive(3L)).when(commands).handle(PLACE);
        mvc.perform(put(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("installation_not_active"));
    }

    @Test
    void removesAnEdge() throws Exception {
        when(commands.handle(REMOVE)).thenReturn(new InstallationEdges(3L, 1L, null, 7L));

        mvc.perform(delete(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.microphoneEdgeId").value(1))
                .andExpect(jsonPath("$.actuatorEdgeId").doesNotExist())
                .andExpect(jsonPath("$.replacedEdgeId").value(7));
    }

    @Test
    void removingAnEdgeThatIsNotThereAnswers409() throws Exception {
        doThrow(new EdgeNotAssigned(7L, 3L)).when(commands).handle(REMOVE);

        mvc.perform(delete(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_not_assigned"));
    }
}
