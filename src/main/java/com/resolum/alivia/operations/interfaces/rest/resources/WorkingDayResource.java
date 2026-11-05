package com.resolum.alivia.operations.interfaces.rest.resources;

import java.time.LocalDate;
import java.util.List;

/** A day the technician works, with the shift blocks of their schedule ("08:00"–"16:00"). */
public record WorkingDayResource(LocalDate date, List<Shift> shifts) {
    public record Shift(String start, String end) {
    }
}
