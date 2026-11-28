package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.EdgeProvisioningToken;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import com.resolum.alivia.assets.domain.model.valueobjects.MacAddress;

@Service
public class EdgeProvisioningService {
    public record IssuedToken(Long edgeId, String token, LocalDateTime expiresAt) {}
    private final EdgeRepository edges; private final EdgeProvisioningTokenRepository tokens;
    private final EdgeCredentialCommandService credentials; private final Clock clock; private final SecureRandom random=new SecureRandom();
    public EdgeProvisioningService(EdgeRepository edges, EdgeProvisioningTokenRepository tokens,
            EdgeCredentialCommandService credentials, Clock clock) { this.edges=edges; this.tokens=tokens; this.credentials=credentials; this.clock=clock; }
    @Transactional public IssuedToken issue(Long edgeId, Long issuedBy) {
        edges.findByIdForUpdate(edgeId).orElseThrow(() -> new EdgeNotFound(edgeId));
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String plain="setup_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expires=LocalDateTime.now(clock).plusMinutes(10);
        tokens.deleteByEdgeId(edgeId);
        tokens.save(new EdgeProvisioningToken(edgeId, hash(plain), expires, issuedBy));
        return new IssuedToken(edgeId, plain, expires);
    }
    @Transactional public IssuedToken issueByMac(String macAddress, Long issuedBy) {
        var edge=edges.findEdgeByMacAddress(new MacAddress(macAddress)).orElseThrow(() -> new EdgeNotFound(0L));
        return issue(edge.getId(), issuedBy);
    }
    @Transactional public IssuedEdgeCredential redeem(String token) {
        var grant=tokens.findByTokenHashForUpdate(hash(token)).orElseThrow(() -> new IllegalArgumentException("Token de aprovisionamiento inválido."));
        if (!grant.consume(LocalDateTime.now(clock))) throw new IllegalArgumentException("Token de aprovisionamiento vencido o utilizado.");
        tokens.save(grant);
        return credentials.handle(new IssueEdgeCredentialCommand(grant.getEdgeId(), grant.getIssuedBy()));
    }
    private static String hash(String text) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
