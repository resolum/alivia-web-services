package com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {
    public boolean existsByEmailAddress(EmailAddress emailAddress);

    public boolean existsByDni(Dni dni);
}
