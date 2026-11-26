package com.resolum.alivia.tracking;

import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssistanceService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.tracking.application.internal.queryservices.TrackingAccessServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may read which home in Tracking: through the care network of the assisted person (which includes the caregivers,
 * who are not members of the household) or through the household itself. A person or a device the user may not read
 * answers exactly as one that does not exist.
 */
class TrackingAccessServiceTest {

    private static final long USER = 7L;
    private static final long STRANGER = 99L;

    private final AssistanceContextFacade assistance = mock(AssistanceContextFacade.class);
    private final HouseholdAccessFacade households = mock(HouseholdAccessFacade.class);
    private final AssetsContextFacade assets = mock(AssetsContextFacade.class);
    private final TrackingAccessServiceImpl access = new TrackingAccessServiceImpl(
            new ExternalAssistanceService(assistance), new ExternalProfilesService(households),
            new ExternalAssetsService(assets));

    // ---- by person

    @Test
    void whoMayReadThePersonGetsTheirHousehold() {
        when(assistance.canReadAssistedPerson(2L, USER)).thenReturn(true);
        when(assistance.fetchHouseholdIdOfAssistedPerson(2L)).thenReturn(Optional.of(30L));

        assertThat(access.requireReadableHouseholdOfPerson(USER, 2L)).isEqualTo(30L);
    }

    @Test
    void whoMayNotReadThePersonAndWhenThePersonDoesNotExistGetTheSameAnswer() {
        when(assistance.canReadAssistedPerson(2L, STRANGER)).thenReturn(false);
        when(assistance.canReadAssistedPerson(404L, USER)).thenReturn(false);
        when(assistance.fetchHouseholdIdOfAssistedPerson(2L)).thenReturn(Optional.of(30L));

        var denied = org.junit.jupiter.api.Assertions.assertThrows(AssistedPersonNotAccessible.class,
                () -> access.requireReadableHouseholdOfPerson(STRANGER, 2L));
        var missing = org.junit.jupiter.api.Assertions.assertThrows(AssistedPersonNotAccessible.class,
                () -> access.requireReadableHouseholdOfPerson(USER, 404L));

        // Nothing in the answer says which one it was, not even the household of the person.
        assertThat(denied.getMessage()).isEqualTo("The assisted person 2 was not found.");
        assertThat(missing.getMessage()).isEqualTo("The assisted person 404 was not found.");
        assertThat(denied.getMessage()).doesNotContain("30");
    }

    @Test
    void aReadablePersonWithoutAHouseholdIsToldApartOnlyToWhoMayReadThem() {
        when(assistance.canReadAssistedPerson(3L, USER)).thenReturn(true);
        when(assistance.fetchHouseholdIdOfAssistedPerson(3L)).thenReturn(Optional.empty());
        when(assistance.canReadAssistedPerson(3L, STRANGER)).thenReturn(false);

        assertThatThrownBy(() -> access.requireReadableHouseholdOfPerson(USER, 3L))
                .isInstanceOf(AssistedPersonWithoutHousehold.class);
        assertThatThrownBy(() -> access.requireReadableHouseholdOfPerson(STRANGER, 3L))
                .isInstanceOf(AssistedPersonNotAccessible.class);
    }

    // ---- by device

    @Test
    void whoIsInTheCareNetworkOfSomeoneWhoLivesThereMayReadTheDevice() {
        when(assets.fetchHouseholdOfDevice(10L)).thenReturn(Optional.of(30L));
        when(assistance.canReadHousehold(30L, USER)).thenReturn(true);

        assertThat(access.requireReadableHouseholdOfDevice(USER, 10L)).isEqualTo(30L);
    }

    @Test
    void aMemberOfTheHouseholdWhoIsInNoCareNetworkMayReadTheDeviceToo() {
        when(assets.fetchHouseholdOfDevice(10L)).thenReturn(Optional.of(30L));
        when(assistance.canReadHousehold(30L, USER)).thenReturn(false);
        when(households.canReadHousehold(30L, USER)).thenReturn(true);

        assertThat(access.requireReadableHouseholdOfDevice(USER, 10L)).isEqualTo(30L);
    }

    @Test
    void aStrangerAnUnplacedDeviceAndAMissingDeviceAllGetTheSameAnswer() {
        when(assets.fetchHouseholdOfDevice(10L)).thenReturn(Optional.of(30L));
        when(assets.fetchHouseholdOfDevice(11L)).thenReturn(Optional.empty());
        when(assets.fetchHouseholdOfDevice(404L)).thenReturn(Optional.empty());

        var stranger = org.junit.jupiter.api.Assertions.assertThrows(DeviceNotAccessible.class,
                () -> access.requireReadableHouseholdOfDevice(STRANGER, 10L));
        var unplaced = org.junit.jupiter.api.Assertions.assertThrows(DeviceNotAccessible.class,
                () -> access.requireReadableHouseholdOfDevice(USER, 11L));
        var missing = org.junit.jupiter.api.Assertions.assertThrows(DeviceNotAccessible.class,
                () -> access.requireReadableHouseholdOfDevice(USER, 404L));

        assertThat(stranger.getMessage()).isEqualTo("The device 10 was not found.");
        assertThat(unplaced.getMessage()).isEqualTo("The device 11 was not found.");
        assertThat(missing.getMessage()).isEqualTo("The device 404 was not found.");
    }

    @Test
    void nobodyReadsWithoutAnIdentity() {
        when(assets.fetchHouseholdOfDevice(10L)).thenReturn(Optional.of(30L));

        assertThatThrownBy(() -> access.requireReadableHouseholdOfPerson(null, 2L))
                .isInstanceOf(AssistedPersonNotAccessible.class);
        assertThatThrownBy(() -> access.requireReadableHouseholdOfDevice(null, 10L))
                .isInstanceOf(DeviceNotAccessible.class);
    }
}
