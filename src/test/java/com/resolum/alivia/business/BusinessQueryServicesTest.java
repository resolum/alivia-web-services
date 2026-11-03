package com.resolum.alivia.business;

import com.resolum.alivia.business.application.internal.queryservices.AdministratorQueryServiceImpl;
import com.resolum.alivia.business.application.internal.queryservices.BusinessQueryServiceImpl;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByUserIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByAdministratorIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.domain.repositories.BusinessRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Business Query Services Tests")
class BusinessQueryServicesTest {

    @Mock
    private AdministratorRepository adminRepo;

    @InjectMocks
    private AdministratorQueryServiceImpl adminQueryService;

    @Mock
    private BusinessRepository businessRepo;

    @InjectMocks
    private BusinessQueryServiceImpl businessQueryService;

    @Test
    @DisplayName("AdministratorQueryServiceImpl handles queries")
    void adminQueryServiceHandlesQueries() {
        Administrator admin = new Administrator(new UserId(1L), new StaffProfileId(2L), null);

        when(adminRepo.findById(10L)).thenReturn(Optional.of(admin));
        when(adminRepo.findByUserId(new UserId(1L))).thenReturn(Optional.of(admin));

        assertThat(adminQueryService.handle(new GetAdministratorByIdQuery(10L))).contains(admin);
        assertThat(adminQueryService.handle(new GetAdministratorByUserIdQuery(1L))).contains(admin);
    }

    @Test
    @DisplayName("BusinessQueryServiceImpl handles queries")
    void businessQueryServiceHandlesQueries() {
        Administrator admin = new Administrator(new UserId(1L), new StaffProfileId(2L), null);
        Business business = new Business(new BusinessProfileId(5L), admin);

        when(businessRepo.findById(20L)).thenReturn(Optional.of(business));
        when(businessRepo.findByAdministratorId(10L)).thenReturn(Optional.of(business));

        assertThat(businessQueryService.handle(new GetBusinessByIdQuery(20L))).contains(business);
        assertThat(businessQueryService.handle(new GetBusinessByAdministratorIdQuery(10L))).contains(business);
    }
}
