package com.resolum.alivia.profiles;

import com.resolum.alivia.profiles.domain.model.valueobjects.*;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProfileValueObjectsTest {

    @Test
    void birthDateValidatesCorrectly() {
        var date = LocalDate.of(1995, 5, 20);
        var birthDate = new BirthDate(date);

        assertEquals(date, birthDate.date());
        assertThrows(IllegalArgumentException.class, () -> new BirthDate(null));
    }

    @Test
    void businessNameValidatesCorrectly() {
        var name = new BusinessName("Alivia Salud");

        assertEquals("Alivia Salud", name.businessName());
        assertThrows(IllegalArgumentException.class, () -> new BusinessName(null));
        assertThrows(IllegalArgumentException.class, () -> new BusinessName(""));
        assertThrows(IllegalArgumentException.class, () -> new BusinessName("   "));
    }

    @Test
    void dniValidatesCorrectly() {
        var dni = new Dni("12345678");

        assertEquals("12345678", dni.dni());
        assertThrows(IllegalArgumentException.class, () -> new Dni(null));
        assertThrows(IllegalArgumentException.class, () -> new Dni(""));
        assertThrows(IllegalArgumentException.class, () -> new Dni("1234567")); // 7 digits
        assertThrows(IllegalArgumentException.class, () -> new Dni("123456789")); // 9 digits
        assertThrows(IllegalArgumentException.class, () -> new Dni("1234567A")); // letters
    }

    @Test
    void legalNameValidatesCorrectly() {
        var legal = new LegalName("Alivia Salud S.A.C.");

        assertEquals("Alivia Salud S.A.C.", legal.name());
        assertThrows(IllegalArgumentException.class, () -> new LegalName(null));
        assertThrows(IllegalArgumentException.class, () -> new LegalName(""));
        assertThrows(IllegalArgumentException.class, () -> new LegalName("A")); // < 2 chars
        assertThrows(IllegalArgumentException.class, () -> new LegalName("A".repeat(501))); // > 500 chars
    }

    @Test
    void personNameValidatesCorrectly() {
        var person = new PersonName("Juan", "Perez");

        assertEquals("Juan", person.firstName());
        assertEquals("Perez", person.lastName());
        assertEquals("Juan Perez", person.getFullName());

        assertThrows(IllegalArgumentException.class, () -> new PersonName(null, "Perez"));
        assertThrows(IllegalArgumentException.class, () -> new PersonName("  ", "Perez"));
        assertThrows(IllegalArgumentException.class, () -> new PersonName("Juan", null));
        assertThrows(IllegalArgumentException.class, () -> new PersonName("Juan", "   "));
    }

    @Test
    void rucValidatesCorrectly() {
        var ruc = new Ruc("20123456789");

        assertEquals("20123456789", ruc.ruc());
        assertThrows(IllegalArgumentException.class, () -> new Ruc(null));
        assertThrows(IllegalArgumentException.class, () -> new Ruc(""));
        assertThrows(IllegalArgumentException.class, () -> new Ruc("1234567890")); // 10 digits
        assertThrows(IllegalArgumentException.class, () -> new Ruc("123456789012")); // 12 digits
        assertThrows(IllegalArgumentException.class, () -> new Ruc("2012345678A")); // non-digits
    }

    @Test
    void businessHoursValidatesAndChecksOpenStatus() {
        var range = new TimeRange(LocalTime.of(9, 0), LocalTime.of(18, 0));
        var period = new WeeklyPeriod(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), range);
        var hours = new BusinessHours(List.of(period));

        assertEquals(1, hours.periods().size());

        // Open on Monday at 10:00
        assertTrue(hours.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(10, 0)));
        // Closed on Monday at 19:00
        assertFalse(hours.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(19, 0)));
        // Closed on Sunday
        assertFalse(hours.isOpenAt(DayOfWeek.SUNDAY, LocalTime.of(10, 0)));
        // Null checks
        assertFalse(hours.isOpenAt(null, LocalTime.of(10, 0)));
        assertFalse(hours.isOpenAt(DayOfWeek.MONDAY, null));

        assertThrows(IllegalArgumentException.class, () -> new BusinessHours(null));
        assertThrows(IllegalArgumentException.class, () -> new BusinessHours(Collections.emptyList()));
    }
}
