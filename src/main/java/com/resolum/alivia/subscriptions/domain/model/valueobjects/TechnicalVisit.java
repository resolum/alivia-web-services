package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A technical visit of the contracting request as Subscriptions reads it from Operations.
 *
 * @param result the technician's decision when the visit closed; null while it is scheduled
 * @param notes  what the technician wrote when closing it, such as the adaptation the home needs
 */
public record TechnicalVisit(String serviceType, boolean completed, LocalDate date, LocalTime startTime,
                             LocalTime endTime, String technicianName, String directions, String result,
                             String notes) {
    public static final String EVALUATION = "EVALUATION";
    public static final String INSTALLATION = "INSTALLATION";

    public boolean isEvaluation() {
        return EVALUATION.equals(serviceType);
    }

    public boolean isInstallation() {
        return INSTALLATION.equals(serviceType);
    }
}
