package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.exceptions.AlertNotFound;
import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.CreateAlertCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredAlertsCommand;
import com.resolum.alivia.communications.domain.model.commands.SendAlertCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.communications.domain.repositories.AlertRepository;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.communications.domain.services.AlertCommandService;
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
public class AlertCommandServiceImpl implements AlertCommandService {

    private final AlertRepository alertRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationSender pushSender;

    public AlertCommandServiceImpl(AlertRepository alertRepository,
                                   PushDeviceRepository pushDeviceRepository,
                                   PushNotificationSender pushSender) {
        this.alertRepository = alertRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.pushSender = pushSender;
    }

    @Override
    public Long handle(CreateAlertCommand command) {
        UserId recipientId = new UserId(command.recipientUserId());
        Alert alert = new Alert(recipientId, command.helpRequestId(), command.title(), command.body());
        Alert saved = alertRepository.save(alert);
        return saved.getId();
    }

    @Override
    public void handle(SendAlertCommand command) {
        Alert alert = alertRepository.findById(command.alertId())
                .orElseThrow(() -> new AlertNotFound(command.alertId()));

        List<PushDevice> devices = pushDeviceRepository.findByUserId(alert.getRecipientId());
        if (devices.isEmpty()) {
            log.warn("Recipient user {} has no registered push devices for alert {}", alert.getRecipientId().userId(), alert.getId());
            alert.markUndelivered();
            alertRepository.save(alert);
            return;
        }

        boolean anySuccess = false;
        Map<String, String> data = Map.of(
                "alertId", String.valueOf(alert.getId()),
                "helpRequestId", alert.getHelpRequestId()
        );

        for (PushDevice device : devices) {
            boolean delivered = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    delivered = pushSender.sendHighPriorityAlert(device.getToken(), device.getPlatform(), alert.getTitle(), alert.getBody(), data);
                    if (delivered) {
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Attempt {} to send emergency alert {} to token {} failed: {}", attempt, alert.getId(), device.getToken(), e.getMessage());
                }
            }

            if (delivered) {
                anySuccess = true;
            } else if (pushSender.isTokenInvalid(device.getToken())) {
                log.info("Token {} reported invalid by push sender; removing device", device.getToken());
                pushDeviceRepository.delete(device);
            }
        }

        if (anySuccess) {
            alert.markSent(LocalDateTime.now());
        } else {
            alert.markUndelivered();
        }
        alertRepository.save(alert);
    }

    @Override
    public void handle(RetryUndeliveredAlertsCommand command) {
        int maxAttempts = command.maxAttempts() != null ? command.maxAttempts() : 5;
        List<Alert> undelivered = alertRepository.findByStatusAndAttemptsLessThan(AlertStatus.UNDELIVERED, maxAttempts);
        for (Alert alert : undelivered) {
            try {
                handle(new SendAlertCommand(alert.getId()));
            } catch (Exception e) {
                log.error("Failed to retry alert {}: {}", alert.getId(), e.getMessage());
            }
        }
    }
}
