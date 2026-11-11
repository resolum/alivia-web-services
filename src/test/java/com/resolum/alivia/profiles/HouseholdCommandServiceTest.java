package com.resolum.alivia.profiles;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.resolum.alivia.profiles.application.internal.commandservices.HouseholdCommandServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.commands.CreateHouseholdCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateHouseholdCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseholdCommandServiceTest {

    private HouseholdRepository repository;
    private HouseholdCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(HouseholdRepository.class);
        service = new HouseholdCommandServiceImpl(repository);
    }

    @Test
    void createHouseholdWithResponsibleMemberSavesAndReturnsId() {
        var command = new CreateHouseholdCommand(
                "Hogar Don Alberto",
                "984123456",
                "familia@alivia.pe",
                "Av. Del Parque Norte 742",
                "San Borja",
                "Lima",
                null,
                -12.046374,
                -77.042793,
                "place-1",
                10L
        );

        when(repository.save(any(Household.class))).thenAnswer(invocation -> {
            Household household = invocation.getArgument(0);
            ReflectionTestUtils.setField(household, "id", 1L);
            return household;
        });

        var id = service.handle(command);
        assertThat(id).isEqualTo(1L);

        verify(repository).save(any(Household.class));
    }

    @Test
    void updateHouseholdUpdatesGeneralInformation() {
        var address = new Address("Av. El Derby 250, Lima", -12.1, -76.9, "p1");
        var existing = new Household("Casa", address, new PhoneNumber("51987654321"), new EmailAddress("old@alivia.com"));
        ReflectionTestUtils.setField(existing, "id", 1L);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Household.class))).thenReturn(existing);

        var command = new UpdateHouseholdCommand(1L, "Nuevo Hogar", "984123456", "nuevo@alivia.pe");
        var updatedId = service.handle(command);

        assertThat(updatedId).isEqualTo(1L);
        assertThat(existing.getAlias()).isEqualTo("Nuevo Hogar");
        assertThat(existing.getContactEmail().email()).isEqualTo("nuevo@alivia.pe");
        assertThat(existing.getContactPhone().phoneNumber()).isEqualTo("51984123456");
    }

    @Test
    void updateHouseholdThrowsNotFoundWhenDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        var command = new UpdateHouseholdCommand(99L, "Nuevo", "984123456", "email@alivia.pe");
        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(HouseholdNotFoundException.class);
    }
}
