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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class IncidentCommandServiceImpl implements IncidentCommandService {
    private static final List<IncidentStatus> PENDING = List.of(IncidentStatus.OPEN, IncidentStatus.SCHEDULED);

    private final IncidentPersistenceRepository incidents;
    private final AppointmentRepository appointments;
    private final ExternalSubscriptionsService subscriptions;
    private final Clock clock;

    public IncidentCommandServiceImpl(IncidentPersistenceRepository incidents, AppointmentRepository appointments,
                                      ExternalSubscriptionsService subscriptions, Clock clock) {
        this.incidents = incidents;
        this.appointments = appointments;
        this.subscriptions = subscriptions;
        this.clock = clock;
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
}
