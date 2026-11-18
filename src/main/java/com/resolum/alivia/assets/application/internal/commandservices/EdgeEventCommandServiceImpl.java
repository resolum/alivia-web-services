package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.ReportEdgeEventCommand;
import com.resolum.alivia.assets.domain.services.EdgeEventCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the relatives in charge of a home what its edge reported. The notification of each relative is kept once per
 * event id, so an edge that resends an event it could not confirm (network loss) does not notify twice.
 */
@Service
public class EdgeEventCommandServiceImpl implements EdgeEventCommandService {
    private final HouseholdDeviceRegistryRepository registries;
    private final ExternalNotificationsService notifications;

    public EdgeEventCommandServiceImpl(HouseholdDeviceRegistryRepository registries,
                                       ExternalNotificationsService notifications) {
        this.registries = registries;
        this.notifications = notifications;
    }

    @Override
    @Transactional(readOnly = true)
    public int handle(ReportEdgeEventCommand command) {
        var registry = registries.findByAssignedEdgeId(command.edgeId())
                .filter(HouseholdDeviceRegistry::isActive)
                .orElseThrow(() -> new EdgeWithoutInstallation(command.edgeId()));
        var device = registry.activeAssignmentOf(command.deviceId())
                .map(assignment -> assignment.getDevice().getAlias())
                .orElse(null);
        var message = EdgeEventMessage.of(command, device);
        var sourceId = "edge-" + command.eventId();
        int notified = 0;
        for (Long userId : notifications.relativesOf(registry.getHouseholdId())) {
            if (notifications.notifyOnce(userId, message.type(), sourceId, message.title(), message.body())) notified++;
        }
        return notified;
    }

    /** What the relative reads, in the notification center and on the phone. */
    record EdgeEventMessage(String type, String title, String body) {
        static EdgeEventMessage of(ReportEdgeEventCommand command, String device) {
            var detail = command.detail();
            return switch (command.type()) {
                case HELP_REQUESTED -> new EdgeEventMessage("HELP_REQUESTED", "Solicitud de asistencia",
                        detail == null ? "Tu familiar pidió ayuda desde casa."
                                : "Tu familiar pidió ayuda desde casa: «" + detail + "».");
                case LOW_BATTERY -> new EdgeEventMessage("LOW_BATTERY", "Batería baja",
                        (device == null ? "Un dispositivo de la casa" : device)
                                + (command.batteryPct() == null ? " tiene poca batería." : " tiene " + command.batteryPct() + "% de batería."));
                case DEVICE_FAILURE -> new EdgeEventMessage("ACTUATOR_FAILURE", "Falla en un dispositivo",
                        (device == null ? "Un dispositivo de la casa" : device) + " no respondió"
                                + (detail == null ? "." : ": " + detail + "."));
            };
        }
    }
}
