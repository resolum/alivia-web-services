package com.resolum.alivia.hcm.interfaces.rest.transform;

import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.interfaces.rest.resources.EmployeeResource;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import org.springframework.stereotype.Component;

@Component
public class EmployeeResourceAssembler {
    private final ProfilesContextFacade profiles;

    public EmployeeResourceAssembler(ProfilesContextFacade profiles) {
        this.profiles = profiles;
    }

    public EmployeeResource toResource(Employee employee) {
        var profile = profiles.fetchStaffProfileById(employee.getStaffProfileId())
                .orElseThrow(() -> new StaffProfileNotFoundException(employee.getStaffProfileId()));
        return new EmployeeResource(employee.getId(), employee.getBusinessId(), employee.getUserId(),
                employee.getStaffProfileId(), profile.emailAddress(), profile.photoUrl(),
                profile.photoPublicId(), profile.phoneNumber(), profile.firstName(), profile.lastName(),
                profile.dni(), profile.birthDate(), employee.getRole(), employee.getStatus(),
                employee.getCreatedAt(), employee.getUpdatedAt());
    }
}
