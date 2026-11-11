package com.resolum.alivia.communications.infrastructure.adapters;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;
import com.resolum.alivia.communications.domain.repositories.EmailRepository;
import com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories.EmailJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EmailRepositoryImpl implements EmailRepository {

    private final EmailJpaRepository jpaRepository;

    public EmailRepositoryImpl(EmailJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Email save(Email email) {
        return jpaRepository.save(email);
    }

    @Override
    public Optional<Email> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Email> findByStatusOrderByCreatedAtDesc(EmailStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtDesc(status);
    }
}
