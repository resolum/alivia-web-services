package com.resolum.alivia.business;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.infrastructure.adapters.AdministratorRepositoryImpl;
import com.resolum.alivia.business.infrastructure.adapters.BusinessRepositoryImpl;
import com.resolum.alivia.business.infrastructure.persistence.jpa.repositories.AdministratorPersistenceRepository;
import com.resolum.alivia.business.infrastructure.persistence.jpa.repositories.BusinessPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Business Repositories Adapter Tests")
class BusinessRepositoriesTest {

    @Mock
    private AdministratorPersistenceRepository adminPersistence;

    @InjectMocks
    private AdministratorRepositoryImpl adminRepo;

    @Mock
    private BusinessPersistenceRepository businessPersistence;

    @InjectMocks
    private BusinessRepositoryImpl businessRepo;

    @Test
    @DisplayName("AdministratorRepositoryImpl delegates methods")
    void adminRepoDelegates() {
        Administrator admin = new Administrator(new UserId(1L), new StaffProfileId(2L), new EmailAddress("test@test.com"));

        when(adminPersistence.findById(1L)).thenReturn(Optional.of(admin));
        when(adminPersistence.findByUserId(new UserId(1L))).thenReturn(Optional.of(admin));
        when(adminPersistence.findByStaffProfileId(new StaffProfileId(2L))).thenReturn(Optional.of(admin));
        when(adminPersistence.existsByUserId(new UserId(1L))).thenReturn(true);
        when(adminPersistence.existsByStaffProfileId(new StaffProfileId(2L))).thenReturn(true);
        when(adminPersistence.existsByBackupPersonalEmail(new EmailAddress("test@test.com"))).thenReturn(true);
        when(adminPersistence.save(admin)).thenReturn(admin);

        assertThat(adminRepo.findById(1L)).contains(admin);
        assertThat(adminRepo.findByUserId(new UserId(1L))).contains(admin);
        assertThat(adminRepo.findByStaffProfileId(new StaffProfileId(2L))).contains(admin);
        assertThat(adminRepo.existsByUserId(new UserId(1L))).isTrue();
        assertThat(adminRepo.existsByStaffProfileId(new StaffProfileId(2L))).isTrue();
        assertThat(adminRepo.existsByBackupPersonalEmail(new EmailAddress("test@test.com"))).isTrue();
        assertThat(adminRepo.existsByBackupPersonalEmail(null)).isFalse();
        assertThat(adminRepo.save(admin)).isEqualTo(admin);
    }

    @Test
    @DisplayName("BusinessRepositoryImpl delegates methods")
    void businessRepoDelegates() {
        Administrator admin = new Administrator(new UserId(1L), new StaffProfileId(2L), null);
        Business business = new Business(new BusinessProfileId(5L), admin);

        when(businessPersistence.findById(10L)).thenReturn(Optional.of(business));
        when(businessPersistence.findByAdministratorId(1L)).thenReturn(Optional.of(business));
        when(businessPersistence.existsByAdministratorId(1L)).thenReturn(true);
        when(businessPersistence.save(business)).thenReturn(business);

        assertThat(businessRepo.findById(10L)).contains(business);
        assertThat(businessRepo.findByAdministratorId(1L)).contains(business);
        assertThat(businessRepo.existsByAdministratorId(1L)).isTrue();
        assertThat(businessRepo.save(business)).isEqualTo(business);
    }
}
