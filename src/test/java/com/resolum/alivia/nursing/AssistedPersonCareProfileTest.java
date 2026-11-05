package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.IdentityDocumentType;
import com.resolum.alivia.nursing.domain.model.valueobjects.KinshipToCaregiver;
import com.resolum.alivia.nursing.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AssistedPersonCareProfile Tests")
class AssistedPersonCareProfileTest {

    private static final UserId CLIENT = new UserId(10L);
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");

    static SaveAssistedPersonCareProfileCommand command(UserId requester, IdentityDocumentType type, String number,
                                                        String phone, String contactName, String contactPhone,
                                                        boolean consent) {
        return new SaveAssistedPersonCareProfileCommand(requester, null, "  Alberto ", "Villanueva Ríos",
                LocalDate.of(1942, 8, 14), type, number, KinshipToCaregiver.PARENT, phone,
                MobilityLevel.PARTIAL, contactName, contactPhone, consent);
    }

    static SaveAssistedPersonCareProfileCommand minimal(UserId requester) {
        return command(requester, null, null, null, null, null, true);
    }

    @Test
    @DisplayName("Creates the profile with only the required data and trims the names")
    void createsWithRequiredData() {
        var profile = new AssistedPersonCareProfile(minimal(CLIENT), NOW);

        assertThat(profile.getRegisteredByUserId()).isEqualTo(CLIENT);
        assertThat(profile.getFirstName()).isEqualTo("Alberto");
        assertThat(profile.getDocumentType()).isNull();
        assertThat(profile.getPhoneNumber()).isNull();
        assertThat(profile.getMobilityLevel()).isEqualTo(MobilityLevel.PARTIAL);
        assertThat(profile.getConsentGivenAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Keeps the optional document, phone and emergency contact when they are valid")
    void keepsOptionalData() {
        var profile = new AssistedPersonCareProfile(command(CLIENT, IdentityDocumentType.DNI, "09482154",
                "51987654321", "Rosa Villanueva", "51912345678", true), NOW);

        assertThat(profile.getDocumentNumber()).isEqualTo("09482154");
        assertThat(profile.getPhoneNumber()).isEqualTo("51987654321");
        assertThat(profile.getEmergencyContactName()).isEqualTo("Rosa Villanueva");
    }

    @Test
    @DisplayName("Rejects the profile without consent")
    void rejectsWithoutConsent() {
        assertThatThrownBy(() -> command(CLIENT, null, null, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects a DNI that is not 8 digits and a document number without type")
    void rejectsInvalidDocument() {
        assertThatThrownBy(() -> command(CLIENT, IdentityDocumentType.DNI, "1234", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(CLIENT, null, "09482154", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects an invalid phone and an emergency contact without phone")
    void rejectsInvalidPhoneAndHalfContact() {
        assertThatThrownBy(() -> command(CLIENT, null, null, "123", null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(CLIENT, null, null, null, "Rosa", null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects a birth date that is not in the past")
    void rejectsFutureBirthDate() {
        assertThatThrownBy(() -> new SaveAssistedPersonCareProfileCommand(CLIENT, null, "Alberto", "Villanueva",
                LocalDate.now(), null, null, KinshipToCaregiver.PARENT, null, MobilityLevel.PARTIAL,
                null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Only the client who registered the profile can update it")
    void onlyOwnerUpdates() {
        var profile = new AssistedPersonCareProfile(minimal(CLIENT), NOW);

        assertThatThrownBy(() -> profile.update(minimal(new UserId(99L)), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
