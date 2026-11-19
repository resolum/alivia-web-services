package com.resolum.alivia.communications.application.acl;

import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.communications.interfaces.acl.NotificationsContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications for other contexts; creating one triggers its push notification. Each one is saved in its
 * own transaction, so a caller that has already committed (an after-commit listener) still saves it, and a failure
 * never rolls back the caller.
 */
@Service
public class NotificationsContextFacadeImpl implements NotificationsContextFacade {

    private final NotificationCommandService commandService;
    private final NotificationRepository repository;

    public NotificationsContextFacadeImpl(NotificationCommandService commandService, NotificationRepository repository) {
        this.commandService = commandService;
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(Long userId, String type, String sourceId, String title, String body) {
        commandService.handle(new CreateNotificationCommand(userId, typeOf(type), sourceId, title, body));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean notifyUserOnce(Long userId, String type, String sourceId, String title, String body) {
        var notificationType = typeOf(type);
        if (userId != null && repository.existsByRecipientIdAndTypeAndSourceId(new UserId(userId), notificationType, sourceId)) {
            return false;
        }
        commandService.handle(new CreateNotificationCommand(userId, notificationType, sourceId, title, body));
        return true;
    }

    private static NotificationType typeOf(String type) {
        if (type == null) throw new IllegalArgumentException("The notification type is required.");
        return NotificationType.valueOf(type);
    }
}
