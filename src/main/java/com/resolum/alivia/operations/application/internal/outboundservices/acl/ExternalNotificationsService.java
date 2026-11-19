package com.resolum.alivia.operations.application.internal.outboundservices.acl;

import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.events.TechnicalOrderAssignedEvent.Change;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/** Tells technicians, through Communications, about the orders they receive or that move. */
@Service("operationsExternalNotificationsService")
public class ExternalNotificationsService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationsContextFacade notifications;
    private final ExternalHcmService hcm;

    public ExternalNotificationsService(NotificationsContextFacade notifications, ExternalHcmService hcm) {
        this.notifications = notifications;
        this.hcm = hcm;
    }

    /** Notifies the technician of the order; does nothing when the technician has no user account. */
    public boolean notifyTechnician(Appointment order, Change change) {
        var userId = hcm.fetchUserIdOfTechnician(order.getTechnicianId());
        if (userId.isEmpty()) return false;
        var when = order.getScheduledDate().format(DATE) + " de " + order.getStartTime().format(TIME)
                + " a " + order.getEndTime().format(TIME);
        if (change == Change.RESCHEDULED) {
            notifications.notifyUser(userId.get(), "ORDER_RESCHEDULED", String.valueOf(order.getId()),
                    "Orden reprogramada", labelOf(order.getServiceType()) + " pasó al " + when + ".");
        } else {
            notifications.notifyUser(userId.get(), "ASSIGNMENT_CREATED", String.valueOf(order.getId()),
                    "Nueva orden asignada", labelOf(order.getServiceType()) + " el " + when + ".");
        }
        return true;
    }

    static String labelOf(ServiceType type) {
        return switch (type) {
            case EVALUATION -> "Evaluación técnica";
            case INSTALLATION -> "Instalación";
            case MAINTENANCE -> "Mantenimiento";
        };
    }
}
