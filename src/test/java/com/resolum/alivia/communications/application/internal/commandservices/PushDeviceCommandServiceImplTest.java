package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.domain.exceptions.PushDeviceNotFound;
import com.resolum.alivia.communications.domain.exceptions.PushDeviceNotForUser;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.RegisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.commands.UnregisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushDeviceCommandServiceImplTest {

    private PushDeviceRepository pushDeviceRepository;
    private PushDeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        pushDeviceRepository = mock(PushDeviceRepository.class);
        service = new PushDeviceCommandServiceImpl(pushDeviceRepository);
    }

    @Test
    void registerCreatesNewDeviceIfNotPresent() {
        when(pushDeviceRepository.findByToken("tok-new")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(any(PushDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.handle(new RegisterPushDeviceCommand(10L, "tok-new", PushPlatform.IOS));

        verify(pushDeviceRepository).save(any(PushDevice.class));
    }

    @Test
    void registerReassignsExistingDevice() {
        PushDevice existing = new PushDevice(new UserId(5L), "tok-existing", PushPlatform.ANDROID);
        when(pushDeviceRepository.findByToken("tok-existing")).thenReturn(Optional.of(existing));
        when(pushDeviceRepository.save(any(PushDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.handle(new RegisterPushDeviceCommand(10L, "tok-existing", PushPlatform.IOS));

        assertEquals(new UserId(10L), existing.getUserId());
        assertEquals(PushPlatform.IOS, existing.getPlatform());
        verify(pushDeviceRepository).save(existing);
    }

    @Test
    void unregisterDeletesDeviceBelongingToUser() {
        PushDevice device = new PushDevice(new UserId(10L), "tok-del", PushPlatform.ANDROID);
        when(pushDeviceRepository.findByToken("tok-del")).thenReturn(Optional.of(device));

        service.handle(new UnregisterPushDeviceCommand(10L, "tok-del"));
        verify(pushDeviceRepository).delete(device);
    }

    @Test
    void unregisterThrowsIfDeviceNotFoundOrNotForUser() {
        when(pushDeviceRepository.findByToken("tok-missing")).thenReturn(Optional.empty());
        assertThrows(PushDeviceNotFound.class, () ->
                service.handle(new UnregisterPushDeviceCommand(10L, "tok-missing")));

        PushDevice device = new PushDevice(new UserId(20L), "tok-other", PushPlatform.ANDROID);
        when(pushDeviceRepository.findByToken("tok-other")).thenReturn(Optional.of(device));
        assertThrows(PushDeviceNotForUser.class, () ->
                service.handle(new UnregisterPushDeviceCommand(10L, "tok-other")));
    }
}
