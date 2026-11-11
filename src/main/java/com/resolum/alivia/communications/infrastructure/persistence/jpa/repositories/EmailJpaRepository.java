package com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailJpaRepository extends JpaRepository<Email, Long> {
    List<Email> findByStatusOrderByCreatedAtDesc(EmailStatus status);
}
