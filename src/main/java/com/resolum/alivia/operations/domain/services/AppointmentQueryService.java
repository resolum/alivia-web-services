package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetMaintenanceOrdersByIncidentIdsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrdersQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianOrdersQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianScheduleQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.WorkingDay;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;

import java.util.List;
import java.util.Optional;

public interface AppointmentQueryService {
    List<AvailableSlot> handle(GetAvailableSlotsQuery query);

    List<AvailableBlock> handle(GetTechnicalAvailabilityQuery query);

    List<Appointment> handle(GetTechnicalOrdersQuery query);

    Optional<Appointment> handle(GetTechnicalOrderByIdQuery query);

    List<Appointment> handle(GetMaintenanceOrdersByIncidentIdsQuery query);

    List<Appointment> handle(GetTechnicianOrdersQuery query);

    Optional<Long> handle(GetTechnicianIdByUserIdQuery query);

    /** Working days of the technician's schedule in the range; days without shifts are left out. */
    List<WorkingDay> handle(GetTechnicianScheduleQuery query);
}
