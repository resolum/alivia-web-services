package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdMemberResource;

/**
 * Builds the household member resource.
 */
public final class HouseholdMemberResourceFromEntityAssembler {

    private HouseholdMemberResourceFromEntityAssembler() {
    }

    /**
     * Maps the membership to its REST representation.
     *
     * @param entity the household member
     * @return the household member resource
     */
    public static HouseholdMemberResource toResourceFromEntity(HouseholdMember entity) {
        return toResourceFromEntity(entity, null);
    }

    /**
     * Maps the membership to its REST representation with the member's name.
     *
     * @param entity   the household member
     * @param fullName the member's first and last names, or null when unknown
     * @return the household member resource
     */
    public static HouseholdMemberResource toResourceFromEntity(HouseholdMember entity, String fullName) {
        return new HouseholdMemberResource(
                entity.getId(),
                entity.getUserId().userId(),
                entity.getResponsibility() != null ? entity.getResponsibility().name() : null,
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                fullName);
    }
}
