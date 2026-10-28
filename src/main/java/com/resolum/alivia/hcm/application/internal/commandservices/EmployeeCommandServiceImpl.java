package com.resolum.alivia.hcm.application.internal.commandservices;

import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.entities.EmployeeChangeLog;
import com.resolum.alivia.hcm.domain.services.EmployeeCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeChangeLogRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;

@Service
public class EmployeeCommandServiceImpl implements EmployeeCommandService {
    private final EmployeeRepository repository;
    private final EmployeeChangeLogRepository changeLogRepository;
    private final ExternalProfilesService profiles;
    private final ExternalIamService iam;
    private final String defaultPhotoUrl;

    public EmployeeCommandServiceImpl(EmployeeRepository repository,
                                      EmployeeChangeLogRepository changeLogRepository,
                                      ExternalProfilesService profiles, ExternalIamService iam,
                                      @Value("${alivia.iam.profiles.default-photo-url}") String defaultPhotoUrl) {
        this.repository = repository;
        this.changeLogRepository = changeLogRepository;
        this.profiles = profiles;
        this.iam = iam;
        this.defaultPhotoUrl = defaultPhotoUrl;
    }

    @Override
    @Transactional
    public Employee handle(CreateEmployeeCommand command) {
        requirePositive(command.businessId(), "Business identifier");
        // Without a photo the employee gets the same default photo as any new profile.
        boolean hasPhoto = command.photoUrl() != null && !command.photoUrl().isBlank();
        var photoUrl = hasPhoto ? command.photoUrl() : defaultPhotoUrl;
        var photoPublicId = hasPhoto ? command.photoPublicId() : publicIdOf(defaultPhotoUrl);
        var profileId = profiles.createStaffProfile(command.corporateEmail(), photoUrl,
                photoPublicId, command.phoneNumber(), command.firstName(), command.lastName(),
                command.dni(), command.birthDate());
        // The account has no password yet: IAM emails the employee a link to create it.
        var userId = iam.inviteEmployee(command.corporateEmail(),
                command.firstName() + " " + command.lastName(), command.role(), profileId);
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
        var current = profiles.fetchStaffProfile(employee.getStaffProfileId())
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

    // Cloudinary public id of a photo URL: the path after /upload/ without the version and the extension.
    private static String publicIdOf(String photoUrl) {
        var path = URI.create(photoUrl).getPath();
        int upload = path.indexOf("/upload/");
        var id = upload < 0 ? path.substring(path.lastIndexOf('/') + 1)
                : path.substring(upload + "/upload/".length()).replaceFirst("^v\\d+/", "");
        int dot = id.lastIndexOf('.');
        return dot > 0 ? id.substring(0, dot) : id;
    }
}
