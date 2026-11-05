package com.resolum.alivia.operations.application.internal.queryservices;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.operations.domain.model.queries.GetReportableDevicesQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportableHome;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class IncidentQueryServiceImpl implements IncidentQueryService {
    private final IncidentPersistenceRepository incidents;
    private final AppointmentRepository appointments;
    private final ExternalSubscriptionsService subscriptions;

    @Autowired
    public IncidentQueryServiceImpl(IncidentPersistenceRepository incidents, AppointmentRepository appointments,
                                    ExternalSubscriptionsService subscriptions) {
        this.incidents = incidents;
        this.appointments = appointments;
        this.subscriptions = subscriptions;
    }

    /** Only the incident queries, without the home of the account. */
    public IncidentQueryServiceImpl(IncidentPersistenceRepository incidents) {
        this(incidents, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> handle(GetIncidentsQuery query) {
        var found = query.reportedBy() == null ? incidents.findAllByOrderByCreatedAtDesc()
                : incidents.findByReportedByOrderByCreatedAtDesc(query.reportedBy());
        return found.stream().filter(incident -> query.status() == null || incident.getStatus() == query.status()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Incident> handle(GetIncidentByIdQuery query) {
        return incidents.findById(query.incidentId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportableHome> handle(GetReportableDevicesQuery query) {
        if (subscriptions == null || query.userId() == null) return Optional.empty();
        return subscriptions.fetchActiveDraftOf(query.userId())
                .flatMap(draftId -> appointments.findFirstByDraftIdAndServiceTypeAndStatus(draftId,
                                ServiceType.INSTALLATION, AppointmentStatus.COMPLETED)
                        .filter(installation -> installation.getInstallationResult() == InstallationResult.APPROVED)
                        .map(installation -> new ReportableHome(draftId, installation.getDirections(),
                                installation.getContactName(), installation.getInstalledDevices())));
    }
}
