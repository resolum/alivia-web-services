package com.resolum.alivia.business;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Business Domain Aggregates Tests")
class BusinessDomainTest {

    @Test
    @DisplayName("Administrator aggregate validates required fields")
    void administratorConstructorValidations() {
        assertThatThrownBy(() -> new Administrator(null, new StaffProfileId(1L), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User identifier is required");

        assertThatThrownBy(() -> new Administrator(new UserId(1L), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Staff profile identifier is required");

        Administrator admin = new Administrator(new UserId(1L), new StaffProfileId(2L), new EmailAddress("backup@test.com"));
        assertThat(admin.getUserId().userId()).isEqualTo(1L);
        assertThat(admin.getStaffProfileId().staffProfileId()).isEqualTo(2L);
        assertThat(admin.getBackupPersonalEmail().email()).isEqualTo("backup@test.com");

        admin.updateBackupPersonalEmail(new EmailAddress("updated@test.com"));
        assertThat(admin.getBackupPersonalEmail().email()).isEqualTo("updated@test.com");
    }

    @Test
    @DisplayName("Business aggregate validates required fields and checks isManagedBy")
    void businessConstructorAndIsManagedBy() {
        Administrator admin1 = new Administrator(new UserId(1L), new StaffProfileId(2L), null);
        ReflectionTestUtils.setField(admin1, "id", 10L);

        Administrator admin2 = new Administrator(new UserId(3L), new StaffProfileId(4L), null);
        ReflectionTestUtils.setField(admin2, "id", 20L);

        assertThatThrownBy(() -> new Business(null, admin1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BusinessProfileId is required");

        assertThatThrownBy(() -> new Business(new BusinessProfileId(5L), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Administrator is required");

        Business business = new Business(new BusinessProfileId(5L), admin1);
        ReflectionTestUtils.setField(business, "id", 100L);

        assertThat(business.getId()).isEqualTo(100L);
        assertThat(business.getBusinessProfileId().businessProfileId()).isEqualTo(5L);
        assertThat(business.getAdministrator()).isEqualTo(admin1);

        assertThat(business.isManagedBy(admin1)).isTrue();
        assertThat(business.isManagedBy(admin2)).isFalse();
        assertThat(business.isManagedBy(null)).isFalse();
    }
}
