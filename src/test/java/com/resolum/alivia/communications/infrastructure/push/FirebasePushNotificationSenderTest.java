package com.resolum.alivia.communications.infrastructure.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushNotificationSenderTest {

    private FirebaseMessaging messaging;
    private FirebasePushNotificationSender sender;

    @BeforeEach
    void setUp() {
        messaging = mock(FirebaseMessaging.class);
        sender = new FirebasePushNotificationSender(messaging);
    }

    @Test
    void sendsANotificationThroughFirebase() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/alivia/messages/1");

        boolean sent = sender.sendNotification("token-1", PushPlatform.ANDROID, "Title", "Body",
                Map.of("notificationId", "7"));

        assertThat(sent).isTrue();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        Message message = captor.getValue();
        assertThat(field(message, "token")).isEqualTo("token-1");
        assertThat(field(message, "data")).isEqualTo(Map.of("notificationId", "7"));
        AndroidConfig android = (AndroidConfig) field(message, "androidConfig");
        assertThat(field(android, "priority")).isEqualTo("normal");
    }

    @Test
    void sendsAlertsWithHighPriorityOnTheAlertsChannel() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/alivia/messages/2");

        assertThat(sender.sendHighPriorityAlert("token-2", PushPlatform.IOS, "Alert", "Fall detected", null)).isTrue();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        AndroidConfig android = (AndroidConfig) field(captor.getValue(), "androidConfig");
        assertThat(field(android, "priority")).isEqualTo("high");
        assertThat(field(field(android, "notification"), "channelId"))
                .isEqualTo(FirebasePushNotificationSender.ALERTS_CHANNEL_ID);
    }

    @Test
    void skipsDataEntriesWithoutValue() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("type", "INFO");
        data.put("sourceId", null);

        Message message = FirebasePushNotificationSender.buildMessage("token", "T", "B", data, false);

        assertThat(field(message, "data")).isEqualTo(Map.of("type", "INFO"));
    }

    @Test
    void remembersTokensThatFirebaseNoLongerKnows() throws Exception {
        FirebaseMessagingException failure = failure(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(failure);

        assertThat(sender.sendNotification("stale", PushPlatform.ANDROID, "T", "B", Map.of())).isFalse();

        assertThat(sender.isTokenInvalid("stale")).isTrue();
        assertThat(sender.isTokenInvalid("stale")).isFalse();
    }

    @Test
    void remembersMalformedTokens() throws Exception {
        FirebaseMessagingException failure = failure(MessagingErrorCode.INVALID_ARGUMENT);
        when(messaging.send(any(Message.class))).thenThrow(failure);

        assertThat(sender.sendHighPriorityAlert("bad", PushPlatform.ANDROID, "T", "B", Map.of())).isFalse();

        assertThat(sender.isTokenInvalid("bad")).isTrue();
    }

    @Test
    void keepsTheTokenWhenFirebaseIsTemporarilyUnavailable() throws Exception {
        FirebaseMessagingException failure = failure(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(failure);

        assertThat(sender.sendNotification("token", PushPlatform.ANDROID, "T", "B", Map.of())).isFalse();

        assertThat(sender.isTokenInvalid("token")).isFalse();
        assertThat(sender.isTokenInvalid(null)).isFalse();
    }

    private static FirebaseMessagingException failure(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(exception.getMessage()).thenReturn("rejected");
        return exception;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
