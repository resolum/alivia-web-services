package com.resolum.alivia.communications.application.internal.queryservices;

import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.queries.GetAllAlertsByUserIdQuery;
import com.resolum.alivia.communications.domain.repositories.AlertRepository;
import com.resolum.alivia.communications.domain.services.AlertQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlertQueryServiceImpl implements AlertQueryService {

    private final AlertRepository alertRepository;

    public AlertQueryServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public List<Alert> handle(GetAllAlertsByUserIdQuery query) {
        return alertRepository.findByRecipientIdOrderByCreatedAtDesc(new UserId(query.userId()));
    }
}
