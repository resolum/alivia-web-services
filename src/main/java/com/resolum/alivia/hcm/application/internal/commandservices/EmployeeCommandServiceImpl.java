package com.resolum.alivia.hcm.application.internal.commandservices;

import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.entities.EmployeeChangeLog;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.services.EmployeeCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeChangeLogRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;

@Service
public class EmployeeCommandServiceImpl implements EmployeeCommandService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmployeeRepository repository;
    private final EmployeeChangeLogRepository changeLogRepository;
    private final ProfilesContextFacade profiles;
    private final IamContextFacade iam;

    public EmployeeCommandServiceImpl(EmployeeRepository repository,
                                      EmployeeChangeLogRepository changeLogRepository,
                                      ProfilesContextFacade profiles, IamContextFacade iam) {
        this.repository = repository;
        this.changeLogRepository = changeLogRepository;
        this.profiles = profiles;
        this.iam = iam;
    }

    @Override
    @Transactional
    public Employee handle(CreateEmployeeCommand command) {
        requirePositive(command.businessId(), "Business identifier");
        var profileId = profiles.createStaffProfile(command.corporateEmail(), command.photoUrl(),
                command.photoPublicId(), command.phoneNumber(), command.firstName(), command.lastName(),
                command.dni(), command.birthDate());
        var userId = iam.registerCompanyUser(command.corporateEmail(), temporaryPassword(),
                command.firstName() + " " + command.lastName(), command.phoneNumber(),
                toIamRole(command.role()), command.businessId());
        return repository.save(new Employee(command.businessId(), userId, profileId, command.role()));
    }

    @Override
    @Transactional
    public Employee handle(UpdateEmployeeCommand command) {
        var employee = repository.findById(command.employeeId())
                .orElseThrow(() -> new EmployeeNotFound(command.employeeId()));
        if (!employee.belongsTo(command.businessId())) {
            throw new EmployeeNotInBusiness(command.employeeId(), command.businessId());
        }
        var current = profiles.fetchStaffProfileById(employee.getStaffProfileId())
                .orElseThrow(() -> new StaffProfileNotFoundException(employee.getStaffProfileId()));
        var changes = new LinkedHashMap<String, String>();
        recordChange(changes, "firstName", current.firstName(), command.firstName());
        recordChange(changes, "lastName", current.lastName(), command.lastName());
        recordChange(changes, "birthDate", current.birthDate(), command.birthDate());
        recordChange(changes, "phoneNumber", current.phoneNumber(), command.phoneNumber());
        recordChange(changes, "photoUrl", current.photoUrl(), command.photoUrl());
        recordChange(changes, "photoPublicId", current.photoPublicId(), command.photoPublicId());
        recordChange(changes, "role", employee.getRole(), command.role());
        profiles.updateStaffProfile(employee.getStaffProfileId(),
                valueOr(command.firstName(), current.firstName()), valueOr(command.lastName(), current.lastName()),
                command.birthDate() == null ? current.birthDate() : command.birthDate(),
                valueOr(command.phoneNumber(), current.phoneNumber()),
                valueOr(command.photoUrl(), current.photoUrl()),
                valueOr(command.photoPublicId(), current.photoPublicId()));
        employee.changeRole(command.role());
        var saved = repository.save(employee);
        if (!changes.isEmpty()) {
            changeLogRepository.save(new EmployeeChangeLog(saved.getId(), saved.getBusinessId(), changes));
        }
        return saved;
    }

    private static String toIamRole(EmployeeRole role) {
        if (role == null) throw new IllegalArgumentException("Employee role is required.");
        return role == EmployeeRole.SUBSCRIPTION_MANAGER ? "SUBSCRIPTION_MANAGER" : "TECHNICIAN";
    }

    private static String temporaryPassword() {
        return "Tmp!A9" + Long.toUnsignedString(RANDOM.nextLong(), 36) + "z7";
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static void recordChange(LinkedHashMap<String, String> changes, String field,
                                     Object previous, Object requested) {
        if (requested != null && !requested.equals(previous)) {
            changes.put(field + ".previous", String.valueOf(previous));
            changes.put(field + ".current", String.valueOf(requested));
        }
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive.");
    }
}
