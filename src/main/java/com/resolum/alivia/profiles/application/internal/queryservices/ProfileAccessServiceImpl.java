package com.resolum.alivia.profiles.application.internal.queryservices;

import com.resolum.alivia.profiles.application.internal.outboundservices.acl.ExternalAssistanceService;
import com.resolum.alivia.profiles.domain.exceptions.ProfileAccessDeniedException;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.services.ProfileAccessService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Implementation of {@link ProfileAccessService}. */
@Service
public class ProfileAccessServiceImpl implements ProfileAccessService {

    private final PersonProfileRepository personProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final ExternalAssistanceService externalAssistanceService;

    public ProfileAccessServiceImpl(PersonProfileRepository personProfileRepository,
                                    StaffProfileRepository staffProfileRepository,
                                    ExternalAssistanceService externalAssistanceService) {
        this.personProfileRepository = personProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.externalAssistanceService = externalAssistanceService;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanReadPersonProfile(Long profileId, Long userId, boolean administrator) {
        if (administrator) return;
        if (isOwnPersonProfile(profileId, userId)
                || externalAssistanceService.canReadPersonProfile(profileId, userId)) return;
        throw denied("person", profileId);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanChangePersonProfile(Long profileId, Long userId, boolean administrator) {
        if (administrator) return;
        if (isOwnPersonProfile(profileId, userId)
                || externalAssistanceService.canOperatePersonProfile(profileId, userId)) return;
        throw denied("person", profileId);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanAccessStaffProfile(Long profileId, Long userId, boolean administrator) {
        if (administrator) return;
        boolean own = userId != null && staffProfileRepository.findByUserId(userId)
                .map(StaffProfile::getId).filter(id -> Objects.equals(id, profileId)).isPresent();
        if (!own) throw denied("staff", profileId);
    }

    private boolean isOwnPersonProfile(Long profileId, Long userId) {
        return userId != null && personProfileRepository.findByUserId(userId)
                .map(PersonProfile::getId).filter(id -> Objects.equals(id, profileId)).isPresent();
    }

    private static ProfileAccessDeniedException denied(String kind, Long profileId) {
        return new ProfileAccessDeniedException("The user may not access the " + kind + " profile " + profileId + ".");
    }
}
