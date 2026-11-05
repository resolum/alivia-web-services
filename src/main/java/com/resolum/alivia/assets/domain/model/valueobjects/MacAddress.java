package com.resolum.alivia.assets.domain.model.valueobjects;

import com.resolum.alivia.assets.domain.exceptions.InvalidMacAddress;
import jakarta.persistence.Embeddable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Physical identifier of an asset, always stored in its canonical form {@code XX:XX:XX:XX:XX:XX}
 * (upper case, colon separated). The value is normalized on construction so two spellings of the
 * same address ({@code aa-bb-cc-dd-ee-ff} and {@code AA:BB:CC:DD:EE:FF}) are equal and a duplicate
 * cannot slip through because of its format.
 */
@Embeddable
public record MacAddress(String macAddress) {

    private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile(
            "^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$|^(?:[0-9A-F]{2}-){5}[0-9A-F]{2}$"
    );

    public MacAddress {
        if (macAddress == null) {
            throw new InvalidMacAddress("Mac address cannot be null.");
        }

        macAddress = macAddress.strip();
        if (macAddress.isEmpty()) {
            throw new InvalidMacAddress("Mac address cannot be blank.");
        }

        var upperCase = macAddress.toUpperCase(Locale.ROOT);
        if (!MAC_ADDRESS_PATTERN.matcher(upperCase).matches()) {
            throw new InvalidMacAddress(
                    "Mac address must follow the format XX:XX:XX:XX:XX:XX."
            );
        }
        macAddress = upperCase.replace('-', ':');
    }

}
