package com.resolum.alivia.operations.application.internal.commandservices;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.exceptions.IncidentAccessDenied;
import com.resolum.alivia.operations.domain.exceptions.IncidentAlreadyOpen;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotAllowed;
import com.resolum.alivia.operations.domain.exceptions.IncidentNotFound;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.valueobjects.Evidence;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.operations.domain.model.commands.BookIncidentVisitCommand;
import com.resolum.alivia.operations.domain.model.commands.ReportIncidentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetReportableDevicesQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportedDevice;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class IncidentCommandServiceImpl implements IncidentCommandService {
    private static final List<IncidentStatus> PENDING = List.of(IncidentStatus.OPEN, IncidentStatus.IN_EVALUATION,
            IncidentStatus.SCHEDULED, IncidentStatus.IN_PROGRESS);

    private final IncidentPersistenceRepository incidents;
    private final AppointmentRepository appointments;
    private final ExternalSubscriptionsService subscriptions;
    private final IncidentQueryService homes;
    private final AppointmentCommandService orders;
    private final Clock clock;

    @Autowired
    public IncidentCommandServiceImpl(IncidentPersistenceRepository incidents, AppointmentRepository appointments,
                                      ExternalSubscriptionsService subscriptions, IncidentQueryService homes,
                                      AppointmentCommandService orders, Clock clock) {
        this.incidents = incidents;
        this.appointments = appointments;
        this.subscriptions = subscriptions;
        this.homes = homes;
        this.orders = orders;
        this.clock = clock;
    }

    /** Only the household reports and the evidences, without the report flow of the caregiver web. */
    public IncidentCommandServiceImpl(IncidentPersistenceRepository incidents, AppointmentRepository appointments,
                                      ExternalSubscriptionsService subscriptions, Clock clock) {
        this(incidents, appointments, subscriptions, null, null, clock);
    }

    /**
     * Only a registered account (the authenticated caller) that holds the active subscription of the
     * household, whose installation was approved, can report an incident.
     */
    @Override
    @Transactional
    public Incident handle(CreateIncidentCommand command) {
        if (command.userId() == null) throw new IncidentNotAllowed();
        var householdId = command.householdId() == null ? null : command.householdId().trim();
        var draftId = subscriptions.fetchSubscribedDraft(householdId, command.userId())
                .orElseThrow(IncidentNotAllowed::new);
        if (!appointments.existsByDraftIdAndServiceTypeAndInstallationResult(draftId, ServiceType.INSTALLATION,
                InstallationResult.APPROVED)) {
            throw new IncidentNotAllowed();
        }
        // An equivalent incident (same device of the household) still waiting for attention is not duplicated.
        incidents.findFirstByHouseholdIdAndDeviceIdAndStatusIn(householdId, command.deviceId(), PENDING)
                .ifPresent(existing -> {
                    throw new IncidentAlreadyOpen(existing.getId());
                });
        return incidents.save(new Incident(householdId, draftId, command.deviceId(), command.userId(),
                command.description(), command.severity(), Instant.now(clock)));
    }

    @Override
    @Transactional
    public Incident handle(AttachEvidenceCommand command) {
        var incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFound(command.incidentId()));
        if (!incident.isReportedBy(command.userId())) throw new IncidentAccessDenied();
        incident.attachEvidence(new Evidence(command.url(), command.publicId(), Instant.now(clock)));
        return incidents.save(incident);
    }

    /**
     * The family reports a failure of an installed device of its home (Registrada). The support team does not
     * evaluate it by hand yet: the report goes straight to evaluation and, when a technician is free in the
     * maintenance window, the visit is booked with them (Visita programada). Without a free block it stays in
     * evaluation.
     */
    @Override
    @Transactional
    public Incident handle(ReportIncidentCommand command) {
        if (command.userId() == null || homes == null) throw new IncidentNotAllowed();
        var home = homes.handle(new GetReportableDevicesQuery(command.userId())).orElseThrow(IncidentNotAllowed::new);
        var mac = command.deviceMac() == null ? "" : InstalledDevice.normalizeMac(command.deviceMac());
        var devices = home.devices();
        var position = java.util.stream.IntStream.range(0, devices.size())
                .filter(index -> devices.get(index).macAddress().equals(mac))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The device is not installed in this home."));
        if (command.problemType() == null) throw new IllegalArgumentException("The problem type is required.");
        var deviceId = (long) position + 1;
        // The home is the contracting request: an incident of the same device still open is not duplicated.
        incidents.findFirstByHouseholdIdAndDeviceIdAndStatusIn(home.draftId(), deviceId, PENDING)
                .ifPresent(existing -> {
                    throw new IncidentAlreadyOpen(existing.getId());
                });
        var now = Instant.now(clock);
        var incident = new Incident(home.draftId(), home.draftId(), deviceId, command.userId(), command.description(),
                command.problemType().severity(), now);
        incident.describeReport(command.problemType(), command.occurredAt(), ReportedDevice.of(devices.get(position)), now);
        if (command.visit() == null || command.technicianId() == null) {
            throw new IllegalArgumentException("Choose the day, the window and the technician of the visit.");
        }
        // Registrada: the technician evaluates it and confirms the visit from the app. The visit the family
        // chose is reserved now, so nobody else takes that technician's block; any failure rolls back both.
        var saved = incidents.saveAndFlush(incident);
        orders.handle(new BookIncidentVisitCommand(saved.getId(), command.visit(), command.technicianId()));
        return saved;
    }
}
