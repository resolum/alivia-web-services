package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;

import java.util.EnumSet;
import java.util.List;

public record ActuatorSelection(List<ActuatorQuantity> items) {
    public ActuatorSelection {
        if (items == null) {
            throw new InvalidActuatorSelection("La selección de actuadores es obligatoria.");
        }
        items = List.copyOf(items);
        var seen = EnumSet.noneOf(ActuatorType.class);
        for (var item : items) {
            if (item == null || !seen.add(item.type())) {
                throw new InvalidActuatorSelection("No se puede repetir un tipo de actuador.");
            }
        }
    }

    public int total() {
        return Math.toIntExact(items.stream().mapToLong(ActuatorQuantity::quantity).sum());
    }

    public int quantityOf(ActuatorType type) {
        return items.stream().filter(item -> item.type() == type)
                .mapToInt(ActuatorQuantity::quantity).sum();
    }
}
