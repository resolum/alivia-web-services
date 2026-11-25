package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;

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
