package com.resolum.alivia.tracking.application.internal.eventhandlers;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.events.DeviceEventRecordedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandAction;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Tells the relatives in charge, on their phone, what a voice command did in the home: the same thing the history
 * shows. Only how a command ended is told (not the request too), once per event. A failure here never undoes what was
 * kept, and a late event (synced after a loss of connection) is not pushed: it is already old news.
 */
@Slf4j
@Component
public class DeviceActionNotifier {

    static final String TYPE = "DEVICE_ACTION";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final ExternalAssetsService assets;
    private final ExternalNotificationsService notifications;
    private final TrackingPolicy policy;

    public DeviceActionNotifier(ExternalAssetsService assets, ExternalNotificationsService notifications,
                                TrackingPolicy policy) {
        this.assets = assets;
        this.notifications = notifications;
        this.policy = policy;
    }

    @EventListener
    public void on(DeviceEventRecordedEvent recorded) {
        var event = recorded.event();
        if (event.type() != EventType.COMMAND_RESULT && event.type() != EventType.COMMAND_REJECTED) return;
        if (event.origin() == EventOrigin.SYNCED) return;
        try {
            var alias = event.deviceId() == null ? null : assets.fetchReportingEdge(event.edgeId())
                    .map(edge -> edge.deviceAliases().get(event.deviceId())).orElse(null);
            var message = messageOf(event, alias);
            for (Long userId : notifications.relativesOf(event.householdId())) {
                notifications.notifyOnce(userId, TYPE, "cmd-" + event.edgeId() + "-" + event.eventId(),
                        message[0], message[1]);
            }
        } catch (RuntimeException failure) {
            log.warn("The relatives could not be told about the command {} of the edge {}.", event.eventId(),
                    event.edgeId(), failure);
        }
    }

    /** The title and the text of the notice. */
    String[] messageOf(DeviceEvent event, String alias) {
        var name = alias == null ? "Un dispositivo" : alias;
        var at = "a las " + HOUR.format(event.occurredAt().atZone(policy.zone()));
        if (event.type() == EventType.COMMAND_REJECTED) {
            return new String[]{"Orden no ejecutada",
                    "No se ejecutó una orden de voz " + at + (event.detail() == null ? "." : ": " + event.detail() + ".")};
        }
        if (event.result() == EventResult.SUCCESS) {
            return new String[]{name + " " + participle(event.action()), "Por comando de voz, " + at + "."};
        }
        var why = event.result() == EventResult.FAILED_TIMEOUT ? "el actuador no respondió a tiempo" : "el actuador informó una falla";
        return new String[]{"No se pudo " + verb(event.action()) + " " + lowerFirst(name),
                "Orden de voz " + at + ": " + why + "."};
    }

    // Puerta, ventana, cortina y luz: todos femeninos.
    private static String participle(CommandAction action) {
        if (action == null) return "accionada";
        return switch (action) {
            case OPEN -> "abierta";
            case CLOSE -> "cerrada";
            case ON -> "encendida";
            case OFF -> "apagada";
        };
    }

    private static String verb(CommandAction action) {
        if (action == null) return "accionar";
        return switch (action) {
            case OPEN -> "abrir";
            case CLOSE -> "cerrar";
            case ON -> "encender";
            case OFF -> "apagar";
        };
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
