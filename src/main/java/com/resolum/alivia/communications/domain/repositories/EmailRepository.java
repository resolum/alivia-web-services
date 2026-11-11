package com.resolum.alivia.communications.domain.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;

import java.util.List;
import java.util.Optional;

public interface EmailRepository {
    Email save(Email email);
    Optional<Email> findById(Long id);
    List<Email> findByStatusOrderByCreatedAtDesc(EmailStatus status);
}
