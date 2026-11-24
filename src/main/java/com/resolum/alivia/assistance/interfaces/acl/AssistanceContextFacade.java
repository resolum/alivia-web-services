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
}
