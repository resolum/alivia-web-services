package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeProvisioningService;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeProvisioningController;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdgeProvisioningControllerTest {
    @Test void issuesTokensByIdAndMacWithoutCachingThem() {
        var service = mock(EdgeProvisioningService.class);
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("7");
        var issued = new EdgeProvisioningService.IssuedToken(2L, "setup_token", LocalDateTime.of(2026, 11, 27, 6, 10));
        when(service.issue(2L, 7L)).thenReturn(issued);
        when(service.issueByMac("AA:BB:CC:DD:EE:FF", 7L)).thenReturn(issued);
        var controller = new EdgeProvisioningController(service);

        var byId = controller.issue(2L, auth);
        var byMac = controller.issueByMac(new EdgeProvisioningController.TokenRequest("AA:BB:CC:DD:EE:FF"), auth);

        assertEquals(201, byId.getStatusCode().value());
        assertEquals("setup_token", byId.getBody().provisioningToken());
        assertEquals(201, byMac.getStatusCode().value());
        assertTrue(byMac.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test void provisionsAnEdgeWithoutCachingTheCredential() {
        var service = mock(EdgeProvisioningService.class);
        when(service.redeem("setup_token")).thenReturn(new IssuedEdgeCredential(2L, EdgeType.ACTUATOR_EDGE, "credential"));
        var controller = new EdgeProvisioningController(service);

        var response = controller.provision(new EdgeProvisioningController.ProvisionRequest("setup_token"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ACTUATOR_EDGE", response.getBody().edgeType());
        assertEquals("credential", response.getBody().credential());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }
}
