package com.resolum.alivia.communications.domain.model.events;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;

public class AlertCreatedEvent {
    private final Alert alert;

    public AlertCreatedEvent(Alert alert) {
        this.alert = alert;
    }

    public Alert getAlert() {
        return alert;
    }

    public Long getAlertId() {
        return alert != null ? alert.getId() : null;
    }
}
