package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidPushToken;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PushDeviceTest {

    @Test
    void createsPushDevice() {
        PushDevice device = new PushDevice(new UserId(1L), "fcm-token-123", PushPlatform.ANDROID);
        assertEquals(new UserId(1L), device.getUserId());
        assertEquals("fcm-token-123", device.getToken());
        assertEquals(PushPlatform.ANDROID, device.getPlatform());
        assertTrue(device.belongsTo(new UserId(1L)));
        assertFalse(device.belongsTo(new UserId(2L)));
    }

    @Test
    void rejectsEmptyToken() {
        assertThrows(InvalidPushToken.class, () ->
                new PushDevice(new UserId(1L), "", PushPlatform.IOS));
    }

    @Test
    void reassignsToNewUser() {
        PushDevice device = new PushDevice(new UserId(1L), "fcm-token-123", PushPlatform.ANDROID);
        device.reassignTo(new UserId(2L), PushPlatform.IOS);

        assertEquals(new UserId(2L), device.getUserId());
        assertEquals(PushPlatform.IOS, device.getPlatform());
        assertTrue(device.belongsTo(new UserId(2L)));
    }
}
