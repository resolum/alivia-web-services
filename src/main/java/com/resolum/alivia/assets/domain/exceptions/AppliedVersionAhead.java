package com.resolum.alivia.assets.domain.exceptions;

/** The edge reported a version that the table of its installation has not reached: it cannot have applied it. */
public class AppliedVersionAhead extends RuntimeException {
    public AppliedVersionAhead(Long edgeId, long applied, long current) {
        super(String.format("edge with id %d reported version %d but the table is at version %d", edgeId, applied, current));
    }
}
