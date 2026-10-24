package com.resolum.alivia.shared;


import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressTest {

    private static final String FORMATTED = "Av. Javier Prado Este 123, San Isidro, Lima";
    private static final String PLACE_ID = "ChIJ-test-place-id";

    static Stream<Arguments> invalidAddresses() {
        return Stream.of(
                Arguments.of(null, -12.09, -77.03, PLACE_ID, "Formatted address cannot be null or blank."),
                Arguments.of("", -12.09, -77.03, PLACE_ID, "Formatted address cannot be null or blank."),
                Arguments.of("   ", -12.09, -77.03, PLACE_ID, "Formatted address cannot be null or blank."),
                Arguments.of(FORMATTED, null, -77.03, PLACE_ID, "Latitude must be between -90 and 90."),
                Arguments.of(FORMATTED, 90.01, -77.03, PLACE_ID, "Latitude must be between -90 and 90."),
                Arguments.of(FORMATTED, -90.01, -77.03, PLACE_ID, "Latitude must be between -90 and 90."),
                Arguments.of(FORMATTED, -12.09, null, PLACE_ID, "Longitude must be between -180 and 180."),
                Arguments.of(FORMATTED, -12.09, 180.01, PLACE_ID, "Longitude must be between -180 and 180."),
                Arguments.of(FORMATTED, -12.09, -180.01, PLACE_ID, "Longitude must be between -180 and 180."),
                Arguments.of(FORMATTED, -12.09, -77.03, null, "Place ID cannot be null or blank."),
                Arguments.of(FORMATTED, -12.09, -77.03, "", "Place ID cannot be null or blank."),
                Arguments.of(FORMATTED, -12.09, -77.03, "  ", "Place ID cannot be null or blank.")
        );
    }

    static Stream<Arguments> boundaryCoordinates() {
        return Stream.of(
                Arguments.of(90.0, 180.0),
                Arguments.of(-90.0, -180.0),
                Arguments.of(0.0, 0.0)
        );
    }

    @Test
    void createsValidAddress() {
        var address = new Address(FORMATTED, -12.0931, -77.0465, PLACE_ID);

        assertEquals(FORMATTED, address.formattedAddress());
        assertEquals(-12.0931, address.latitude());
        assertEquals(-77.0465, address.longitude());
        assertEquals(PLACE_ID, address.placeId());
    }

    @ParameterizedTest(name = "acepta lat={0}, lon={1}")
    @MethodSource("boundaryCoordinates")
    void acceptsBoundaryCoordinates(Double latitude, Double longitude) {
        var address = new Address(FORMATTED, latitude, longitude, PLACE_ID);

        assertEquals(latitude, address.latitude());
        assertEquals(longitude, address.longitude());
    }

    @ParameterizedTest
    @MethodSource("invalidAddresses")
    void rejectsInvalidValues(String formatted, Double lat, Double lon, String placeId, String message) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Address(formatted, lat, lon, placeId));

        assertEquals(message, ex.getMessage());
    }

    @Test
    void equalityIsBasedOnAllComponents() {
        var a = new Address(FORMATTED, -12.09, -77.03, PLACE_ID);
        var b = new Address(FORMATTED, -12.09, -77.03, PLACE_ID);
        var other = new Address(FORMATTED, -12.10, -77.03, PLACE_ID);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, other);
    }
}