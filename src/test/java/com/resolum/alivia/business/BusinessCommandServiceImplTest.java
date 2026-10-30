package com.resolum.alivia.business;

import com.resolum.alivia.business.application.internal.commandservices.BusinessCommandServiceImpl;
import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BusinessAlreadyExistsForAdministrator;
import com.resolum.alivia.business.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.business.domain.exceptions.UnauthorizedAdministratorException;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.commands.CreateBusinessCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateBusinessCommand;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.domain.repositories.BusinessRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessCommandServiceImpl Tests")
class BusinessCommandServiceImplTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private AdministratorRepository administratorRepository;

    @Mock
    private ExternalProfilesService externalProfilesService;

    private BusinessCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessCommandServiceImpl(businessRepository, administratorRepository, externalProfilesService);
    }

    private Administrator createAdmin(Long id) {
        Administrator admin = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("admin@test.com"));
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    private Business createBusiness(Long id, Long profileId, Administrator admin) {
        Business business = new Business(new BusinessProfileId(profileId), admin);
        ReflectionTestUtils.setField(business, "id", id);
        return business;
    }

    @Test
    @DisplayName("handle(CreateBusinessCommand) successfully creates business")
    void createBusinessSuccess() {
        Administrator admin = createAdmin(1L);
        when(administratorRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(businessRepository.existsByAdministratorId(1L)).thenReturn(false);
        when(externalProfilesService.createBusinessProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BusinessProfileId(50L));

        Business saved = createBusiness(1L, 50L, admin);
        when(businessRepository.save(any(Business.class))).thenReturn(saved);

        var command = new CreateBusinessCommand(
                null, null, "Clínica San Pablo", "San Pablo S.A.C.",
                20601234567L, "Atención Médica", "Centro médico",
                "contacto@sanpablo.com", "5112345678", "Av. El Derby 250",
                -12.109, -76.974, "place123", null, "soporte@sanpablo.com",
                "51987654321", "51987654321", "Tu salud primero", 1L
        );

        Long resultId = service.handle(command);

        assertThat(resultId).isEqualTo(1L);
        verify(businessRepository).save(any(Business.class));
    }

    @Test
    @DisplayName("handle(CreateBusinessCommand) throws AdministratorNotFound when admin does not exist")
    void createBusinessAdminNotFoundThrows() {
        when(administratorRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new CreateBusinessCommand(
                null, null, "Clínica San Pablo", "San Pablo S.A.C.",
                20601234567L, "Atención Médica", "Centro médico",
                "contacto@sanpablo.com", "5112345678", "Av. El Derby 250",
                -12.109, -76.974, "place123", null, "soporte@sanpablo.com",
                "51987654321", "51987654321", "Tu salud primero", 999L
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(AdministratorNotFound.class);
    }

    @Test
    @DisplayName("handle(CreateBusinessCommand) throws BusinessAlreadyExistsForAdministrator when already exists")
    void createBusinessAlreadyExistsThrows() {
        Administrator admin = createAdmin(1L);
        when(administratorRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(businessRepository.existsByAdministratorId(1L)).thenReturn(true);

        var command = new CreateBusinessCommand(
                null, null, "Clínica San Pablo", "San Pablo S.A.C.",
                20601234567L, "Atención Médica", "Centro médico",
                "contacto@sanpablo.com", "5112345678", "Av. El Derby 250",
                -12.109, -76.974, "place123", null, "soporte@sanpablo.com",
                "51987654321", "51987654321", "Tu salud primero", 1L
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(BusinessAlreadyExistsForAdministrator.class);
    }

    @Test
    @DisplayName("handle(UpdateBusinessCommand) successfully updates business")
    void updateBusinessSuccess() {
        Administrator admin = createAdmin(1L);
        Business business = createBusiness(1L, 50L, admin);

        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(administratorRepository.findById(1L)).thenReturn(Optional.of(admin));

        var command = new UpdateBusinessCommand(
                1L, 1L, null, null, "Clínica San Pablo Sur", "San Pablo S.A.C.",
                "Atención Médica", "Centro médico", "5112345678", null,
                "soporte@sanpablo.com", "51987654321", "51987654321", "Tu salud primero"
        );

        Long resultId = service.handle(command);

        assertThat(resultId).isEqualTo(1L);
        verify(externalProfilesService).updateBusinessProfile(eq(50L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle(UpdateBusinessCommand) throws BusinessNotFound when not found")
    void updateBusinessNotFoundThrows() {
        when(businessRepository.findById(999L)).thenReturn(Optional.empty());

        var command = new UpdateBusinessCommand(
                999L, 1L, null, null, "Clínica San Pablo Sur", "San Pablo S.A.C.",
                "Atención Médica", "Centro médico", "5112345678", null,
                "soporte@sanpablo.com", "51987654321", "51987654321", "Tu salud primero"
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(BusinessNotFound.class);
    }

    @Test
    @DisplayName("handle(UpdateBusinessCommand) throws UnauthorizedAdministratorException when admin is different")
    void updateBusinessUnauthorizedThrows() {
        Administrator owner = createAdmin(1L);
        Administrator otherAdmin = createAdmin(2L);
        Business business = createBusiness(1L, 50L, owner);

        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(administratorRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));

        var command = new UpdateBusinessCommand(
                1L, 2L, null, null, "Clínica San Pablo Sur", "San Pablo S.A.C.",
                "Atención Médica", "Centro médico", "5112345678", null,
                "soporte@sanpablo.com", "51987654321", "51987654321", "Tu salud primero"
        );

        assertThatThrownBy(() -> service.handle(command))
                .isInstanceOf(UnauthorizedAdministratorException.class);
    }
}
