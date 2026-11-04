package com.resolum.alivia.profiles.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Horario semanal de atención comercial")
public record BusinessHours(
        @Schema(description = "Lista de periodos semanales de atención")
        List<WeeklyPeriod> periods) {
    public BusinessHours(List<WeeklyPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            throw new IllegalArgumentException("Periods cannot be null or empty");
        }
        this.periods = periods;
    }

    public boolean isOpenAt(DayOfWeek day, LocalTime time) {
        if (day == null || time == null) {
            return false; // o lanzar IllegalArgumentException / Objects.requireNonNull según tu regla de
                          // dominio
        }

        return periods.stream()
                .filter(period -> period.days().contains(day))
                .anyMatch(period -> period.range().contains(time));
    }

}
