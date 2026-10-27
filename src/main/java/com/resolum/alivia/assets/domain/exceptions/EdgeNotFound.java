package com.resolum.alivia.assets.domain.exceptions;

public class EdgeNotFound extends RuntimeException {
    public EdgeNotFound(Long edgeId) {
        super(String.format("edge with id %d not found",edgeId));
    }
}
