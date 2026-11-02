package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeDomainTest {
    @Test
    void createsActiveEmployeeLinkedToIamProfilesAndBusiness() {
        var employee = new Employee(1L, 2L, 3L, EmployeeRole.TECH);

        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(EmployeeRole.TECH, employee.getRole());
        assertTrue(employee.belongsTo(1L));
        employee.changeRole(EmployeeRole.SUBSCRIPTION_MANAGER);
        assertEquals(EmployeeRole.SUBSCRIPTION_MANAGER, employee.getRole());
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new Employee(0L, 2L, 3L, EmployeeRole.TECH));
    }
}
