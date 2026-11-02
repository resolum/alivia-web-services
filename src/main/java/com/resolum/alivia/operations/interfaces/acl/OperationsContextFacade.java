package com.resolum.alivia.operations.interfaces.acl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** Anti-corruption layer of Operations for the contexts that follow a contracting request after its payment. */
public interface OperationsContextFacade {

    /**
     * The most advanced technical order of a contracting request that was not canceled.
     *
     * @param serviceType          {@code EVALUATION} or {@code INSTALLATION}
     * @param date                 day of the visit
     * @param installationApproved true once the installation visit closed approved
     */
    record ScheduledVisit(String serviceType, LocalDate date, boolean installationApproved) {
    }

    /**
     * Furthest scheduled or completed visit of each request (the installation when both exist). Requests
     * without one are left out of the map.
     */
    Map<String, ScheduledVisit> fetchScheduledVisitsOfDrafts(Collection<String> draftIds);
}
