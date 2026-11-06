package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assets.application.internal.queryservices.EdgeCredentialQueryServiceImpl;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCredentialStatusQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialStatus;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade.EdgeCredentialSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EdgeCredentialQueryServiceTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 1, 1, 8, 0);

    private EdgeRepository edges;
    private ExternalIamService iam;
    private EdgeCredentialQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        edges = mock(EdgeRepository.class);
        iam = mock(ExternalIamService.class);
        service = new EdgeCredentialQueryServiceImpl(edges, iam);
        when(edges.existsById(7L)).thenReturn(true);
    }

    @Test
    void anActiveCredentialIsReportedWithWhoIssuedItAndItsAge() {
        when(iam.getEdgeCredentialSummary(7L)).thenReturn(Optional.of(
                new EdgeCredentialSummary(7L, true, ISSUED, 3L, 308, true, ISSUED.plusDays(300), null, null, null)));

        var status = service.handle(new GetEdgeCredentialStatusQuery(7L));

        assertEquals(EdgeCredentialStatus.State.ACTIVE, status.state());
        assertEquals(3L, status.issuedBy());
        assertEquals(308L, status.ageDays());
        assertEquals(true, status.rotationRecommended());
        assertEquals(ISSUED, status.issuedAt());
        assertNull(status.revokedAt());
    }

    @Test
    void aRevokedCredentialIsReportedWithWhoAndWhy() {
        var revokedAt = ISSUED.plusDays(10);
        when(iam.getEdgeCredentialSummary(7L)).thenReturn(Optional.of(
                new EdgeCredentialSummary(7L, false, ISSUED, null, 10, false, null, revokedAt, 9L, "REVOKED_BY_ADMINISTRATOR")));

        var status = service.handle(new GetEdgeCredentialStatusQuery(7L));

        assertEquals(EdgeCredentialStatus.State.REVOKED, status.state());
        assertNull(status.issuedBy()); // issued before it was recorded
        assertEquals(revokedAt, status.revokedAt());
        assertEquals(9L, status.revokedBy());
        assertEquals("REVOKED_BY_ADMINISTRATOR", status.revokedReason());
    }

    @Test
    void anEdgeWithoutCredentialIsNotIssuedAndEveryOtherFieldIsEmpty() {
        when(iam.getEdgeCredentialSummary(7L)).thenReturn(Optional.empty());

        var status = service.handle(new GetEdgeCredentialStatusQuery(7L));

        assertEquals(EdgeCredentialStatus.notIssued(7L), status);
        assertEquals(EdgeCredentialStatus.State.NOT_ISSUED, status.state());
        assertNull(status.issuedAt());
        assertNull(status.rotationRecommended());
    }

    @Test
    void aMissingEdgeIsNotFoundAndIamIsNotAsked() {
        when(edges.existsById(9L)).thenReturn(false);

        assertThrows(EdgeNotFound.class, () -> service.handle(new GetEdgeCredentialStatusQuery(9L)));

        verifyNoInteractions(iam);
    }

    @Test
    void theQueryRejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new GetEdgeCredentialStatusQuery(null));
        assertThrows(IllegalArgumentException.class, () -> new GetEdgeCredentialStatusQuery(0L));
    }
}
