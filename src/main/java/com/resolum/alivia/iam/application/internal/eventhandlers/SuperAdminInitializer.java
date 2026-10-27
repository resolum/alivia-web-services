package com.resolum.alivia.iam.application.internal.eventhandlers;

import com.resolum.alivia.iam.application.internal.outboundservices.hashing.HashingService;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Creates the preloaded administrator account on startup when its email is not registered yet.
 * The account is active and verified so the team can sign in right away.
 */
@Slf4j
@Component
public class SuperAdminInitializer {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final Clock clock;
    private final String email;
    private final String password;

    public SuperAdminInitializer(UserRepository userRepository,
                                 HashingService hashingService,
                                 Clock clock,
                                 @Value("${alivia.iam.super-admin.email}") String email,
                                 @Value("${alivia.iam.super-admin.password}") String password) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        var emailAddress = new EmailAddress(email);
        if (userRepository.existsByEmail(emailAddress)) {
            return;
        }
        var user = new User(emailAddress, new PasswordHash(hashingService.encode(password)),
                AccessRole.ADMINISTRATOR, LocalDateTime.now(clock));
        user.verifyEmail();
        userRepository.save(user);
        log.info("Seeded super admin account {}", user.getAccountId());
    }
}
