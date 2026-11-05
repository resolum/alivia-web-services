package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * Life cycle of the network of an installation. It starts {@code PENDING_CONFIRMATION} while the technicians of the
 * business place the devices; their confirmation makes it {@code ACTIVE}, and the household takes over.
 */
public enum NetworkStatus {

    PENDING_CONFIRMATION,
    ACTIVE,
    DEACTIVATED,
    DISMANTLED,
}
