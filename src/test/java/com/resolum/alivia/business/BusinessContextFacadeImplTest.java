package com.resolum.alivia.business;

import com.resolum.alivia.business.application.acl.BusinessContextFacadeImpl;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.services.BusinessQueryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessContextFacadeImpl Tests")
class BusinessContextFacadeImplTest {

    @Mock
    private BusinessQueryService businessQueryService;

    private BusinessContextFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new BusinessContextFacadeImpl(businessQueryService);
    }

    @Test
    @DisplayName("fetchBusinessById returns data when present")
    void fetchBusinessByIdFound() {
        Administrator admin = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("admin@test.com"));
        ReflectionTestUtils.setField(admin, "id", 1L);
        Business business = new Business(new BusinessProfileId(50L), admin);
        ReflectionTestUtils.setField(business, "id", 20L);

        when(businessQueryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.of(business));

        var data = facade.fetchBusinessById(20L);

        assertThat(data).isPresent();
        assertThat(data.get().id()).isEqualTo(20L);
        assertThat(data.get().administratorId()).isEqualTo(1L);
        assertThat(data.get().businessProfileId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("fetchBusinessById returns empty when not found")
    void fetchBusinessByIdNotFound() {
        when(businessQueryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.empty());

        var data = facade.fetchBusinessById(999L);

        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("existsBusinessById returns true when exists")
    void existsBusinessByIdTrue() {
        Administrator admin = new Administrator(new UserId(100L), new StaffProfileId(10L), new EmailAddress("admin@test.com"));
        Business business = new Business(new BusinessProfileId(50L), admin);

        when(businessQueryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.of(business));

        assertThat(facade.existsBusinessById(20L)).isTrue();
    }

    @Test
    @DisplayName("existsBusinessById returns false when does not exist")
    void existsBusinessByIdFalse() {
        when(businessQueryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.empty());

        assertThat(facade.existsBusinessById(999L)).isFalse();
    }
}
