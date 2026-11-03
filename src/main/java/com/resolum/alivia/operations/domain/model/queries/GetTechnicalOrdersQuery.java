package com.resolum.alivia.operations.domain.model.queries;

import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;

import java.time.LocalDate;

/** Orders filtered by any of the given fields; null fields do not filter. */
public record GetTechnicalOrdersQuery(ServiceType serviceType, LocalDate date, AppointmentStatus status,
                                      String draftId) {
}
