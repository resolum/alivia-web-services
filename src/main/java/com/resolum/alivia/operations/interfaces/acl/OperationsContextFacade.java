package com.resolum.alivia.operations.interfaces.acl;

import java.time.Instant;
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

    /**
     * The family confirmed the configuration after a viable evaluation: opens the installation order for the
     * technician who evaluated the home, unless it is already open.
     *
     * @param draftId the configuration draft of the request
     */
    void openInstallationOfConfirmedRequest(String draftId);

    /** A device installed in a home: its alias, MAC address and type ({@code MICROPHONE}, {@code SENSOR}...). */
    record HomeDevice(String alias, String macAddress, String type) {
    }

    /** A technical order of a home, with the incident it attends when it is a maintenance visit. */
    record HomeOrder(Long id, String code, String serviceType, String status, LocalDate date, LocalTime startTime,
                     LocalTime endTime, String technicianName, Instant startedAt, Instant completedAt,
                     String incidentCode, String incidentStatus) {
    }

    /**
     * A home in service: its installation closed approved.
     *
     * @param installedAt when the installation was completed
     * @param installedBy technician who installed it
     * @param orders      every technical order of the request, latest visit first
     */
    record InstalledHome(String draftId, String contactName, String contactPhone, String directions, Instant installedAt,
                         String installedBy, List<HomeDevice> devices, List<HomeOrder> orders) {
    }

    /**
     * Homes whose installation closed approved. With {@code technicianUserId}, only the homes where that
     * technician has or had an order; a user who is not a technician gets none.
     */
    List<InstalledHome> fetchInstalledHomes(Long technicianUserId);
}
