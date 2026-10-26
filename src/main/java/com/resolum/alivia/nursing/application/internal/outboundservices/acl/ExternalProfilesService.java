package com.resolum.alivia.nursing.application.internal.outboundservices.acl;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Outbound service acting as an Anti-Corruption Layer (ACL) adapter to read display names
 * from the Profiles bounded context, in batches.
 *
 * <p>Nursing aggregates keep only person profile identifiers, for assisted persons and for
 * caregivers and relatives alike, so every name is resolved by {@link PersonProfileId}.</p>
 *
 * <p>PENDING(profiles): delegate to {@code ProfilesContextFacade} once that context exposes
 * it. Until then no name is resolved and callers receive {@code null} names.</p>
 */
@Slf4j
@Service("nursingExternalProfilesService")
public class ExternalProfilesService {

    /**
     * Resolves the full names of the given person profiles.
     *
     * @param personProfileIds the person profiles
     * @return the names by profile; profiles without a resolved name are absent
     */
    public Map<PersonProfileId, String> fetchPersonNames(Set<PersonProfileId> personProfileIds) {
        log.debug("Person name resolution is pending for {} profiles", personProfileIds.size());
        return Map.of();
    }
}
