package com.resolum.alivia.hcm.application.internal.outboundservices.acl;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.util.Map;

/** HCM's view of IAM: the corporate account of each employee. */
@Service("hcmExternalIamService")
public class ExternalIamService {
    private static final Map<EmployeeRole, String> ROLE_LABELS = Map.of(
            EmployeeRole.TECH, "Soporte técnico",
            EmployeeRole.SUBSCRIPTION_MANAGER, "Gestor de suscripciones",
            EmployeeRole.CLEANING, "Limpieza");

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /**
     * Creates the employee's corporate account without a password; IAM emails the invitation link
     * where the employee chooses it.
     *
     * @return the id of the new account
     */
    public Long inviteEmployee(String corporateEmail, String fullName, EmployeeRole role, Long staffProfileId) {
        if (role == null) throw new IllegalArgumentException("Employee role is required.");
        return iamContextFacade.inviteCompanyUser(corporateEmail, fullName, ROLE_LABELS.get(role),
                toIamRole(role), staffProfileId);
    }

    private static String toIamRole(EmployeeRole role) {
        return role == EmployeeRole.SUBSCRIPTION_MANAGER ? "SUBSCRIPTION_MANAGER" : "TECHNICIAN";
    }
}
