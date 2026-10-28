package com.resolum.alivia.support.application.internal.outboundservices.acl;

import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import com.resolum.alivia.support.domain.model.valueobjects.TechnicianAvailability;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** Support's view of HCM: which technicians work on a date and in which blocks. */
@Service("supportExternalHcmService")
public class ExternalHcmService {
    private final HcmContextFacade hcmContextFacade;

    public ExternalHcmService(HcmContextFacade hcmContextFacade) {
        this.hcmContextFacade = hcmContextFacade;
    }

    public List<TechnicianAvailability> fetchTechniciansWorkingOn(LocalDate date) {
        return hcmContextFacade.fetchTechniciansWorkingOn(date).stream()
                .map(technician -> new TechnicianAvailability(technician.employeeId(), technician.fullName(), technician.shifts()))
                .toList();
    }
}
