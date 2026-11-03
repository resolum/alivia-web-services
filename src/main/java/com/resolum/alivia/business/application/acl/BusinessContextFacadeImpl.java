package com.resolum.alivia.business.application.acl;

import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.services.BusinessQueryService;
import com.resolum.alivia.business.interfaces.acl.BusinessContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BusinessContextFacadeImpl implements BusinessContextFacade {

    private final BusinessQueryService businessQueryService;

    public BusinessContextFacadeImpl(BusinessQueryService businessQueryService) {
        this.businessQueryService = businessQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessData> fetchBusinessById(Long businessId) {
        return businessQueryService.handle(new GetBusinessByIdQuery(businessId))
                .map(b -> new BusinessData(
                        b.getId(),
                        b.getAdministrator() != null ? b.getAdministrator().getId() : null,
                        b.getBusinessProfileId() != null ? b.getBusinessProfileId().businessProfileId() : null,
                        b.getCreatedAt()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBusinessById(Long businessId) {
        return businessQueryService.handle(new GetBusinessByIdQuery(businessId)).isPresent();
    }
}
