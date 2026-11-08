package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.queries.GetAllAlertsByUserIdQuery;

import java.util.List;

public interface AlertQueryService {
    List<Alert> handle(GetAllAlertsByUserIdQuery query);
}
