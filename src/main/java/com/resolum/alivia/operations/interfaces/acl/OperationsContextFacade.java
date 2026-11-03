package com.resolum.alivia.operations.interfaces.acl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * A visit of the contracting flow as the family follows it.
     *
     * @param serviceType {@code EVALUATION} or {@code INSTALLATION}
     * @param status      {@code SCHEDULED} or {@code COMPLETED}
     * @param result      the evaluation result ({@code VIABLE}, {@code VIABLE_WITH_ADAPTATION}, {@code NOT_VIABLE})
     *                    or the installation result ({@code APPROVED}, {@code REJECTED}); null while scheduled
     * @param notes       what the technician wrote when closing the visit, such as the adaptation
     */
    record ContractingVisit(String serviceType, String status, LocalDate date, LocalTime startTime, LocalTime endTime,
                            String technicianName, String directions, String result, String notes) {
    }

    /** Latest scheduled or completed evaluation and installation of a request, evaluation first. */
    List<ContractingVisit> fetchContractingVisitsOfDraft(String draftId);
}
