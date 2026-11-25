package com.resolum.alivia.tracking.application.internal.outboundservices.acl;

import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Tracking's view of who is told about a home (Profiles) and how (Communications). */
@Service("trackingExternalNotificationsService")
public class ExternalNotificationsService {

    private final HouseholdMembershipFacade memberships;
    private final NotificationsContextFacade notifications;

    public ExternalNotificationsService(HouseholdMembershipFacade memberships, NotificationsContextFacade notifications) {
        this.memberships = memberships;
        this.notifications = notifications;
    }

    /** The relatives in charge of the household; empty when it has none or does not exist. */
    public Set<Long> relativesOf(Long householdId) {
        return memberships.fetchActivePrincipalUserIds(householdId).orElse(Set.of());
    }

    /** Notifies the user once per source; true when the notification is new. */
    public boolean notifyOnce(Long userId, String type, String sourceId, String title, String body) {
        return notifications.notifyUserOnce(userId, type, sourceId, title, body);
    }
}
