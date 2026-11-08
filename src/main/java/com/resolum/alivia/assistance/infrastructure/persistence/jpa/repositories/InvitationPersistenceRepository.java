package com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvitationPersistenceRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findAllByAssistedPersonId(Long assistedPersonId);
    List<Invitation> findAllByCaregiverUserIdAndStatus(UserId caregiverUserId, InvitationStatus status);
    boolean existsByAssistedPersonIdAndInviteeEmailAndStatus(Long assistedPersonId, EmailAddress inviteeEmail, InvitationStatus status);
}
