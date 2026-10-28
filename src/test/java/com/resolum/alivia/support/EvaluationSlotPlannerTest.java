package com.resolum.alivia.support;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.support.domain.services.EvaluationSlotPlanner;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationSlotPlannerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    @Test
    void cutsTwoHourWindowsThatFitWholeInEachShift() {
        var windows = EvaluationSlotPlanner.windowsWithin(List.of(range(8, 12), range(13, 18), range(23, 1)));

        assertEquals(List.of(range(8, 10), range(10, 12), range(13, 15), range(15, 17)), windows);
    }

    @Test
    void assignsTheLeastBusyFreeTechnicianToEachWindow() {
        var ana = new TechnicianAvailability(1L, "Ana Pérez", List.of(range(8, 12)));
        var luis = new TechnicianAvailability(2L, "Luis Rojas", List.of(range(10, 14)));
        var anaAt8 = visit(1L, 8);

        var slots = EvaluationSlotPlanner.plan(DAY, List.of(ana, luis), List.of(anaAt8));

        assertEquals(List.of(range(10, 12), range(12, 14)), slots.stream().map(slot -> slot.window()).toList());
        // Both can take 10:00; Luis has no visits that day, so he gets it.
        assertEquals(2L, slots.getFirst().technicianId());
        assertEquals(2, slots.getFirst().availableTechnicians());
        assertTrue(EvaluationSlotPlanner.plan(DAY, List.of(ana), List.of(anaAt8, visit(1L, 10))).isEmpty());
    }

    private static Appointment visit(Long technicianId, int hour) {
        return new Appointment("draft-" + hour, DAY, range(hour, hour + 2), technicianId, "Técnico",
                "María Villanueva", "+51984123456", null);
    }

    private static TimeRange range(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to % 24, 0));
    }
}
