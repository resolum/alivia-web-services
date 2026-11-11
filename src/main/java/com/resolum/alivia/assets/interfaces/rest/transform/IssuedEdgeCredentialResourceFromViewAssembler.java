package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.interfaces.rest.resources.IssuedEdgeCredentialResource;

public class IssuedEdgeCredentialResourceFromViewAssembler {
    public static IssuedEdgeCredentialResource toResourceFromView(IssuedEdgeCredential view) {
        return new IssuedEdgeCredentialResource(view.edgeId(), view.edgeType().name(), view.credential());
    }
}
