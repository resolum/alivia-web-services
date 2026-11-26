package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.InvitationCommandService;
import com.resolum.alivia.assistance.domain.services.InvitationQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.InvitationsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.AcceptInvitationResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateInvitationResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.invitation;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationsControllerTest {

    private InvitationCommandService commands;
    private InvitationQueryService queries;
    private RelativeQueryService relativeQueryService;
    private AssistedPersonQueryService assistedPersonQueryService;
    private AssistanceAccessService access;
    private ExternalIamService iam;
    private InvitationsController controller;
    private Relative relative;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        commands = mock(InvitationCommandService.class);
        queries = mock(InvitationQueryService.class);
        relativeQueryService = mock(RelativeQueryService.class);
        assistedPersonQueryService = mock(AssistedPersonQueryService.class);
        access = mock(AssistanceAccessService.class);
        iam = mock(ExternalIamService.class);
        controller = new InvitationsController(commands, queries, relativeQueryService, assistedPersonQueryService,
                access, iam);
        relative = relative(4L);
        person = assistedPerson(8L, relative);
        when(commands.handle(any(CreateInvitationCommand.class))).thenReturn(10L);
        when(commands.handle(any(AcceptInvitationCommand.class))).thenReturn(11L);
        when(commands.handle(any(RevokeInvitationCommand.class))).thenReturn(12L);
        when(commands.handle(any(ResendInvitationCommand.class))).thenReturn(13L);
        when(access.requireManage(8L, 4L)).thenReturn(person);
    }

    @Test
    void createInvitesForAPersonTheCallerManagesOnBehalfOfItsRelative() {
        var response = controller.createInvitation(principal("4"), new CreateInvitationResource(99L, 8L, "c@mail.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        // The relative id of the body (99) is not used.
        verify(commands).handle(new CreateInvitationCommand(4L, 8L, "c@mail.com"));
    }

    @Test
    void createWithoutAPersonUsesTheFirstOneOfTheCallingRelative() {
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(4L))).thenReturn(Optional.of(relative));
        when(assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(4L))).thenReturn(List.of(person));

        controller.createInvitation(principal("4"), new CreateInvitationResource(null, null, "c@mail.com"));

        verify(commands).handle(new CreateInvitationCommand(4L, 8L, "c@mail.com"));
    }

    @Test
    void createRefusesACallerWhoDoesNotManageThePersonOrIsNotARelative() {
        when(access.requireManage(8L, 7L)).thenThrow(new AssistanceAccessDeniedException("no"));
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(7L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.createInvitation(principal("7"), new CreateInvitationResource(null, 8L, "c@mail.com")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.createInvitation(principal("7"), new CreateInvitationResource(null, null, "c@mail.com")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.createInvitation(null, new CreateInvitationResource(null, 8L, "c@mail.com")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(CreateInvitationCommand.class));
    }

    @Test
    void aSignedInUserAcceptsOnlyAnInvitationSentToTheEmailOfItsAccount() {
        when(queries.handle(new GetInvitationByIdQuery(1L))).thenReturn(Optional.of(invitation(1L, person, relative)));
        when(iam.fetchEmails(Set.of(new UserId(6L)))).thenReturn(Map.of(new UserId(6L), "CARE@mail.com"));
        when(iam.fetchEmails(Set.of(new UserId(7L)))).thenReturn(Map.of(new UserId(7L), "other@mail.com"));

        assertThat(controller.acceptInvitation(1L, null, principal("6")).getBody()).isEqualTo(11L);
        verify(commands).handle(new AcceptInvitationCommand(1L, 6L));

        assertThatThrownBy(() -> controller.acceptInvitation(1L, null, principal("7")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(new AcceptInvitationCommand(1L, 7L));
    }

    @Test
    void anInvitationIsNeverAcceptedForTheUserOfTheBodyOrADefaultAccount() {
        when(queries.handle(new GetInvitationByIdQuery(1L))).thenReturn(Optional.of(invitation(1L, person, relative)));

        // Not signed in and without a password: nobody to accept for, whatever the body says.
        assertThatThrownBy(() -> controller.acceptInvitation(1L, new AcceptInvitationResource(5L), null))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.acceptInvitation(1L, null, principal("edge:3")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(any(AcceptInvitationCommand.class));
    }

    @Test
    void anInviteeWithoutAccountRegistersOneWithAPasswordAndAccepts() {
        when(queries.handle(new GetInvitationByIdQuery(1L))).thenReturn(Optional.of(invitation(1L, person, relative)));
        when(iam.registerCaregiverAccount("care@mail.com", "Secret#123", "Ana", "Diaz", "51999"))
                .thenReturn(21L);

        controller.acceptInvitation(1L, new AcceptInvitationResource(null, "Secret#123", "Ana", "Diaz", "51999"), null);

        verify(commands).handle(new AcceptInvitationCommand(1L, 21L));
    }

    @Test
    void revokeAndResendAreOnBehalfOfTheRelativeInChargeAndOnlyForWhoManagesThePerson() {
        var found = invitation(1L, person, relative);
        when(access.requireManageInvitation(1L, 4L)).thenReturn(found);
        when(access.requireManageInvitation(1L, 7L)).thenThrow(new AssistanceAccessDeniedException("no"));

        assertThat(controller.revokeInvitation(1L, principal("4")).getBody()).isEqualTo(12L);
        assertThat(controller.resendInvitation(1L, principal("4")).getBody()).isEqualTo(13L);
        verify(commands).handle(new RevokeInvitationCommand(1L, 4L));
        verify(commands).handle(new ResendInvitationCommand(1L, 4L));

        assertThatThrownBy(() -> controller.revokeInvitation(1L, principal("7"))).isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.resendInvitation(1L, principal("7"))).isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands, never()).handle(new RevokeInvitationCommand(1L, 9L));
    }

    @Test
    void getByIdAnswersOkOrNotFound() {
        when(queries.handle(new GetInvitationByIdQuery(1L))).thenReturn(Optional.of(invitation(1L, person, relative)));
        when(queries.handle(new GetInvitationByIdQuery(2L))).thenReturn(Optional.empty());

        assertThat(controller.getInvitationById(1L).getBody().inviteeEmail()).isEqualTo("care@mail.com");
        assertThat(controller.getInvitationById(2L).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void listsInvitationsOfAManagedPersonOrOfTheCallerAndNeverOfEveryone() {
        var invitation = invitation(1L, person, relative);
        when(queries.handle(new GetInvitationsByAssistedPersonIdQuery(8L))).thenReturn(List.of(invitation));
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(4L))).thenReturn(Optional.of(relative));
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(5L))).thenReturn(Optional.empty());
        when(assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(4L))).thenReturn(List.of(person));

        assertThat(controller.getInvitations(principal("4"), 8L).getBody()).hasSize(1);
        assertThat(controller.getInvitations(principal("4"), null).getBody()).hasSize(1);
        assertThat(controller.getInvitations(principal("5"), null).getBody()).isEmpty();
        assertThatThrownBy(() -> controller.getInvitations(null, null)).isInstanceOf(AssistanceAccessDeniedException.class);

        when(access.requireManage(8L, 7L)).thenThrow(new AssistanceAccessDeniedException("no"));
        assertThatThrownBy(() -> controller.getInvitations(principal("7"), 8L))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void pendingInvitationsAreThoseOfTheCallerOnly() {
        when(queries.handle(new GetPendingInvitationsByCaregiverUserIdQuery(3L)))
                .thenReturn(List.of(invitation(1L, person, relative)));

        assertThat(controller.getPendingInvitations(3L, principal("3")).getBody()).hasSize(1);
        assertThat(controller.getPendingInvitations(null, principal("3")).getBody()).hasSize(1);
        assertThatThrownBy(() -> controller.getPendingInvitations(3L, principal("4")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }
}
