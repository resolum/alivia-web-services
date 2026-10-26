package com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.profiles.domain.model.aggregates.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfilePersistenceRepository extends JpaRepository<Profile, Long> {
}
