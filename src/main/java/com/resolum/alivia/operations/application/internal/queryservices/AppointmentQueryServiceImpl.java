package com.resolum.alivia.operations.application.internal.queryservices;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrdersQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.EvaluationSlotPlanner;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentQueryServiceImpl implements AppointmentQueryService {
    /** The card hold lasts seven days, so the visit must happen within the next week. */
    public static final int MAX_DAYS_AHEAD = ServiceType.EVALUATION.daysAhead();

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
        var slots = new ArrayList<AvailableSlot>();
        for (var date : bookableDates(ServiceType.EVALUATION, query.from(), query.days(), clock)) {
            slots.addAll(EvaluationSlotPlanner.plan(date, hcm.fetchTechniciansWorkingOn(date),
                    appointments.findByScheduledDateAndStatus(date, AppointmentStatus.SCHEDULED)));
        }
        return slots;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableBlock> handle(GetTechnicalAvailabilityQuery query) {
        var serviceType = query.serviceType() == null ? ServiceType.EVALUATION : query.serviceType();
        var blocks = new ArrayList<AvailableBlock>();
        for (var date : bookableDates(serviceType, query.from(), query.days(), clock)) {
            blocks.addAll(EvaluationSlotPlanner.blocks(date, serviceType, hcm.fetchTechniciansWorkingOn(date),
                    appointments.findByScheduledDateAndStatus(date, AppointmentStatus.SCHEDULED)));
        }
        return blocks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Appointment> handle(GetTechnicalOrdersQuery query) {
        return appointments.findAll().stream()
                .filter(order -> query.serviceType() == null || order.getServiceType() == query.serviceType())
                .filter(order -> query.date() == null || order.getScheduledDate().equals(query.date()))
                .filter(order -> query.status() == null || order.getStatus() == query.status())
                .filter(order -> query.draftId() == null || order.getDraftId().equals(query.draftId()))
                .sorted(Comparator.comparing(Appointment::getScheduledDate).thenComparing(Appointment::getStartTime))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Appointment> handle(GetTechnicalOrderByIdQuery query) {
        return appointments.findById(query.orderId());
    }

    /** Visits are booked from tomorrow on, so the technician knows their agenda a day ahead. */
    public static LocalDate firstBookableDate(Clock clock) {
        return LocalDate.now(clock).plusDays(1);
    }

    /** Last day an order of {@code serviceType} can be booked. */
    public static LocalDate lastBookableDate(ServiceType serviceType, Clock clock) {
        return firstBookableDate(clock).plusDays(serviceType.daysAhead() - 1L);
    }

    /** Dates from {@code from} (never before tomorrow) for {@code days} days, cut at the service horizon. */
    public static List<LocalDate> bookableDates(ServiceType serviceType, LocalDate from, int days, Clock clock) {
        var first = firstBookableDate(clock);
        var start = from == null || from.isBefore(first) ? first : from;
        var requestedLast = start.plusDays(Math.max(1, days) - 1L);
        var lastBookable = lastBookableDate(serviceType, clock);
        var last = requestedLast.isBefore(lastBookable) ? requestedLast : lastBookable;
        var dates = new ArrayList<LocalDate>();
        for (var date = start; !date.isAfter(last); date = date.plusDays(1)) dates.add(date);
        return dates;
    }
}
