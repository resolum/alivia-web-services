package com.resolum.alivia.tracking;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What Tracking learns about where an edge reports from, and that it never asks about an edge it does not have. */
class ExternalAssetsServiceTest {

    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);
    private final ExternalAssetsService service = new ExternalAssetsService(facade);

    @Test
    void theContextCarriesTheHomeOfTheEdgeAndTheDevicesOfItsInstallation() {
        when(facade.fetchEdgeScope(7L)).thenReturn(Optional.of(new AssetsContextFacade.EdgeScope(7L, "ACTUATOR", 3L, 5L,
                List.of(new AssetsContextFacade.ScopedDevice(10L, "Puerta principal", "DOOR", "IN_USE", "entrada"),
                        new AssetsContextFacade.ScopedDevice(11L, null, "LIGHT", "MAINTENANCE", "sala")))));

        var edge = service.fetchReportingEdge(7L).orElseThrow();

        assertThat(edge.edgeKind()).isEqualTo("ACTUATOR");
        assertThat(edge.context().edgeId()).isEqualTo(7L);
        assertThat(edge.context().installationId()).isEqualTo(3L);
        assertThat(edge.context().householdId()).isEqualTo(5L);
        assertThat(edge.context().knows(10L)).isTrue();
        // A device that is in maintenance is still of the installation: reports about it are accepted.
        assertThat(edge.context().knows(11L)).isTrue();
        assertThat(edge.context().knows(99L)).isFalse();
        assertThat(edge.deviceAliases()).containsOnly(java.util.Map.entry(10L, "Puerta principal"));
    }

    @Test
    void anEdgeWithoutAnActiveInstallationHasNoContext() {
        when(facade.fetchEdgeScope(8L)).thenReturn(Optional.empty());

        assertThat(service.fetchReportingEdge(8L)).isEmpty();
    }

    @Test
    void withoutAnEdgeIdNothingIsAsked() {
        assertThat(service.fetchReportingEdge(null)).isEmpty();
        verifyNoInteractions(facade);
    }
}
