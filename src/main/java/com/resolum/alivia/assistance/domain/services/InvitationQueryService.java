package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetInvitationsByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetPendingInvitationsByCaregiverUserIdQuery;

import java.util.List;
import java.util.Optional;

public interface InvitationQueryService {
    Optional<Invitation> handle(GetInvitationByIdQuery query);
    List<Invitation> handle(GetInvitationsByAssistedPersonIdQuery query);
    List<Invitation> handle(GetPendingInvitationsByCaregiverUserIdQuery query);
}
