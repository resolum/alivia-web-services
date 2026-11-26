package com.resolum.alivia.iam.application.internal.outboundservices.acl;

import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import org.springframework.stereotype.Service;

import java.util.Set;

/** IAM's view of HCM: which accounts belong to a business. */
@Service("iamExternalHcmService")
public class ExternalHcmService {
    private final HcmContextFacade hcmContextFacade;

    public ExternalHcmService(HcmContextFacade hcmContextFacade) {
        this.hcmContextFacade = hcmContextFacade;
    }

    public Set<Long> fetchUserIdsOfBusiness(Long businessId) {
        return hcmContextFacade.fetchUserIdsOfBusiness(businessId);
    }
}
