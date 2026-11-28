package com.resolum.alivia.operations.domain.exceptions;

public class InstallationAssetTypeMismatch extends RuntimeException {
    public InstallationAssetTypeMismatch() {
        super("La MAC no corresponde al tipo de dispositivo seleccionado.");
    }
}
