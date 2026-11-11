package com.resolum.alivia.assistance.infrastructure.adapters;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.assistance.domain.repositories.InvitationRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.InvitationPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InvitationRepositoryImpl implements InvitationRepository {

    private final InvitationPersistenceRepository repository;

    public InvitationRepositoryImpl(InvitationPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Invitation save(Invitation invitation) {
        return repository.save(invitation);
    }

    @Override
    public Optional<Invitation> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Invitation> findAllByAssistedPersonId(Long assistedPersonId) {
        return repository.findAllByAssistedPersonId(assistedPersonId);
    }

    @Override
    public List<Invitation> findAllByCaregiverUserIdAndStatus(UserId caregiverUserId, InvitationStatus status) {
        return repository.findAllByCaregiverUserIdAndStatus(caregiverUserId, status);
    }

    @Override
    public boolean existsByAssistedPersonIdAndInviteeEmailAndStatus(Long assistedPersonId, EmailAddress inviteeEmail, InvitationStatus status) {
        return repository.existsByAssistedPersonIdAndInviteeEmailAndStatus(assistedPersonId, inviteeEmail, status);
    }
}
