package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;

public record ExistsPersonProfileByDniQuery(String dni) {
    public ExistsPersonProfileByDniQuery {
        if (dni == null || dni.isBlank()) {
            throw new IllegalArgumentException("DNI cannot be null or blank");
        }
    }

    public ExistsPersonProfileByDniQuery(Dni dni) {
        this(dni != null ? dni.dni() : null);
    }
}
