package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.AssistedPersonsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateAssistedPersonResource;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistedPersonsControllerTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 1, 1);

    private AssistedPersonCommandService commands;
    private AssistedPersonQueryService queries;
    private ExternalProfilesService profiles;
    private AssistanceAccessService access;
    private RelativeQueryService relatives;
    private AssistedPersonsController controller;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        commands = mock(AssistedPersonCommandService.class);
        queries = mock(AssistedPersonQueryService.class);
        profiles = mock(ExternalProfilesService.class);
        access = mock(AssistanceAccessService.class);
        relatives = mock(RelativeQueryService.class);
        controller = new AssistedPersonsController(commands, queries, profiles, access, relatives);
        person = assistedPerson(2L, relative(1L));
        when(profiles.fetchPersonProfile(any())).thenReturn(Optional.of(new ProfilesContextFacade.PersonProfileData(
                102L, "a@mail.com", null, null, "519", "Ana", "Diaz", "12345678", BIRTH)));
        // The caller 7 is the relative 1.
        when(relatives.handle(new GetRelativeByUserIdQuery(7L))).thenReturn(Optional.of(relative(1L)));
        when(access.canManageHousehold(3L, 7L)).thenReturn(true);
    }

    private static CreateAssistedPersonResource createResource(String kinship, String mobility, Boolean consent) {
        return new CreateAssistedPersonResource(1L, 3L, "a@mail.com", "519", "Ana", "Diaz", "12345678", null,
                "DNI", BIRTH, kinship, mobility, "Luis", "51922", consent);
    }

    private static CreateAssistedPersonResource withoutRelativeId() {
        return new CreateAssistedPersonResource(null, 3L, "a@mail.com", "519", "Ana", "Diaz", "12345678", null,
                "DNI", BIRTH, null, null, "Luis", "51922", true);
    }

    private static Authentication caller(String name, String... authorities) {
        return new TestingAuthenticationToken(name, null, authorities);
    }

    @ParameterizedTest
    @CsvSource({
            "parent, partial, PARENT, PARTIAL",
            "MADRE, unknown, PARENT, PARTIAL",
            "abuela, VERY_LIMITED, GRANDPARENT, VERY_LIMITED",
            "Tio, NO_LIMB_MOBILITY, AUNT_UNCLE, NO_LIMB_MOBILITY",
            "esposa, partial, SPOUSE, PARTIAL",
            "hermano, partial, SIBLING, PARTIAL",
            "vecino, partial, OTHER, PARTIAL"
    })
    void createMapsKinshipAndMobilityWords(String kinship, String mobility, Kinship expectedKinship,
                                           MobilityLevel expectedMobility) {
        when(commands.handle(any(CreateAssistedPersonCommand.class))).thenReturn(2L);
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.of(person));

        var response = controller.createAssistedPerson(createResource(kinship, mobility, false), caller("7"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().kinship()).isEqualTo(expectedKinship);
        assertThat(command.getValue().mobilityLevel()).isEqualTo(expectedMobility);
        assertThat(command.getValue().userId()).isEqualTo(7L);
        assertThat(command.getValue().consentGiven()).isFalse();
    }

    @Test
    void createAnswersWithTheIdWhenThePersonCannotBeReadBack() {
        when(commands.handle(any(CreateAssistedPersonCommand.class))).thenReturn(2L);
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.empty());

        var response = controller.createAssistedPerson(createResource(" ", null, null), caller("7"));

        assertThat(response.getBody().id()).isEqualTo(2L);
        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().kinship()).isNull();
        assertThat(command.getValue().mobilityLevel()).isNull();
        assertThat(command.getValue().consentGiven()).isTrue();
    }

    @Test
    void createRegistersThePersonForTheCallerNeverForTheRelativeOfTheBody() {
        when(commands.handle(any(CreateAssistedPersonCommand.class))).thenReturn(2L);
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.of(person));

        controller.createAssistedPerson(withoutRelativeId(), caller("7"));

        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().relativeId()).isEqualTo(1L);
    }

    @Test
    void createLetsAnAccountWithoutARelativeRecordRegisterItsFirstPerson() {
        when(commands.handle(any(CreateAssistedPersonCommand.class))).thenReturn(2L);
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.of(person));
        when(access.canManageHousehold(3L, 8L)).thenReturn(true);

        var response = controller.createAssistedPerson(withoutRelativeId(), caller("8", "ROLE_CAREGIVER"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        // No relative id: the command service creates the record of the caller.
        assertThat(command.getValue().relativeId()).isNull();
        assertThat(command.getValue().userId()).isEqualTo(8L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ROLE_ADMINISTRATOR", "ROLE_TECHNICIAN", "ROLE_SUBSCRIPTION_MANAGER", "ROLE_CLEANING" })
    void createNeverTurnsAStaffAccountIntoARelative(String role) {
        when(access.canManageHousehold(3L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> controller.createAssistedPerson(withoutRelativeId(), caller("8", role)))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateAssistedPersonCommand.class));
    }

    @Test
    void createKeepsWorkingForAStaffAccountThatAlreadyIsARelative() {
        when(commands.handle(any(CreateAssistedPersonCommand.class))).thenReturn(2L);
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.of(person));

        controller.createAssistedPerson(createResource(null, null, null), caller("7", "ROLE_ADMINISTRATOR"));

        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().relativeId()).isEqualTo(1L);
    }

    @Test
    void createRefusesARelativeIdFromAnAccountThatHasNone() {
        when(access.canManageHousehold(3L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> controller.createAssistedPerson(createResource(null, null, null), caller("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateAssistedPersonCommand.class));
    }

    @Test
    void createRefusesAHouseholdOfAnotherToANewAccount() {
        assertThatThrownBy(() -> controller.createAssistedPerson(withoutRelativeId(), caller("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateAssistedPersonCommand.class));
    }

    @Test
    void createRefusesToActForAnotherRelative() {
        var forAnotherRelative = new CreateAssistedPersonResource(99L, 3L, "a@mail.com", "519", "Ana", "Diaz",
                "12345678", null, "DNI", BIRTH, null, null, "Luis", "51922", true);

        assertThatThrownBy(() -> controller.createAssistedPerson(forAnotherRelative, caller("7")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateAssistedPersonCommand.class));
    }

    @Test
    void createRefusesAHouseholdTheCallerDoesNotManage() {
        when(access.canManageHousehold(3L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.createAssistedPerson(createResource(null, null, null), caller("7")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateAssistedPersonCommand.class));
    }

    @Test
    void createRefusesACallerWithoutAnAccount() {
        assertThatThrownBy(() -> controller.createAssistedPerson(createResource(null, null, null), null))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.createAssistedPerson(createResource(null, null, null), caller("edge:4")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void updatesTheAssistedPersonAsTheRelativeInChargeOfIt() {
        when(access.requireManage(2L, 7L)).thenReturn(person);
        when(commands.handle(any(UpdateAssistedPersonCommand.class))).thenReturn(2L);

        var response = controller.updateAssistedPerson(2L, principal("7"), new UpdateAssistedPersonResource(99L, 3L,
                "519", "Ana", "Diaz", BIRTH, "SPOUSE", "partial", "Luis", "51922"));

        assertThat(response.getBody()).isEqualTo(2L);
        ArgumentCaptor<UpdateAssistedPersonCommand> command = ArgumentCaptor.forClass(UpdateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().relativeId()).isEqualTo(1L);
        controller.updateAssistedPerson(2L, principal("7"), new UpdateAssistedPersonResource(1L, "519", "Ana", "Diaz", BIRTH));
    }

    @Test
    void updateRefusesAPersonTheCallerDoesNotManage() {
        when(access.requireManage(2L, 8L)).thenThrow(new AssistanceAccessDeniedException("no"));

        assertThatThrownBy(() -> controller.updateAssistedPerson(2L, principal("8"),
                new UpdateAssistedPersonResource(1L, "519", "Ana", "Diaz", BIRTH)))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(UpdateAssistedPersonCommand.class));
    }

    @Test
    void updateRefusesMovingThePersonToAHouseholdTheCallerDoesNotManage() {
        when(access.requireManage(2L, 7L)).thenReturn(person);
        when(access.canManageHousehold(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.updateAssistedPerson(2L, principal("7"), new UpdateAssistedPersonResource(
                1L, 9L, "519", "Ana", "Diaz", BIRTH, "SPOUSE", "partial", "Luis", "51922")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(UpdateAssistedPersonCommand.class));
    }

    @Test
    void readsOneAssistedPersonOnlyWhenTheCallerMayRead() {
        when(access.requireRead(2L, 7L)).thenReturn(person);
        when(access.requireRead(3L, 7L)).thenThrow(new AssistedPersonNotFoundException(3L));
        when(access.requireRead(2L, 8L)).thenThrow(new AssistanceAccessDeniedException("no"));

        assertThat(controller.getAssistedPersonById(2L, principal("7")).getStatusCode().value()).isEqualTo(200);
        assertThatThrownBy(() -> controller.getAssistedPersonById(3L, principal("7")))
                .isInstanceOf(AssistedPersonNotFoundException.class);
        assertThatThrownBy(() -> controller.getAssistedPersonById(2L, principal("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void listsOnlyThePersonsTheCallerTakesCareOf() {
        var other = assistedPerson(5L, relative(4L));
        when(access.readableBy(7L)).thenReturn(List.of(person, other));
        when(access.readableBy(8L)).thenReturn(List.of());

        assertThat(controller.getAssistedPersons(principal("7"), null).getBody()).hasSize(2);
        // A relative id narrows the list; it never adds persons of someone else.
        assertThat(controller.getAssistedPersons(principal("7"), 1L).getBody()).hasSize(1);
        assertThat(controller.getAssistedPersons(principal("7"), 77L).getBody()).isEmpty();
        // A caller who takes care of nobody gets nothing, not everyone.
        assertThat(controller.getAssistedPersons(principal("8"), null).getBody()).isEmpty();
        assertThatThrownBy(() -> controller.getAssistedPersons(null, null))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }
}
