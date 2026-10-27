package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotEditable;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkloadCompliance;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static java.time.DayOfWeek.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkScheduleDomainTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    static List<WorkShift> splitWeekdays() {
        var shifts = new ArrayList<WorkShift>();
        for (DayOfWeek day : EnumSet.range(MONDAY, FRIDAY)) {
            shifts.add(shift(day, 13, 17));
            shifts.add(shift(day, 8, 12));
        }
        return shifts;
    }

    static WorkShift shift(DayOfWeek day, int from, int to) {
        return new WorkShift(day, LocalTime.of(from, 0), LocalTime.of(to, 0));
    }

    private static WorkSchedule draft(List<WorkShift> shifts) {
        return new WorkSchedule(3L, 1L, 5L, " Jornada Ordinaria Diurna ",
                new WorkSchedulePeriod(TODAY, null), shifts, 9L);
    }

    @Test
    void computesWeeklyHoursWorkingDaysAndOrdersShifts() {
        var schedule = draft(splitWeekdays());

        assertEquals("Jornada Ordinaria Diurna", schedule.getName());
        assertEquals(new BigDecimal("40.0"), schedule.weeklyHours());
        assertEquals(EnumSet.range(MONDAY, FRIDAY), schedule.workingDays());
        assertEquals(shift(MONDAY, 8, 12), schedule.getShifts().getFirst());
        assertEquals(WorkScheduleStatus.DRAFT, schedule.getStatus());
    }

    @Test
    void rejectsInvalidShiftsAndPeriods() {
        assertThrows(IllegalArgumentException.class, () -> shift(MONDAY, 22, 6));
        assertThrows(IllegalArgumentException.class, () -> shift(MONDAY, 8, 8));
        assertThrows(IllegalArgumentException.class,
                () -> draft(List.of(shift(MONDAY, 8, 12), shift(MONDAY, 11, 15))));
        assertThrows(IllegalArgumentException.class, () -> new WorkSchedulePeriod(TODAY, TODAY.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new WorkSchedule(3L, 1L, 5L, " ",
                new WorkSchedulePeriod(TODAY, null), List.of(), 9L));
    }

    @Test
    void allowsSameHoursOnDifferentDays() {
        assertDoesNotThrow(() -> draft(List.of(shift(MONDAY, 8, 16), shift(TUESDAY, 8, 16))));
    }

    @Test
    void activationRequiresShiftsAndDeactivationIsFinal() {
        var empty = draft(List.of());
        assertThrows(IllegalArgumentException.class, empty::activate);
        assertThrows(IllegalArgumentException.class, () -> new WorkSchedule(3L, 1L, null, "Turno",
                new WorkSchedulePeriod(TODAY, null), List.of(), 9L));

        var schedule = draft(splitWeekdays());
        schedule.activate();
        assertEquals(5L, schedule.getContractId());
        assertEquals(WorkScheduleStatus.ACTIVE, schedule.getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> schedule.update("Turno", new WorkSchedulePeriod(TODAY, null), List.of(), 9L));

        schedule.deactivate(9L);
        assertEquals(WorkScheduleViewStatus.INACTIVE, schedule.viewStatus(TODAY));
        assertThrows(WorkScheduleNotEditable.class, schedule::activate);
        assertThrows(WorkScheduleNotEditable.class, () -> schedule.deactivate(9L));
    }

    @Test
    void derivesViewStatusFromDates() {
        var schedule = new WorkSchedule(3L, 1L, 5L, "Campaña", new WorkSchedulePeriod(TODAY.plusDays(1),
                TODAY.plusDays(30)), splitWeekdays(), 9L);
        assertEquals(WorkScheduleViewStatus.DRAFT, schedule.viewStatus(TODAY));

        schedule.activate();
        assertEquals(WorkScheduleViewStatus.SCHEDULED, schedule.viewStatus(TODAY));
        assertEquals(WorkScheduleViewStatus.ACTIVE, schedule.viewStatus(TODAY.plusDays(1)));
        assertEquals(WorkScheduleViewStatus.FINISHED, schedule.viewStatus(TODAY.plusDays(31)));
        assertTrue(schedule.isInEffectOn(TODAY.plusDays(30)));
    }

    @Test
    void coversVisitsInsideASingleShift() {
        var schedule = draft(splitWeekdays());

        assertTrue(schedule.covers(MONDAY, new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0))));
        assertFalse(schedule.covers(MONDAY, new TimeRange(LocalTime.of(11, 0), LocalTime.of(14, 0))));
        assertFalse(schedule.covers(SATURDAY, new TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0))));
    }

    @Test
    void periodFitsWithinContract() {
        var contract = new ContractPeriod(TODAY, TODAY.plusMonths(6));

        assertTrue(new WorkSchedulePeriod(TODAY, null).fitsWithin(contract));
        assertTrue(new WorkSchedulePeriod(TODAY.plusDays(1), TODAY.plusMonths(6)).fitsWithin(contract));
        assertFalse(new WorkSchedulePeriod(TODAY.minusDays(1), null).fitsWithin(contract));
        assertFalse(new WorkSchedulePeriod(TODAY, TODAY.plusMonths(7)).fitsWithin(contract));
    }

    @Test
    void comparesConfiguredHoursWithContract() {
        var forty = new BigDecimal("40.0");

        assertEquals(WorkloadCompliance.COMPLIANT, WorkloadCompliance.of(forty, 40));
        assertEquals(WorkloadCompliance.UNDER, WorkloadCompliance.of(forty, 48));
        assertEquals(WorkloadCompliance.OVER, WorkloadCompliance.of(forty, 36));
        assertEquals(WorkloadCompliance.NO_REFERENCE, WorkloadCompliance.of(forty, null));
    }
}
