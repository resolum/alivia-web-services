package com.resolum.alivia.operations.interfaces.rest.transform;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportedDevice;
import com.resolum.alivia.operations.interfaces.rest.resources.IncidentSummaryResource;
import com.resolum.alivia.operations.interfaces.rest.resources.ReportedDeviceResource;
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
                incident.getCreatedAt(),
                incident.code(),
                incident.getProblemType() == null ? null : incident.getProblemType().name(),
                incident.getOccurredAt(),
                toResource(incident.getDevice()),
                visits.stream().map(Appointment::getDirections).filter(java.util.Objects::nonNull).findFirst().orElse(null));
    }

    public static ReportedDeviceResource toResource(ReportedDevice device) {
        return device == null ? null : new ReportedDeviceResource(device.alias(), device.macAddress(), device.type());
    }

    /** The incident behind a maintenance order, for the technician's app. */
    public static IncidentSummaryResource toSummary(Incident incident) {
        return new IncidentSummaryResource(incident.getId(), incident.code(), incident.getStatus().name(),
                incident.getProblemType() == null ? null : incident.getProblemType().name(), incident.getDescription(),
                incident.getOccurredAt(), toResource(incident.getDevice()),
                incident.getEvidences().stream().map(evidence -> evidence.url()).toList());
    }
}
