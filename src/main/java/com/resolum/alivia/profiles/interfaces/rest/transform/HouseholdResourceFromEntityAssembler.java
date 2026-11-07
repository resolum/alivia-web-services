package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.interfaces.rest.resources.HouseholdResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Builds the household resource with its members.
 */
public final class HouseholdResourceFromEntityAssembler {

    private HouseholdResourceFromEntityAssembler() {
    }

    /**
     * Maps the household to its REST representation.
     *
     * @param entity the household
     * @return the household resource
     */
    public static HouseholdResource toResourceFromEntity(Household entity) {
        var address = entity.getAddress();
        Long responsibleAccountId = entity.getMembers().stream()
                .filter(m -> m.getResponsibility() == HouseholdResponsibility.PRINCIPAL)
                .map(HouseholdMember::getUserId)
                .map(UserId::userId)
                .findFirst()
                .orElse(null);

        return new HouseholdResource(
                entity.getId(),
                entity.getAlias(),
                address != null ? address.formattedAddress() : null,
                address != null ? address.latitude() : null,
                address != null ? address.longitude() : null,
                address != null ? address.placeId() : null,
                entity.getContactPhone() != null ? entity.getContactPhone().phoneNumber() : null,
                entity.getContactEmail() != null ? entity.getContactEmail().email() : null,
                entity.getMembers().stream()
                        .map(HouseholdMemberResourceFromEntityAssembler::toResourceFromEntity)
                        .toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                responsibleAccountId);
    }
}
