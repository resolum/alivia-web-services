package com.resolum.alivia.shared;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyPeriodTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static WeeklyPeriod period(String start, String end, DayOfWeek... days) {
        return new WeeklyPeriod(Set.of(days), range(start, end));
    }

    // ---------- construcción ----------

    @Test
    void rejectsNullDays() {
        var range = range("09:00", "17:00");

        var ex = assertThrows(NullPointerException.class, () -> new WeeklyPeriod(null, range));

        assertEquals("Days cannot be null.", ex.getMessage());
    }

    @Test
    void rejectsNullRange() {
        Set<DayOfWeek> days = Set.of(DayOfWeek.MONDAY);

        var ex = assertThrows(NullPointerException.class, () -> new WeeklyPeriod(days, null));

        assertEquals("Time range cannot be null.", ex.getMessage());
    }

    @Test
    void rejectsEmptyDays() {
        Set<DayOfWeek> days = Set.of();
        var range = range("09:00", "17:00");

        var ex = assertThrows(IllegalArgumentException.class, () -> new WeeklyPeriod(days, range));

        assertEquals("Days cannot be empty.", ex.getMessage());
    }

    @Test
    void keepsDaysAndRange() {
        var period = period("09:00", "17:00", DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), period.days());
        assertEquals(range("09:00", "17:00"), period.range());
    }

    @Test
    void makesDefensiveCopyOfDays() {
        Set<DayOfWeek> mutable = new HashSet<>(Set.of(DayOfWeek.MONDAY));
        var period = new WeeklyPeriod(mutable, range("09:00", "17:00"));

        mutable.add(DayOfWeek.TUESDAY);

        assertEquals(Set.of(DayOfWeek.MONDAY), period.days());
    }

    @Test
    void daysAreImmutable() {
        var days = period("09:00", "17:00", DayOfWeek.MONDAY).days();

        assertThrows(UnsupportedOperationException.class, () -> days.add(DayOfWeek.TUESDAY));
    }

    // ---------- occursOn ----------

    @ParameterizedTest(name = "{0} ocurre: {1}")
    @CsvSource({
            "2026-10-26, true",   // lunes
            "2026-10-30, true",   // viernes
            "2026-10-27, false",  // martes
            "2026-10-24, false"   // sábado
    })
    void occursOnlyOnConfiguredDays(String date, boolean expected) {
        var period = period("09:00", "17:00", DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        assertEquals(expected, period.occursOn(LocalDate.parse(date)));
    }

    @Test
    void occursOnRejectsNull() {
        var period = period("09:00", "17:00", DayOfWeek.MONDAY);

        var ex = assertThrows(NullPointerException.class, () -> period.occursOn(null));

        assertEquals("Date cannot be null.", ex.getMessage());
    }

    // ---------- overlapsWith ----------

    @Test
    void overlapsWhenSameDayAndOverlappingHours() {
        var a = period("09:00", "12:00", DayOfWeek.MONDAY);
        var b = period("11:00", "14:00", DayOfWeek.MONDAY);

        assertTrue(a.overlapsWith(b));
        assertTrue(b.overlapsWith(a));
    }

    @Test
    void overlapsWhenAnyDayInCommon() {
        var a = period("09:00", "12:00", DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        var b = period("10:00", "11:00", DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        assertTrue(a.overlapsWith(b));
    }

    @Test
    void doesNotOverlapWhenAdjacentHours() {
        var a = period("09:00", "12:00", DayOfWeek.MONDAY);
        var b = period("12:00", "14:00", DayOfWeek.MONDAY);

        assertFalse(a.overlapsWith(b));
    }

    @Test
    void doesNotOverlapOnDifferentDays() {
        var a = period("09:00", "12:00", DayOfWeek.MONDAY);
        var b = period("09:00", "12:00", DayOfWeek.TUESDAY);

        assertFalse(a.overlapsWith(b));
    }

    @Test
    void overnightPeriodOverlapsNextDayMorning() {
        var overnight = period("22:00", "06:00", DayOfWeek.MONDAY);
        var tuesdayMorning = period("05:00", "07:00", DayOfWeek.TUESDAY);

        assertTrue(overnight.overlapsWith(tuesdayMorning));
        assertTrue(tuesdayMorning.overlapsWith(overnight));
    }

    @Test
    void overnightPeriodDoesNotOverlapSameDayMorning() {
        var overnight = period("22:00", "06:00", DayOfWeek.MONDAY);
        var mondayMorning = period("05:00", "07:00", DayOfWeek.MONDAY);

        assertFalse(overnight.overlapsWith(mondayMorning));
    }

    @Test
    void overlapWrapsAroundFromSundayToMonday() {
        var sundayNight = period("22:00", "06:00", DayOfWeek.SUNDAY);
        var mondayMorning = period("05:00", "07:00", DayOfWeek.MONDAY);

        assertTrue(sundayNight.overlapsWith(mondayMorning));
        assertTrue(mondayMorning.overlapsWith(sundayNight));
    }

    @Test
    void overlapsWithRejectsNull() {
        var period = period("09:00", "17:00", DayOfWeek.MONDAY);

        var ex = assertThrows(NullPointerException.class, () -> period.overlapsWith(null));

        assertEquals("Weekly period cannot be null.", ex.getMessage());
    }

    // ---------- igualdad ----------

    @Test
    void equalityIsBasedOnDaysAndRange() {
        var a = period("09:00", "17:00", DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        var b = period("09:00", "17:00", DayOfWeek.TUESDAY, DayOfWeek.MONDAY);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, period("09:00", "18:00", DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        assertNotEquals(a, period("09:00", "17:00", DayOfWeek.MONDAY));
    }
}
