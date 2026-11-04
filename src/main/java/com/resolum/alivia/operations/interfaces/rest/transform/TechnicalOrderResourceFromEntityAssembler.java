package com.resolum.alivia.operations.interfaces.rest.transform;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.AssignmentRecord;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.MaintenanceReport;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.interfaces.rest.resources.AssignmentRecordResource;
import com.resolum.alivia.operations.interfaces.rest.resources.InstalledDeviceResource;
import com.resolum.alivia.operations.interfaces.rest.resources.MaintenanceReportResource;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalAvailabilityResource;
import com.resolum.alivia.operations.interfaces.rest.resources.TechnicalOrderResource;

import java.time.format.DateTimeFormatter;

public final class TechnicalOrderResourceFromEntityAssembler {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private TechnicalOrderResourceFromEntityAssembler() {
    }

    public static TechnicalOrderResource toResourceFromEntity(Appointment order) {
        return new TechnicalOrderResource(order.getId(), order.getDraftId(), order.getServiceType().name(),
                order.getIncidentId(), order.getStatus().name(), order.slotId().value(), order.getScheduledDate(),
                order.getStartTime().format(TIME), order.getEndTime().format(TIME), order.getTechnicianId(),
                order.getTechnicianName(), order.getContactName(), order.getContactPhone(), order.getDirections(),
                order.getEvaluationResult() == null ? null : order.getEvaluationResult().name(),
                order.getInstallationResult() == null ? null : order.getInstallationResult().name(),
                order.getCompletionNotes(),
                toResource(order.getMaintenanceReport()),
                order.getAssignments().stream().map(TechnicalOrderResourceFromEntityAssembler::toResource).toList(),
                codeOf(order), order.getStartedAt(), order.getCompletedAt(),
                order.getInstalledDevices().stream()
                        .map(device -> new InstalledDeviceResource(device.alias(), device.macAddress(), device.type().name()))
                        .toList());
    }

    /** Readable code of the order, such as {@code ORD-2026-0042}, from the year of its visit and its identifier. */
    static String codeOf(Appointment order) {
        if (order.getId() == null) return null;
        return "ORD-%d-%04d".formatted(order.getScheduledDate().getYear(), order.getId());
    }

    public static TechnicalAvailabilityResource toResourceFromBlock(AvailableBlock block) {
        var slot = block.slotId();
        return new TechnicalAvailabilityResource(slot.value(), block.serviceType().name(), slot.date(),
                slot.window().start().format(TIME), slot.window().end().format(TIME), block.capacity());
    }

    private static MaintenanceReportResource toResource(MaintenanceReport report) {
        return report == null ? null : new MaintenanceReportResource(report.diagnosis(), report.maintenancePerformed(),
                report.testResult().name(), report.resolution());
    }

    private static AssignmentRecordResource toResource(AssignmentRecord record) {
        var slot = new SlotId(record.scheduledDate(), new TimeRange(record.startTime(), record.endTime()));
        return new AssignmentRecordResource(record.technicianId(), record.technicianName(), slot.value(),
                record.reason().name(), record.note(), record.assignedAt());
    }
}
