package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.EdgeCredentialConflictException;
import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.infrastructure.adapters.EdgeCredentialRepositoryImpl;
import com.resolum.alivia.iam.infrastructure.persistence.jpa.repositories.EdgeCredentialPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdgeCredentialRepositoryImplTest {

    private final EdgeCredentialPersistenceRepository persistence = mock(EdgeCredentialPersistenceRepository.class);
    private final EdgeCredentialRepositoryImpl repository = new EdgeCredentialRepositoryImpl(persistence);

    @Test
    void delegatesTheQueries() {
        var credential = mock(EdgeCredential.class);
        when(persistence.findByEdgeId(7L)).thenReturn(Optional.of(credential));
        when(persistence.findByEdgeIdForUpdate(7L)).thenReturn(Optional.of(credential));

        assertEquals(Optional.of(credential), repository.findByEdgeId(7L));
        assertEquals(Optional.of(credential), repository.findByEdgeIdForUpdate(7L));
    }

    @Test
    void recordUseReportsWhetherARowChanged() {
        var now = LocalDateTime.of(2026, 11, 5, 12, 0);
        when(persistence.recordUse(7L, now, now.minusMinutes(1))).thenReturn(1);
        when(persistence.recordUse(8L, now, now.minusMinutes(1))).thenReturn(0);

        assertTrue(repository.recordUse(7L, now, now.minusMinutes(1)));
        assertFalse(repository.recordUse(8L, now, now.minusMinutes(1)));
    }

    @Test
    void aSecondFirstCredentialOfTheSameEdgeIsAConflict() {
        var credential = mock(EdgeCredential.class);
        when(persistence.saveAndFlush(credential)).thenThrow(new DataIntegrityViolationException("uk_edge_credentials_edge"));

        assertThrows(EdgeCredentialConflictException.class, () -> repository.save(credential));
    }
}
