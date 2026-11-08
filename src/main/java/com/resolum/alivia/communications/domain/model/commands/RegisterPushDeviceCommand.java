package com.resolum.alivia.communications.domain.model.commands;

import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;

public record RegisterPushDeviceCommand(Long userId, String token, PushPlatform platform) {
}
