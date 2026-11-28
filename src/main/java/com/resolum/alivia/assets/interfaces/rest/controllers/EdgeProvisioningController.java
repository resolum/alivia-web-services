package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.application.internal.commandservices.EdgeProvisioningService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EdgeProvisioningController {
    public record ProvisionRequest(String provisioningToken) {}
    public record ProvisionResponse(Long edgeId, String edgeType, String credential) {}
    public record TokenResponse(Long edgeId, String provisioningToken, java.time.LocalDateTime expiresAt) {}
    public record TokenRequest(String macAddress) {}
    private final EdgeProvisioningService service;
    public EdgeProvisioningController(EdgeProvisioningService service) { this.service=service; }

    @PostMapping("/edges/{edgeId}/provisioning-token")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','TECHNICIAN')")
    public ResponseEntity<TokenResponse> issue(@PathVariable Long edgeId, Authentication auth) {
        var issued=service.issue(edgeId, Long.valueOf(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new TokenResponse(issued.edgeId(), issued.token(), issued.expiresAt()));
    }
    @PostMapping("/edge-provisioning-tokens")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','TECHNICIAN')")
    public ResponseEntity<TokenResponse> issueByMac(@RequestBody TokenRequest request, Authentication auth) {
        var issued=service.issueByMac(request.macAddress(), Long.valueOf(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new TokenResponse(issued.edgeId(), issued.token(), issued.expiresAt()));
    }

    @PostMapping("/edge/provision")
    public ResponseEntity<ProvisionResponse> provision(@RequestBody ProvisionRequest request) {
        var issued=service.redeem(request.provisioningToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ProvisionResponse(issued.edgeId(), issued.edgeType().name(), issued.credential()));
    }
}
