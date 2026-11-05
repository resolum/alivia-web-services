package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Lets an edge service that is up to date wait for the next version of its table instead of asking again and again.
 * The wait holds no thread: the answer arrives through the returned future.
 */
public interface EdgeTableWaitService {

    /**
     * Waits for the table of the installation of the edge to pass the version the edge already has.
     *
     * @param edgeId         edge that waits; a newer request of the same edge replaces its previous wait
     * @param installationId installation where the edge is assigned
     * @param knownVersion   version the edge already has
     * @param timeout        how long to wait at most
     * @return a future that completes with the new table as soon as its version is higher than {@code knownVersion}; with
     *         empty when the time ran out or the wait was replaced; and exceptionally with
     *         {@link com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation} when the edge stopped
     *         belonging to an active installation
     */
    CompletableFuture<Optional<EdgeAccessTable>> awaitNewerTable(Long edgeId, Long installationId, long knownVersion,
                                                                 Duration timeout);
}
