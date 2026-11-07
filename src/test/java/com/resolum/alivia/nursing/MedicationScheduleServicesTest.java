package com.resolum.alivia.nursing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.nursing.application.internal.commandservices.MedicationScheduleCommandServiceImpl;
import com.resolum.alivia.nursing.application.internal.queryservices.MedicationScheduleQueryServiceImpl;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationScheduleNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.ChangeMedicationResponsibleCommand;
import com.resolum.alivia.nursing.domain.model.commands.CreateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.SuspendMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.UpdateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationByIdQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationsByAssistedPersonQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MedicationScheduleServices Tests")
class MedicationScheduleServicesTest {

    private static final UserId USER = new UserId(10L);
    private static final PersonProfileId PERSON = new PersonProfileId(8L);

    private final CareActivityRepository activityRepository = mock(CareActivityRepository.class);
    private final CareMembershipRepository membershipRepository = mock(CareMembershipRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MedicationScheduleCommandServiceImpl commandService = new MedicationScheduleCommandServiceImpl(
            activityRepository, membershipRepository, clock, objectMapper);
    private final MedicationScheduleQueryServiceImpl queryService = new MedicationScheduleQueryServiceImpl(
            activityRepository, membershipRepository);

    @Test
    @DisplayName("Create medication schedules successfully when user has access")
    void createMedicationSuccess() {
        var membership = new CareMembership(USER, new PersonProfileId(100L), PERSON, CareRole.RELATIVE);
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of(membership));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new CreateMedicationScheduleCommand(
                USER, PERSON, "Paracetamol", "500 mg", List.of("08:00", "20:00"),
                "daily", LocalDate.of(2026, 11, 7), null, 2L, "Receta 123");

        var result = commandService.handle(command);
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(CareActivityType.MEDICATION);
        assertThat(result.getTitle()).isEqualTo("Paracetamol 500 mg");
        assertThat(result.getActivityAssignments().getActivityAssignments()).hasSize(2);
    }

    @Test
    @DisplayName("Create medication throws when user has no access")
    void createMedicationAccessDenied() {
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of());

        var command = new CreateMedicationScheduleCommand(
                USER, PERSON, "Paracetamol", "500 mg", List.of("08:00"),
                "daily", LocalDate.of(2026, 11, 7), null, 2L, "Receta 123");

        assertThatThrownBy(() -> commandService.handle(command))
                .isInstanceOf(AssistedPersonAccessDeniedException.class);
    }

    @Test
    @DisplayName("Update medication updates indication and replaces assignments")
    void updateMedicationSuccess() {
        var membership = new CareMembership(USER, new PersonProfileId(100L), PERSON, CareRole.RELATIVE);
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of(membership));

        var activity = new CareActivity(PERSON, CareActivityType.MEDICATION, "Paracetamol 500 mg", "{\"medication\":\"Paracetamol\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);
        activity.assign(2L, Schedule.oneTime(LocalDate.now(), new TimeRange(LocalTime.of(8, 0), LocalTime.of(8, 15))), LocalDate.now());

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new UpdateMedicationScheduleCommand(
                USER, 1L, "1 g", List.of("08:00", "14:00", "20:00"), "daily", LocalDate.of(2026, 11, 8), "Ajuste dosis");

        var updated = commandService.handle(command);
        assertThat(updated.getTitle()).isEqualTo("Paracetamol 1 g");
        assertThat(updated.getActivityAssignments().getActivityAssignments()).hasSize(3);
    }

    @Test
    @DisplayName("Suspend medication deactivates activity")
    void suspendMedicationSuccess() {
        var membership = new CareMembership(USER, new PersonProfileId(100L), PERSON, CareRole.RELATIVE);
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of(membership));

        var activity = new CareActivity(PERSON, CareActivityType.MEDICATION, "Paracetamol 500 mg", "{\"medication\":\"Paracetamol\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new SuspendMedicationScheduleCommand(USER, 1L, LocalDate.of(2026, 11, 8), "Dr. Salas");
        var suspended = commandService.handle(command);
        assertThat(suspended.isActive()).isFalse();
    }

    @Test
    @DisplayName("Change responsible updates assignments caregiver")
    void changeResponsibleSuccess() {
        var membership = new CareMembership(USER, new PersonProfileId(100L), PERSON, CareRole.RELATIVE);
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of(membership));

        var activity = new CareActivity(PERSON, CareActivityType.MEDICATION, "Paracetamol 500 mg", "{\"medication\":\"Paracetamol\"}");
        ReflectionTestUtils.setField(activity, "id", 1L);
        activity.assign(2L, Schedule.oneTime(LocalDate.now(), new TimeRange(LocalTime.of(8, 0), LocalTime.of(8, 15))), LocalDate.now());

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new ChangeMedicationResponsibleCommand(USER, 1L, 99L);
        var updated = commandService.handle(command);
        assertThat(updated.getActivityAssignments().getActivityAssignments().get(0).getCaregiverId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Query medications by assisted person returns list")
    void queryMedicationsSuccess() {
        var membership = new CareMembership(USER, new PersonProfileId(100L), PERSON, CareRole.RELATIVE);
        when(membershipRepository.findActiveByUserId(USER)).thenReturn(List.of(membership));

        var activity = new CareActivity(PERSON, CareActivityType.MEDICATION, "Paracetamol 500 mg", null);
        when(activityRepository.findAllByAssistedPersonProfileIds(List.of(PERSON))).thenReturn(List.of(activity));

        var list = queryService.handle(new GetMedicationsByAssistedPersonQuery(USER, PERSON));
        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("Query medication by id not found throws exception")
    void queryMedicationNotFound() {
        when(activityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.handle(new GetMedicationByIdQuery(USER, 1L)))
                .isInstanceOf(MedicationScheduleNotFoundException.class);
    }
}
