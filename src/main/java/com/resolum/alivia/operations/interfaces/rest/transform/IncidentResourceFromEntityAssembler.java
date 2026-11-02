package com.resolum.alivia.operations.interfaces.rest.transform;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.interfaces.rest.resources.EvidenceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentResource;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentStatusChangeResource;
import com.resolum.alivia.operations.interfaces.rest.resources.MaintenanceVisitResource;

import java.util.List;

public final class IncidentResourceFromEntityAssembler {
    private IncidentResourceFromEntityAssembler() {
    }

    /** @param visits the maintenance orders of the incident, oldest first */
    public static IncidentResource toResourceFromEntity(Incident incident, List<Appointment> visits) {
        return new IncidentResource(incident.getId(), incident.getHouseholdId(), incident.getDeviceId(),
                incident.getReportedBy(), incident.getDescription(), incident.getSeverity().name(),
                incident.getStatus().name(), incident.getResolution(),
                incident.getEvidences().stream()
                        .map(evidence -> new EvidenceResource(evidence.url(), evidence.publicId(), evidence.attachedAt()))
                        .toList(),
                incident.getStatusHistory().stream()
                        .map(change -> new IncidentStatusChangeResource(change.status().name(), change.changedAt()))
                        .toList(),
                visits.stream()
                        .map(order -> new MaintenanceVisitResource(order.getId(), order.getScheduledDate(),
                                order.getStartTime(), order.getEndTime(), order.getTechnicianId(),
                                order.getTechnicianName(), order.getStatus().name()))
                        .toList(),
                incident.getCreatedAt());
    }
}
