package com.resolum.alivia.iam.application.internal.commandservices;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.commands.AuthenticateEdgeCommand;
import com.resolum.alivia.iam.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthenticatedEdge;
import com.resolum.alivia.iam.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.iam.domain.repositories.EdgeCredentialRepository;
import com.resolum.alivia.iam.domain.services.EdgeCredentialCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Issues, revokes and checks the credentials of the edge services.
 *
 * <p>What an edge sends is {@code <edgeId>.<secret>}. The id tells which credential to read, so the secret is compared
 * with that one hash in constant time instead of being searched for in the table. The secret is random and 256 bits
 * long, which is why a plain SHA-256 hash is enough to store it. The secret is never logged.</p>
 */
@Slf4j
@Service
public class EdgeCredentialCommandServiceImpl implements EdgeCredentialCommandService {

    static final char SEPARATOR = '.';
    /** A real credential is about 60 characters; anything much longer is not worth hashing. */
    static final int MAX_PRESENTED_LENGTH = 200;
    /** The last use is written at most this often, so an edge that asks every few seconds does not write every time. */
    static final Duration USE_RECORD_INTERVAL = Duration.ofMinutes(1);

    private final EdgeCredentialRepository repository;
    private final TokenService tokenService;
    private final Clock clock;

    public EdgeCredentialCommandServiceImpl(EdgeCredentialRepository repository, TokenService tokenService,
                                            Clock clock) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedEdgeCredential handle(IssueEdgeCredentialCommand command) {
        var secret = tokenService.generateEdgeSecret();
        var hash = tokenService.hashEdgeSecret(secret);
        var credential = repository.findByEdgeIdForUpdate(command.edgeId())
                .map(existing -> existing.rotate(command.edgeKind(), hash))
                .orElseGet(() -> EdgeCredential.issue(command.edgeId(), command.edgeKind(), hash));
        repository.save(credential);
        log.info("Credential issued for edge {} ({})", command.edgeId(), command.edgeKind());
        return new IssuedEdgeCredential(command.edgeId(), command.edgeKind(), command.edgeId() + "" + SEPARATOR + secret);
    }

    @Override
    @Transactional
    public void handle(RevokeEdgeCredentialCommand command) {
        repository.findByEdgeIdForUpdate(command.edgeId()).ifPresent(credential -> {
            if (credential.revoke(LocalDateTime.now(clock))) {
                repository.save(credential);
                log.info("Credential revoked for edge {}", command.edgeId());
            }
        });
    }

    @Override
    @Transactional
    public Optional<AuthenticatedEdge> handle(AuthenticateEdgeCommand command) {
        var presented = command.presentedCredential();
        if (presented == null || presented.length() > MAX_PRESENTED_LENGTH) {
            return Optional.empty();
        }
        var separator = presented.indexOf(SEPARATOR);
        if (separator <= 0 || separator == presented.length() - 1) {
            return Optional.empty();
        }
        var edgeId = parseEdgeId(presented.substring(0, separator));
        if (edgeId == null) {
            return Optional.empty();
        }
        // The hash is computed before looking the edge up, so an unknown edge takes as long as a wrong secret.
        var hash = tokenService.hashEdgeSecret(presented.substring(separator + 1));
        var authenticated = repository.findByEdgeId(edgeId)
                .filter(EdgeCredential::isActive)
                .filter(credential -> credential.matches(hash));
        authenticated.ifPresent(credential -> {
            var now = LocalDateTime.now(clock);
            repository.recordUse(edgeId, now, now.minus(USE_RECORD_INTERVAL));
        });
        return authenticated.map(credential -> new AuthenticatedEdge(credential.getEdgeId(), credential.getEdgeKind()));
    }

    private static Long parseEdgeId(String text) {
        try {
            var edgeId = Long.parseLong(text);
            return edgeId > 0 ? edgeId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
