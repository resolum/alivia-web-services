package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AssignCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.MedicationsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssignMedicationResponsibleRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.ScheduleMedicationRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.SuspendMedicationRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateMedicationRequestResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MedicationsControllerTest {

    private CareActivityCommandService commandService;
    private CareActivityQueryService queryService;
    private RelativeQueryService relativeQueryService;
    private AssistedPersonQueryService assistedPersonQueryService;
    private ExternalIamService externalIamService;
    private ExternalProfilesService externalProfilesService;
    private MedicationsController controller;

    private Relative relative;
    private AssistedPerson person;
    private Caregiver caregiver;

    @BeforeEach
    void setUp() {
        commandService = mock(CareActivityCommandService.class);
        queryService = mock(CareActivityQueryService.class);
        relativeQueryService = mock(RelativeQueryService.class);
        assistedPersonQueryService = mock(AssistedPersonQueryService.class);
        externalIamService = mock(ExternalIamService.class);
        externalProfilesService = mock(ExternalProfilesService.class);

        controller = new MedicationsController(
                commandService, queryService, relativeQueryService, assistedPersonQueryService,
                externalIamService, externalProfilesService);

        relative = relative(10L);
        person = assistedPerson(20L, relative);
        caregiver = caregiver(30L, 99L, person);

        when(relativeQueryService.handle(any(GetRelativeByUserIdQuery.class))).thenReturn(Optional.of(relative));
        when(assistedPersonQueryService.handle(any(GetAssistedPersonsByRelativeIdQuery.class))).thenReturn(List.of(person));
        when(assistedPersonQueryService.handle(any(GetAssistedPersonByIdQuery.class))).thenReturn(Optional.of(person));
        when(assistedPersonQueryService.handle(any(GetAllAssistedPersonsQuery.class))).thenReturn(List.of(person));
    }

    private CareActivity medicationActivity(Long id, String title, String dose, Set<DayOfWeek> days, LocalTime start) {
        CareActivity activity = new CareActivity(person, CareActivityType.MEDICATION, title, dose);
        ReflectionTestUtils.setField(activity, "id", id);
        var assignment = activity.assign(caregiver,
                Schedule.weekly(new WeeklyPeriod(days, new TimeRange(start, start.plusMinutes(30)))),
                LocalDate.now());
        ReflectionTestUtils.setField(assignment, "id", id * 10);
        return activity;
    }

    @Test
    void listMedicationsFiltersAndEnrichesCaregiverNames() {
        CareActivity med = medicationActivity(1L, "Losartán", "50mg", EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0));
        CareActivity nonMed = new CareActivity(person, CareActivityType.HYDRATION, "Agua", "1 vaso");
        ReflectionTestUtils.setField(nonMed, "id", 2L);

        when(queryService.handle(new GetCareActivitiesByAssistedPersonIdQuery(20L))).thenReturn(List.of(med, nonMed));
        when(externalIamService.fetchPersonProfileIds(Set.of(new UserId(99L))))
                .thenReturn(Map.of(new UserId(99L), new PersonProfileId(500L)));
        when(externalProfilesService.fetchPersonNames(Set.of(new PersonProfileId(500L))))
                .thenReturn(Map.of(new PersonProfileId(500L), "Ana Torres"));

        var response = controller.listMedications(principal("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).medication()).isEqualTo("Losartán");
        assertThat(response.getBody().get(0).caregiverName()).isEqualTo("Ana Torres");
        assertThat(response.getBody().get(0).revisions().get(0).frequency()).isEqualTo("daily");
    }

    @Test
    void scheduleMedicationCreatesActivityAndAssignmentsForFrequencies() {
        when(commandService.handle(any(CreateCareActivityCommand.class))).thenReturn(55L);
        when(commandService.handle(any(AssignCareActivityCommand.class))).thenReturn(1L);

        CareActivity created = medicationActivity(55L, "Metformina", "850mg",
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0));
        when(queryService.handle(new GetCareActivityByIdQuery(55L))).thenReturn(Optional.of(created));

        var req = new ScheduleMedicationRequestResource(
                "Metformina", "850mg", List.of("09:00", "23:59"), "every_other_day",
                "2026-11-15", null, 30L, 20L, "ref-1");

        var response = controller.scheduleMedication(20L, principal("1"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo("55");
        assertThat(response.getBody().revisions().get(0).frequency()).isEqualTo("every_other_day");
        verify(commandService, times(2)).handle(any(AssignCareActivityCommand.class));
    }

    @Test
    void updateMedicationReconcilesAssignmentsAndHandlesNotFound() {
        when(queryService.handle(new GetCareActivityByIdQuery(999L))).thenReturn(Optional.empty());
        var notFound = controller.updateMedication(999L, principal("bad"),
                new UpdateMedicationRequestResource("100mg", List.of("10:00"), "weekly", "2026-11-16", ""));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        CareActivity existing = medicationActivity(7L, "Enalapril", "10mg", EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0));
        when(queryService.handle(new GetCareActivityByIdQuery(7L))).thenReturn(Optional.of(existing));

        var ok = controller.updateMedication(7L, null,
                new UpdateMedicationRequestResource("20mg", List.of("08:00", "20:00"), "weekly", "", ""));

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(commandService).handle(any(UpdateCareActivityCommand.class));
        verify(commandService).handle(any(UpdateActivityAssignmentCommand.class));
        verify(commandService).handle(any(AssignCareActivityCommand.class));

        // Now shrink from 2 times to 1 time (deactivates extra)
        existing.assign(caregiver,
                Schedule.weekly(new WeeklyPeriod(EnumSet.of(DayOfWeek.MONDAY), new TimeRange(LocalTime.of(20, 0), LocalTime.of(20, 30)))),
                LocalDate.now());
        controller.updateMedication(7L, principal("1"),
                new UpdateMedicationRequestResource("10mg", List.of("08:00"), "daily", "bad-date", ""));
        verify(commandService).handle(any(DeactivateActivityAssignmentCommand.class));
    }

    @Test
    void suspendMedicationDeactivatesAndReturnsSuspension() {
        when(queryService.handle(new GetCareActivityByIdQuery(404L))).thenReturn(Optional.empty());
        assertThat(controller.suspendMedication(404L, principal("1"), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        CareActivity med = medicationActivity(8L, "Omeprazol", "20mg", EnumSet.of(DayOfWeek.MONDAY), LocalTime.of(7, 0));
        med.deactivate();
        when(queryService.handle(new GetCareActivityByIdQuery(8L))).thenReturn(Optional.of(med));

        var response = controller.suspendMedication(8L, principal("1"),
                new SuspendMedicationRequestResource("2026-11-15", "manual"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().suspension()).isNotNull();
        verify(commandService).handle(any(DeactivateCareActivityCommand.class));
    }

    @Test
    void assignMedicationResponsibleUpdatesOrCreatesAssignment() {
        when(queryService.handle(new GetCareActivityByIdQuery(404L))).thenReturn(Optional.empty());
        assertThat(controller.assignMedicationResponsible(404L, principal("1"),
                new AssignMedicationResponsibleRequestResource(30L)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        CareActivity noAssignments = new CareActivity(person, CareActivityType.MEDICATION, "Vitamina D", "1000UI");
        ReflectionTestUtils.setField(noAssignments, "id", 11L);
        when(queryService.handle(new GetCareActivityByIdQuery(11L))).thenReturn(Optional.of(noAssignments));

        var createdAssign = controller.assignMedicationResponsible(11L, principal("1"),
                new AssignMedicationResponsibleRequestResource(30L));
        assertThat(createdAssign.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(commandService).handle(any(AssignCareActivityCommand.class));

        CareActivity withAssignment = medicationActivity(12L, "Aspirina", "100mg", EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0));
        when(queryService.handle(new GetCareActivityByIdQuery(12L))).thenReturn(Optional.of(withAssignment));

        var updatedAssign = controller.assignMedicationResponsible(12L, principal("1"),
                new AssignMedicationResponsibleRequestResource(30L));
        assertThat(updatedAssign.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(commandService).handle(any(UpdateActivityAssignmentCommand.class));
    }
}
