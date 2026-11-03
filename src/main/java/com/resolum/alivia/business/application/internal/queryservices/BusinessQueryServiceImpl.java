package com.resolum.alivia.business.application.internal.queryservices;

import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByAdministratorIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.repositories.BusinessRepository;
import com.resolum.alivia.business.domain.services.BusinessQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class BusinessQueryServiceImpl implements BusinessQueryService {

    private final BusinessRepository businessRepository;

    public BusinessQueryServiceImpl(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Business> handle(GetBusinessByAdministratorIdQuery query) {
        return businessRepository.findByAdministratorId(query.administratorId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Business> handle(GetBusinessByIdQuery query) {
        return businessRepository.findById(query.businessId());
    }
}
