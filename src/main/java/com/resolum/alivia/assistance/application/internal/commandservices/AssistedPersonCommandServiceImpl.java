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
import com.resolum.alivia.assistance.domain.model.valueobjects.EmergencyContact;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
        Relative relative = null;
        if (command.relativeId() != null) {
            relative = relativeRepository.findById(command.relativeId())
                    .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));
        } else if (command.userId() != null) {
            UserId userId = new UserId(command.userId());
            relative = relativeRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        Relative newRel = new Relative(userId, new PersonProfileId(1L), new BusinessId(1L));
                        return relativeRepository.save(newRel);
                    });
        } else {
            throw new RelativeNotFoundException(null);
        }

        String effectiveDni = command.dni();
        if (effectiveDni == null || effectiveDni.isBlank()) {
            do {
                effectiveDni = String.format("%08d", ThreadLocalRandom.current().nextInt(10000000, 99999999));
            } while (externalProfilesService.existsPersonProfileByDni(effectiveDni));
        } else if (externalProfilesService.existsPersonProfileByDni(effectiveDni)) {
            throw new DniAlreadyExistsException(effectiveDni);
        }

        String effectiveEmail = command.emailAddress();
        if (effectiveEmail == null || effectiveEmail.isBlank()) {
            effectiveEmail = "asistido." + effectiveDni + "@alivia.local";
        }
        if (externalProfilesService.existsPersonProfileByEmail(effectiveEmail)) {
            if (command.emailAddress() != null && !command.emailAddress().isBlank()) {
                throw new EmailAddressAlreadyExistsException(command.emailAddress());
            }
            effectiveEmail = "asistido." + effectiveDni + "." + UUID.randomUUID().toString().substring(0, 4) + "@alivia.local";
        }

        byte[] effectivePhotoData = (command.photoData() != null && command.photoData().length > 0)
                ? command.photoData()
                : new byte[]{1};
        String effectivePhotoFileName = (command.photoFileName() != null && !command.photoFileName().isBlank())
                ? command.photoFileName()
                : "default.jpg";

        String effectivePhoneNumber = command.phoneNumber();
        if (effectivePhoneNumber == null || effectivePhoneNumber.isBlank()) {
            if (command.emergencyContactPhone() != null && !command.emergencyContactPhone().isBlank()) {
                effectivePhoneNumber = command.emergencyContactPhone();
            } else {
                effectivePhoneNumber = "51999999999";
            }
        }

        Long personProfileId = externalProfilesService.createPersonProfile(
                effectiveEmail,
                effectivePhotoData,
                effectivePhotoFileName,
                effectivePhoneNumber,
                command.firstName(),
                command.lastName(),
                effectiveDni,
                command.birthDate()
        );

        EmergencyContact emergencyContact = null;
        if (command.emergencyContactName() != null || command.emergencyContactPhone() != null) {
            emergencyContact = new EmergencyContact(command.emergencyContactName(), command.emergencyContactPhone());
        }

        LocalDateTime consentGivenAt = Boolean.TRUE.equals(command.consentGiven()) ? LocalDateTime.now() : null;

        AssistedPerson assistedPerson = new AssistedPerson(
                new PersonProfileId(personProfileId),
                relative,
                command.householdId(),
                command.kinship(),
                command.mobilityLevel(),
                emergencyContact,
                consentGivenAt
        );

        assistedPerson = assistedPersonRepository.save(assistedPerson);
        return assistedPerson.getId();
    }

    @Override
    public Long handle(UpdateAssistedPersonCommand command) {
        AssistedPerson assistedPerson = assistedPersonRepository.findById(command.assistedPersonId())
                .orElseThrow(() -> new AssistedPersonNotFoundException(command.assistedPersonId()));

        if (command.relativeId() != null) {
            Relative relative = relativeRepository.findById(command.relativeId())
                    .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));
            if (!assistedPerson.isManagedBy(relative)) {
                throw new AssistedPersonNotManagedByRelativeException(assistedPerson.getId(), relative.getId());
            }
        }

        EmergencyContact emergencyContact = null;
        if (command.emergencyContactName() != null || command.emergencyContactPhone() != null) {
            emergencyContact = new EmergencyContact(command.emergencyContactName(), command.emergencyContactPhone());
        }

        assistedPerson.updateCareDetails(
                command.householdId(),
                command.kinship(),
                command.mobilityLevel(),
                emergencyContact
        );

        byte[] effectivePhotoData = (command.photoData() != null && command.photoData().length > 0)
                ? command.photoData()
                : new byte[]{1};
        String effectivePhotoFileName = (command.photoFileName() != null && !command.photoFileName().isBlank())
                ? command.photoFileName()
                : "default.jpg";

        String effectivePhoneNumber = command.phoneNumber();
        if (effectivePhoneNumber == null || effectivePhoneNumber.isBlank()) {
            if (command.emergencyContactPhone() != null && !command.emergencyContactPhone().isBlank()) {
                effectivePhoneNumber = command.emergencyContactPhone();
            } else {
                effectivePhoneNumber = "51999999999";
            }
        }

        externalProfilesService.updatePersonProfile(
                assistedPerson.getPersonProfileId().personProfileId(),
                effectivePhotoData,
                effectivePhotoFileName,
                effectivePhoneNumber,
                command.firstName(),
                command.lastName(),
                command.birthDate()
        );

        assistedPersonRepository.save(assistedPerson);
        return assistedPerson.getId();
    }
}
