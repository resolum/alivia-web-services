package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * The current version of the table of devices of the installation where an edge is assigned.
 *
 * @param installationId installation of that table
 * @param version        its current version
 */
public record EdgeTableVersion(Long installationId, long version) {
}
