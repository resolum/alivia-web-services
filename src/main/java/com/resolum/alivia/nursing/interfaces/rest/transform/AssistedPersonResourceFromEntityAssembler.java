package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.interfaces.rest.resources.AssistedPersonResource;

/**
 * Assembles {@link AssistedPersonResource} instances from assisted person summaries.
 */
public final class AssistedPersonResourceFromEntityAssembler {

    private AssistedPersonResourceFromEntityAssembler() {
    }

    /**
     * Converts an assisted person summary to its resource.
     *
     * @param summary the assisted person summary
     * @return the resource
     */
    public static AssistedPersonResource toResourceFromEntity(AssistedPersonSummary summary) {
        return new AssistedPersonResource(summary.personProfileId().personProfileId(), summary.fullName());
    }
}
