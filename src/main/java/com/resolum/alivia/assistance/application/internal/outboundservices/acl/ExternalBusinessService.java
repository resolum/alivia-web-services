package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.business.interfaces.acl.BusinessContextFacade;
import org.springframework.stereotype.Service;

@Service("assistanceExternalBusinessService")
public class ExternalBusinessService {

    private final BusinessContextFacade businessContextFacade;

    public ExternalBusinessService(BusinessContextFacade businessContextFacade) {
        this.businessContextFacade = businessContextFacade;
    }

    public boolean existsBusinessById(Long businessId) {
        return businessContextFacade.existsBusinessById(businessId);
    }
}
