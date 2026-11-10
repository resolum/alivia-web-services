package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.commandservices.AssistedPersonCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.Kinship;
import com.resolum.alivia.assistance.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistedPersonCommandServiceImplTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 1, 1);

    private AssistedPersonRepository assistedPersons;
    private RelativeRepository relatives;
    private ExternalProfilesService profiles;
    private AssistedPersonCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        assistedPersons = mock(AssistedPersonRepository.class);
        relatives = mock(RelativeRepository.class);
        profiles = mock(ExternalProfilesService.class);
        service = new AssistedPersonCommandServiceImpl(assistedPersons, relatives, profiles);
        when(assistedPersons.save(any())).thenAnswer(invocation -> {
            AssistedPerson saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });
        when(relatives.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.createPersonProfile(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(7L);
    }

    private static Relative relative(long id) {
        Relative relative = new Relative(new UserId(id), new PersonProfileId(id), new BusinessId(1L));
        ReflectionTestUtils.setField(relative, "id", id);
        return relative;
    }

    private static CreateAssistedPersonCommand create(Long relativeId, Long userId, String email, byte[] photo,
                                                      String fileName, String phone, String dni,
                                                      String contactName, String contactPhone, Boolean consent) {
        return new CreateAssistedPersonCommand(relativeId, email, photo, fileName, phone, "Ana", "Diaz", dni, BIRTH,
                3L, Kinship.PARENT, MobilityLevel.PARTIAL, contactName, contactPhone, consent, userId);
    }

    @Test
    void createsWithExplicitDataForAnExistingRelative() {
        when(relatives.findById(1L)).thenReturn(Optional.of(relative(1L)));

        Long id = service.handle(create(1L, null, "ana@mail.com", new byte[]{9}, "ana.png", "51911111111",
                "12345678", "Luis", "51922222222", true));

        assertThat(id).isEqualTo(50L);
        verify(profiles).createPersonProfile("ana@mail.com", new byte[]{9}, "ana.png", "51911111111",
                "Ana", "Diaz", "12345678", BIRTH);
        ArgumentCaptor<AssistedPerson> saved = ArgumentCaptor.forClass(AssistedPerson.class);
        verify(assistedPersons).save(saved.capture());
        assertThat(saved.getValue().getEmergencyContact()).isNotNull();
        assertThat(saved.getValue().getConsentGivenAt()).isNotNull();
    }

    @Test
    void createsTheRelativeForAUserWithoutOneAndFillsDefaults() {
        when(relatives.findByUserId(new UserId(9L))).thenReturn(Optional.empty());
        when(profiles.existsPersonProfileByDni(anyString())).thenReturn(true, false);
        when(profiles.existsPersonProfileByEmail(anyString())).thenReturn(false);

        service.handle(create(null, 9L, " ", null, " ", null, null, null, null, null));

        verify(relatives).save(any(Relative.class));
        verify(profiles).createPersonProfile(startsWith("asistido."), eq(new byte[]{1}), eq("default.jpg"),
                eq("51999999999"), eq("Ana"), eq("Diaz"), anyString(), eq(BIRTH));
        ArgumentCaptor<AssistedPerson> saved = ArgumentCaptor.forClass(AssistedPerson.class);
        verify(assistedPersons).save(saved.capture());
        assertThat(saved.getValue().getEmergencyContact()).isNull();
        assertThat(saved.getValue().getConsentGivenAt()).isNull();
    }

    @Test
    void reusesTheRelativeOfTheUserAndFallsBackToTheEmergencyPhone() {
        when(relatives.findByUserId(new UserId(9L))).thenReturn(Optional.of(relative(9L)));
        when(profiles.existsPersonProfileByEmail(startsWith("asistido.12345678@"))).thenReturn(true);

        service.handle(create(null, 9L, null, new byte[0], null, " ", "12345678", "Luis", "51922222222", false));

        verify(relatives, never()).save(any());
        verify(profiles).createPersonProfile(startsWith("asistido.12345678."), any(), eq("default.jpg"),
                eq("51922222222"), any(), any(), eq("12345678"), any());
    }

    @Test
    void usesTheDefaultPhoneWhenTheEmergencyPhoneIsBlank() {
        when(relatives.findById(1L)).thenReturn(Optional.of(relative(1L)));

        service.handle(create(1L, null, "ana@mail.com", null, null, null, "12345678", "Luis", " ", null));

        verify(profiles).createPersonProfile(any(), any(), any(), eq("51999999999"), any(), any(), any(), any());
    }

    @Test
    void rejectsUnknownOrMissingRelatives() {
        when(relatives.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(create(1L, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(RelativeNotFoundException.class);
        assertThatThrownBy(() -> service.handle(create(null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(RelativeNotFoundException.class);
    }

    @Test
    void rejectsADuplicatedDniOrEmail() {
        when(relatives.findById(1L)).thenReturn(Optional.of(relative(1L)));
        when(profiles.existsPersonProfileByDni("12345678")).thenReturn(true);
        assertThatThrownBy(() -> service.handle(
                create(1L, null, "ana@mail.com", null, null, null, "12345678", null, null, null)))
                .isInstanceOf(DniAlreadyExistsException.class);

        when(profiles.existsPersonProfileByDni("87654321")).thenReturn(false);
        when(profiles.existsPersonProfileByEmail("ana@mail.com")).thenReturn(true);
        assertThatThrownBy(() -> service.handle(
                create(1L, null, "ana@mail.com", null, null, null, "87654321", null, null, null)))
                .isInstanceOf(EmailAddressAlreadyExistsException.class);
    }

    private AssistedPerson existing(Relative owner) {
        AssistedPerson person = new AssistedPerson(new PersonProfileId(7L), owner);
        ReflectionTestUtils.setField(person, "id", 50L);
        return person;
    }

    @Test
    void updatesCareDetailsAndProfileForTheManagingRelative() {
        Relative owner = relative(1L);
        when(assistedPersons.findById(50L)).thenReturn(Optional.of(existing(owner)));
        when(relatives.findById(1L)).thenReturn(Optional.of(owner));

        Long id = service.handle(new UpdateAssistedPersonCommand(50L, 1L, new byte[]{9}, "ana.png", "51911111111",
                "Ana", "Diaz", BIRTH, 4L, Kinship.SPOUSE, MobilityLevel.VERY_LIMITED, "Luis", "51922222222"));

        assertThat(id).isEqualTo(50L);
        verify(profiles).updatePersonProfile(7L, new byte[]{9}, "ana.png", "51911111111", "Ana", "Diaz", BIRTH);
    }

    @Test
    void updateFallsBackToDefaultsWithoutARelative() {
        when(assistedPersons.findById(50L)).thenReturn(Optional.of(existing(relative(1L))));

        service.handle(new UpdateAssistedPersonCommand(50L, null, null, null, null, "Ana", "Diaz", BIRTH,
                null, null, null, "Luis", "51922222222"));
        verify(profiles).updatePersonProfile(7L, new byte[]{1}, "default.jpg", "51922222222", "Ana", "Diaz", BIRTH);

        service.handle(new UpdateAssistedPersonCommand(50L, null, new byte[0], " ", " ", "Ana", "Diaz", BIRTH,
                null, null, null, null, " "));
        verify(profiles).updatePersonProfile(7L, new byte[]{1}, "default.jpg", "51999999999", "Ana", "Diaz", BIRTH);

        service.handle(new UpdateAssistedPersonCommand(50L, null, null, null, null, "Ana", "Diaz", BIRTH));
    }

    @Test
    void updateRejectsMissingPeopleAndForeignRelatives() {
        when(assistedPersons.findById(50L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(new UpdateAssistedPersonCommand(50L, null, null, null, null,
                "Ana", "Diaz", BIRTH))).isInstanceOf(AssistedPersonNotFoundException.class);

        when(assistedPersons.findById(51L)).thenReturn(Optional.of(existing(relative(1L))));
        when(relatives.findById(2L)).thenReturn(Optional.of(relative(2L)));
        assertThatThrownBy(() -> service.handle(new UpdateAssistedPersonCommand(51L, 2L, null, null, null,
                "Ana", "Diaz", BIRTH))).isInstanceOf(AssistedPersonNotManagedByRelativeException.class);

        when(relatives.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.handle(new UpdateAssistedPersonCommand(51L, 3L, null, null, null,
                "Ana", "Diaz", BIRTH))).isInstanceOf(RelativeNotFoundException.class);
    }
}
