package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;

import java.util.ArrayList;

public record ActuatorQuantities(int lights, int doors, int windows, int curtains) {
    public ActuatorQuantities {
        if (lights < 0 || doors < 0 || windows < 0 || curtains < 0) {
            throw new InvalidActuatorSelection("Las cantidades de actuadores no pueden ser negativas.");
        }
    }

    public ActuatorSelection toSelection() {
        var items = new ArrayList<ActuatorQuantity>();
        if (lights > 0) items.add(new ActuatorQuantity(ActuatorType.LIGHT, lights));
        if (doors > 0) items.add(new ActuatorQuantity(ActuatorType.DOOR, doors));
        if (windows > 0) items.add(new ActuatorQuantity(ActuatorType.WINDOW, windows));
        if (curtains > 0) items.add(new ActuatorQuantity(ActuatorType.CURTAIN, curtains));
        return new ActuatorSelection(items);
    }
}
