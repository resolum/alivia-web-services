package com.resolum.alivia.operations.application.internal.queryservices;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.EvaluationSlotPlanner;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AppointmentQueryServiceImpl implements AppointmentQueryService {
    /** The card hold lasts seven days, so the visit must happen within the next week. */
    public static final int MAX_DAYS_AHEAD = 7;

    private final AppointmentRepository appointments;
    private final ExternalHcmService hcm;
    private final Clock clock;

    public AppointmentQueryServiceImpl(AppointmentRepository appointments, ExternalHcmService hcm, Clock clock) {
        this.appointments = appointments;
        this.hcm = hcm;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableSlot> handle(GetAvailableSlotsQuery query) {
        var first = firstBookableDate(clock);
        var from = query.from() == null || query.from().isBefore(first) ? first : query.from();
        var requestedLast = from.plusDays(Math.max(1, query.days()) - 1L);
        var lastBookable = first.plusDays(MAX_DAYS_AHEAD - 1L);
        var last = requestedLast.isBefore(lastBookable) ? requestedLast : lastBookable;
        var slots = new ArrayList<AvailableSlot>();
        for (var date = from; !date.isAfter(last); date = date.plusDays(1)) {
            slots.addAll(EvaluationSlotPlanner.plan(date, hcm.fetchTechniciansWorkingOn(date),
                    appointments.findByScheduledDateAndStatus(date, AppointmentStatus.SCHEDULED)));
        }
        return slots;
    }

    /** Visits are booked from tomorrow on, so the technician knows their agenda a day ahead. */
    public static LocalDate firstBookableDate(Clock clock) {
        return LocalDate.now(clock).plusDays(1);
    }
}
