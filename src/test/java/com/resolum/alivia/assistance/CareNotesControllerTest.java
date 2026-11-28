package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareNoteCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetCareNotesQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareSummaryQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary;
import com.resolum.alivia.assistance.domain.services.CareNoteCommandService;
import com.resolum.alivia.assistance.domain.services.CareSummaryQueryService;
import com.resolum.alivia.assistance.interfaces.rest.AssistanceExceptionHandler;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CareNotesController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CareNotesControllerTest {

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final CareNoteCommandService commands = mock(CareNoteCommandService.class);
    private final CareSummaryQueryService queries = mock(CareSummaryQueryService.class);
    private final CareNote note = mock(CareNote.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CareNotesController(commands, queries))
                .setControllerAdvice(new AssistanceExceptionHandler(), new GlobalExceptionHandler()).build();
        when(note.getId()).thenReturn(1L);
        when(note.getAssistedPersonId()).thenReturn(5L);
        when(note.getAuthorUserId()).thenReturn(7L);
        when(note.getCategory()).thenReturn(CareNoteCategory.HEALTH);
        when(note.getContent()).thenReturn("Slept well");
        when(note.getCreatedAt()).thenReturn(DAY.atTime(8, 0));
        when(commands.handle(any(CreateCareNoteCommand.class))).thenReturn(note);
    }

    private org.springframework.test.web.servlet.ResultActions postNote(String json) throws Exception {
        return mvc.perform(post("/api/v1/assisted-persons/5/care-notes").principal(USER)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createParsesTheCategoryCaseInsensitively() throws Exception {
        postNote("{\"content\":\"Slept well\",\"category\":\" health \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.category").value("HEALTH"));
        verify(commands).handle(new CreateCareNoteCommand(5L, 7L, CareNoteCategory.HEALTH, "Slept well"));
    }

    @Test
    void aMissingOrBlankCategoryIsGeneral() throws Exception {
        postNote("{\"content\":\"a\"}").andExpect(status().isCreated());
        verify(commands).handle(new CreateCareNoteCommand(5L, 7L, CareNoteCategory.GENERAL, "a"));
        postNote("{\"content\":\"b\",\"category\":\"  \"}").andExpect(status().isCreated());
        verify(commands).handle(new CreateCareNoteCommand(5L, 7L, CareNoteCategory.GENERAL, "b"));
    }

    @Test
    void aNullBodyIsPassedAsAnEmptyGeneralNote() {
        // Spring rejects a null body over HTTP, so the guard is reached only by a direct call.
        new CareNotesController(commands, queries).createNote(5L, null, USER);
        verify(commands).handle(new CreateCareNoteCommand(5L, 7L, CareNoteCategory.GENERAL, null));
    }

    @Test
    void anUnknownCategoryIs400() throws Exception {
        postNote("{\"content\":\"a\",\"category\":\"DANCE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void notesAndSummaryAreReadForTheCaller() throws Exception {
        when(queries.handle(new GetCareNotesQuery(5L, 7L))).thenReturn(List.of(note));
        when(queries.handle(new GetCareSummaryQuery(5L, 7L, DAY, DAY)))
                .thenReturn(new CareSummary(5L, DAY, DAY, 1, 0, 0, List.of(), List.of()));

        mvc.perform(get("/api/v1/assisted-persons/5/care-notes").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Slept well"));
        mvc.perform(get("/api/v1/assisted-persons/5/care-summary").principal(USER)
                        .param("from", DAY.toString()).param("to", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attended").value(1));
    }
}
