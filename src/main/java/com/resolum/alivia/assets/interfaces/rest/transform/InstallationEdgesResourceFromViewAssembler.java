package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdges;
import com.resolum.alivia.assets.interfaces.rest.resources.InstallationEdgesResource;

public class InstallationEdgesResourceFromViewAssembler {
    public static InstallationEdgesResource toResourceFromView(InstallationEdges view) {
        return new InstallationEdgesResource(view.installationId(), view.microphoneEdgeId(), view.actuatorEdgeId(),
                view.replacedEdgeId());
    }
}
