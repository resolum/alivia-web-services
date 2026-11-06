package com.resolum.alivia.operations.application.internal.commandservices;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.AppointmentAlreadyExists;
import com.resolum.alivia.operations.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.operations.domain.model.aggregates.EvaluationSlotHold;
import com.resolum.alivia.operations.domain.model.commands.HoldEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ReleaseEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.EvaluationSlotHoldCommandService;
import com.resolum.alivia.operations.domain.services.EvaluationSlotPlanner;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.exceptions.ConstraintViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class EvaluationSlotHoldCommandServiceImpl implements EvaluationSlotHoldCommandService {
    /** Unique constraint that keeps one hold per technician and window (EvaluationSlotHold, Flyway V9). */
    static final String TECHNICIAN_WINDOW_CONSTRAINT = "uk_evaluation_slot_holds_technician_window";
    private final EvaluationSlotHoldRepository holds;
    private final AppointmentRepository appointments;
    private final ExternalHcmService hcm;
    private final Clock clock;

    public EvaluationSlotHoldCommandServiceImpl(EvaluationSlotHoldRepository holds, AppointmentRepository appointments,
                                                ExternalHcmService hcm, Clock clock) {
        this.holds = holds;
        this.appointments = appointments;
        this.hcm = hcm;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvaluationSlotHold handle(HoldEvaluationSlotCommand command) {
        if (command.draftId() == null || command.draftId().isBlank()) {
            throw new IllegalArgumentException("The contracting request is required.");
        }
        if (command.date() == null || command.startTime() == null || command.endTime() == null
                || command.technicianId() == null) {
            throw new IllegalArgumentException("The date, the window and the technician are required.");
        }
        if (command.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || command.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.EVALUATION, clock))) {
            throw new IllegalArgumentException("The visit must be booked between tomorrow and the next seven days.");
        }
        if (appointments.existsByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.EVALUATION,
                AppointmentStatus.SCHEDULED)) {
            throw new AppointmentAlreadyExists();
        }
        var now = Instant.now(clock);
        holds.deleteExpired(now);
        var window = new TimeRange(command.startTime(), command.endTime());
        var technician = EvaluationSlotPlanner.candidates(window, hcm.fetchTechniciansWorkingOn(command.date()),
                        AppointmentQueryServiceImpl.occupancyOf(appointments, holds, command.date(), command.draftId(), now))
                .stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(AppointmentSlotTaken::new);
        var hold = holds.findByDraftId(command.draftId()).orElse(null);
        if (hold == null) {
            hold = new EvaluationSlotHold(command.draftId(), command.date(), window, technician.technicianId(),
                    technician.fullName(), now);
        } else {
            hold.moveTo(command.date(), window, technician.technicianId(), technician.fullName(), now);
        }
        try {
            return holds.saveAndFlush(hold);
        } catch (DataIntegrityViolationException exception) {
            // Another family held the same technician and window at the same instant; anything else is rethrown.
            if (ConstraintViolations.isViolationOf(exception, TECHNICIAN_WINDOW_CONSTRAINT)) throw new AppointmentSlotTaken();
            throw exception;
        }
    }

    @Override
    @Transactional
    public void handle(ReleaseEvaluationSlotCommand command) {
        if (command.draftId() == null || command.draftId().isBlank()) return;
        holds.deleteByDraft(command.draftId());
    }
}
