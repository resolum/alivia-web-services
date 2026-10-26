package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.commandservices.MedicationAdministrationCommandServiceImpl;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.application.internal.queryservices.MedicationAdministrationQueryServiceImpl;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException;
import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Medication administration services Tests")
class MedicationAdministrationServicesTest {

    // Monday 2026-10-26 10:00 in America/Lima (UTC-5).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final MedicationAdministrationId ID = new MedicationAdministrationId(12L, MONDAY);
    private static final UserId CAREGIVER = new UserId(7L);
    private static final UserId OTHER_CAREGIVER = new UserId(9L);
    private static final UserId RELATIVE = new UserId(5L);
    private static final UserId STRANGER = new UserId(30L);
    private static final Long CAREGIVER_ID = 1L;
    private static final PersonProfileId PERSON = new PersonProfileId(8L);
    private static final PersonProfileId CAREGIVER_PROFILE = new PersonProfileId(70L);

    @Mock
    private CareActivityRepository careActivityRepository;
    @Mock
    private CareMembershipRepository careMembershipRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private ExternalProfilesService externalProfilesService;

    private MedicationAdministrationQueryServiceImpl queryService;
    private MedicationAdministrationCommandServiceImpl commandService;

    @BeforeEach
    void setUp() {
        queryService = new MedicationAdministrationQueryServiceImpl(careActivityRepository, careMembershipRepository,
                activityLogRepository, externalProfilesService);
        commandService = new MedicationAdministrationCommandServiceImpl(queryService, activityLogRepository, CLOCK);
    }

    @Test
    @DisplayName("The assigned caregiver gets the medication, schedule, responsible and PENDING status")
    void assignedCaregiverGetsAdministration() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(CAREGIVER, CareRole.CAREGIVER);
        givenAssignedCaregiverMembership();
        when(externalProfilesService.fetchPersonNames(Set.of(PERSON, CAREGIVER_PROFILE)))
                .thenReturn(Map.of(PERSON, "Maria Lopez", CAREGIVER_PROFILE, "Ana Torres"));

        var administration = queryService.handle(new GetMedicationAdministrationQuery(CAREGIVER, ID));

        assertThat(administration.medication()).isEqualTo("Losartan 50 mg");
        assertThat(administration.careActivityId()).isEqualTo(3L);
        assertThat(administration.assistedPersonName()).isEqualTo("Maria Lopez");
        assertThat(administration.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(administration.endsAt()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(administration.caregiverId()).isEqualTo(CAREGIVER_ID);
        assertThat(administration.caregiverUserId()).isEqualTo(CAREGIVER);
        assertThat(administration.responsibleName()).isEqualTo("Ana Torres");
        assertThat(administration.status()).isEqualTo(MedicationAdministrationStatus.PENDING);
    }

    @Test
    @DisplayName("A relative of the assisted person can read it")
    void relativeGetsAdministration() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(RELATIVE, CareRole.RELATIVE);

        assertThat(queryService.handle(new GetMedicationAdministrationQuery(RELATIVE, ID)).id()).isEqualTo(ID);
    }

    @Test
    @DisplayName("A caregiver who is not assigned, or an unlinked user, is denied")
    void otherUsersAreDenied() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(OTHER_CAREGIVER, CareRole.CAREGIVER);

        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(OTHER_CAREGIVER, ID)))
                .isInstanceOf(MedicationAdministrationAccessDeniedException.class);
        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(STRANGER, ID)))
                .isInstanceOf(MedicationAdministrationAccessDeniedException.class);
    }

    @Test
    @DisplayName("A missing assignment, a non medication or inactive activity or a date without occurrence is not found")
    void notFoundCases() {
        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(CAREGIVER, ID)))
                .isInstanceOf(MedicationAdministrationNotFoundException.class);

        givenActivity(CareActivityType.MEAL);
        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(CAREGIVER, ID)))
                .isInstanceOf(MedicationAdministrationNotFoundException.class);

        var inactive = givenActivity(CareActivityType.MEDICATION);
        ReflectionTestUtils.setField(inactive, "status", ActivityStatus.INACTIVE);
        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(CAREGIVER, ID)))
                .isInstanceOf(MedicationAdministrationNotFoundException.class);

        givenActivity(CareActivityType.MEDICATION);
        var tuesday = new MedicationAdministrationId(12L, MONDAY.plusDays(1));
        assertThatThrownBy(() -> queryService.handle(new GetMedicationAdministrationQuery(CAREGIVER, tuesday)))
                .isInstanceOf(MedicationAdministrationNotFoundException.class);
    }

    @Test
    @DisplayName("The assigned caregiver records an administration and is stored as completedBy")
    void caregiverRecordsOutcome() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(CAREGIVER, CareRole.CAREGIVER);
        givenAssignedCaregiverMembership();
        givenSaveReturnsLog();

        var administration = commandService.handle(command(CAREGIVER, MedicationResult.ADMINISTERED, 8, 15, null));

        var saved = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(saved.capture());
        assertThat(saved.getValue().getCompletedBy()).isEqualTo(CAREGIVER_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(ActivityLogStatus.COMPLETED);
        assertThat(saved.getValue().getRegisteredAt()).isEqualTo(MONDAY.atTime(10, 0));
        assertThat(saved.getValue().isLate()).isFalse();
        assertThat(administration.status()).isEqualTo(MedicationAdministrationStatus.ADMINISTERED);
    }

    @Test
    @DisplayName("A relative records a late omission: completedBy stays null and late is true")
    void relativeRecordsLateOmission() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(RELATIVE, CareRole.RELATIVE);
        givenSaveReturnsLog();

        var administration = commandService.handle(command(RELATIVE, MedicationResult.OMITTED, 9, 30, "Refused"));

        assertThat(administration.status()).isEqualTo(MedicationAdministrationStatus.OMITTED);
        assertThat(administration.outcome().getCompletedBy()).isNull();
        assertThat(administration.outcome().isLate()).isTrue();
        assertThat(administration.outcome().getCompletedAt()).isEqualTo(MONDAY.atTime(9, 30));
    }

    @Test
    @DisplayName("A closed administration answers conflict without saving, MISSED included")
    void closedAdministrationIsConflict() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(CAREGIVER, CareRole.CAREGIVER);
        var missed = BeanUtils.instantiateClass(ActivityLog.class);
        ReflectionTestUtils.setField(missed, "status", ActivityLogStatus.MISSED);
        when(activityLogRepository.findByAssignmentIdAndDate(12L, MONDAY)).thenReturn(Optional.of(missed));

        assertThatThrownBy(() -> commandService.handle(command(CAREGIVER, MedicationResult.ADMINISTERED, 8, 15, null)))
                .isInstanceOf(MedicationOutcomeAlreadyRecordedException.class);
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("A concurrent duplicate rejected by the unique constraint answers conflict")
    void uniqueViolationIsConflict() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(CAREGIVER, CareRole.CAREGIVER);
        when(activityLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> commandService.handle(command(CAREGIVER, MedicationResult.ADMINISTERED, 8, 15, null)))
                .isInstanceOf(MedicationOutcomeAlreadyRecordedException.class);
    }

    @Test
    @DisplayName("A user without permission cannot record")
    void strangerCannotRecord() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(OTHER_CAREGIVER, CareRole.CAREGIVER);

        assertThatThrownBy(() -> commandService.handle(command(OTHER_CAREGIVER, MedicationResult.ADMINISTERED, 8, 15, null)))
                .isInstanceOf(MedicationAdministrationAccessDeniedException.class);
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("A future moment is rejected before looking anything up")
    void futureMomentIsRejectedFirst() {
        assertThatThrownBy(() -> commandService.handle(command(CAREGIVER, MedicationResult.ADMINISTERED, 10, 1, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(careActivityRepository, activityLogRepository);
    }

    @Test
    @DisplayName("A moment before the start is rejected after the duplicate check")
    void momentBeforeStartIsRejected() {
        givenActivity(CareActivityType.MEDICATION);
        givenMemberships(CAREGIVER, CareRole.CAREGIVER);

        assertThatThrownBy(() -> commandService.handle(command(CAREGIVER, MedicationResult.ADMINISTERED, 7, 30, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before the scheduled start");
        verify(activityLogRepository, never()).save(any());
    }

    // Activity 3 with assignment 12: caregiver membership 1, Monday 08:00-09:00.
    private CareActivity givenActivity(CareActivityType type) {
        var activity = new CareActivity(PERSON, type, "Losartan 50 mg", null);
        ReflectionTestUtils.setField(activity, "id", 3L);
        var assignment = activity.assign(CAREGIVER_ID,
                Schedule.oneTime(MONDAY, new TimeRange(LocalTime.of(8, 0), LocalTime.of(9, 0))), MONDAY);
        ReflectionTestUtils.setField(assignment, "id", 12L);
        when(careActivityRepository.findByAssignmentId(12L)).thenReturn(Optional.of(activity));
        return activity;
    }

    private void givenMemberships(UserId user, CareRole role) {
        when(careMembershipRepository.findActiveByUserId(user)).thenReturn(List.of(membership(user, role)));
    }

    private void givenAssignedCaregiverMembership() {
        when(careMembershipRepository.findActiveByAssistedPersonProfileIds(Set.of(PERSON)))
                .thenReturn(List.of(membership(CAREGIVER, CareRole.CAREGIVER)));
    }

    private void givenSaveReturnsLog() {
        when(activityLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // Membership ids: the caregiver of the assignment is 1, any other user gets a different id.
    private static CareMembership membership(UserId user, CareRole role) {
        var profile = user.equals(CAREGIVER) ? CAREGIVER_PROFILE : new PersonProfileId(user.userId() * 10);
        var membership = new CareMembership(user, profile, PERSON, role);
        ReflectionTestUtils.setField(membership, "id", user.equals(CAREGIVER) ? CAREGIVER_ID : user.userId() * 100);
        return membership;
    }

    private static RecordMedicationOutcomeCommand command(UserId user, MedicationResult result, int hour, int minute,
                                                          String reason) {
        return new RecordMedicationOutcomeCommand(user, ID, result, LocalDateTime.of(MONDAY, LocalTime.of(hour, minute)),
                reason, null);
    }
}
