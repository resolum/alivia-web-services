package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import com.resolum.alivia.communications.domain.model.aggregates.Alert;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.CreateAlertCommand;
import com.resolum.alivia.communications.domain.model.commands.SendAlertCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.communications.domain.repositories.AlertRepository;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertCommandServiceImplTest {

    private AlertRepository alertRepository;
    private PushDeviceRepository pushDeviceRepository;
    private PushNotificationSender pushSender;
    private AlertCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        pushDeviceRepository = mock(PushDeviceRepository.class);
        pushSender = mock(PushNotificationSender.class);
        service = new AlertCommandServiceImpl(alertRepository, pushDeviceRepository, pushSender);
    }

    @Test
    void handleCreateAlertSavesAlert() {
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAlertCommand command = new CreateAlertCommand(
                15L,
                "req-99",
                "Auxilio",
                "Botón SOS presionado"
        );

        service.handle(command);
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void handleSendAlertMarksUndeliveredWhenNoDevices() {
        Alert alert = new Alert(new UserId(15L), "req-99", "Auxilio", "Botón SOS");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(pushDeviceRepository.findByUserId(new UserId(15L))).thenReturn(List.of());

        service.handle(new SendAlertCommand(1L));

        assertEquals(AlertStatus.UNDELIVERED, alert.getStatus());
        assertEquals(1, alert.getAttempts());
        verify(alertRepository).save(alert);
    }

    @Test
    void handleSendAlertMarksSentWhenPushSucceeds() {
        Alert alert = new Alert(new UserId(15L), "req-99", "Auxilio", "Botón SOS");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(pushDeviceRepository.findByUserId(new UserId(15L))).thenReturn(List.of(
                new PushDevice(new UserId(15L), "fcm-tok", PushPlatform.ANDROID)
        ));
        when(pushSender.sendHighPriorityAlert(any(), any(), any(), any(), any())).thenReturn(true);

        service.handle(new SendAlertCommand(1L));

        assertEquals(AlertStatus.SENT, alert.getStatus());
        assertNotNull(alert.getSentAt());
        verify(alertRepository).save(alert);
    }
}
