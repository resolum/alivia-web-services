package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link AssistanceAccessService}.
 *
 * <p>Caregivers are not members of the household in Profiles (accepting an invitation creates a
 * {@link Caregiver} in this context), so the care network of the person is read from the aggregates of this context
 * and the household membership is added on top of it, not instead of it.</p>
 */
@Service
public class AssistanceAccessServiceImpl implements AssistanceAccessService {

    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;
    private final CareActivityRepository careActivityRepository;
    private final CareShiftRepository careShiftRepository;
    private final HandoverRepository handoverRepository;
    private final ExternalHouseholdsService externalHouseholdsService;

    public AssistanceAccessServiceImpl(AssistedPersonRepository assistedPersonRepository,
                                       RelativeRepository relativeRepository,
                                       CaregiverRepository caregiverRepository,
                                       CareActivityRepository careActivityRepository,
                                       CareShiftRepository careShiftRepository,
                                       HandoverRepository handoverRepository,
                                       ExternalHouseholdsService externalHouseholdsService) {
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
        this.careActivityRepository = careActivityRepository;
        this.careShiftRepository = careShiftRepository;
        this.handoverRepository = handoverRepository;
        this.externalHouseholdsService = externalHouseholdsService;
    }

    @Override
    @Transactional(readOnly = true)
    public AssistedPerson requireRead(Long assistedPersonId, Long userId) {
        var person = find(assistedPersonId);
        if (!canRead(person, userId)) throw denied("read", person);
        return person;
    }

    @Override
    @Transactional(readOnly = true)
    public AssistedPerson requireOperate(Long assistedPersonId, Long userId) {
        var person = find(assistedPersonId);
        if (!canOperate(person, userId)) throw denied("operate on", person);
        return person;
    }

    @Override
    @Transactional(readOnly = true)
    public AssistedPerson requireManage(Long assistedPersonId, Long userId) {
        var person = find(assistedPersonId);
        if (!canManage(person, userId)) throw denied("manage", person);
        return person;
    }

    @Override
    @Transactional(readOnly = true)
    public CareActivity requireReadActivity(Long activityId, Long userId) {
        var activity = findActivity(activityId);
        if (!canRead(activity.getAssistedPerson(), userId)) throw denied("read", activity.getAssistedPerson());
        return activity;
    }

    @Override
    @Transactional(readOnly = true)
    public CareActivity requireOperateActivity(Long activityId, Long userId) {
        var activity = findActivity(activityId);
        if (!canOperate(activity.getAssistedPerson(), userId)) throw denied("operate on", activity.getAssistedPerson());
        return activity;
    }

    @Override
    @Transactional(readOnly = true)
    public CareActivity requireOperateOccurrence(Long careActivityId, Long assignmentId, Long userId) {
        CareActivity activity = null;
        if (careActivityId != null) {
            activity = findActivity(careActivityId);
        } else if (assignmentId != null) {
            activity = careActivityRepository.findAllActive().stream()
                    .filter(candidate -> candidate.getActivityAssignments().getActivityAssignments().stream()
                            .anyMatch(assignment -> Objects.equals(assignment.getId(), assignmentId)))
                    .findFirst()
                    .orElse(null);
        }
        if (activity == null) throw new CareActivityNotFoundException(careActivityId != null ? careActivityId : 0L);
        if (!canOperate(activity.getAssistedPerson(), userId)) throw denied("operate on", activity.getAssistedPerson());
        return activity;
    }

    @Override
    @Transactional(readOnly = true)
    public CareShift requireOperateShift(Long shiftId, Long userId) {
        var shift = careShiftRepository.findById(shiftId).orElseThrow(() -> new CareShiftNotFoundException(shiftId));
        if (!canOperate(shift.getAssistedPerson(), userId)) throw denied("operate on", shift.getAssistedPerson());
        return shift;
    }

    @Override
    @Transactional(readOnly = true)
    public Handover requireOperateHandover(Long handoverId, Long userId) {
        var handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new HandoverNotFoundException(handoverId));
        var person = handover.getFromShift().getAssistedPerson();
        if (!canOperate(person, userId)) throw denied("operate on", person);
        return handover;
    }

    @Override
    @Transactional(readOnly = true)
    public Caregiver requireReadCaregiver(Long caregiverId, Long userId) {
        var caregiver = caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new CaregiverNotFoundException(caregiverId));
        boolean isTheCaregiver = caregiver.getUserId() != null && Objects.equals(caregiver.getUserId().userId(), userId);
        if (!isTheCaregiver && !canRead(caregiver.getAssistedPerson(), userId)) {
            throw denied("read", caregiver.getAssistedPerson());
        }
        return caregiver;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistedPerson> readableBy(Long userId) {
        if (userId == null) return List.of();
        var own = relativeRepository.findByUserId(new UserId(userId))
                .map(relative -> assistedPersonRepository.findAllByRelativeId(relative.getId()))
                .orElse(List.of());
        var asCaregiver = caregiverRepository.findAllByUserId(new UserId(userId)).stream()
                .filter(caregiver -> caregiver.getStatus() == CaregiverStatus.ACTIVE)
                .map(Caregiver::getAssistedPerson)
                .filter(Objects::nonNull)
                .toList();
        return java.util.stream.Stream.concat(own.stream(), asCaregiver.stream())
                .collect(java.util.stream.Collectors.toMap(AssistedPerson::getId, person -> person,
                        (first, second) -> first, java.util.LinkedHashMap::new))
                .values().stream().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canOperate(AssistedPerson person, Long userId) {
        if (person == null || userId == null) return false;
        return canManage(person, userId) || isActiveCaregiver(person, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManage(AssistedPerson person, Long userId) {
        if (person == null || userId == null) return false;
        return isRelativeInCharge(person, userId)
                || externalHouseholdsService.canManage(person.getHouseholdId(), userId);
    }

    @Override
    public boolean canManageHousehold(Long householdId, Long userId) {
        return externalHouseholdsService.canManage(householdId, userId);
    }

    private boolean canRead(AssistedPerson person, Long userId) {
        if (person == null || userId == null) return false;
        return canOperate(person, userId) || externalHouseholdsService.canRead(person.getHouseholdId(), userId);
    }

    private boolean isRelativeInCharge(AssistedPerson person, Long userId) {
        var relative = person.getRelative();
        return relative != null && relative.getUserId() != null && Objects.equals(relative.getUserId().userId(), userId);
    }

    private boolean isActiveCaregiver(AssistedPerson person, Long userId) {
        return caregiverRepository.existsByAssistedPersonIdAndUserIdAndStatus(
                person.getId(), new UserId(userId), CaregiverStatus.ACTIVE);
    }

    private AssistedPerson find(Long assistedPersonId) {
        if (assistedPersonId == null) throw new AssistedPersonNotFoundException(0L);
        return assistedPersonRepository.findById(assistedPersonId)
                .orElseThrow(() -> new AssistedPersonNotFoundException(assistedPersonId));
    }

    private CareActivity findActivity(Long activityId) {
        if (activityId == null) throw new CareActivityNotFoundException(0L);
        return careActivityRepository.findById(activityId).orElseThrow(() -> new CareActivityNotFoundException(activityId));
    }

    private static AssistanceAccessDeniedException denied(String action, AssistedPerson person) {
        return new AssistanceAccessDeniedException("The user may not " + action + " assisted person "
                + (person == null ? null : person.getId()));
    }
}
