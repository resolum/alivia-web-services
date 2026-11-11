package com.resolum.alivia.communications.application.internal.outboundservices.push;

import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import java.util.Map;

public interface PushNotificationSender {
    boolean sendHighPriorityAlert(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data);
    boolean sendNotification(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data);
    boolean isTokenInvalid(String pushToken);
}
