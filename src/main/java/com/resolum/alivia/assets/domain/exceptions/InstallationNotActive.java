package com.resolum.alivia.assets.domain.exceptions;

/** Devices can only be placed in an installation whose network is {@code ACTIVE}. */
public class InstallationNotActive extends RuntimeException {
    public InstallationNotActive(Long installationId) {
        super(String.format("installation with id %d is not active", installationId));
    }
}
