package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;

import java.util.List;

/**
 * Decides what a user may do with the care data of an assisted person.
 *
 * <p>There are three levels, each one including the previous:</p>
 * <ul>
 *   <li><b>read</b>: the relative in charge, an active caregiver of the person, or a member of the person's
 *       household with read access;</li>
 *   <li><b>operate</b> (record or change care data: activities, medications, shifts, handovers, results): the
 *       relative in charge or an active caregiver of the person;</li>
 *   <li><b>manage</b> (the person's own data, caregivers and invitations): the relative in charge or the
 *       {@code PRINCIPAL} of the person's household.</li>
 * </ul>
 *
 * <p>Every method decides from the aggregates and never from the role carried by the token. A resource that does
 * not exist raises its own not-found exception; one that exists but is out of reach raises
 * {@link com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException}.</p>
 */
public interface AssistanceAccessService {

    AssistedPerson requireRead(Long assistedPersonId, Long userId);

    AssistedPerson requireOperate(Long assistedPersonId, Long userId);

    AssistedPerson requireManage(Long assistedPersonId, Long userId);

    CareActivity requireReadActivity(Long activityId, Long userId);

    CareActivity requireOperateActivity(Long activityId, Long userId);

    /**
     * Finds the activity an occurrence belongs to, from the activity id or from the id of one of its assignments,
     * and checks that the user may operate it.
     */
    CareActivity requireOperateOccurrence(Long careActivityId, Long assignmentId, Long userId);

    CareShift requireOperateShift(Long shiftId, Long userId);

    Handover requireOperateHandover(Long handoverId, Long userId);

    /** The caregiver, when the user is that caregiver or may read the caregiver's assisted person. */
    Caregiver requireReadCaregiver(Long caregiverId, Long userId);

    /** The caregiver, when the user may manage the caregiver's assisted person (to revoke the access). */
    Caregiver requireManageCaregiver(Long caregiverId, Long userId);

    /** The invitation, when the user may manage the assisted person it is for (to revoke or resend it). */
    Invitation requireManageInvitation(Long invitationId, Long userId);

    /** The relative, when the user is that relative or may read one of the persons the relative is in charge of. */
    Relative requireReadRelative(Long relativeId, Long userId);

    /** The relative, only when the user is that relative. */
    Relative requireSelfRelative(Long relativeId, Long userId);

    /** The assisted persons the user is the relative in charge of, or is an active caregiver of. */
    List<AssistedPerson> readableBy(Long userId);

    /** Whether the user may operate on the assisted person; {@code false} when it does not exist. */
    boolean canOperate(AssistedPerson assistedPerson, Long userId);

    /** Whether the user may manage the assisted person. */
    boolean canManage(AssistedPerson assistedPerson, Long userId);

    /**
     * Whether the user may manage the household. Used when a person is created in, or moved to, a household.
     */
    boolean canManageHousehold(Long householdId, Long userId);
}
