package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.CancelActivityOccurrenceCommand;
import com.resolum.alivia.assistance.domain.model.commands.CompleteActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.MarkMissedActivitiesCommand;

public interface ActivityLogCommandService {
    Long handle(CompleteActivityCommand command);
    Long handle(CancelActivityOccurrenceCommand command);
    void handle(MarkMissedActivitiesCommand command);
}
