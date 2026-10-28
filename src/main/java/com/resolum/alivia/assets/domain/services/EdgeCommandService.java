package com.resolum.alivia.assets.domain.services;

import com.resolum.alivia.assets.domain.model.commands.ChangeEdgeStatusCommand;
import com.resolum.alivia.assets.domain.model.commands.RegisterEdgeCommand;
import com.resolum.alivia.assets.domain.model.commands.UpdateEdgeCommand;

public interface EdgeCommandService {

    Long handle(RegisterEdgeCommand command);
    Long handle(UpdateEdgeCommand command) ;
    Long handle(ChangeEdgeStatusCommand command);

}
