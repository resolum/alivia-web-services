package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotActive;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.entities.EmployeeChangeLog;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HcmDomainRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void createsContractNormalizingPositionAndNotes() {
        var contract = new Contract(1L, 2L, "  Supervisor  ", ContractModality.SERVICE, null,
                new ContractPeriod(TODAY, TODAY.plusMonths(3)), ContractStatus.PENDING, "   ");

        assertEquals("Supervisor", contract.getPosition());
        assertNull(contract.getNotes());
        assertNull(contract.getWeeklyHours());
        assertEquals(new ContractPeriod(TODAY, TODAY.plusMonths(3)), contract.getPeriod());
        assertEquals(ContractViewStatus.PENDING, contract.viewStatus(TODAY));
        assertFalse(contract.isEligibleOn(TODAY));
    }

    @Test
    void reportsFutureActiveContractAsPending() {
        var contract = activeContract(TODAY.plusDays(5), TODAY.plusMonths(6));

        assertEquals(ContractViewStatus.PENDING, contract.viewStatus(TODAY));
        assertFalse(contract.isEligibleOn(TODAY));
    }

    @Test
    void rejectsInvalidContractCreationData() {
        var period = new ContractPeriod(TODAY, TODAY.plusMonths(1));

        assertThrows(IllegalArgumentException.class, () -> new Contract(null, 1L, "Tech",
                ContractModality.SERVICE, null, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 0L, "Tech",
                ContractModality.SERVICE, null, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, " ",
                ContractModality.SERVICE, null, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, null,
                ContractModality.SERVICE, null, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                null, null, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.HOURLY, 0, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.HOURLY, 49, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, 0, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, 60, period, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, null, period, ContractStatus.ACTIVE, "x".repeat(301)));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, null, null, ContractStatus.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, null, period, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Tech",
                ContractModality.SERVICE, null, period, ContractStatus.TERMINATED, null));
    }

    @Test
    void assignsCodeOnlyOnce() {
        var contract = activeContract(TODAY, TODAY.plusMonths(1));

        assertThrows(IllegalStateException.class, () -> contract.assignCode(null));
        assertThrows(IllegalStateException.class, () -> contract.assignCode(" "));
        contract.assignCode("CTR-ALV-2026-0001");

        assertEquals("CTR-ALV-2026-0001", contract.getCode());
        assertThrows(IllegalStateException.class, () -> contract.assignCode("CTR-ALV-2026-0002"));
    }

    @Test
    void updatesContractTerms() {
        var contract = activeContract(TODAY, TODAY.plusMonths(1));

        contract.update(" Coordinator ", ContractModality.SERVICE, null,
                new ContractPeriod(TODAY.plusDays(1), TODAY.plusMonths(2)), " Part time ");

        assertEquals("Coordinator", contract.getPosition());
        assertEquals(ContractModality.SERVICE, contract.getModality());
        assertNull(contract.getWeeklyHours());
        assertEquals(TODAY.plusDays(1), contract.getStartDate());
        assertEquals(TODAY.plusMonths(2), contract.getEndDate());
        assertEquals("Part time", contract.getNotes());
        assertThrows(IllegalArgumentException.class, () -> contract.update("Coordinator",
                ContractModality.SERVICE, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> contract.update("Coordinator",
                ContractModality.HOURLY, null, new ContractPeriod(TODAY, TODAY), null));
    }

    @Test
    void renewsOnlyWhenEndDateIsExtended() {
        var contract = new Contract(1L, 1L, "Tech", ContractModality.SERVICE, null,
                new ContractPeriod(TODAY.minusMonths(6), TODAY.minusDays(1)), ContractStatus.PENDING, null);

        assertThrows(IllegalArgumentException.class, () -> contract.renew(null));
        assertThrows(IllegalArgumentException.class,
                () -> contract.renew(new ContractPeriod(TODAY.minusMonths(6), TODAY.minusDays(1))));

        contract.renew(new ContractPeriod(TODAY, TODAY.plusYears(1)));

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(TODAY.plusYears(1), contract.getEndDate());
        assertEquals(ContractViewStatus.ACTIVE, contract.viewStatus(TODAY));
    }

    @Test
    void forbidsChangesOnTerminatedContracts() {
        var contract = activeContract(TODAY, TODAY.plusMonths(1));
        contract.terminate();
        var period = new ContractPeriod(TODAY, TODAY.plusYears(1));

        assertThrows(ContractNotActive.class, () -> contract.update("Tech", ContractModality.SERVICE,
                null, period, null));
        assertThrows(ContractNotActive.class, () -> contract.renew(period));
        assertFalse(contract.isEligibleOn(TODAY));
    }

    @Test
    void validatesContractPeriods() {
        var period = new ContractPeriod(TODAY, TODAY.plusDays(10));

        assertThrows(NullPointerException.class, () -> new ContractPeriod(null, TODAY));
        assertThrows(NullPointerException.class, () -> new ContractPeriod(TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> new ContractPeriod(TODAY, TODAY.minusDays(1)));
        assertTrue(period.contains(TODAY));
        assertTrue(period.contains(TODAY.plusDays(10)));
        assertFalse(period.contains(TODAY.minusDays(1)));
        assertFalse(period.contains(TODAY.plusDays(11)));
        assertFalse(period.contains(null));
        assertFalse(period.overlapsWith(new ContractPeriod(TODAY.plusDays(11), TODAY.plusDays(20))));
        assertFalse(period.overlapsWith(new ContractPeriod(TODAY.minusDays(20), TODAY.minusDays(1))));
        assertThrows(NullPointerException.class, () -> period.overlapsWith(null));
    }

    @Test
    void createsImmutableContractVersions() {
        var snapshot = new HashMap<String, String>();
        snapshot.put("status", "ACTIVE");

        var version = new ContractVersion(5L, 1, ContractAction.CREATED, 9L, snapshot);
        snapshot.put("status", "TERMINATED");

        assertEquals(5L, version.getContractId());
        assertEquals(1, version.getVersion());
        assertEquals(ContractAction.CREATED, version.getAction());
        assertEquals(9L, version.getActorUserId());
        assertEquals(Map.of("status", "ACTIVE"), version.getSnapshot());
        assertThrows(UnsupportedOperationException.class, () -> version.getSnapshot().put("x", "y"));
    }

    @Test
    void rejectsInvalidContractVersions() {
        var snapshot = Map.of("status", "ACTIVE");

        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(null, 1, ContractAction.CREATED, 9L, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(0L, 1, ContractAction.CREATED, 9L, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(5L, 0, ContractAction.CREATED, 9L, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(5L, 1, null, 9L, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(5L, 1, ContractAction.CREATED, null, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(5L, 1, ContractAction.CREATED, 0L, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractVersion(5L, 1, ContractAction.CREATED, 9L, null));
    }

    @Test
    void createsEmployeeChangeLogsWithAtLeastOneChange() {
        var log = new EmployeeChangeLog(3L, 1L, Map.of("role.previous", "TECH", "role.current", "CLEANING"));

        assertEquals(3L, log.getEmployeeId());
        assertEquals(1L, log.getBusinessId());
        assertEquals("CLEANING", log.getChanges().get("role.current"));
        assertThrows(IllegalArgumentException.class, () -> new EmployeeChangeLog(null, 1L, Map.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> new EmployeeChangeLog(3L, null, Map.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> new EmployeeChangeLog(3L, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> new EmployeeChangeLog(3L, 1L, Map.of()));
    }

    @Test
    void validatesEmployeeInvariants() {
        var employee = new Employee(1L, 2L, 3L, EmployeeRole.CLEANING);

        employee.changeRole(null);

        assertEquals(EmployeeRole.CLEANING, employee.getRole());
        assertFalse(employee.belongsTo(2L));
        assertThrows(IllegalArgumentException.class, () -> new Employee(1L, null, 3L, EmployeeRole.TECH));
        assertThrows(IllegalArgumentException.class, () -> new Employee(1L, 2L, -1L, EmployeeRole.TECH));
        assertThrows(IllegalArgumentException.class, () -> new Employee(1L, 2L, 3L, null));
    }

    @Test
    void describesDomainErrors() {
        assertEquals("Contract 5 was not found.", new ContractNotFound(5L).getMessage());
        assertEquals("Employee 3 already has an incompatible contract in that period.",
                new ContractOverlap(3L).getMessage());
        assertEquals("A terminated contract cannot be modified.", new ContractNotActive().getMessage());
        assertEquals("Employee 3 was not found.", new EmployeeNotFound(3L).getMessage());
        assertEquals("Employee 3 does not belong to business 1.", new EmployeeNotInBusiness(3L, 1L).getMessage());
    }

    private static Contract activeContract(LocalDate start, LocalDate end) {
        return new Contract(1L, 1L, "Field technician", ContractModality.HOURLY, 40,
                new ContractPeriod(start, end), ContractStatus.ACTIVE, "D.L. 728");
    }
}
