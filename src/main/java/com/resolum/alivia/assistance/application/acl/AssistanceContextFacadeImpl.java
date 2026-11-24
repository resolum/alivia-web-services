package com.resolum.alivia.assistance.application.acl;

import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of {@link AssistanceContextFacade}. */
@Service
public class AssistanceContextFacadeImpl implements AssistanceContextFacade {

    private final AssistedPersonRepository assistedPersonRepository;
    private final AssistanceAccessService accessService;

    public AssistanceContextFacadeImpl(AssistedPersonRepository assistedPersonRepository,
                                       AssistanceAccessService accessService) {
        this.assistedPersonRepository = assistedPersonRepository;
        this.accessService = accessService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReadPersonProfile(Long personProfileId, Long userId) {
        if (personProfileId == null || userId == null) return false;
        return assistedPersonRepository.findAllByPersonProfileId(personProfileId).stream()
                .anyMatch(person -> accessService.canRead(person, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canOperatePersonProfile(Long personProfileId, Long userId) {
        if (personProfileId == null || userId == null) return false;
        return assistedPersonRepository.findAllByPersonProfileId(personProfileId).stream()
                .anyMatch(person -> accessService.canOperate(person, userId));
    }
}
