package com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {
    List<ContractVersion> findByContractIdOrderByVersionDesc(Long contractId);
    long countByContractId(Long contractId);
}
