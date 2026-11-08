package com.resolum.alivia.communications.application.internal.queryservices;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.queries.GetAllEmailsByStatusQuery;
import com.resolum.alivia.communications.domain.repositories.EmailRepository;
import com.resolum.alivia.communications.domain.services.EmailQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmailQueryServiceImpl implements EmailQueryService {

    private final EmailRepository emailRepository;

    public EmailQueryServiceImpl(EmailRepository emailRepository) {
        this.emailRepository = emailRepository;
    }

    @Override
    public List<Email> handle(GetAllEmailsByStatusQuery query) {
        return emailRepository.findByStatusOrderByCreatedAtDesc(query.status());
    }
}
