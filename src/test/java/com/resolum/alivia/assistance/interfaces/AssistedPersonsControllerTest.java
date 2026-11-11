package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.AssistedPersonsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateAssistedPersonResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateAssistedPersonResource;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistedPersonsControllerTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 1, 1);

    private AssistedPersonCommandService commands;
    private AssistedPersonQueryService queries;
    private ExternalProfilesService profiles;
    private AssistedPersonsController controller;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        commands = mock(AssistedPersonCommandService.class);
        queries = mock(AssistedPersonQueryService.class);
        profiles = mock(ExternalProfilesService.class);
        controller = new AssistedPersonsController(commands, queries, profiles);
        person = assistedPerson(2L, relative(1L));
        when(profiles.fetchPersonProfile(any())).thenReturn(Optional.of(new ProfilesContextFacade.PersonProfileData(
                102L, "a@mail.com", null, null, "519", "Ana", "Diaz", "12345678", BIRTH)));
    }

    private static CreateAssistedPersonResource createResource(String kinship, String mobility, Boolean consent) {
        return new CreateAssistedPersonResource(1L, 3L, "a@mail.com", "519", "Ana", "Diaz", "12345678", null,
                "DNI", BIRTH, kinship, mobility, "Luis", "51922", consent);
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

        var response = controller.createAssistedPerson(createResource(kinship, mobility, false), principal("7"));

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

        var response = controller.createAssistedPerson(createResource(" ", null, null), principal("x"));

        assertThat(response.getBody().id()).isEqualTo(2L);
        ArgumentCaptor<CreateAssistedPersonCommand> command = ArgumentCaptor.forClass(CreateAssistedPersonCommand.class);
        verify(commands).handle(command.capture());
        assertThat(command.getValue().kinship()).isNull();
        assertThat(command.getValue().mobilityLevel()).isNull();
        assertThat(command.getValue().userId()).isNull();
        assertThat(command.getValue().consentGiven()).isTrue();

        controller.createAssistedPerson(createResource(null, " ", true), null);
    }

    @Test
    void updatesTheAssistedPerson() {
        when(commands.handle(any(UpdateAssistedPersonCommand.class))).thenReturn(2L);

        var response = controller.updateAssistedPerson(2L, new UpdateAssistedPersonResource(1L, 3L, "519", "Ana",
                "Diaz", BIRTH, "SPOUSE", "partial", "Luis", "51922"));

        assertThat(response.getBody()).isEqualTo(2L);
        controller.updateAssistedPerson(2L, new UpdateAssistedPersonResource(1L, "519", "Ana", "Diaz", BIRTH));
    }

    @Test
    void readsOneAssistedPersonOrAnswersNotFound() {
        when(queries.handle(new GetAssistedPersonByIdQuery(2L))).thenReturn(Optional.of(person));
        when(queries.handle(new GetAssistedPersonByIdQuery(3L))).thenReturn(Optional.empty());

        assertThat(controller.getAssistedPersonById(2L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getAssistedPersonById(3L).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void listsByRelativeByCallerOrEveryone() {
        when(queries.handle(new GetAssistedPersonsByRelativeIdQuery(1L))).thenReturn(List.of(person));
        when(queries.handle(new GetAssistedPersonsByUserIdQuery(new UserId(1L)))).thenReturn(List.of(person));
        when(queries.handle(new GetAssistedPersonsByUserIdQuery(new UserId(5L)))).thenReturn(List.of());
        when(queries.handle(new GetAllAssistedPersonsQuery())).thenReturn(List.of(person, person));

        assertThat(controller.getAssistedPersons(null, 1L).getBody()).hasSize(1);
        assertThat(controller.getAssistedPersons(principal("1"), null).getBody()).hasSize(1);
        assertThat(controller.getAssistedPersons(principal("5"), null).getBody()).hasSize(2);
        assertThat(controller.getAssistedPersons(principal("x"), null).getBody()).hasSize(2);
        assertThat(controller.getAssistedPersons(null, null).getBody()).hasSize(2);
    }
}
