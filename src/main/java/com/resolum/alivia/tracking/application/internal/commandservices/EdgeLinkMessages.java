package com.resolum.alivia.tracking.application.internal.commandservices;

/** What the relative reads when the home loses the connection with the backend, or gets it back. */
public final class EdgeLinkMessages {

    /** The type of the notification in Communications. */
    public static final String DISCONNECTED_TYPE = "EDGE_DISCONNECTED";
    public static final String RECONNECTED_TYPE = "EDGE_RECONNECTED";

    public static final String DISCONNECTED_TITLE = "Casa sin conexión";
    public static final String RECONNECTED_TITLE = "Casa conectada de nuevo";

    /** For an edge whose kind is not known. */
    public static final String DISCONNECTED_BODY =
            "Un equipo de la casa dejó de reportar. Lo que ves es lo último que recibimos y pueden existir eventos pendientes.";
    public static final String RECONNECTED_BODY = "El equipo de la casa volvió a reportar.";

    private static final String PENDING = " Lo que ves es lo último que recibimos y pueden existir eventos pendientes.";

    private EdgeLinkMessages() {
    }

    /**
     * What stopped reporting, in words the family uses: the voice equipment (which is where a request for help starts) or
     * the equipment of the doors, windows, curtains and lights.
     *
     * @param kind {@code MICROPHONE}, {@code ACTUATOR} or {@code null} when it is not known
     */
    public static String disconnectedBody(String kind) {
        if ("MICROPHONE".equals(kind)) {
            return "El equipo de voz de la casa dejó de reportar: sus avisos, como un pedido de ayuda, pueden llegar con retraso." + PENDING;
        }
        if ("ACTUATOR".equals(kind)) {
            return "El equipo de puertas, ventanas, cortinas y luces de la casa dejó de reportar." + PENDING;
        }
        return DISCONNECTED_BODY;
    }

    public static String reconnectedBody(String kind) {
        if ("MICROPHONE".equals(kind)) return "El equipo de voz de la casa volvió a reportar.";
        if ("ACTUATOR".equals(kind)) return "El equipo de puertas, ventanas, cortinas y luces de la casa volvió a reportar.";
        return RECONNECTED_BODY;
    }
}
