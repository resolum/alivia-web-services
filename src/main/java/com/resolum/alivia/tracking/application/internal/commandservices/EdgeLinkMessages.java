package com.resolum.alivia.tracking.application.internal.commandservices;

/** What the relative reads when the home loses the connection with the backend, or gets it back. */
public final class EdgeLinkMessages {

    /** The type of the notification in Communications. */
    public static final String DISCONNECTED_TYPE = "EDGE_DISCONNECTED";
    public static final String RECONNECTED_TYPE = "EDGE_RECONNECTED";

    public static final String DISCONNECTED_TITLE = "Casa sin conexión";
    public static final String DISCONNECTED_BODY =
            "Un equipo de la casa dejó de reportar. Lo que ves es lo último que recibimos y pueden existir eventos pendientes.";

    public static final String RECONNECTED_TITLE = "Casa conectada de nuevo";
    public static final String RECONNECTED_BODY = "El equipo de la casa volvió a reportar.";

    private EdgeLinkMessages() {
    }
}
