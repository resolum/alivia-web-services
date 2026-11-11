package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateCaregiverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ReassignCaregiverActivitiesCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeCaregiverAccessCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.assistance.domain.services.CaregiverCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CaregiverCommandServiceImpl implements CaregiverCommandService {

    private final CaregiverRepository caregiverRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CareActivityCommandService careActivityCommandService;

    public CaregiverCommandServiceImpl(CaregiverRepository caregiverRepository,
                                      AssistedPersonRepository assistedPersonRepository,
                                      RelativeRepository relativeRepository,
                                      CareActivityCommandService careActivityCommandService) {
        this.caregiverRepository = caregiverRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.careActivityCommandService = careActivityCommandService;
    }

    @Override
    public Long handle(CreateCaregiverCommand command) {
        AssistedPerson assistedPerson = assistedPersonRepository.findById(command.assistedPersonId())
                .orElseThrow(() -> new AssistedPersonNotFoundException(command.assistedPersonId()));

        if (caregiverRepository.existsByAssistedPersonIdAndUserIdAndStatus(
                assistedPerson.getId(), new UserId(command.caregiverUserId()), CaregiverStatus.ACTIVE)) {
            throw new CaregiverAlreadyExistsException(assistedPerson.getId(), command.caregiverUserId());
        }

        Caregiver caregiver = new Caregiver(new UserId(command.caregiverUserId()), assistedPerson);
        caregiver = caregiverRepository.save(caregiver);
        return caregiver.getId();
    }

    @Override
    public Long handle(RevokeCaregiverAccessCommand command) {
        Caregiver caregiver = caregiverRepository.findById(command.caregiverId())
                .orElseThrow(() -> new CaregiverNotFoundException(command.caregiverId()));

        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        if (!caregiver.getAssistedPerson().isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(caregiver.getAssistedPerson().getId(), relative.getId());
        }

        caregiver.revoke();
        caregiverRepository.save(caregiver);

        careActivityCommandService.handle(new ReassignCaregiverActivitiesCommand(caregiver.getId(), caregiver.getAssistedPerson().getId()));
        return caregiver.getId();
    }
}
