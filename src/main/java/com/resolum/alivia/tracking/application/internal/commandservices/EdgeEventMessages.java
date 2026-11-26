package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** What the relative reads, in the notification center and on the phone, about an urgent event. */
final class EdgeEventMessages {

    private EdgeEventMessages() {
    }

    /**
     * @param type  the type of the notification in Communications
     * @param title the title
     * @param body  the text
     */
    record Message(String type, String title, String body) {
    }

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_AND_HOUR = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    /** The type of the notification in Communications for a request for help that reached the backend late. */
    static final String HELP_REQUEST_DELAYED = "HELP_REQUEST_DELAYED";

    /**
     * What the relative reads when a request for help reaches the backend long after it was made, because the home was
     * without connection: when it was made, and that the notice is late, so it is not mistaken for an emergency that is
     * happening now.
     *
     * @param zone the time zone of the home, in which the hour is told
     */
    static Message helpRequestDelayed(DeviceEvent event, ZoneId zone) {
        var happened = event.occurredAt().atZone(zone);
        var sameDay = happened.toLocalDate().equals(event.receivedAt().atZone(zone).toLocalDate());
        var when = sameDay ? "a las " + HOUR.format(happened) : "el " + DAY_AND_HOUR.format(happened);
        var detail = event.detail();
        return new Message(HELP_REQUEST_DELAYED, "Pedido de ayuda con retraso",
                "Tu familiar pidió ayuda " + when + ", pero el aviso llegó con retraso porque la casa estuvo sin conexión."
                        + (detail == null ? "" : " Dijo: «" + detail + "»."));
    }

    /**
     * @param device the name of the device involved, or {@code null} when there is none or it is not known
     * @throws IllegalArgumentException when the event is not one of the urgent ones
     */
    static Message of(DeviceEvent event, String device) {
        var detail = event.detail();
        var name = device == null ? "Un dispositivo de la casa" : device;
        return switch (event.type()) {
            case HELP_REQUESTED -> new Message("HELP_REQUESTED", "Solicitud de asistencia",
                    detail == null ? "Tu familiar pidió ayuda desde casa."
                            : "Tu familiar pidió ayuda desde casa: «" + detail + "».");
            case LOW_BATTERY -> new Message("LOW_BATTERY", "Batería baja",
                    event.batteryPct() == null ? name + " tiene poca batería."
                            : name + " tiene " + event.batteryPct() + "% de batería.");
            case DEVICE_FAILURE -> new Message("ACTUATOR_FAILURE", "Falla en un dispositivo",
                    name + " no respondió" + (detail == null ? "." : ": " + detail + "."));
            default -> throw new IllegalArgumentException(event.type() + " is not an urgent event.");
        };
    }
}
