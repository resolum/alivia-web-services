package com.resolum.alivia.operations.application.internal.commandservices;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.exceptions.AppointmentAlreadyExists;
import com.resolum.alivia.operations.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.operations.domain.exceptions.DraftNotReadyForAppointment;
import com.resolum.alivia.operations.domain.exceptions.EvaluationRequiredForInstallation;
import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotFound;
import com.resolum.alivia.operations.domain.exceptions.SlotUnavailable;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderAlreadyExists;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotFound;
import com.resolum.alivia.operations.domain.exceptions.TechnicalOrderNotScheduled;
import com.resolum.alivia.operations.domain.exceptions.TechnicianNotEligible;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.AssignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.CompleteMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.BookIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.ConfirmIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.EvaluateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.StartMaintenanceOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.commands.OpenInstallationCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordEvaluationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.RecordInstallationResultCommand;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.operations.domain.model.commands.RegisterInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.RemoveInstalledDeviceCommand;
import com.resolum.alivia.operations.domain.model.commands.StartTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.EvaluationSlotPlanner;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianOccupancy;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.exceptions.ConstraintViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AppointmentCommandServiceImpl implements AppointmentCommandService {
    /** How many blocks a conflict suggests instead of the one that ran out. */
    static final int ALTERNATIVES = 3;
    /** Homes are in Lima, so an installation opened at the home takes the local time. */
    static final ZoneId HOME_ZONE = ZoneId.of("America/Lima");
    /** Latest start of an installation opened at the home, so its window stays within the day. */
    static final LocalTime LAST_INSTALLATION_START = LocalTime.of(23, 58);
    /** Unique constraint that keeps one visit per technician and window (Appointment, Flyway V4). */
    static final String TECHNICIAN_WINDOW_CONSTRAINT = "uk_appointments_technician_window";

    private final AppointmentRepository appointments;
    private final EvaluationSlotHoldRepository holds;
    private final IncidentPersistenceRepository incidents;
    private final ExternalHcmService hcm;
    private final ExternalSubscriptionsService subscriptions;
    private final Clock clock;

    public AppointmentCommandServiceImpl(AppointmentRepository appointments, EvaluationSlotHoldRepository holds,
                                         IncidentPersistenceRepository incidents, ExternalHcmService hcm,
                                         ExternalSubscriptionsService subscriptions, Clock clock) {
        this.appointments = appointments;
        this.holds = holds;
        this.incidents = incidents;
        this.hcm = hcm;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Appointment handle(ScheduleAppointmentCommand command) {
        if (!subscriptions.isReadyForEvaluation(command.draftId())) throw new DraftNotReadyForAppointment();
        var booked = appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.EVALUATION,
                AppointmentStatus.SCHEDULED);
        if (booked.isPresent()) {
            // A repeated request for the same visit (a retry or a double submit) gets the booking it already made.
            if (isSameVisit(booked.get(), command)) return booked.get();
            throw new AppointmentAlreadyExists();
        }
        var first = AppointmentQueryServiceImpl.firstBookableDate(clock);
        if (command.date() == null || command.date().isBefore(first)
                || command.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.EVALUATION, clock))) {
            throw new IllegalArgumentException("The visit must be booked between tomorrow and the next seven days.");
        }
        var window = new TimeRange(command.startTime(), command.endTime());
        // Recomputed here: the window shown to the family may have been taken since. Their own hold keeps it free.
        var technician = EvaluationSlotPlanner.candidates(window, hcm.fetchTechniciansWorkingOn(command.date()),
                        occupancyOf(command.date(), null, command.draftId())).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(AppointmentSlotTaken::new);
        try {
            var appointment = appointments.saveAndFlush(new Appointment(command.draftId(), ServiceType.EVALUATION,
                    command.date(), window, technician.technicianId(), technician.fullName(), command.contactName(),
                    command.contactPhone(), command.directions(), Instant.now(clock)));
            holds.deleteByDraft(command.draftId());
            return appointment;
        } catch (DataIntegrityViolationException exception) {
            // Two families confirmed the same technician and window at once; the unique constraint kept one.
            // Any other violation is a real data error and must not look like a taken window.
            if (ConstraintViolations.isViolationOf(exception, TECHNICIAN_WINDOW_CONSTRAINT)) throw new AppointmentSlotTaken();
            throw exception;
        }
    }

    @Override
    @Transactional
    public Appointment handle(CreateTechnicalOrderCommand command) {
        var serviceType = command.serviceType();
        var slot = command.slotId();
        if (serviceType == null || slot == null) throw new IllegalArgumentException("The service type and the block are required.");
        if (serviceType == ServiceType.MAINTENANCE) {
            throw new IllegalArgumentException("Maintenance visits are booked for an incident in /api/v1/maintenance-orders.");
        }
        if (slot.window().duration().compareTo(serviceType.window()) != 0) {
            throw new IllegalArgumentException("An %s block lasts %d hours.".formatted(
                    serviceType.name().toLowerCase(), serviceType.window().toHours()));
        }
        var evaluation = Optional.<Appointment>empty();
        if (serviceType == ServiceType.EVALUATION) {
            if (!subscriptions.isReadyForEvaluation(command.draftId())) throw new DraftNotReadyForAppointment();
        } else {
            // The installation needs the evaluation of the same request, and happens on a later day.
            evaluation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(),
                    ServiceType.EVALUATION, AppointmentStatus.SCHEDULED);
            if (evaluation.isEmpty() || !slot.date().isAfter(evaluation.get().getScheduledDate())) {
                throw new EvaluationRequiredForInstallation();
            }
        }
        var earliest = evaluation.map(visit -> visit.getScheduledDate().plusDays(1))
                .orElse(AppointmentQueryServiceImpl.firstBookableDate(clock));
        if (appointments.existsByDraftIdAndServiceTypeAndStatus(command.draftId(), serviceType, AppointmentStatus.SCHEDULED)) {
            throw new TechnicalOrderAlreadyExists(serviceType);
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(serviceType, clock))) {
            throw new IllegalArgumentException("An %s must be booked between tomorrow and the next %d days.".formatted(
                    serviceType.name().toLowerCase(), serviceType.daysAhead()));
        }
        var contact = evaluation.orElse(null);
        var contactName = command.contactName() != null ? command.contactName() : contact == null ? null : contact.getContactName();
        var contactPhone = command.contactPhone() != null ? command.contactPhone() : contact == null ? null : contact.getContactPhone();
        var directions = command.directions() != null ? command.directions() : contact == null ? null : contact.getDirections();
        // Recomputed here: the capacity shown may have been used since.
        var technician = candidates(slot.date(), slot.window(), null).stream().findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(serviceType, earliest, slot)));
        try {
            return appointments.saveAndFlush(new Appointment(command.draftId(), serviceType, slot.date(), slot.window(),
                    technician.technicianId(), technician.fullName(), contactName, contactPhone, directions,
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            // Another order took the last technician of the block at the same instant.
            throw new SlotUnavailable(alternatives(serviceType, earliest, slot));
        }
    }

    @Override
    @Transactional
    public Appointment handle(AssignTechnicianCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var technician = candidates(order.getScheduledDate(), order.window(), order).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(TechnicianNotEligible::new);
        order.assignTechnician(technician.technicianId(), technician.fullName(), command.note(), Instant.now(clock));
        try {
            return appointments.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new TechnicianNotEligible();
        }
    }

    /**
     * Keeps the block when another technician can take it; otherwise moves the order to the nearest block
     * where someone other than the unavailable technician is free.
     */
    @Override
    @Transactional
    public Appointment handle(ReassignTechnicianCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var unavailable = order.getTechnicianId();
        var target = replacementIn(order.slotId(), order, unavailable)
                .map(technician -> new Placement(order.slotId(), technician))
                .or(() -> nearestPlacement(order, unavailable))
                .orElseThrow(() -> new SlotUnavailable(List.of()));
        order.reassign(target.slot(), target.technician().technicianId(), target.technician().fullName(),
                command.reason(), Instant.now(clock));
        try {
            return appointments.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(order.getServiceType(), earliestFor(order), order.slotId()));
        }
    }

    @Override
    @Transactional
    public Appointment handle(RecordEvaluationResultCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var now = Instant.now(clock);
        order.recordEvaluationResult(command.result(), command.notes(), now);
        // A home that is not viable closes the request: the payment the family authorized is released, so the
        // held amount returns to the card. If Stripe cannot release it, the result is not recorded and the
        // technician retries.
        if (!command.result().isViable()) subscriptions.releasePaymentOf(order.getDraftId());
        return appointments.saveAndFlush(order);
    }

    /**
     * The family confirmed the configuration after a viable evaluation: the installation order opens for the
     * technician who evaluated the home, from the current minute, so it appears in their orders. Confirming
     * again returns the installation already open.
     */
    @Override
    @Transactional
    public Optional<Appointment> handle(OpenInstallationCommand command) {
        if (command.draftId() == null || command.draftId().isBlank()) return Optional.empty();
        var open = appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.INSTALLATION,
                AppointmentStatus.SCHEDULED);
        if (open.isPresent()) return open;
        return appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.EVALUATION,
                        AppointmentStatus.COMPLETED)
                .filter(evaluation -> evaluation.getEvaluationResult() != null && evaluation.getEvaluationResult().isViable())
                .map(evaluation -> openInstallation(evaluation, Instant.now(clock)));
    }

    private Appointment openInstallation(Appointment evaluation, Instant now) {
        var local = now.atZone(HOME_ZONE);
        var today = local.toLocalDate();
        var taken = appointments.findByTechnicianIdOrderByScheduledDateAscStartTimeAsc(evaluation.getTechnicianId())
                .stream().filter(order -> order.getScheduledDate().equals(today)).map(Appointment::getStartTime)
                .collect(Collectors.toSet());
        var start = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (start.isAfter(LAST_INSTALLATION_START)) start = LAST_INSTALLATION_START;
        while (taken.contains(start)) start = start.plusMinutes(1);
        var end = start.plus(ServiceType.INSTALLATION.window());
        if (end.isBefore(start)) end = LocalTime.of(23, 59);
        var installation = new Appointment(evaluation.getDraftId(), ServiceType.INSTALLATION, today,
                new TimeRange(start, end), evaluation.getTechnicianId(), evaluation.getTechnicianName(),
                evaluation.getContactName(), evaluation.getContactPhone(), evaluation.getDirections(), now);
        return appointments.saveAndFlush(installation);
    }

    @Override
    @Transactional
    public Appointment handle(RecordInstallationResultCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.recordInstallationResult(command.result(), command.notes(), Instant.now(clock));
        var completed = appointments.saveAndFlush(order);
        if (command.result() == InstallationResult.APPROVED && !subscriptions.capturePaymentOf(order.getDraftId())) {
            throw new IllegalStateException("La preautorización de la contratación no está disponible para captura.");
        }
        return completed;
    }

    /**
     * Books a maintenance visit for an open incident in a block with capacity, with the contact of the home's
     * installation, and marks the incident as scheduled.
     */
    @Override
    @Transactional
    public Appointment handle(CreateMaintenanceOrderCommand command) {
        var slot = command.slotId();
        if (slot == null) throw new IllegalArgumentException("The block is required.");
        var incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFound(command.incidentId()));
        if (!command.administrator() && !incident.isReportedBy(command.userId())) throw new IncidentAccessDenied();
        if (slot.window().duration().compareTo(ServiceType.MAINTENANCE.window()) != 0) {
            throw new IllegalArgumentException("A maintenance block lasts %d hours."
                    .formatted(ServiceType.MAINTENANCE.window().toHours()));
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.MAINTENANCE, clock))) {
            throw new IllegalArgumentException("A maintenance visit must be booked between tomorrow and the next %d days."
                    .formatted(ServiceType.MAINTENANCE.daysAhead()));
        }
        incident.scheduleMaintenance(Instant.now(clock));
        var installation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(incident.getDraftId(),
                ServiceType.INSTALLATION, AppointmentStatus.COMPLETED).orElseThrow(IncidentNotAllowed::new);
        var earliest = AppointmentQueryServiceImpl.firstBookableDate(clock);
        var technician = candidates(slot.date(), slot.window(), null).stream().findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot)));
        try {
            var order = appointments.saveAndFlush(new Appointment(incident.getDraftId(), ServiceType.MAINTENANCE,
                    incident.getId(), slot.date(), slot.window(), technician.technicianId(), technician.fullName(),
                    installation.getContactName(), installation.getContactPhone(), installation.getDirections(),
                    Instant.now(clock)));
            incidents.save(incident);
            return order;
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot));
        }
    }

    /**
     * Reserves the block and technician the family chose when reporting: the order occupies the technician's
     * time, and the incident stays registered until the technician evaluates it.
     */
    @Override
    @Transactional
    public Appointment handle(BookIncidentVisitCommand command) {
        var slot = command.slotId();
        if (slot == null || command.technicianId() == null) {
            throw new IllegalArgumentException("Choose the day, the window and the technician of the visit.");
        }
        var incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFound(command.incidentId()));
        requireMaintenanceBlock(slot);
        var installation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(incident.getDraftId(),
                ServiceType.INSTALLATION, AppointmentStatus.COMPLETED).orElseThrow(IncidentNotAllowed::new);
        var earliest = AppointmentQueryServiceImpl.firstBookableDate(clock);
        var technician = candidates(slot.date(), slot.window(), null).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot)));
        try {
            return appointments.saveAndFlush(new Appointment(incident.getDraftId(), ServiceType.MAINTENANCE,
                    incident.getId(), slot.date(), slot.window(), technician.technicianId(), technician.fullName(),
                    installation.getContactName(), installation.getContactPhone(), installation.getDirections(),
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot));
        }
    }

    @Override
    @Transactional
    public Appointment handle(EvaluateIncidentCommand command) {
        var order = scheduledMaintenance(command.orderId());
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.startEvaluation(Instant.now(clock));
        incidents.save(incident);
        return order;
    }

    @Override
    @Transactional
    public Appointment handle(ConfirmIncidentVisitCommand command) {
        var order = scheduledMaintenance(command.orderId());
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.confirmVisit(Instant.now(clock));
        incidents.save(incident);
        return order;
    }

    private Appointment scheduledMaintenance(Long orderId) {
        var order = appointments.findById(orderId).orElseThrow(() -> new TechnicalOrderNotFound(orderId));
        if (order.getServiceType() != ServiceType.MAINTENANCE || order.getIncidentId() == null) {
            throw new IllegalArgumentException("Only maintenance orders attend an incident.");
        }
        if (order.getStatus() != AppointmentStatus.SCHEDULED) throw new TechnicalOrderNotScheduled();
        return order;
    }

    private void requireMaintenanceBlock(SlotId slot) {
        if (slot.window().duration().compareTo(ServiceType.MAINTENANCE.window()) != 0) {
            throw new IllegalArgumentException("A maintenance block lasts %d hours."
                    .formatted(ServiceType.MAINTENANCE.window().toHours()));
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.MAINTENANCE, clock))) {
            throw new IllegalArgumentException("A maintenance visit must be booked between tomorrow and the next %d days."
                    .formatted(ServiceType.MAINTENANCE.daysAhead()));
        }
    }

    /**
     * The technician arrived for a scheduled maintenance visit: its incident moves to IN_PROGRESS. The order
     * stays scheduled until its report is recorded.
     */
    @Override
    @Transactional
    public Appointment handle(StartMaintenanceOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        if (order.getServiceType() != ServiceType.MAINTENANCE) {
            throw new IllegalArgumentException("Only maintenance orders are attended for an incident.");
        }
        if (order.getStatus() != AppointmentStatus.SCHEDULED) throw new TechnicalOrderNotScheduled();
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.startAttention(Instant.now(clock));
        incidents.save(incident);
        order.start(Instant.now(clock));
        return appointments.saveAndFlush(order);
    }

    /**
     * The technician arrived at the home: the order is in progress from now. A maintenance order also puts
     * its incident in attention.
     */
    @Override
    @Transactional
    public Appointment handle(StartTechnicalOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        if (order.getServiceType() == ServiceType.MAINTENANCE) {
            return handle(new StartMaintenanceOrderCommand(command.orderId()));
        }
        order.start(Instant.now(clock));
        return appointments.saveAndFlush(order);
    }

    @Override
    @Transactional
    public Appointment handle(RegisterInstalledDeviceCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.registerDevice(new InstalledDevice(command.alias(), command.macAddress(), command.type()));
        return appointments.saveAndFlush(order);
    }

    @Override
    @Transactional
    public Appointment handle(RemoveInstalledDeviceCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.removeDevice(command.macAddress());
        return appointments.saveAndFlush(order);
    }

    /**
     * Records the diagnosis, the maintenance and the test of a visit. A passed test resolves the incident; a
     * failed one opens it again. Without the test result nothing changes and the order stays scheduled.
     */
    @Override
    @Transactional
    public Appointment handle(CompleteMaintenanceOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var report = new MaintenanceReport(command.diagnosis(), command.maintenancePerformed(), command.testResult(),
                command.resolution());
        order.completeMaintenance(report);
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.applyMaintenanceResult(report, Instant.now(clock));
        incidents.save(incident);
        return appointments.saveAndFlush(order);
    }

    private record Placement(SlotId slot, TechnicianAvailability technician) {
    }

    private Optional<TechnicianAvailability> replacementIn(SlotId slot, Appointment order, Long unavailable) {
        return candidates(slot.date(), slot.window(), order).stream()
                .filter(candidate -> !candidate.technicianId().equals(unavailable))
                .findFirst();
    }

    private Optional<Placement> nearestPlacement(Appointment order, Long unavailable) {
        for (var date : AppointmentQueryServiceImpl.bookableDates(order.getServiceType(), earliestFor(order),
                order.getServiceType().daysAhead(), clock)) {
            var technicians = hcm.fetchTechniciansWorkingOn(date);
            var visits = visitsOf(date, order);
            for (var block : EvaluationSlotPlanner.blocks(date, order.getServiceType(), technicians, visits)) {
                if (block.slotId().equals(order.slotId())) continue;
                var replacement = EvaluationSlotPlanner.candidates(block.slotId().window(), technicians, visits).stream()
                        .filter(candidate -> !candidate.technicianId().equals(unavailable))
                        .findFirst();
                if (replacement.isPresent()) return Optional.of(new Placement(block.slotId(), replacement.get()));
            }
        }
        return Optional.empty();
    }

    /** An installation can only move to a day after its evaluation. */
    private LocalDate earliestFor(Appointment order) {
        if (order.getServiceType() != ServiceType.INSTALLATION) return AppointmentQueryServiceImpl.firstBookableDate(clock);
        return appointments.findFirstByDraftIdAndServiceTypeAndStatus(order.getDraftId(), ServiceType.EVALUATION,
                        AppointmentStatus.SCHEDULED)
                .map(evaluation -> evaluation.getScheduledDate().plusDays(1))
                .orElse(AppointmentQueryServiceImpl.firstBookableDate(clock));
    }

    /** Nearest blocks with capacity from {@code earliest}, leaving out {@code excluded}. */
    private List<AvailableBlock> alternatives(ServiceType serviceType, LocalDate earliest, SlotId excluded) {
        var blocks = new ArrayList<AvailableBlock>();
        for (var date : AppointmentQueryServiceImpl.bookableDates(serviceType, earliest, serviceType.daysAhead(), clock)) {
            for (var block : EvaluationSlotPlanner.blocks(date, serviceType, hcm.fetchTechniciansWorkingOn(date),
                    visitsOf(date, null))) {
                if (block.slotId().equals(excluded)) continue;
                blocks.add(block);
                if (blocks.size() == ALTERNATIVES) return blocks;
            }
        }
        return blocks;
    }

    /** Technicians free for the window, least busy first, ignoring {@code ignored} (the order being changed). */
    private List<TechnicianAvailability> candidates(LocalDate date, TimeRange window, Appointment ignored) {
        return EvaluationSlotPlanner.candidates(window, hcm.fetchTechniciansWorkingOn(date), visitsOf(date, ignored));
    }

    private List<TechnicianOccupancy> visitsOf(LocalDate date, Appointment ignored) {
        return occupancyOf(date, ignored, null);
    }

    /** Whether {@code appointment} is the visit {@code command} asks for: same day, window and technician. */
    private static boolean isSameVisit(Appointment appointment, ScheduleAppointmentCommand command) {
        return appointment.getScheduledDate().equals(command.date())
                && appointment.window().equals(new TimeRange(command.startTime(), command.endTime()))
                && java.util.Objects.equals(appointment.getTechnicianId(), command.technicianId());
    }

    /** Scheduled orders and windows held by families, without {@code ignored} and the hold of {@code ownDraftId}. */
    private List<TechnicianOccupancy> occupancyOf(LocalDate date, Appointment ignored, String ownDraftId) {
        return AppointmentQueryServiceImpl.occupancyOf(appointments, holds, date, ownDraftId, Instant.now(clock)).stream()
                .filter(visit -> ignored == null || !visit.equals(ignored)
                        && !(visit instanceof Appointment order && isSame(order, ignored)))
                .toList();
    }

    private static boolean isSame(Appointment visit, Appointment ignored) {
        return visit.getId() != null && visit.getId().equals(ignored.getId());
    }

    @Override
    @Transactional
    public Appointment handle(CreateTechnicalOrderCommand command) {
        var serviceType = command.serviceType();
        var slot = command.slotId();
        if (serviceType == null || slot == null) throw new IllegalArgumentException("The service type and the block are required.");
        if (serviceType == ServiceType.MAINTENANCE) {
            throw new IllegalArgumentException("Maintenance visits are booked for an incident in /api/v1/maintenance-orders.");
        }
        if (slot.window().duration().compareTo(serviceType.window()) != 0) {
            throw new IllegalArgumentException("An %s block lasts %d hours.".formatted(
                    serviceType.name().toLowerCase(), serviceType.window().toHours()));
        }
        var evaluation = Optional.<Appointment>empty();
        if (serviceType == ServiceType.EVALUATION) {
            if (!subscriptions.isReadyForEvaluation(command.draftId())) throw new DraftNotReadyForAppointment();
        } else {
            // The installation needs the evaluation of the same request, and happens on a later day.
            evaluation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(),
                    ServiceType.EVALUATION, AppointmentStatus.SCHEDULED);
            if (evaluation.isEmpty() || !slot.date().isAfter(evaluation.get().getScheduledDate())) {
                throw new EvaluationRequiredForInstallation();
            }
        }
        var earliest = evaluation.map(visit -> visit.getScheduledDate().plusDays(1))
                .orElse(AppointmentQueryServiceImpl.firstBookableDate(clock));
        if (appointments.existsByDraftIdAndServiceTypeAndStatus(command.draftId(), serviceType, AppointmentStatus.SCHEDULED)) {
            throw new TechnicalOrderAlreadyExists(serviceType);
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(serviceType, clock))) {
            throw new IllegalArgumentException("An %s must be booked between tomorrow and the next %d days.".formatted(
                    serviceType.name().toLowerCase(), serviceType.daysAhead()));
        }
        var contact = evaluation.orElse(null);
        var contactName = command.contactName() != null ? command.contactName() : contact == null ? null : contact.getContactName();
        var contactPhone = command.contactPhone() != null ? command.contactPhone() : contact == null ? null : contact.getContactPhone();
        var directions = command.directions() != null ? command.directions() : contact == null ? null : contact.getDirections();
        // Recomputed here: the capacity shown may have been used since.
        var technician = candidates(slot.date(), slot.window(), null).stream().findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(serviceType, earliest, slot)));
        try {
            return appointments.saveAndFlush(new Appointment(command.draftId(), serviceType, slot.date(), slot.window(),
                    technician.technicianId(), technician.fullName(), contactName, contactPhone, directions,
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            // Another order took the last technician of the block at the same instant.
            throw new SlotUnavailable(alternatives(serviceType, earliest, slot));
        }
    }

    @Override
    @Transactional
    public Appointment handle(AssignTechnicianCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var technician = candidates(order.getScheduledDate(), order.window(), order).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(TechnicianNotEligible::new);
        order.assignTechnician(technician.technicianId(), technician.fullName(), command.note(), Instant.now(clock));
        try {
            return appointments.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new TechnicianNotEligible();
        }
    }

    /**
     * Keeps the block when another technician can take it; otherwise moves the order to the nearest block
     * where someone other than the unavailable technician is free.
     */
    @Override
    @Transactional
    public Appointment handle(ReassignTechnicianCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var unavailable = order.getTechnicianId();
        var target = replacementIn(order.slotId(), order, unavailable)
                .map(technician -> new Placement(order.slotId(), technician))
                .or(() -> nearestPlacement(order, unavailable))
                .orElseThrow(() -> new SlotUnavailable(List.of()));
        order.reassign(target.slot(), target.technician().technicianId(), target.technician().fullName(),
                command.reason(), Instant.now(clock));
        try {
            return appointments.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(order.getServiceType(), earliestFor(order), order.slotId()));
        }
    }

    @Override
    @Transactional
    public Appointment handle(RecordEvaluationResultCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var now = Instant.now(clock);
        order.recordEvaluationResult(command.result(), command.notes(), now);
        // A home that is not viable closes the request: the payment the family authorized is released, so the
        // held amount returns to the card. If Stripe cannot release it, the result is not recorded and the
        // technician retries.
        if (!command.result().isViable()) subscriptions.releasePaymentOf(order.getDraftId());
        return appointments.saveAndFlush(order);
    }

    /**
     * The family confirmed the configuration after a viable evaluation: the installation order opens for the
     * technician who evaluated the home, from the current minute, so it appears in their orders. Confirming
     * again returns the installation already open.
     */
    @Override
    @Transactional
    public Optional<Appointment> handle(OpenInstallationCommand command) {
        if (command.draftId() == null || command.draftId().isBlank()) return Optional.empty();
        var open = appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.INSTALLATION,
                AppointmentStatus.SCHEDULED);
        if (open.isPresent()) return open;
        return appointments.findFirstByDraftIdAndServiceTypeAndStatus(command.draftId(), ServiceType.EVALUATION,
                        AppointmentStatus.COMPLETED)
                .filter(evaluation -> evaluation.getEvaluationResult() != null && evaluation.getEvaluationResult().isViable())
                .map(evaluation -> openInstallation(evaluation, Instant.now(clock)));
    }

    private Appointment openInstallation(Appointment evaluation, Instant now) {
        var local = now.atZone(HOME_ZONE);
        var today = local.toLocalDate();
        var taken = appointments.findByTechnicianIdOrderByScheduledDateAscStartTimeAsc(evaluation.getTechnicianId())
                .stream().filter(order -> order.getScheduledDate().equals(today)).map(Appointment::getStartTime)
                .collect(Collectors.toSet());
        var start = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (start.isAfter(LAST_INSTALLATION_START)) start = LAST_INSTALLATION_START;
        while (taken.contains(start)) start = start.plusMinutes(1);
        var end = start.plus(ServiceType.INSTALLATION.window());
        if (end.isBefore(start)) end = LocalTime.of(23, 59);
        var installation = new Appointment(evaluation.getDraftId(), ServiceType.INSTALLATION, today,
                new TimeRange(start, end), evaluation.getTechnicianId(), evaluation.getTechnicianName(),
                evaluation.getContactName(), evaluation.getContactPhone(), evaluation.getDirections(), now);
        return appointments.saveAndFlush(installation);
    }

    @Override
    @Transactional
    public Appointment handle(RecordInstallationResultCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.recordInstallationResult(command.result(), command.notes(), Instant.now(clock));
        var completed = appointments.saveAndFlush(order);
        if (command.result() == InstallationResult.APPROVED && !subscriptions.capturePaymentOf(order.getDraftId())) {
            throw new IllegalStateException("La preautorización de la contratación no está disponible para captura.");
        }
        return completed;
    }

    /**
     * Books a maintenance visit for an open incident in a block with capacity, with the contact of the home's
     * installation, and marks the incident as scheduled.
     */
    @Override
    @Transactional
    public Appointment handle(CreateMaintenanceOrderCommand command) {
        var slot = command.slotId();
        if (slot == null) throw new IllegalArgumentException("The block is required.");
        var incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFound(command.incidentId()));
        if (!command.administrator() && !incident.isReportedBy(command.userId())) throw new IncidentAccessDenied();
        if (slot.window().duration().compareTo(ServiceType.MAINTENANCE.window()) != 0) {
            throw new IllegalArgumentException("A maintenance block lasts %d hours."
                    .formatted(ServiceType.MAINTENANCE.window().toHours()));
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.MAINTENANCE, clock))) {
            throw new IllegalArgumentException("A maintenance visit must be booked between tomorrow and the next %d days."
                    .formatted(ServiceType.MAINTENANCE.daysAhead()));
        }
        incident.scheduleMaintenance(Instant.now(clock));
        var installation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(incident.getDraftId(),
                ServiceType.INSTALLATION, AppointmentStatus.COMPLETED).orElseThrow(IncidentNotAllowed::new);
        var earliest = AppointmentQueryServiceImpl.firstBookableDate(clock);
        var technician = candidates(slot.date(), slot.window(), null).stream().findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot)));
        try {
            var order = appointments.saveAndFlush(new Appointment(incident.getDraftId(), ServiceType.MAINTENANCE,
                    incident.getId(), slot.date(), slot.window(), technician.technicianId(), technician.fullName(),
                    installation.getContactName(), installation.getContactPhone(), installation.getDirections(),
                    Instant.now(clock)));
            incidents.save(incident);
            return order;
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot));
        }
    }

    /**
     * Reserves the block and technician the family chose when reporting: the order occupies the technician's
     * time, and the incident stays registered until the technician evaluates it.
     */
    @Override
    @Transactional
    public Appointment handle(BookIncidentVisitCommand command) {
        var slot = command.slotId();
        if (slot == null || command.technicianId() == null) {
            throw new IllegalArgumentException("Choose the day, the window and the technician of the visit.");
        }
        var incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFound(command.incidentId()));
        requireMaintenanceBlock(slot);
        var installation = appointments.findFirstByDraftIdAndServiceTypeAndStatus(incident.getDraftId(),
                ServiceType.INSTALLATION, AppointmentStatus.COMPLETED).orElseThrow(IncidentNotAllowed::new);
        var earliest = AppointmentQueryServiceImpl.firstBookableDate(clock);
        var technician = candidates(slot.date(), slot.window(), null).stream()
                .filter(candidate -> candidate.technicianId().equals(command.technicianId()))
                .findFirst()
                .orElseThrow(() -> new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot)));
        try {
            return appointments.saveAndFlush(new Appointment(incident.getDraftId(), ServiceType.MAINTENANCE,
                    incident.getId(), slot.date(), slot.window(), technician.technicianId(), technician.fullName(),
                    installation.getContactName(), installation.getContactPhone(), installation.getDirections(),
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            throw new SlotUnavailable(alternatives(ServiceType.MAINTENANCE, earliest, slot));
        }
    }

    @Override
    @Transactional
    public Appointment handle(EvaluateIncidentCommand command) {
        var order = scheduledMaintenance(command.orderId());
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.startEvaluation(Instant.now(clock));
        incidents.save(incident);
        return order;
    }

    @Override
    @Transactional
    public Appointment handle(ConfirmIncidentVisitCommand command) {
        var order = scheduledMaintenance(command.orderId());
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.confirmVisit(Instant.now(clock));
        incidents.save(incident);
        return order;
    }

    private Appointment scheduledMaintenance(Long orderId) {
        var order = appointments.findById(orderId).orElseThrow(() -> new TechnicalOrderNotFound(orderId));
        if (order.getServiceType() != ServiceType.MAINTENANCE || order.getIncidentId() == null) {
            throw new IllegalArgumentException("Only maintenance orders attend an incident.");
        }
        if (order.getStatus() != AppointmentStatus.SCHEDULED) throw new TechnicalOrderNotScheduled();
        return order;
    }

    private void requireMaintenanceBlock(SlotId slot) {
        if (slot.window().duration().compareTo(ServiceType.MAINTENANCE.window()) != 0) {
            throw new IllegalArgumentException("A maintenance block lasts %d hours."
                    .formatted(ServiceType.MAINTENANCE.window().toHours()));
        }
        if (slot.date().isBefore(AppointmentQueryServiceImpl.firstBookableDate(clock))
                || slot.date().isAfter(AppointmentQueryServiceImpl.lastBookableDate(ServiceType.MAINTENANCE, clock))) {
            throw new IllegalArgumentException("A maintenance visit must be booked between tomorrow and the next %d days."
                    .formatted(ServiceType.MAINTENANCE.daysAhead()));
        }
    }

    /**
     * The technician arrived for a scheduled maintenance visit: its incident moves to IN_PROGRESS. The order
     * stays scheduled until its report is recorded.
     */
    @Override
    @Transactional
    public Appointment handle(StartMaintenanceOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        if (order.getServiceType() != ServiceType.MAINTENANCE) {
            throw new IllegalArgumentException("Only maintenance orders are attended for an incident.");
        }
        if (order.getStatus() != AppointmentStatus.SCHEDULED) throw new TechnicalOrderNotScheduled();
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.startAttention(Instant.now(clock));
        incidents.save(incident);
        order.start(Instant.now(clock));
        return appointments.saveAndFlush(order);
    }

    /**
     * The technician arrived at the home: the order is in progress from now. A maintenance order also puts
     * its incident in attention.
     */
    @Override
    @Transactional
    public Appointment handle(StartTechnicalOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        if (order.getServiceType() == ServiceType.MAINTENANCE) {
            return handle(new StartMaintenanceOrderCommand(command.orderId()));
        }
        order.start(Instant.now(clock));
        return appointments.saveAndFlush(order);
    }

    @Override
    @Transactional
    public Appointment handle(RegisterInstalledDeviceCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.registerDevice(new InstalledDevice(command.alias(), command.macAddress(), command.type()));
        return appointments.saveAndFlush(order);
    }

    @Override
    @Transactional
    public Appointment handle(RemoveInstalledDeviceCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        order.removeDevice(command.macAddress());
        return appointments.saveAndFlush(order);
    }

    /**
     * Records the diagnosis, the maintenance and the test of a visit. A passed test resolves the incident; a
     * failed one opens it again. Without the test result nothing changes and the order stays scheduled.
     */
    @Override
    @Transactional
    public Appointment handle(CompleteMaintenanceOrderCommand command) {
        var order = appointments.findById(command.orderId()).orElseThrow(() -> new TechnicalOrderNotFound(command.orderId()));
        var report = new MaintenanceReport(command.diagnosis(), command.maintenancePerformed(), command.testResult(),
                command.resolution());
        order.completeMaintenance(report);
        var incident = incidents.findById(order.getIncidentId())
                .orElseThrow(() -> new IncidentNotFound(order.getIncidentId()));
        incident.applyMaintenanceResult(report, Instant.now(clock));
        incidents.save(incident);
        return appointments.saveAndFlush(order);
    }

    private record Placement(SlotId slot, TechnicianAvailability technician) {
    }

    private Optional<TechnicianAvailability> replacementIn(SlotId slot, Appointment order, Long unavailable) {
        return candidates(slot.date(), slot.window(), order).stream()
                .filter(candidate -> !candidate.technicianId().equals(unavailable))
                .findFirst();
    }

    private Optional<Placement> nearestPlacement(Appointment order, Long unavailable) {
        for (var date : AppointmentQueryServiceImpl.bookableDates(order.getServiceType(), earliestFor(order),
                order.getServiceType().daysAhead(), clock)) {
            var technicians = hcm.fetchTechniciansWorkingOn(date);
            var visits = visitsOf(date, order);
            for (var block : EvaluationSlotPlanner.blocks(date, order.getServiceType(), technicians, visits)) {
                if (block.slotId().equals(order.slotId())) continue;
                var replacement = EvaluationSlotPlanner.candidates(block.slotId().window(), technicians, visits).stream()
                        .filter(candidate -> !candidate.technicianId().equals(unavailable))
                        .findFirst();
                if (replacement.isPresent()) return Optional.of(new Placement(block.slotId(), replacement.get()));
            }
        }
        return Optional.empty();
    }

    /** An installation can only move to a day after its evaluation. */
    private LocalDate earliestFor(Appointment order) {
        if (order.getServiceType() != ServiceType.INSTALLATION) return AppointmentQueryServiceImpl.firstBookableDate(clock);
        return appointments.findFirstByDraftIdAndServiceTypeAndStatus(order.getDraftId(), ServiceType.EVALUATION,
                        AppointmentStatus.SCHEDULED)
                .map(evaluation -> evaluation.getScheduledDate().plusDays(1))
                .orElse(AppointmentQueryServiceImpl.firstBookableDate(clock));
    }

    /** Nearest blocks with capacity from {@code earliest}, leaving out {@code excluded}. */
    private List<AvailableBlock> alternatives(ServiceType serviceType, LocalDate earliest, SlotId excluded) {
        var blocks = new ArrayList<AvailableBlock>();
        for (var date : AppointmentQueryServiceImpl.bookableDates(serviceType, earliest, serviceType.daysAhead(), clock)) {
            for (var block : EvaluationSlotPlanner.blocks(date, serviceType, hcm.fetchTechniciansWorkingOn(date),
                    visitsOf(date, null))) {
                if (block.slotId().equals(excluded)) continue;
                blocks.add(block);
                if (blocks.size() == ALTERNATIVES) return blocks;
            }
        }
        return blocks;
    }

    /** Technicians free for the window, least busy first, ignoring {@code ignored} (the order being changed). */
    private List<TechnicianAvailability> candidates(LocalDate date, TimeRange window, Appointment ignored) {
        return EvaluationSlotPlanner.candidates(window, hcm.fetchTechniciansWorkingOn(date), visitsOf(date, ignored));
    }

    private List<TechnicianOccupancy> visitsOf(LocalDate date, Appointment ignored) {
        return occupancyOf(date, ignored, null);
    }

    /** Whether {@code appointment} is the visit {@code command} asks for: same day, window and technician. */
    private static boolean isSameVisit(Appointment appointment, ScheduleAppointmentCommand command) {
        return appointment.getScheduledDate().equals(command.date())
                && appointment.window().equals(new TimeRange(command.startTime(), command.endTime()))
                && java.util.Objects.equals(appointment.getTechnicianId(), command.technicianId());
    }

    /** Scheduled orders and windows held by families, without {@code ignored} and the hold of {@code ownDraftId}. */
    private List<TechnicianOccupancy> occupancyOf(LocalDate date, Appointment ignored, String ownDraftId) {
        return AppointmentQueryServiceImpl.occupancyOf(appointments, holds, date, ownDraftId, Instant.now(clock)).stream()
                .filter(visit -> ignored == null || !visit.equals(ignored)
                        && !(visit instanceof Appointment order && isSame(order, ignored)))
                .toList();
    }

    private static boolean isSame(Appointment visit, Appointment ignored) {
        return visit.getId() != null && visit.getId().equals(ignored.getId());
    }
}
