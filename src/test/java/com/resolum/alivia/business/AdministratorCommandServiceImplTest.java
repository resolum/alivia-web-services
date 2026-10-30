package com.resolum.alivia.business;

import com.resolum.alivia.business.application.internal.commandservices.AdministratorCommandServiceImpl;
import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BackupPersonalEmailAlreadyExists;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.commands.CreateAdministratorCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateAdministratorCommand;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdministratorCommandServiceImpl Tests")
class AdministratorCommandServiceImplTest {

    @Mock
    private AdministratorRepository administratorRepository;

    @Mock
    private ExternalIamService externalIamService;

    @Mock
    private ExternalProfilesService externalProfilesService;

    private AdministratorCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdministratorCommandServiceImpl(administratorRepository, externalIamService, externalProfilesService);
    }

    @Test
    @DisplayName("handle(CreateAdministratorCommand) successfully creates administrator")
    void createAdministratorSuccess() {
        var command = new CreateAdministratorCommand(
                "admin@alivia.com",
                "Password123!",
                null,
                null,
                "Carlos",
                "Mendoza",
                "12345678",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "backup@gmail.com"
        );

        when(administratorRepository.existsByBackupPersonalEmail(new EmailAddress("backup@gmail.com"))).thenReturn(false);
        when(externalProfilesService.createStaffProfile(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new StaffProfileId(10L));
        when(externalIamService.signUpAdministrator(any(), any(), any(), any(), any()))
                .thenReturn(100L);

        Administrator savedAdmin = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("backup@gmail.com"));
        ReflectionTestUtils.setField(savedAdmin, "id", 1L);
        when(administratorRepository.save(any(Administrator.class))).thenReturn(savedAdmin);

        Long resultId = service.handle(command);

        assertThat(resultId).isEqualTo(1L);
        verify(administratorRepository).save(any(Administrator.class));
    }

    @Test
    @DisplayName("handle(CreateAdministratorCommand) throws BackupPersonalEmailAlreadyExists when duplicate")
    void createAdministratorDuplicateBackupEmailThrows() {
        var command = new CreateAdministratorCommand(
                "admin@alivia.com",
                "Password123!",
                null,
                null,
                "Carlos",
                "Mendoza",
                "12345678",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "duplicate@gmail.com"
        );

        when(administratorRepository.existsByBackupPersonalEmail(new EmailAddress("duplicate@gmail.com"))).thenReturn(true);

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(BackupPersonalEmailAlreadyExists.class);

        verifyNoInteractions(externalProfilesService);
        verifyNoInteractions(externalIamService);
    }

    @Test
    @DisplayName("handle(UpdateAdministratorCommand) successfully updates administrator")
    void updateAdministratorSuccess() {
        Administrator existing = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("oldbackup@gmail.com"));
        ReflectionTestUtils.setField(existing, "id", 1L);

        when(administratorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(administratorRepository.existsByBackupPersonalEmail(new EmailAddress("newbackup@gmail.com"))).thenReturn(false);

        var command = new UpdateAdministratorCommand(
                1L,
                null,
                null,
                "Carlos Alberto",
                "Mendoza Ruiz",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "newbackup@gmail.com"
        );

        Long resultId = service.handle(command);

        assertThat(resultId).isEqualTo(1L);
        assertThat(existing.getBackupPersonalEmail().email()).isEqualTo("newbackup@gmail.com");
        verify(externalProfilesService).updateStaffProfile(eq(10L), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle(UpdateAdministratorCommand) throws AdministratorNotFound when not found")
    void updateAdministratorNotFoundThrows() {
        when(administratorRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new UpdateAdministratorCommand(
                999L,
                null,
                null,
                "Carlos Alberto",
                "Mendoza Ruiz",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "newbackup@gmail.com"
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(AdministratorNotFound.class);
    }

    @Test
    @DisplayName("handle(UpdateAdministratorCommand) throws BackupPersonalEmailAlreadyExists when duplicate")
    void updateAdministratorDuplicateBackupEmailThrows() {
        Administrator existing = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("oldbackup@gmail.com"));
        ReflectionTestUtils.setField(existing, "id", 1L);

        when(administratorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(administratorRepository.existsByBackupPersonalEmail(new EmailAddress("duplicate@gmail.com"))).thenReturn(true);

        var command = new UpdateAdministratorCommand(
                1L,
                null,
                null,
                "Carlos Alberto",
                "Mendoza Ruiz",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "duplicate@gmail.com"
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(BackupPersonalEmailAlreadyExists.class);
    }
}
