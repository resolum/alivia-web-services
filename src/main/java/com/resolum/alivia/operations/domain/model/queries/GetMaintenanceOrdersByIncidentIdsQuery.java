package com.resolum.alivia.operations.domain.model.queries;

import java.util.Collection;

public record GetMaintenanceOrdersByIncidentIdsQuery(Collection<Long> incidentIds) {
}
