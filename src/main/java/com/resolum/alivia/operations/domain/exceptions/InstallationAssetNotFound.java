package com.resolum.alivia.operations.domain.exceptions;

public class InstallationAssetNotFound extends RuntimeException {
    public InstallationAssetNotFound() {
        super("La MAC no está registrada en el inventario del negocio.");
    }
}
