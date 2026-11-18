package com.resolum.alivia.communications.interfaces.acl;

/**
 * Facade for the contexts that notify a user in the notification center; every notification is also sent to the
 * user's mobile devices as a push notification.
 *
 * <p>The type is the name of a Communications notification type, for example {@code ASSIGNMENT_CREATED}.</p>
 */
public interface NotificationsContextFacade {

    /**
     * Creates a notification for the user.
     *
     * @param userId   the IAM user identifier of the recipient
     * @param type     the notification type
     * @param sourceId the identifier of what the notification is about, for example an order
     * @param title    the title shown to the user
     * @param body     the text shown to the user
     * @throws IllegalArgumentException when the type is unknown or a value is missing
     */
    void notifyUser(Long userId, String type, String sourceId, String title, String body);

    /**
     * Creates the notification only when the user has none of the same type about the same source, so a task
     * that runs repeatedly notifies once.
     *
     * @return whether a notification was created
     */
    boolean notifyUserOnce(Long userId, String type, String sourceId, String title, String body);
}
