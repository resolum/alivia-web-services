package com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeRepository extends JpaRepository<Edge,Long> {
    Optional<Edge> findEdgeByBusinessId(BusinessId businessId);

    List<Edge> findAllByBusinessId(BusinessId businessId);

    boolean existsEdgeByMacAddress(MacAddress macAddress);

    Optional<Edge> findEdgeByMacAddress(MacAddress macAddress);

    Long countByBusinessId(BusinessId businessId);

    /**
     * The edge, locked until the transaction ends. Issuing its credential holds this lock, so a change of its type or
     * status that revokes the credential cannot slip between reading the type and storing the credential.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Edge e where e.id = :edgeId")
    Optional<Edge> findByIdForUpdate(@Param("edgeId") Long edgeId);

}
