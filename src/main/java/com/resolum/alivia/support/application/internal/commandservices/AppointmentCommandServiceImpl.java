package com.resolum.alivia.support.application.internal.commandservices;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.support.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.support.domain.exceptions.AppointmentAlreadyExists;
import com.resolum.alivia.support.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.support.domain.exceptions.DraftNotReadyForAppointment;
import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.support.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.support.domain.services.AppointmentCommandService;
import com.resolum.alivia.support.domain.services.EvaluationSlotPlanner;
import com.resolum.alivia.support.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AppointmentCommandServiceImpl implements AppointmentCommandService {
    private final AppointmentRepository appointments;
    private final ExternalHcmService hcm;
    private final ExternalSubscriptionsService subscriptions;
    private final Clock clock;

    public AppointmentCommandServiceImpl(AppointmentRepository appointments, ExternalHcmService hcm,
                                         ExternalSubscriptionsService subscriptions, Clock clock) {
        this.appointments = appointments;
        this.hcm = hcm;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Appointment handle(ScheduleAppointmentCommand command) {
        if (!subscriptions.isReadyForEvaluation(command.draftId())) throw new DraftNotReadyForAppointment();
        if (appointments.existsByDraftIdAndStatus(command.draftId(), AppointmentStatus.SCHEDULED)) {
            throw new AppointmentAlreadyExists();
        }
        var first = AppointmentQueryServiceImpl.firstBookableDate(clock);
        if (command.date() == null || command.date().isBefore(first)
                || command.date().isAfter(first.plusDays(AppointmentQueryServiceImpl.MAX_DAYS_AHEAD - 1L))) {
            throw new IllegalArgumentException("The visit must be booked between tomorrow and the next seven days.");
        }
        var window = new TimeRange(command.startTime(), command.endTime());
        // Recomputed here: the window shown to the family may have been taken since.
        var technician = EvaluationSlotPlanner.candidates(window, hcm.fetchTechniciansWorkingOn(command.date()),
                        appointments.findByScheduledDateAndStatus(command.date(), AppointmentStatus.SCHEDULED)).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(AppointmentSlotTaken::new);
        try {
            return appointments.saveAndFlush(new Appointment(command.draftId(), command.date(), window,
                    technician.technicianId(), technician.fullName(), command.contactName(), command.contactPhone(),
                    command.directions()));
        } catch (DataIntegrityViolationException exception) {
            // Two families confirmed the same technician and window at once; the unique constraint kept one.
            throw new AppointmentSlotTaken();
        }
    }
}
