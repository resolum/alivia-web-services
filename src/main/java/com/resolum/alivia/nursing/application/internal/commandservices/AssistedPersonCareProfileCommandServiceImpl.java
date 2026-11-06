package com.resolum.alivia.nursing.application.internal.commandservices;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;
import com.resolum.alivia.nursing.domain.repositories.AssistedPersonCareProfileRepository;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCareProfileCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Implementation of {@link AssistedPersonCareProfileCommandService}.
 */
@Slf4j
@Service
public class AssistedPersonCareProfileCommandServiceImpl implements AssistedPersonCareProfileCommandService {

    private final AssistedPersonCareProfileRepository repository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param repository the care profile repository
     * @param clock      the clock used for the consent date
     */
    public AssistedPersonCareProfileCommandServiceImpl(AssistedPersonCareProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AssistedPersonCareProfile handle(SaveAssistedPersonCareProfileCommand command) {
        log.debug("Handling save assisted person care profile command: {}", command);
        var now = Instant.now(clock);
        var profile = repository.findByRegisteredBy(command.requesterUserId())
                .map(existing -> {
                    existing.update(command, now);
                    return existing;
                })
                .orElseGet(() -> new AssistedPersonCareProfile(command, now));
        var saved = repository.save(profile);
        log.info("Saved assisted person care profile {} for user id {}", saved.getId(),
                command.requesterUserId().userId());
        return saved;
    }
}
