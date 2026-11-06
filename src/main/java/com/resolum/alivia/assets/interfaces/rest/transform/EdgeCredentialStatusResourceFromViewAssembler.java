package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialStatus;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeCredentialStatusResource;

public class EdgeCredentialStatusResourceFromViewAssembler {
    public static EdgeCredentialStatusResource toResourceFromView(EdgeCredentialStatus view) {
        return new EdgeCredentialStatusResource(view.edgeId(), view.state().name(), view.issuedAt(), view.issuedBy(),
                view.ageDays(), view.rotationRecommended(), view.lastUsedAt(), view.revokedAt(), view.revokedBy(),
                view.revokedReason());
    }
}
