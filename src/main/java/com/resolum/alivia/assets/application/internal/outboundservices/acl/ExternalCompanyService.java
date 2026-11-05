package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.business.interfaces.acl.BusinessContextFacade;
import org.springframework.stereotype.Service;

/** Assets' view of the Business context: only whether the business that owns an asset exists. */
@Service
public class ExternalCompanyService {
    private final BusinessContextFacade businessContextFacade;

    public ExternalCompanyService(BusinessContextFacade businessContextFacade) {
        this.businessContextFacade = businessContextFacade;
    }

    public boolean existsBusiness(Long businessId) {
        return businessId != null && businessContextFacade.existsBusinessById(businessId);
    }
}
