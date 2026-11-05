package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonCareProfileQuery;
import com.resolum.alivia.nursing.domain.repositories.AssistedPersonCareProfileRepository;
import com.resolum.alivia.nursing.application.internal.commandservices.AssistedPersonCareProfileCommandServiceImpl;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCareProfileQueryService;
import com.resolum.alivia.nursing.interfaces.rest.NursingExceptionHandler;
import com.resolum.alivia.nursing.interfaces.rest.controllers.AssistedPersonCareProfilesController;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AssistedPersonCareProfilesController Tests")
class AssistedPersonCareProfilesControllerTest {

    private static final String URL = "/api/v1/assisted-person-care-profiles/mine";
    private static final String BODY = """
            {"firstName":"Alberto","lastName":"Villanueva Ríos","birthDate":"1942-08-14",
             "documentType":"DNI","documentNumber":"09482154","kinship":"PARENT","phoneNumber":"51987654321",
             "mobilityLevel":"PARTIAL",
             "emergencyContactName":"","emergencyContactPhone":"","consentGiven":true}""";

    private final AssistedPersonCareProfileRepository repository = mock(AssistedPersonCareProfileRepository.class);
    private final AssistedPersonCareProfileQueryService queryService = mock(AssistedPersonCareProfileQueryService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AssistedPersonCareProfilesController(
                    new AssistedPersonCareProfileCommandServiceImpl(repository, clock), queryService))
            .setControllerAdvice(new NursingExceptionHandler(), new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("PUT mine creates the profile of the principal and answers it without personal leaks")
    void saveCreatesProfile() throws Exception {
        when(repository.findByRegisteredBy(new UserId(10L))).thenReturn(Optional.empty());
        when(repository.save(any(AssistedPersonCareProfile.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(put(URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alberto"))
                .andExpect(jsonPath("$.kinship").value("PARENT"))
                .andExpect(jsonPath("$.emergencyContactName").isEmpty())
                .andExpect(jsonPath("$.consentGivenAt").value("2026-11-05T15:00:00Z"));
    }

    @Test
    @DisplayName("PUT mine replaces the existing profile instead of creating another")
    void saveReplacesProfile() throws Exception {
        var existing = new AssistedPersonCareProfile(
                AssistedPersonCareProfileTest.minimal(new UserId(10L)), Instant.EPOCH);
        when(repository.findByRegisteredBy(new UserId(10L))).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        mvc.perform(put(URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("09482154"));
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("PUT mine answers 400 for an unknown mobility level or missing consent")
    void saveRejectsInvalidBody() throws Exception {
        mvc.perform(put(URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"PARTIAL\"", "\"FLYING\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(put(URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"consentGiven\":true", "\"consentGiven\":false")))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("GET mine answers 404 when the client has no profile yet")
    void getMineNotFound() throws Exception {
        when(queryService.handle(any(GetAssistedPersonCareProfileQuery.class))).thenReturn(Optional.empty());

        mvc.perform(get(URL).principal(() -> "10")).andExpect(status().isNotFound());
    }
}
