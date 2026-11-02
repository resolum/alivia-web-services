package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.ActivityLogStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationId;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministrationStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationResult;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Medication administration domain Tests")
class MedicationAdministrationDomainTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final MedicationAdministrationId ID = new MedicationAdministrationId(12L, MONDAY);
    private static final UserId CAREGIVER = new UserId(7L);
    private static final Long CAREGIVER_ID = 1L;
    private static final LocalDateTime STARTS_AT = MONDAY.atTime(8, 0);
    private static final LocalDateTime ENDS_AT = MONDAY.atTime(9, 0);
    private static final LocalDateTime NOW = MONDAY.atTime(10, 0);
    private static final MedicationAdministration ADMINISTRATION = new MedicationAdministration(ID, 3L,
            "Losartan 50 mg", new PersonProfileId(8L), null, MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0),
            STARTS_AT, ENDS_AT, CAREGIVER_ID, CAREGIVER, null, null);

    @Test
    @DisplayName("The administration id encodes assignment and date and decodes back")
    void idRoundTrip() {
        assertThat(ID.toString()).isEqualTo("12-20261026");
        assertThat(MedicationAdministrationId.parse("12-20261026")).isEqualTo(ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "12", "12-2026102", "12-20261332", "12-20260230", "0-20261026", "-1-20261026",
            "12-20261026-1", "99999999999999999999-20261026"})
    @DisplayName("A malformed administration id is rejected")
    void malformedIdIsRejected(String value) {
        assertThatThrownBy(() -> MedicationAdministrationId.parse(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("An omission without reason is rejected")
    void omissionWithoutReasonIsRejected(String reason) {
        assertThatThrownBy(() -> command(MedicationResult.OMITTED, MONDAY.atTime(8, 15), reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("A missing result or moment is rejected")
    void missingFieldsAreRejected() {
        assertThatThrownBy(() -> command(null, MONDAY.atTime(8, 15), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(MedicationResult.ADMINISTERED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("An administration on time is logged as COMPLETED, not late, without reason, copying the schedule")
    void administeredOnTime() {
        var log = ActivityLog.medicationOutcome(ADMINISTRATION,
                command(MedicationResult.ADMINISTERED, MONDAY.atTime(8, 15), "ignored"), CAREGIVER_ID, NOW);

        assertThat(log.getAssignmentId()).isEqualTo(12L);
        assertThat(log.getDate()).isEqualTo(MONDAY);
        assertThat(log.getCareActivityId()).isEqualTo(3L);
        assertThat(log.getTitle()).isEqualTo("Losartan 50 mg");
        assertThat(log.getTimeRange().start()).isEqualTo(LocalTime.of(8, 0));
        assertThat(log.getAssignedCaregiverId()).isEqualTo(CAREGIVER_ID);
        assertThat(log.getStatus()).isEqualTo(ActivityLogStatus.COMPLETED);
        assertThat(log.getCompletedAt()).isEqualTo(MONDAY.atTime(8, 15));
        assertThat(log.getRegisteredAt()).isEqualTo(NOW);
        assertThat(log.isLate()).isFalse();
        assertThat(log.getOmissionReason()).isNull();
        assertThat(log.getCompletedBy()).isEqualTo(CAREGIVER_ID);
    }

    @Test
    @DisplayName("A moment after the end is accepted, keeps the real time and is marked late")
    void lateOmissionKeepsRealTime() {
        var log = ActivityLog.medicationOutcome(ADMINISTRATION,
                command(MedicationResult.OMITTED, MONDAY.atTime(9, 30), " Refused "), null, NOW);

        assertThat(log.getStatus()).isEqualTo(ActivityLogStatus.OMITTED);
        assertThat(log.getCompletedAt()).isEqualTo(MONDAY.atTime(9, 30));
        assertThat(log.isLate()).isTrue();
        assertThat(log.getOmissionReason()).isEqualTo("Refused");
        assertThat(log.getCompletedBy()).isNull();
    }

    @Test
    @DisplayName("A moment before the start or in the future is rejected")
    void momentOutsideAllowedRangeIsRejected() {
        assertThatThrownBy(() -> ActivityLog.medicationOutcome(ADMINISTRATION,
                command(MedicationResult.ADMINISTERED, MONDAY.atTime(7, 59), null), CAREGIVER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before the scheduled start");
        assertThatThrownBy(() -> ActivityLog.medicationOutcome(ADMINISTRATION,
                command(MedicationResult.ADMINISTERED, NOW.plusMinutes(1), null), CAREGIVER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("Statuses map from the logged status; the agenda shows OMITTED too")
    void statusMapping() {
        assertThat(MedicationAdministration.statusOf(null)).isEqualTo(MedicationAdministrationStatus.PENDING);
        assertThat(MedicationAdministration.statusOf(ActivityLogStatus.COMPLETED))
                .isEqualTo(MedicationAdministrationStatus.ADMINISTERED);
        assertThat(MedicationAdministration.statusOf(ActivityLogStatus.OMITTED))
                .isEqualTo(MedicationAdministrationStatus.OMITTED);
        assertThat(MedicationAdministration.statusOf(ActivityLogStatus.MISSED))
                .isEqualTo(MedicationAdministrationStatus.MISSED);
        assertThat(MedicationAdministration.statusOf(ActivityLogStatus.CANCELLED))
                .isEqualTo(MedicationAdministrationStatus.CANCELLED);
        assertThat(AgendaItem.resolveStatus(STARTS_AT, ENDS_AT, NOW, ActivityLogStatus.OMITTED))
                .isEqualTo(OccurrenceStatus.OMITTED);
    }

    private static RecordMedicationOutcomeCommand command(MedicationResult result, LocalDateTime occurredAt,
                                                          String reason) {
        return new RecordMedicationOutcomeCommand(CAREGIVER, ID, result, occurredAt, reason, null);
    }
}
