package com.resolum.alivia.operations.interfaces.acl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** Anti-corruption layer of Operations for the contexts that follow a contracting request after its payment. */
public interface OperationsContextFacade {

    /**
     * The most advanced technical order scheduled for a contracting request.
     *
     * @param serviceType {@code EVALUATION} or {@code INSTALLATION}
     * @param date        day of the visit
     */
    record ScheduledVisit(String serviceType, LocalDate date) {
    }

    /**
     * Scheduled visit of each request that has one (the installation when both exist). Requests without a
     * scheduled order are left out of the map.
     */
    Map<String, ScheduledVisit> fetchScheduledVisitsOfDrafts(Collection<String> draftIds);
}
