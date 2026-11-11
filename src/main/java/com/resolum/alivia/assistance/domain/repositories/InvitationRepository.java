package com.resolum.alivia.assistance.domain.repositories;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository {
    Invitation save(Invitation invitation);
    Optional<Invitation> findById(Long id);
    List<Invitation> findAllByAssistedPersonId(Long assistedPersonId);
    List<Invitation> findAllByCaregiverUserIdAndStatus(UserId caregiverUserId, InvitationStatus status);
    boolean existsByAssistedPersonIdAndInviteeEmailAndStatus(Long assistedPersonId, EmailAddress inviteeEmail, InvitationStatus status);
}
