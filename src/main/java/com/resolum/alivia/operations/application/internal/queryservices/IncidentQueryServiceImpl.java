package com.resolum.alivia.operations.application.internal.queryservices;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentsQuery;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class IncidentQueryServiceImpl implements IncidentQueryService {
    private final IncidentPersistenceRepository incidents;

    public IncidentQueryServiceImpl(IncidentPersistenceRepository incidents) {
        this.incidents = incidents;
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
}
