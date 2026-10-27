package com.resolum.alivia.assets.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

@Embeddable
public record MacAddress(String macAddress) {

    private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile(
            "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"
    );

    public MacAddress {
        if (macAddress == null) {
            throw new IllegalArgumentException("Mac address cannot be null.");
        }

        if (macAddress.isBlank()) {
            throw new IllegalArgumentException("Mac address cannot be blank.");
        }

        if (!MAC_ADDRESS_PATTERN.matcher(macAddress).matches()) {
            throw new IllegalArgumentException(
                    "Mac address must follow the format XX:XX:XX:XX:XX:XX."
            );
        }
    }

}
