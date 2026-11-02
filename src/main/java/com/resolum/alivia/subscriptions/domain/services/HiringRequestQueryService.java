package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;

import java.util.List;

public interface HiringRequestQueryService {
    List<HiringRequest> handle(GetHiringRequestsQuery query);
}
