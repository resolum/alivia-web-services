package com.resolum.alivia.support.domain.services;

import com.resolum.alivia.support.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.support.domain.model.valueobjects.AvailableSlot;

import java.util.List;

public interface AppointmentQueryService {
    List<AvailableSlot> handle(GetAvailableSlotsQuery query);
}
