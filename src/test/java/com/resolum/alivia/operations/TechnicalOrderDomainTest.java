package com.resolum.alivia.operations;

import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AssignmentReason;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalOrderDomainTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    @Test
    void parsesAndPrintsBlockIds() {
        var slot = SlotId.parse("2026-11-05T08:00-12:00");

        assertEquals(DAY, slot.date());
        assertEquals(new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)), slot.window());
        assertEquals("2026-11-05T08:00-12:00", slot.value());
        assertThrows(IllegalArgumentException.class, () -> SlotId.parse("2026-11-05 08:00"));
        assertThrows(IllegalArgumentException.class, () -> SlotId.parse("2026-13-05T08:00-10:00"));
        assertThrows(IllegalArgumentException.class, () -> SlotId.parse("2026-11-05T22:00-02:00"));
    }

    @Test
    void keepsEveryAssignmentOfTheOrder() {
        var order = installation();

        order.assignTechnician(8L, " Luis Rojas ", "Conoce la zona", NOW.plusSeconds(60));
        var later = new SlotId(DAY.plusDays(1), window(14, 18));
        order.reassign(later, 9L, "Rosa Díaz", "Luis tiene descanso médico", NOW.plusSeconds(120));

        assertEquals(AppointmentStatus.SCHEDULED, order.getStatus());
        assertEquals(9L, order.getTechnicianId());
        assertEquals(later, order.slotId());
        var history = order.getAssignments();
        assertEquals(3, history.size());
        assertEquals(AssignmentReason.BOOKED, history.get(0).reason());
        assertEquals(LocalTime.of(8, 0), history.get(0).startTime());
        assertEquals("Luis Rojas", history.get(1).technicianName());
        assertEquals("Conoce la zona", history.get(1).note());
        assertEquals(AssignmentReason.REASSIGNED, history.get(2).reason());
        assertEquals(DAY.plusDays(1), history.get(2).scheduledDate());
    }

    @Test
    void rejectsAssignmentsThatChangeNothingOrBreakTheBlock() {
        var order = installation();

        assertThrows(IllegalArgumentException.class, () -> order.assignTechnician(7L, "Ana Pérez", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> order.reassign(order.slotId(), 7L, "Ana Pérez", "x", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> order.reassign(new SlotId(DAY, window(8, 10)), 8L, "Luis Rojas", "x", NOW));
        ReflectionTestUtils.setField(order, "status", AppointmentStatus.CANCELED);
        assertThrows(TechnicalOrderNotScheduled.class, () -> order.assignTechnician(8L, "Luis Rojas", null, NOW));
    }

    private static Appointment installation() {
        return new Appointment("draft", ServiceType.INSTALLATION, DAY, window(8, 12), 7L, "Ana Pérez",
                "María Villanueva", "984123456", null, NOW);
    }

    private static TimeRange window(int from, int to) {
        return new TimeRange(LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
