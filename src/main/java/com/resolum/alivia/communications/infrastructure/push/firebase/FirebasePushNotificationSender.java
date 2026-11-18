package com.resolum.alivia.communications.infrastructure.push.firebase;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends push notifications through Firebase Cloud Messaging.
 *
 * <p>Tokens that Firebase reports as unregistered or malformed are remembered until the
 * caller asks {@link #isTokenInvalid(String)}, so the device can be removed.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "alivia.communications.push", name = "provider", havingValue = "firebase")
public class FirebasePushNotificationSender implements PushNotificationSender {

    /** Android channel of everyday notifications; the app creates it with the same id. */
    public static final String NOTIFICATIONS_CHANNEL_ID = "alivia_notifications";
    /** Android channel of emergency alerts; the app creates it with the same id. */
    public static final String ALERTS_CHANNEL_ID = "alivia_alerts";

    private final FirebaseMessaging messaging;
    private final Set<String> invalidTokens = ConcurrentHashMap.newKeySet();

    public FirebasePushNotificationSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public boolean sendHighPriorityAlert(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data) {
        return send(pushToken, buildMessage(pushToken, title, body, data, true));
    }

    @Override
    public boolean sendNotification(String pushToken, PushPlatform platform, String title, String body, Map<String, String> data) {
        return send(pushToken, buildMessage(pushToken, title, body, data, false));
    }

    @Override
    public boolean isTokenInvalid(String pushToken) {
        return pushToken != null && invalidTokens.remove(pushToken);
    }

    static Message buildMessage(String pushToken, String title, String body, Map<String, String> data, boolean highPriority) {
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(highPriority ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                .setNotification(AndroidNotification.builder()
                        .setChannelId(highPriority ? ALERTS_CHANNEL_ID : NOTIFICATIONS_CHANNEL_ID)
                        .build())
                .build();
        ApnsConfig apns = ApnsConfig.builder()
                .putHeader("apns-priority", highPriority ? "10" : "5")
                .setAps(Aps.builder().setSound("default").build())
                .build();
        Message.Builder builder = Message.builder()
                .setToken(pushToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(android)
                .setApnsConfig(apns);
        if (data != null) {
            data.forEach((key, value) -> {
                if (key != null && value != null) {
                    builder.putData(key, value);
                }
            });
        }
        return builder.build();
    }

    private boolean send(String pushToken, Message message) {
        try {
            String messageId = messaging.send(message);
            log.debug("Firebase accepted push message {}", messageId);
            return true;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (pushToken != null && (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT)) {
                invalidTokens.add(pushToken);
            }
            log.warn("Firebase rejected a push message ({}): {}", code, e.getMessage());
            return false;
        }
    }
}
