package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.exceptions.BusinessNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateRelativeCommand;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.RelativeCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RelativeCommandServiceImpl implements RelativeCommandService {

    private final RelativeRepository relativeRepository;
    private final ExternalBusinessService externalBusinessService;
    private final ExternalProfilesService externalProfilesService;
    private final ExternalIamService externalIamService;

    public RelativeCommandServiceImpl(RelativeRepository relativeRepository,
                                     ExternalBusinessService externalBusinessService,
                                     ExternalProfilesService externalProfilesService,
                                     ExternalIamService externalIamService) {
        this.relativeRepository = relativeRepository;
        this.externalBusinessService = externalBusinessService;
        this.externalProfilesService = externalProfilesService;
        this.externalIamService = externalIamService;
    }

    @Override
    public Long handle(CreateRelativeCommand command) {
        if (!externalBusinessService.existsBusinessById(command.businessId())) {
            throw new BusinessNotFoundException(command.businessId());
        }
        if (externalProfilesService.existsPersonProfileByDni(command.dni())) {
            throw new DniAlreadyExistsException(command.dni());
        }
        if (externalProfilesService.existsPersonProfileByEmail(command.emailAddress())) {
            throw new EmailAddressAlreadyExistsException(command.emailAddress());
        }
        if (externalIamService.existsUserByEmail(command.emailAddress())) {
            throw new EmailAddressAlreadyExistsException(command.emailAddress());
        }

        Long userId = externalIamService.createRelativeUser(
                command.emailAddress(),
                command.password(),
                command.businessId(),
                command.firstName(),
                command.lastName(),
                command.phoneNumber(),
                command.dni(),
                command.birthDate()
        );

        Long personProfileId = externalProfilesService.createPersonProfile(
                command.emailAddress(),
                command.photoData(),
                command.photoFileName(),
                command.phoneNumber(),
                command.firstName(),
                command.lastName(),
                command.dni(),
                command.birthDate()
        );

        Relative relative = new Relative(
                new UserId(userId),
                new PersonProfileId(personProfileId),
                new BusinessId(command.businessId())
        );

        relative = relativeRepository.save(relative);
        return relative.getId();
    }

    @Override
    public Long handle(UpdateRelativeCommand command) {
        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        externalProfilesService.updatePersonProfile(
                relative.getPersonProfileId().personProfileId(),
                command.photoData(),
                command.photoFileName(),
                command.phoneNumber(),
                command.firstName(),
                command.lastName(),
                command.birthDate()
        );

        return relative.getId();
    }
}
