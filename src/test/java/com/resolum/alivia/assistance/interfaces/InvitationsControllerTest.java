package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.InvitationCommandService;
import com.resolum.alivia.assistance.domain.services.InvitationQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.InvitationsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.AcceptInvitationResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateInvitationResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.invitation;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationsControllerTest {

    private InvitationCommandService commands;
    private InvitationQueryService queries;
    private RelativeRepository relatives;
    private AssistedPersonRepository persons;
    private InvitationsController controller;
    private Relative relative;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        commands = mock(InvitationCommandService.class);
        queries = mock(InvitationQueryService.class);
        relatives = mock(RelativeRepository.class);
        persons = mock(AssistedPersonRepository.class);
        controller = new InvitationsController(commands, queries, relatives, persons);
        relative = relative(4L);
        person = assistedPerson(8L, relative);
        when(commands.handle(any(CreateInvitationCommand.class))).thenReturn(10L);
        when(commands.handle(any(AcceptInvitationCommand.class))).thenReturn(11L);
        when(commands.handle(any(RevokeInvitationCommand.class))).thenReturn(12L);
        when(commands.handle(any(ResendInvitationCommand.class))).thenReturn(13L);
    }

    @Test
    void createResolvesTheRelativeAndAssistedPersonFromTheCaller() {
        when(relatives.findByUserId(new UserId(4L))).thenReturn(Optional.of(relative));
        when(persons.findAllByRelativeId(4L)).thenReturn(List.of(person));

        var response = controller.createInvitation(principal("4"), new CreateInvitationResource(null, null, "c@mail.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(commands).handle(new CreateInvitationCommand(4L, 8L, "c@mail.com"));
    }

    @Test
    void createFallsBackToDefaultsWhenTheCallerCannotBeResolved() {
        when(persons.findAllByRelativeId(1L)).thenReturn(List.of());

        controller.createInvitation(principal("not-a-number"), new CreateInvitationResource(null, null, "c@mail.com"));
        controller.createInvitation(null, new CreateInvitationResource(null, null, "c@mail.com"));
        controller.createInvitation(null, new CreateInvitationResource(2L, 3L, "c@mail.com"));

        verify(commands, org.mockito.Mockito.times(2)).handle(new CreateInvitationCommand(1L, 1L, "c@mail.com"));
        verify(commands).handle(new CreateInvitationCommand(2L, 3L, "c@mail.com"));
    }

    @Test
    void acceptUsesTheBodyThePrincipalOrTheDefaultUser() {
        assertThat(controller.acceptInvitation(1L, new AcceptInvitationResource(5L), null).getBody()).isEqualTo(11L);
        controller.acceptInvitation(1L, null, principal("6"));
        controller.acceptInvitation(1L, new AcceptInvitationResource(null), principal("x"));
        controller.acceptInvitation(1L, null, null);

        verify(commands).handle(new AcceptInvitationCommand(1L, 5L));
        verify(commands).handle(new AcceptInvitationCommand(1L, 6L));
        verify(commands, org.mockito.Mockito.times(2)).handle(new AcceptInvitationCommand(1L, 1L));
    }

    @Test
    void revokeAndResendResolveTheRelative() {
        when(relatives.findByUserId(new UserId(4L))).thenReturn(Optional.of(relative));

        assertThat(controller.revokeInvitation(1L, 9L, null).getBody()).isEqualTo(12L);
        controller.revokeInvitation(1L, null, principal("4"));
        controller.revokeInvitation(1L, null, principal("bad"));
        controller.revokeInvitation(1L, null, null);
        verify(commands).handle(new RevokeInvitationCommand(1L, 9L));
        verify(commands).handle(new RevokeInvitationCommand(1L, 4L));
        verify(commands, org.mockito.Mockito.times(2)).handle(new RevokeInvitationCommand(1L, 1L));

        assertThat(controller.resendInvitation(1L, 9L, null).getBody()).isEqualTo(13L);
        controller.resendInvitation(1L, null, principal("4"));
        controller.resendInvitation(1L, null, principal("bad"));
        controller.resendInvitation(1L, null, null);
        verify(commands).handle(new ResendInvitationCommand(1L, 9L));
        verify(commands).handle(new ResendInvitationCommand(1L, 4L));
        verify(commands, org.mockito.Mockito.times(2)).handle(new ResendInvitationCommand(1L, 1L));
    }

    @Test
    void getByIdAnswersOkOrNotFound() {
        when(queries.handle(new GetInvitationByIdQuery(1L))).thenReturn(Optional.of(invitation(1L, person, relative)));
        when(queries.handle(new GetInvitationByIdQuery(2L))).thenReturn(Optional.empty());

        assertThat(controller.getInvitationById(1L).getBody().inviteeEmail()).isEqualTo("care@mail.com");
        assertThat(controller.getInvitationById(2L).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void listsInvitationsByAssistedPersonCallerOrEveryone() {
        var invitation = invitation(1L, person, relative);
        when(queries.handle(new GetInvitationsByAssistedPersonIdQuery(8L))).thenReturn(List.of(invitation));
        when(relatives.findByUserId(new UserId(4L))).thenReturn(Optional.of(relative));
        when(persons.findAllByRelativeId(4L)).thenReturn(List.of(person));
        when(persons.findAll()).thenReturn(List.of(person));

        assertThat(controller.getInvitations(null, 8L).getBody()).hasSize(1);
        assertThat(controller.getInvitations(principal("4"), null).getBody()).hasSize(1);
        assertThat(controller.getInvitations(principal("5"), null).getBody()).hasSize(1);
        assertThat(controller.getInvitations(principal("bad"), null).getBody()).hasSize(1);
        assertThat(controller.getInvitations(null, null).getBody()).hasSize(1);
    }

    @Test
    void listsPendingInvitationsOfACaregiver() {
        when(queries.handle(new GetPendingInvitationsByCaregiverUserIdQuery(3L)))
                .thenReturn(List.of(invitation(1L, person, relative)));

        assertThat(controller.getPendingInvitations(3L).getBody()).hasSize(1);
    }
}
