package com.resolum.alivia.operations.interfaces.rest.transform;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.interfaces.rest.resources.EvidenceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentResource;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentStatusChangeResource;

public final class IncidentResourceFromEntityAssembler {
    private IncidentResourceFromEntityAssembler() {
    }

    public static IncidentResource toResourceFromEntity(Incident incident) {
        return new IncidentResource(incident.getId(), incident.getHouseholdId(), incident.getDeviceId(),
                incident.getReportedBy(), incident.getDescription(), incident.getSeverity().name(),
                incident.getStatus().name(), incident.getResolution(),
                incident.getEvidences().stream()
                        .map(evidence -> new EvidenceResource(evidence.url(), evidence.publicId(), evidence.attachedAt()))
                        .toList(),
                incident.getStatusHistory().stream()
                        .map(change -> new IncidentStatusChangeResource(change.status().name(), change.changedAt()))
                        .toList(),
                incident.getCreatedAt());
    }
}
