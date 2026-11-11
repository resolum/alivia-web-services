package com.resolum.alivia.communications.infrastructure.push;

import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnMissingBean(type = "com.resolum.alivia.communications.infrastructure.push.FirebasePushNotificationSender")
public class LoggingPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationSender.class);

    @Override
    public boolean sendHighPriorityAlert(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data) {
        log.info("Sending HIGH PRIORITY emergency alert to platform={}, token={}: title='{}', body='{}'",
                platform, pushToken, title, body);
        return true;
    }

    @Override
    public boolean sendNotification(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data) {
        log.info("Sending push notification to platform={}, token={}: title='{}', body='{}'",
                platform, pushToken, title, body);
        return true;
    }

    @Override
    public boolean isTokenInvalid(String pushToken) {
        return false;
    }
}
