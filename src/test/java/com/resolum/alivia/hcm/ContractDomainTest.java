package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotActive;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ContractDomainTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void appliesHourlyRulesAndCalculatesEligibility() {
        var contract = contract(TODAY.minusDays(1), TODAY.plusMonths(6), ContractStatus.ACTIVE);

        assertEquals(ContractViewStatus.ACTIVE, contract.viewStatus(TODAY));
        assertTrue(contract.isEligibleOn(TODAY));
        assertThrows(IllegalArgumentException.class, () -> new Contract(1L, 1L, "Technician",
                ContractModality.HOURLY, null, new ContractPeriod(TODAY, TODAY.plusMonths(1)),
                ContractStatus.ACTIVE, null));
    }

    @Test
    void calculatesExpiredWithoutMutatingStoredStatus() {
        var contract = contract(TODAY.minusYears(1), TODAY.minusDays(1), ContractStatus.ACTIVE);

        assertEquals(ContractViewStatus.EXPIRED, contract.viewStatus(TODAY));
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertFalse(contract.isEligibleOn(TODAY));
    }

    @Test
    void terminatesWithoutDeletingAndPreservesFinalState() {
        var contract = contract(TODAY, TODAY.plusMonths(6), ContractStatus.ACTIVE);

        contract.terminate();

        assertEquals(ContractViewStatus.TERMINATED, contract.viewStatus(TODAY));
        assertThrows(ContractNotActive.class, contract::terminate);
    }

    @Test
    void activatesOnlyPendingContractsThatHaveNotEnded() {
        var pending = contract(TODAY.minusMonths(1), TODAY.plusMonths(6), ContractStatus.PENDING);

        pending.activate(TODAY);

        assertEquals(ContractStatus.ACTIVE, pending.getStatus());
        assertEquals(ContractViewStatus.ACTIVE, pending.viewStatus(TODAY));
        assertThrows(IllegalArgumentException.class, () -> pending.activate(TODAY));
        var expired = contract(TODAY.minusYears(1), TODAY.minusDays(1), ContractStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () -> expired.activate(TODAY));
        var terminated = contract(TODAY, TODAY.plusMonths(6), ContractStatus.PENDING);
        terminated.terminate();
        assertThrows(ContractNotActive.class, () -> terminated.activate(TODAY));
    }

    @Test
    void detectsInclusivePeriodOverlap() {
        var first = new ContractPeriod(TODAY, TODAY.plusDays(10));
        var second = new ContractPeriod(TODAY.plusDays(10), TODAY.plusDays(20));

        assertTrue(first.overlapsWith(second));
    }

    private static Contract contract(LocalDate start, LocalDate end, ContractStatus status) {
        return new Contract(1L, 1L, "Field technician", ContractModality.HOURLY, 40,
                new ContractPeriod(start, end), status, "D.L. 728");
    }
}
