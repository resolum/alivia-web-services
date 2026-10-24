package com.resolum.alivia.shared;


import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRangeTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    // ---------- construcción ----------

    @Test
    void rejectsNullStart() {
        var ex = assertThrows(NullPointerException.class, () -> new TimeRange(null, LocalTime.NOON));

        assertEquals("Start time cannot be null.", ex.getMessage());
    }

    @Test
    void rejectsNullEnd() {
        var ex = assertThrows(NullPointerException.class, () -> new TimeRange(LocalTime.NOON, null));

        assertEquals("End time cannot be null.", ex.getMessage());
    }

    @Test
    void rejectsSameStartAndEnd() {
        var ex = assertThrows(IllegalArgumentException.class, () -> range("09:00", "09:00"));

        assertEquals("Start and end times cannot be the same.", ex.getMessage());
    }

    // ---------- crossesMidnight / duration ----------

    @ParameterizedTest(name = "{0}-{1} cruza medianoche: {2}")
    @CsvSource({
            "09:00, 17:00, false",
            "00:00, 23:59, false",
            "22:00, 06:00, true",
            "23:59, 00:00, true"
    })
    void detectsMidnightCrossing(String start, String end, boolean expected) {
        assertEquals(expected, range(start, end).crossesMidnight());
    }

    @ParameterizedTest(name = "{0}-{1} dura {2} minutos")
    @CsvSource({
            "09:00, 17:00, 480",
            "22:00, 06:00, 480",
            "23:30, 00:30, 60",
            "00:00, 23:59, 1439"
    })
    void calculatesDuration(String start, String end, long minutes) {
        assertEquals(Duration.ofMinutes(minutes), range(start, end).duration());
    }

    // ---------- contains(LocalTime) ----------

    @ParameterizedTest(name = "09:00-17:00 contiene {0}: {1}")
    @CsvSource({
            "08:59, false",
            "09:00, true",
            "12:00, true",
            "16:59, true",
            "17:00, false",
            "23:00, false"
    })
    void containsTimeInSameDayRange(String time, boolean expected) {
        assertEquals(expected, range("09:00", "17:00").contains(LocalTime.parse(time)));
    }

    @ParameterizedTest(name = "22:00-06:00 contiene {0}: {1}")
    @CsvSource({
            "21:59, false",
            "22:00, true",
            "23:59, true",
            "00:00, true",
            "05:59, true",
            "06:00, false",
            "12:00, false"
    })
    void containsTimeInOvernightRange(String time, boolean expected) {
        assertEquals(expected, range("22:00", "06:00").contains(LocalTime.parse(time)));
    }

    @Test
    void containsTimeRejectsNull() {
        var range = range("09:00", "17:00");

        var ex = assertThrows(NullPointerException.class, () -> range.contains((LocalTime) null));

        assertEquals("Time cannot be null.", ex.getMessage());
    }

    // ---------- contains(TimeRange) ----------

    @ParameterizedTest(name = "08:00-18:00 contiene {0}-{1}: {2}")
    @CsvSource({
            "09:00, 17:00, true",
            "08:00, 18:00, true",
            "07:00, 17:00, false",
            "09:00, 19:00, false",
            "19:00, 20:00, false"
    })
    void containsAnotherRangeInSameDay(String start, String end, boolean expected) {
        assertEquals(expected, range("08:00", "18:00").contains(range(start, end)));
    }

    @Test
    void overnightRangeContainsInnerOvernightRange() {
        assertTrue(range("22:00", "06:00").contains(range("23:00", "05:00")));
        assertFalse(range("23:00", "05:00").contains(range("22:00", "06:00")));
    }

    @Test
    void containsRangeRejectsNull() {
        var range = range("09:00", "17:00");

        var ex = assertThrows(NullPointerException.class, () -> range.contains((TimeRange) null));

        assertEquals("Time range cannot be null.", ex.getMessage());
    }

    // ---------- overlaps ----------

    @ParameterizedTest(name = "09:00-12:00 se solapa con {0}-{1}: {2}")
    @CsvSource({
            "11:00, 14:00, true",
            "08:00, 10:00, true",
            "09:00, 12:00, true",
            "10:00, 11:00, true",
            "12:00, 14:00, false",
            "07:00, 09:00, false",
            "13:00, 15:00, false"
    })
    void detectsOverlapInSameDay(String start, String end, boolean expected) {
        var base = range("09:00", "12:00");
        var other = range(start, end);

        assertEquals(expected, base.overlaps(other));
        assertEquals(expected, other.overlaps(base));
    }

    @Test
    void detectsOverlapWithOvernightRange() {
        assertTrue(range("22:00", "06:00").overlaps(range("23:00", "01:00")));
        assertFalse(range("22:00", "06:00").overlaps(range("08:00", "10:00")));
    }

    @Test
    void overlapsRejectsNull() {
        var range = range("09:00", "17:00");

        var ex = assertThrows(NullPointerException.class, () -> range.overlaps(null));

        assertEquals("Time range cannot be null.", ex.getMessage());
    }

    // ---------- igualdad ----------

    @Test
    void equalityIsBasedOnStartAndEnd() {
        assertEquals(range("09:00", "17:00"), range("09:00", "17:00"));
        assertEquals(range("09:00", "17:00").hashCode(), range("09:00", "17:00").hashCode());
        assertNotEquals(range("09:00", "17:00"), range("09:00", "18:00"));
    }
}