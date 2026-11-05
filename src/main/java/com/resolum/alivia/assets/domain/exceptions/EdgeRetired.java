package com.resolum.alivia.assets.domain.exceptions;

/** A retired edge is out of the inventory for good, so it cannot receive a credential. */
public class EdgeRetired extends RuntimeException {
    public EdgeRetired(Long edgeId) {
        super(String.format("edge with id %d is retired and cannot receive a credential", edgeId));
    }
}
