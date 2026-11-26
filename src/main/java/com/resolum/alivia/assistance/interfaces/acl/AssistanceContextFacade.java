package com.resolum.alivia.assistance.interfaces.acl;

/**
 * Anti-corruption layer for other bounded contexts that need to know who takes care of an assisted person.
 *
 * <p>It exposes booleans only. A person profile that belongs to no assisted person answers {@code false}.</p>
 */
public interface AssistanceContextFacade {

    /**
     * Whether the user may read the assisted person whose person profile is the given one: the relative in charge,
     * an active caregiver, or a member of the household with read access.
     */
    boolean canReadPersonProfile(Long personProfileId, Long userId);

    /**
     * Whether the user may change the assisted person whose person profile is the given one: the relative in
     * charge or an active caregiver.
     */
    boolean canOperatePersonProfile(Long personProfileId, Long userId);

    /**
     * Whether the user may read the assisted person: the relative in charge, an active caregiver, or a member of the
     * household of the person with read access. {@code false} also when the person does not exist, so a consumer that
     * answers {@code false} the same way as a missing person does not tell which ones exist.
     */
    boolean canReadAssistedPerson(Long assistedPersonId, Long userId);

    /**
     * The household where the assisted person lives; empty when the person does not exist or has not been placed in a
     * household yet. Authorization is not decided here: the consumer checks it first.
     */
    java.util.Optional<Long> fetchHouseholdIdOfAssistedPerson(Long assistedPersonId);

    /**
     * Whether the user may read at least one of the assisted persons who live in the household, which includes the care
     * network of that person (the relative in charge and the active caregivers, who are not members of the household).
     */
    boolean canReadHousehold(Long householdId, Long userId);
}
