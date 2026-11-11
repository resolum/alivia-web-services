package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.exceptions.NotificationNotFound;
import com.resolum.alivia.communications.domain.model.aggregates.Notification;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.commands.MarkNotificationAsReadCommand;
import com.resolum.alivia.communications.domain.model.commands.RetryUndeliveredPushNotificationsCommand;
import com.resolum.alivia.communications.domain.model.commands.SendPushNotificationCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.communications.domain.repositories.NotificationRepository;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationCommandServiceImplTest {

    private NotificationRepository notificationRepository;
    private PushDeviceRepository pushDeviceRepository;
    private PushNotificationSender pushSender;
    private NotificationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        pushDeviceRepository = mock(PushDeviceRepository.class);
        pushSender = mock(PushNotificationSender.class);
        service = new NotificationCommandServiceImpl(notificationRepository, pushDeviceRepository, pushSender);
    }

    @Test
    void handleCreateNotificationSavesNotification() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationCommand command = new CreateNotificationCommand(
                10L,
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor al 5%"
        );

        service.handle(command);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handleSendPushNotificationSendsToDevices() {
        Notification notification = new Notification(
                new UserId(10L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor al 5%"
        );
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(pushDeviceRepository.findByUserId(new UserId(10L))).thenReturn(List.of(
                new PushDevice(new UserId(10L), "token-1", PushPlatform.ANDROID)
        ));
        when(pushSender.sendNotification(any(), any(), any(), any(), any())).thenReturn(true);

        service.handle(new SendPushNotificationCommand(1L));

        assertEquals(PushStatus.SENT, notification.getPushStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void handleMarkNotificationAsReadUpdatesStatus() {
        Notification notification = new Notification(
                new UserId(10L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor al 5%"
        );
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        service.handle(new MarkNotificationAsReadCommand(1L, 10L));

        assertEquals(NotificationStatus.READ, notification.getStatus());
        assertNotNull(notification.getReadAt());
        verify(notificationRepository).save(notification);
    }
}
