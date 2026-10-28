package com.resolum.alivia.business;

import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.business.domain.exceptions.*;
import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalProfilesService (Business ACL) Tests")
class ExternalProfilesServiceTest {

    @Mock
    private ProfilesContextFacade profilesContextFacade;

    @Mock
    private StorageService storageService;

    private ExternalProfilesService service;

    @BeforeEach
    void setUp() {
        service = new ExternalProfilesService(profilesContextFacade, storageService, "http://default.photo/url");
    }

    @Test
    @DisplayName("createStaffProfile creates staff profile successfully with photo upload")
    void createStaffProfileSuccess() {
        when(profilesContextFacade.existsStaffProfileByEmailAddress("admin@test.com")).thenReturn(false);
        when(profilesContextFacade.existsStaffProfileByDni("12345678")).thenReturn(false);
        when(storageService.upload(any(), eq("photo.png")))
                .thenReturn(Map.of("url", "http://cloud.com/photo.png", "publicId", "pub123"));
        when(profilesContextFacade.createStaffProfile(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(10L);

        var result = service.createStaffProfile(
                "admin@test.com",
                new byte[]{1, 2, 3},
                "photo.png",
                "Carlos",
                "Mendoza",
                "12345678",
                LocalDate.of(1985, 5, 20),
                "51987654321"
        );

        assertThat(result.staffProfileId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createStaffProfile throws EmailAddressAlreadyExists when duplicate email")
    void createStaffProfileDuplicateEmailThrows() {
        when(profilesContextFacade.existsStaffProfileByEmailAddress("duplicate@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createStaffProfile(
                "duplicate@test.com", null, null, "Carlos", "Mendoza", "12345678",
                LocalDate.of(1985, 5, 20), "51987654321"))
                .isInstanceOf(EmailAddressAlreadyExists.class);
    }

    @Test
    @DisplayName("createStaffProfile throws DniAlreadyExists when duplicate DNI")
    void createStaffProfileDuplicateDniThrows() {
        when(profilesContextFacade.existsStaffProfileByEmailAddress("admin@test.com")).thenReturn(false);
        when(profilesContextFacade.existsStaffProfileByDni("12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.createStaffProfile(
                "admin@test.com", null, null, "Carlos", "Mendoza", "12345678",
                LocalDate.of(1985, 5, 20), "51987654321"))
                .isInstanceOf(DniAlreadyExists.class);
    }

    @Test
    @DisplayName("updateStaffProfile delegates successfully")
    void updateStaffProfileSuccess() {
        service.updateStaffProfile(10L, null, null, "Carlos Alberto", "Mendoza Ruiz",
                LocalDate.of(1985, 5, 20), "51987654321");

        verify(profilesContextFacade).updateStaffProfile(eq(10L), eq("Carlos Alberto"), eq("Mendoza Ruiz"),
                eq(LocalDate.of(1985, 5, 20)), eq("51987654321"), isNull(), isNull());
    }

    @Test
    @DisplayName("createBusinessProfile creates business profile successfully with default photo")
    void createBusinessProfileSuccess() {
        when(profilesContextFacade.existsBusinessProfileByEmailAddress("biz@test.com")).thenReturn(false);
        when(profilesContextFacade.existsBusinessProfileByRuc("20601234567")).thenReturn(false);
        when(profilesContextFacade.existsBusinessProfileByBusinessName("Clinica San Pablo")).thenReturn(false);
        when(profilesContextFacade.existsBusinessProfileByLegalName("San Pablo SAC")).thenReturn(false);
        when(profilesContextFacade.createBusinessProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(50L);

        var result = service.createBusinessProfile(
                null, null, "Clinica San Pablo", "San Pablo SAC", 20601234567L,
                "Salud", "Descripcion", "biz@test.com", "5112345678",
                "Av. El Derby 250", -12.0, -77.0, "place1",
                null, "support@biz.com", "51987654321", "51987654321", "Eslogan"
        );

        assertThat(result.businessProfileId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("createBusinessProfile throws RucAlreadyExists when duplicate RUC")
    void createBusinessProfileDuplicateRucThrows() {
        when(profilesContextFacade.existsBusinessProfileByEmailAddress("biz@test.com")).thenReturn(false);
        when(profilesContextFacade.existsBusinessProfileByRuc("20601234567")).thenReturn(true);

        assertThatThrownBy(() -> service.createBusinessProfile(
                null, null, "Clinica San Pablo", "San Pablo SAC", 20601234567L,
                "Salud", "Descripcion", "biz@test.com", "5112345678",
                "Av. El Derby 250", -12.0, -77.0, "place1",
                null, "support@biz.com", "51987654321", "51987654321", "Eslogan"))
                .isInstanceOf(RucAlreadyExists.class);
    }

    @Test
    @DisplayName("updateBusinessProfile delegates successfully")
    void updateBusinessProfileSuccess() {
        service.updateBusinessProfile(50L, null, null, "Clinica San Pablo", "San Pablo SAC",
                "Salud", "Descripcion", "5112345678", null, "support@biz.com",
                "51987654321", "51987654321", "Eslogan");

        verify(profilesContextFacade).updateBusinessProfile(eq(50L), isNull(), isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateBusinessProfile translates BusinessNameAlreadyExistsException")
    void updateBusinessProfileTranslatesNameConflict() {
        doThrow(new BusinessNameAlreadyExistsException(new BusinessName("Clinica San Pablo")))
                .when(profilesContextFacade).updateBusinessProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.updateBusinessProfile(50L, null, null, "Clinica San Pablo", "San Pablo SAC",
                "Salud", "Descripcion", "5112345678", null, "support@biz.com",
                "51987654321", "51987654321", "Eslogan"))
                .isInstanceOf(BusinessNameAlreadyExists.class);
    }
}
