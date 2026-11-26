package com.resolum.alivia.tracking;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** What the API answers when a user asks for a person or a device of Tracking that is not theirs to read. */
class TrackingNotFoundResponsesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aPersonOrDeviceThatCannotBeReadIsA404WithTheCodeOfAMissingOne() {
        var person = handler.handleAssistedPersonNotAccessible(new AssistedPersonNotAccessible(2L));
        var device = handler.handleDeviceNotAccessible(new DeviceNotAccessible(10L));

        assertThat(person.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(person.getProperties()).containsEntry("code", "assisted_person_not_found");
        assertThat(device.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(device.getProperties()).containsEntry("code", "device_not_found");
    }

    @Test
    void aReadablePersonWithoutAHouseholdHasItsOwnCode() {
        var problem = handler.handleAssistedPersonWithoutHousehold(new AssistedPersonWithoutHousehold(3L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getProperties()).containsEntry("code", "assisted_person_without_household");
    }
}
