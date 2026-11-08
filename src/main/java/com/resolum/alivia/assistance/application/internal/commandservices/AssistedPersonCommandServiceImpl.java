package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssistedPersonCommandServiceImpl implements AssistedPersonCommandService {

    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final ExternalProfilesService externalProfilesService;

    public AssistedPersonCommandServiceImpl(AssistedPersonRepository assistedPersonRepository,
                                           RelativeRepository relativeRepository,
                                           ExternalProfilesService externalProfilesService) {
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.externalProfilesService = externalProfilesService;
    }

    @Override
    public Long handle(CreateAssistedPersonCommand command) {
        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        if (externalProfilesService.existsPersonProfileByDni(command.dni())) {
            throw new DniAlreadyExistsException(command.dni());
        }
        if (command.emailAddress() != null && !command.emailAddress().isBlank()
                && externalProfilesService.existsPersonProfileByEmail(command.emailAddress())) {
            throw new EmailAddressAlreadyExistsException(command.emailAddress());
        }

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

        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(personProfileId), relative);
        assistedPerson = assistedPersonRepository.save(assistedPerson);
        return assistedPerson.getId();
    }

    @Override
    public Long handle(UpdateAssistedPersonCommand command) {
        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        AssistedPerson assistedPerson = assistedPersonRepository.findById(command.assistedPersonId())
                .orElseThrow(() -> new AssistedPersonNotFoundException(command.assistedPersonId()));

        if (!assistedPerson.isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(assistedPerson.getId(), relative.getId());
        }

        externalProfilesService.updatePersonProfile(
                assistedPerson.getPersonProfileId().personProfileId(),
                command.photoData(),
                command.photoFileName(),
                command.phoneNumber(),
                command.firstName(),
                command.lastName(),
                command.birthDate()
        );

        return assistedPerson.getId();
    }
}
