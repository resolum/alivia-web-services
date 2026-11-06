package com.resolum.alivia.assets.domain.model.queries;

/** The version of the table of devices of the installation where the edge is assigned (cheap: no entries are read). */
public record GetEdgeTableVersionByEdgeIdQuery(Long edgeId) {
}
