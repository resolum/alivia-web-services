package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.exceptions.NotificationNotFound;
import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.commands.MarkNotificationAsReadCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredPushNotificationsCommand;
import com.resolum.alivia.communications.domain.model.commands.SendPushNotificationCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class NotificationCommandServiceImpl implements NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationSender pushSender;

    public NotificationCommandServiceImpl(NotificationRepository notificationRepository,
                                          PushDeviceRepository pushDeviceRepository,
                                          PushNotificationSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.pushSender = pushSender;
    }

    @Override
    public Long handle(CreateNotificationCommand command) {
        UserId recipientId = new UserId(command.recipientUserId());
        Notification notification = new Notification(
                recipientId,
                command.type(),
                command.sourceId(),
                command.title(),
                command.body()
        );
        Notification saved = notificationRepository.save(notification);
        return saved.getId();
    }

    @Override
    public void handle(SendPushNotificationCommand command) {
        Notification notification = notificationRepository.findById(command.notificationId())
                .orElseThrow(() -> new NotificationNotFound(command.notificationId()));

        List<PushDevice> devices = pushDeviceRepository.findByUserId(notification.getRecipientId());
        if (devices.isEmpty()) {
            log.info("Recipient {} has no push devices for notification {}", notification.getRecipientId().userId(), notification.getId());
            notification.markPushUndelivered();
            notificationRepository.save(notification);
            return;
        }

        boolean anySuccess = false;
        Map<String, String> data = Map.of(
                "notificationId", String.valueOf(notification.getId()),
                "type", notification.getType().name(),
                "sourceId", notification.getSourceId()
        );

        for (PushDevice device : devices) {
            try {
                boolean sent = pushSender.sendNotification(device.getToken(), device.getPlatform(), notification.getTitle(), notification.getBody(), data);
                if (sent) {
                    anySuccess = true;
                } else if (pushSender.isTokenInvalid(device.getToken())) {
                    log.info("Invalid push token {}; removing push device", device.getToken());
                    pushDeviceRepository.delete(device);
                }
            } catch (Exception e) {
                log.warn("Failed to send push notification to device {}: {}", device.getToken(), e.getMessage());
            }
        }

        if (anySuccess) {
            notification.markPushSent();
        } else {
            notification.markPushUndelivered();
        }
        notificationRepository.save(notification);
    }

    @Override
    public void handle(RetryUndeliveredPushNotificationsCommand command) {
        int maxAttempts = command.maxAttempts() != null ? command.maxAttempts() : 5;
        List<Notification> undelivered = notificationRepository.findByPushStatusAndPushAttemptsLessThan(PushStatus.UNDELIVERED, maxAttempts);
        for (Notification n : undelivered) {
            try {
                handle(new SendPushNotificationCommand(n.getId()));
            } catch (Exception e) {
                log.error("Failed to retry push notification {}: {}", n.getId(), e.getMessage());
            }
        }
    }

    @Override
    public Long handle(MarkNotificationAsReadCommand command) {
        Notification notification = notificationRepository.findById(command.notificationId())
                .orElseThrow(() -> new NotificationNotFound(command.notificationId()));

        notification.markAsRead(new UserId(command.userId()), LocalDateTime.now());
        notificationRepository.save(notification);
        return notification.getId();
    }
}
