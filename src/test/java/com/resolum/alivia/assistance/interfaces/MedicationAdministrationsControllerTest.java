package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.queries.GetActivityLogsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.Schedule;
import com.resolum.alivia.assistance.domain.services.ActivityLogCommandService;
import com.resolum.alivia.assistance.domain.services.ActivityLogQueryService;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.MedicationAdministrationsController;
import com.resolum.alivia.assistance.interfaces.rest.resources.RecordMedicationOutcomeResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MedicationAdministrationsControllerTest {

    private CareActivityQueryService careActivityQueryService;
    private AssistedPersonQueryService assistedPersonQueryService;
    private ActivityLogQueryService activityLogQueryService;
    private ActivityLogCommandService activityLogCommandService;
    private MedicationAdministrationsController controller;

    private AssistedPerson person;
    private LocalDate occurrenceDate;
    private AgendaItem agendaItem;

    @BeforeEach
    void setUp() {
        careActivityQueryService = mock(CareActivityQueryService.class);
        assistedPersonQueryService = mock(AssistedPersonQueryService.class);
        activityLogQueryService = mock(ActivityLogQueryService.class);
        activityLogCommandService = mock(ActivityLogCommandService.class);

        controller = new MedicationAdministrationsController(
                careActivityQueryService, assistedPersonQueryService,
                activityLogQueryService, activityLogCommandService);

        Relative rel = relative(1L);
        person = assistedPerson(2L, rel);
        occurrenceDate = LocalDate.of(2026, 11, 15);
        agendaItem = new AgendaItem(
                10L, 5L, "Losartán", CareActivityType.MEDICATION,
                new PersonProfileId(100L), occurrenceDate,
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(8, 30)),
                LocalDateTime.of(occurrenceDate, LocalTime.of(8, 0)),
                LocalDateTime.of(occurrenceDate, LocalTime.of(8, 30)),
                30L, true, OccurrenceStatus.SCHEDULED,
                new AgendaItem.Contact("Paciente", new PersonProfileId(101L), "Ana", "ana@alivia.test")
        );

        when(assistedPersonQueryService.handle(any(GetAllAssistedPersonsQuery.class))).thenReturn(List.of(person));
    }

    @Test
    void getAdministrationReturnsNotFoundForInvalidOrMissingOccurrence() {
        assertThat(controller.getAdministration("invalid-id").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        when(careActivityQueryService.handle(any(GetAgendaByAssistedPersonIdQuery.class))).thenReturn(List.of());
        assertThat(controller.getAdministration("5-20261115").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAdministrationMapsOccurrenceAndActivityLog() {
        CareActivity activity = new CareActivity(person, CareActivityType.MEDICATION, "Losartán", "50mg");
        ReflectionTestUtils.setField(activity, "id", 10L);
        ActivityAssignment assignment = activity.assign(null,
                Schedule.oneTime(occurrenceDate, new TimeRange(LocalTime.of(8, 0), LocalTime.of(8, 30))),
                occurrenceDate);
        ReflectionTestUtils.setField(assignment, "id", 5L);
        ActivityLog log = ActivityLog.completed(
                activity, assignment, occurrenceDate, null,
                LocalDateTime.of(occurrenceDate, LocalTime.of(8, 15)), "Tomado sin novedad");

        when(careActivityQueryService.handle(any(GetAgendaByAssistedPersonIdQuery.class))).thenReturn(List.of(agendaItem));
        when(activityLogQueryService.handle(any(GetActivityLogsByAssistedPersonIdQuery.class))).thenReturn(List.of(log));

        var response = controller.getAdministration("5-20261115");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().administrationId()).isEqualTo("5-20261115");
        assertThat(response.getBody().medication()).isEqualTo("Losartán");
        assertThat(response.getBody().outcome().reason()).isEqualTo("Tomado sin novedad");
    }

    @Test
    void recordOutcomeHandlesAdministeredOmittedAndErrors() {
        assertThat(controller.recordOutcome("bad", new RecordMedicationOutcomeResource("ADMINISTERED", "n", null), principal("1"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(careActivityQueryService.handle(any(GetAgendaByAssistedPersonIdQuery.class))).thenReturn(List.of());
        assertThat(controller.recordOutcome("5-20261115", new RecordMedicationOutcomeResource("ADMINISTERED", "n", null), principal("1"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        when(careActivityQueryService.handle(any(GetAgendaByAssistedPersonIdQuery.class))).thenReturn(List.of(agendaItem));
        when(activityLogQueryService.handle(any(GetActivityLogsByAssistedPersonIdQuery.class))).thenReturn(List.of());

        var administered = controller.recordOutcome("5-20261115",
                new RecordMedicationOutcomeResource("ADMINISTERED", "ok", null), principal("1"));
        assertThat(administered.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityLogCommandService).handle(any(CompleteActivityCommand.class));

        var omitted = controller.recordOutcome("5-20261115",
                new RecordMedicationOutcomeResource("OMITTED", "no quiso", null), principal("bad"));
        assertThat(omitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityLogCommandService).handle(any(CancelActivityOccurrenceCommand.class));
    }
}
