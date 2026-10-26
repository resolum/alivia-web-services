package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.interfaces.rest.resources.BusinessProfileResource;

public final class BusinessProfileResourceFromEntityAssembler {
    private BusinessProfileResourceFromEntityAssembler() {
    }

    public static BusinessProfileResource toResourceFromEntity(BusinessProfile entity) {
        return new BusinessProfileResource(
                entity.getId(),
                entity.getEmailAddress() != null ? entity.getEmailAddress().email() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoUrl() : null,
                entity.getPhoto() != null ? entity.getPhoto().photoPublicId() : null,
                entity.getPhoneNumber() != null ? entity.getPhoneNumber().phoneNumber() : null,
                entity.getRuc() != null ? entity.getRuc().ruc() : null,
                entity.getBusinessName() != null ? entity.getBusinessName().businessName() : null,
                entity.getLegalName() != null ? entity.getLegalName().name() : null,
                entity.getDescription(),
                entity.getMainActivity(),
                entity.getBusinessPhone() != null ? entity.getBusinessPhone().phoneNumber() : null,
                entity.getBusinessHours(),
                entity.getSlogan(),
                entity.getSupportEmail() != null ? entity.getSupportEmail().email() : null,
                entity.getAddress()
        );
    }
}
