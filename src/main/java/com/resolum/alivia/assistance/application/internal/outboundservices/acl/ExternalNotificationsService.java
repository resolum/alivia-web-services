package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import org.springframework.stereotype.Service;

/** Assistance's view of the notification center of Communications. */
@Service("assistanceExternalNotificationsService")
public class ExternalNotificationsService {
    private final NotificationsContextFacade notifications;

    public ExternalNotificationsService(NotificationsContextFacade notifications) {
        this.notifications = notifications;
    }

    /** Reminds the user of an occurrence of a care activity once, however often it is asked. */
    public boolean remindCareActivity(Long userId, String occurrenceId, String title, String body) {
        return notifications.notifyUserOnce(userId, "CARE_ACTIVITY_REMINDER", occurrenceId, title, body);
    }
}
