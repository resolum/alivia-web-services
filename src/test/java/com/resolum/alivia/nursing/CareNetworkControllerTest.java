package com.resolum.alivia.nursing;

import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonNotInHouseholdException;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CaregiverAlreadyLinkedException;
import com.resolum.alivia.nursing.domain.model.commands.LinkCaregiverCommand;
import com.resolum.alivia.nursing.domain.model.commands.RegisterAssistedPersonCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetCaregiversQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.nursing.domain.services.CareNetworkQueryService;
import com.resolum.alivia.nursing.domain.services.CaregiverMembershipCommandService;
import com.resolum.alivia.nursing.interfaces.rest.NursingExceptionHandler;
import com.resolum.alivia.nursing.interfaces.rest.controllers.CareNetworkController;
import com.resolum.alivia.nursing.interfaces.rest.resources.RegisterAssistedPersonResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CareNetworkController Tests")
class CareNetworkControllerTest {

    private static final UserId REQUESTER = new UserId(10L);
    private static final PersonProfileId ASSISTED_PROFILE = new PersonProfileId(200L);
    private static final PersonProfileId CAREGIVER_PROFILE = new PersonProfileId(300L);
    private static final UserId CAREGIVER = new UserId(30L);
    private static final String ASSISTED_URL = "/api/v1/households/1/assisted-persons";
    private static final String CAREGIVERS_URL = "/api/v1/households/1/caregivers";
    private static final String ASSISTED_BODY = """
            {"email":"maria@alivia.com","password":"Secret#123","firstName":"Maria","lastName":"Lopez",
             "phoneNumber":"+51987654321","dni":"12345678","birthDate":"1950-03-04"}""";
    private static final String LINK_BODY = "{\"assistedPersonId\":200,\"caregiverUserId\":30}";

    private final CareNetworkQueryService queryService = mock(CareNetworkQueryService.class);
    private final AssistedPersonCommandService assistedPersonService = mock(AssistedPersonCommandService.class);
    private final CaregiverMembershipCommandService caregiverService = mock(CaregiverMembershipCommandService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new CareNetworkController(queryService, assistedPersonService, caregiverService))
            .setControllerAdvice(new NursingExceptionHandler(), new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("POST assisted-persons returns 201 with the linked profile and builds the command from the principal")
    void registerReturns201() throws Exception {
        when(assistedPersonService.handle(any(RegisterAssistedPersonCommand.class)))
                .thenReturn(new AssistedPersonSummary(ASSISTED_PROFILE, "Maria Lopez"));

        mvc.perform(post(ASSISTED_URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON).content(ASSISTED_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.fullName").value("Maria Lopez"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(assistedPersonService).handle(argThat(command -> command.requesterUserId().equals(REQUESTER)
                && command.householdId().equals(1L) && command.email().equals("maria@alivia.com")
                && command.dni().equals("12345678") && command.birthDate().equals(LocalDate.of(1950, 3, 4))));
    }

    @Test
    @DisplayName("POST assisted-persons with an invalid body returns 400 listing the fields and never calls the service")
    void registerWithInvalidBodyReturns400() throws Exception {
        var body = """
                {"email":"","password":"Secret#123","firstName":"Maria","lastName":"Lopez",
                 "phoneNumber":"+51987654321","dni":"123","birthDate":"2999-01-01"}""";

        mvc.perform(post(ASSISTED_URL).principal(() -> "10").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='dni')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='birthDate')]").exists());

        verifyNoInteractions(assistedPersonService);
    }

    @Test
    @DisplayName("POST assisted-persons returns 403, 404 and 409 for a denied user, a missing household and a taken email")
    void registerErrors() throws Exception {
        when(assistedPersonService.handle(any(RegisterAssistedPersonCommand.class)))
                .thenThrow(new CareNetworkAccessDeniedException(1L))
                .thenThrow(new CareNetworkHouseholdNotFoundException(1L))
                .thenThrow(new EmailAlreadyRegisteredException());

        perform(post(ASSISTED_URL), ASSISTED_BODY).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("household_access_denied"));
        perform(post(ASSISTED_URL), ASSISTED_BODY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("household_not_found"));
        perform(post(ASSISTED_URL), ASSISTED_BODY).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET assisted-persons returns 200 with the assisted persons of the household")
    void listAssistedPersons() throws Exception {
        when(queryService.handle(any(GetAssistedPersonsQuery.class)))
                .thenReturn(List.of(new AssistedPersonSummary(ASSISTED_PROFILE, "Maria Lopez")));

        mvc.perform(get(ASSISTED_URL).principal(() -> "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(200))
                .andExpect(jsonPath("$[0].fullName").value("Maria Lopez"));

        verify(queryService).handle(argThat((GetAssistedPersonsQuery query) ->
                query.userId().equals(REQUESTER) && query.householdId().equals(1L)));
    }

    @Test
    @DisplayName("GET assisted-persons and caregivers return 403 for a non member and 404 for a missing household")
    void listErrors() throws Exception {
        when(queryService.handle(any(GetAssistedPersonsQuery.class)))
                .thenThrow(new CareNetworkAccessDeniedException(1L))
                .thenThrow(new CareNetworkHouseholdNotFoundException(1L));
        when(queryService.handle(any(GetCaregiversQuery.class)))
                .thenThrow(new CareNetworkAccessDeniedException(1L))
                .thenThrow(new CareNetworkHouseholdNotFoundException(1L));

        mvc.perform(get(ASSISTED_URL).principal(() -> "99")).andExpect(status().isForbidden());
        mvc.perform(get(ASSISTED_URL).principal(() -> "10")).andExpect(status().isNotFound());
        mvc.perform(get(CAREGIVERS_URL).principal(() -> "99")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("household_access_denied"));
        mvc.perform(get(CAREGIVERS_URL).principal(() -> "10")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("household_not_found"));
    }

    @Test
    @DisplayName("POST caregivers returns 201 with the linked caregiver and builds the command from the principal")
    void linkReturns201() throws Exception {
        when(caregiverService.handle(any(LinkCaregiverCommand.class))).thenReturn(
                new CaregiverSummary(CAREGIVER, CAREGIVER_PROFILE, ASSISTED_PROFILE, "Ana Torres", "ana@alivia.com"));

        perform(post(CAREGIVERS_URL), LINK_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(30))
                .andExpect(jsonPath("$.profileId").value(300))
                .andExpect(jsonPath("$.assistedPersonId").value(200))
                .andExpect(jsonPath("$.fullName").value("Ana Torres"))
                .andExpect(jsonPath("$.email").value("ana@alivia.com"));

        verify(caregiverService).handle(argThat(command -> command.requesterUserId().equals(REQUESTER)
                && command.householdId().equals(1L) && command.assistedPersonProfileId().equals(ASSISTED_PROFILE)
                && command.caregiverUserId().equals(CAREGIVER)));
    }

    @Test
    @DisplayName("POST caregivers returns 400 for a missing or non positive identifier")
    void linkWithInvalidBodyReturns400() throws Exception {
        perform(post(CAREGIVERS_URL), "{\"assistedPersonId\":200}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='caregiverUserId')]").exists());
        perform(post(CAREGIVERS_URL), "{\"assistedPersonId\":0,\"caregiverUserId\":30}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='assistedPersonId')]").exists());

        verify(caregiverService, never()).handle(any());
    }

    @Test
    @DisplayName("POST caregivers maps 403, 404 for household, assisted person and caregiver, 400 for self care, and 409")
    void linkErrors() throws Exception {
        when(caregiverService.handle(any(LinkCaregiverCommand.class)))
                .thenThrow(new CareNetworkAccessDeniedException(1L))
                .thenThrow(new CareNetworkHouseholdNotFoundException(1L))
                .thenThrow(new AssistedPersonNotInHouseholdException(1L, ASSISTED_PROFILE))
                .thenThrow(new CareMemberProfileNotFoundException(CAREGIVER))
                .thenThrow(new IllegalArgumentException("An assisted person cannot be their own caregiver."))
                .thenThrow(new CaregiverAlreadyLinkedException(CAREGIVER, ASSISTED_PROFILE));

        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isForbidden());
        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("household_not_found"));
        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("assisted_person_not_found"));
        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("person_profile_not_found"));
        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_argument"));
        perform(post(CAREGIVERS_URL), LINK_BODY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("caregiver_already_linked"));
    }

    @Test
    @DisplayName("GET caregivers returns 200 with the caregivers of the household")
    void listCaregivers() throws Exception {
        when(queryService.handle(any(GetCaregiversQuery.class))).thenReturn(List.of(
                new CaregiverSummary(CAREGIVER, CAREGIVER_PROFILE, ASSISTED_PROFILE, "Ana Torres", null)));

        mvc.perform(get(CAREGIVERS_URL).principal(() -> "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(30))
                .andExpect(jsonPath("$[0].assistedPersonId").value(200))
                .andExpect(jsonPath("$[0].email").isEmpty());
    }

    @Test
    @DisplayName("The registration body never prints the password")
    void bodyHidesPassword() {
        var resource = new RegisterAssistedPersonResource("a@b.com", "Secret#123", "Maria", "Lopez",
                "+51987654321", "12345678", LocalDate.of(1950, 3, 4));

        assertThat(resource.toString()).doesNotContain("Secret#123", "a@b.com", "12345678");
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return mvc.perform(request.principal(() -> "10").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
