package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.business.interfaces.acl.BusinessContextFacade;
import org.springframework.stereotype.Service;

/**
 * Assets' view of the Business context: only whether the business that owns an asset exists.
 * The business (Alivia) owns the inventory of devices and edges; the household they end up in
 * is a different concept, reached when a device is assigned to an installation.
 */
@Service
public class ExternalBusinessService {
    private final BusinessContextFacade businessContextFacade;

    public ExternalBusinessService(BusinessContextFacade businessContextFacade) {
        this.businessContextFacade = businessContextFacade;
    }

    public boolean existsBusiness(Long businessId) {
        return businessId != null && businessContextFacade.existsBusinessById(businessId);
    }
}
