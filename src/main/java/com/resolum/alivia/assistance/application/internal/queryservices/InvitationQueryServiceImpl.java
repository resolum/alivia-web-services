package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.assistance.domain.repositories.InvitationRepository;
import com.resolum.alivia.assistance.domain.services.InvitationQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InvitationQueryServiceImpl implements InvitationQueryService {

    private final InvitationRepository invitationRepository;

    public InvitationQueryServiceImpl(InvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    @Override
    public Optional<Invitation> handle(GetInvitationByIdQuery query) {
        return invitationRepository.findById(query.invitationId());
    }

    @Override
    public List<Invitation> handle(GetInvitationsByAssistedPersonIdQuery query) {
        return invitationRepository.findAllByAssistedPersonId(query.assistedPersonId());
    }

    @Override
    public List<Invitation> handle(GetPendingInvitationsByCaregiverUserIdQuery query) {
        return invitationRepository.findAllByCaregiverUserIdAndStatus(new UserId(query.caregiverUserId()), InvitationStatus.PENDING);
    }
}
