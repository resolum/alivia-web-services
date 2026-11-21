package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateCaregiverCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeCaregiverAccessCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.CaregiverCommandService;
import com.resolum.alivia.assistance.domain.services.CaregiverQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CaregiversController;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateCaregiverResource;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaregiversControllerTest {

    private CaregiverCommandService commands;
    private CaregiverQueryService queries;
    private RelativeQueryService relativeQueryService;
    private AssistedPersonQueryService assistedPersonQueryService;
    private CaregiversController controller;
    private Relative relative;
    private AssistedPerson person;
    private Caregiver caregiver;

    @BeforeEach
    void setUp() {
        commands = mock(CaregiverCommandService.class);
        queries = mock(CaregiverQueryService.class);
        relativeQueryService = mock(RelativeQueryService.class);
        assistedPersonQueryService = mock(AssistedPersonQueryService.class);
        controller = new CaregiversController(commands, queries, relativeQueryService, assistedPersonQueryService);
        relative = relative(1L);
        person = assistedPerson(2L, relative);
        caregiver = caregiver(3L, 9L, person);
        when(commands.handle(any(RevokeCaregiverAccessCommand.class))).thenReturn(3L);
    }

    @Test
    void createsACaregiver() {
        when(commands.handle(new CreateCaregiverCommand(2L, 9L))).thenReturn(3L);

        var response = controller.createCaregiver(new CreateCaregiverResource(2L, 9L));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(3L);
    }

    @Test
    void revokeResolvesTheRelative() {
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(1L))).thenReturn(Optional.of(relative));

        assertThat(controller.revokeCaregiver(3L, 4L, null).getBody()).isEqualTo(3L);
        controller.revokeCaregiver(3L, null, principal("1"));
        controller.revokeCaregiver(3L, null, principal("x"));
        controller.revokeCaregiver(3L, null, null);

        verify(commands).handle(new RevokeCaregiverAccessCommand(3L, 4L));
        verify(commands, times(3)).handle(new RevokeCaregiverAccessCommand(3L, 1L));
    }

    @Test
    void listsCaregiversByPersonRelativeCaregiverOrEveryone() {
        when(queries.handle(new GetCaregiversByAssistedPersonIdQuery(2L))).thenReturn(List.of(caregiver));
        when(queries.handle(new GetCaregiversByUserIdQuery(9L))).thenReturn(List.of(caregiver));
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(1L))).thenReturn(Optional.of(relative));
        when(relativeQueryService.handle(new GetRelativeByUserIdQuery(9L))).thenReturn(Optional.empty());
        when(assistedPersonQueryService.handle(new GetAssistedPersonsByRelativeIdQuery(1L))).thenReturn(List.of(person));
        when(assistedPersonQueryService.handle(any(GetAllAssistedPersonsQuery.class))).thenReturn(List.of(person));

        assertThat(controller.getCaregivers(null, 2L).getBody()).hasSize(1);
        assertThat(controller.getCaregivers(principal("1"), null).getBody()).hasSize(1);
        assertThat(controller.getCaregivers(principal("9"), null).getBody()).hasSize(1);
        assertThat(controller.getCaregivers(principal("x"), null).getBody()).hasSize(1);
        assertThat(controller.getCaregivers(null, null).getBody()).hasSize(1);
    }

    @Test
    void readsCaregiversOfAUser() {
        when(queries.handle(new GetCaregiversByUserIdQuery(9L))).thenReturn(List.of(caregiver));
        when(queries.handle(new GetCaregiversByUserIdQuery(8L))).thenReturn(List.of());

        assertThat(controller.getCaregiverById(9L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getCaregiverById(8L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.getCaregiversByUserId(9L).getBody()).hasSize(1);
    }

    @Test
    void enrichesCaregiversWithIamAndProfileDetails() {
        var iam = mock(ExternalIamService.class);
        var profiles = mock(ExternalProfilesService.class);
        var enrichedController = new CaregiversController(
                commands, queries, relativeQueryService, assistedPersonQueryService, iam, profiles);

        UserId uid = new UserId(9L);
        PersonProfileId pid = new PersonProfileId(77L);
        when(queries.handle(new GetCaregiversByAssistedPersonIdQuery(2L))).thenReturn(List.of(caregiver));
        when(iam.fetchEmails(Set.of(uid))).thenReturn(Map.of(uid, "ana@alivia.test"));
        when(iam.fetchPersonProfileIds(Set.of(uid))).thenReturn(Map.of(uid, pid));
        when(profiles.fetchPersonProfile(pid)).thenReturn(Optional.of(new ProfilesContextFacade.PersonProfileData(
                77L, "ana@alivia.test", "https://img/ana.png", "pub-1", "+51999999999",
                "Ana", "Torres", "12345678", LocalDate.of(1990, 1, 1))));

        var body = enrichedController.getCaregivers(null, 2L).getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).fullName()).isEqualTo("Ana Torres");
        assertThat(body.get(0).email()).isEqualTo("ana@alivia.test");
        assertThat(body.get(0).phoneNumber()).isEqualTo("+51999999999");
        assertThat(body.get(0).photoUrl()).isEqualTo("https://img/ana.png");

        when(queries.handle(new GetCaregiversByAssistedPersonIdQuery(99L))).thenReturn(List.of());
        assertThat(enrichedController.getCaregivers(null, 99L).getBody()).isEmpty();
    }
}
