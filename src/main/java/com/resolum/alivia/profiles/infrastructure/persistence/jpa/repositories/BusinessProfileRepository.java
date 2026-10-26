package com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
    boolean existsByEmailAddress(EmailAddress emailAddress);
    boolean existsByRuc(Ruc ruc);
    boolean existsByBusinessName(BusinessName businessName);
    boolean existsByLegalName(LegalName legalName);
}