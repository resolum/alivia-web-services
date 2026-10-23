package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;

public record ActuatorQuantity(ActuatorType type, int quantity) {
    public ActuatorQuantity {
        if (type == null || quantity <= 0) {
            throw new InvalidActuatorSelection("Cada actuador debe tener un tipo y una cantidad positiva.");
        }
    }
}
