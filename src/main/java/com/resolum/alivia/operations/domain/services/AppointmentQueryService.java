package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;

import java.util.List;

public interface AppointmentQueryService {
    List<AvailableSlot> handle(GetAvailableSlotsQuery query);
}
