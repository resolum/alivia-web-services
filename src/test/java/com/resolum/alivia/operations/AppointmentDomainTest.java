package com.resolum.alivia.operations;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentDomainTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private static final TimeRange WINDOW = new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0));

    @Test
    void normalizesTheContactAndStartsScheduled() {
        var appointment = new Appointment("draft", DAY, WINDOW, 7L, " Ana Pérez ", " María ", "+51 984 123 456", "  ");

        assertEquals(AppointmentStatus.SCHEDULED, appointment.getStatus());
        assertEquals("+51984123456", appointment.getContactPhone());
        assertEquals("Ana Pérez", appointment.getTechnicianName());
        assertNull(appointment.getDirections());
        assertTrue(appointment.isAttendedBy(7L));
        assertFalse(appointment.isAttendedBy(8L));
        assertEquals(WINDOW, appointment.window());
    }

    @Test
    void rejectsIncompleteVisits() {
        assertThrows(IllegalArgumentException.class, () -> new Appointment(" ", DAY, WINDOW, 7L, "Ana", "María", "984123456", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("d", null, WINDOW, 7L, "Ana", "María", "984123456", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("d", DAY, WINDOW, null, "Ana", "María", "984123456", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("d", DAY, WINDOW, 7L, "Ana", " ", "984123456", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("d", DAY, WINDOW, 7L, "Ana", "María", "12ab", null));
        assertThrows(IllegalArgumentException.class, () -> new Appointment("d", DAY, WINDOW, 7L, "Ana", "María", "984123456", "x".repeat(301)));
    }
}
