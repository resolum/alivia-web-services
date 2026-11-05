package com.resolum.alivia.assets.domain.exceptions;

public class InstallationNotFound extends RuntimeException {
    public InstallationNotFound(Long installationId) {
        super(String.format("installation with id %d not found", installationId));
    }
}
