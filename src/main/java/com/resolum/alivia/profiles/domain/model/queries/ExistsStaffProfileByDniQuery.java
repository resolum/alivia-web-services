package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;

public record ExistsStaffProfileByDniQuery(String dni) {
    public ExistsStaffProfileByDniQuery {
        if (dni == null || dni.isBlank()) {
            throw new IllegalArgumentException("DNI cannot be null or blank");
        }
    }

    public ExistsStaffProfileByDniQuery(Dni dni) {
        this(dni != null ? dni.dni() : null);
    }
}
