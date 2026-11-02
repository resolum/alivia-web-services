package com.resolum.alivia.hcm;

import com.resolum.alivia.hcm.application.internal.commandservices.ContractCommandServiceImpl;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractVersionRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract creation writes the contract and its first version in one command. When the version
 * cannot be stored, the contract insert must roll back instead of leaving a contract without
 * history.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the service must commit or roll back on its own
@Import({ContractCommandServiceImpl.class, ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        // H2 in PostgreSQL mode, closer to the production database than the default embedded one.
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:contract-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL and H2 cannot run them; this slice builds its
        // schema with Hibernate. See docs/database-migrations.md.
        "spring.flyway.enabled=false"})
class ContractCommandServiceTransactionTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 6, 30);

    @Autowired
    private ContractCommandService contractCommandService;
    @Autowired
    private ContractPersistenceRepository contracts;
    @Autowired
    private EmployeeRepository employees;
    @MockitoBean
    private ContractVersionRepository versions;

    @Test
    void rollsBackContractWhenVersionCannotBeStored() {
        var employee = employees.save(new Employee(1L, 20L, 30L, EmployeeRole.TECH));
        when(versions.save(any(ContractVersion.class)))
                .thenThrow(new IllegalStateException("version store down"));

        assertThrows(IllegalStateException.class, () -> contractCommandService.handle(
                new CreateContractCommand(employee.getId(), 1L, "Technician",
                        ContractModality.SERVICE, null, START_DATE, END_DATE, null, null, 9L)));

        assertEquals(0, contracts.count());
        verify(versions).save(any(ContractVersion.class));
    }
}
