package com.resolum.alivia.operations.domain.model.valueobjects;

/** What the technician decided at the home during the evaluation visit. */
public enum EvaluationResult {
    /** The home takes the configuration as contracted. */
    VIABLE,
    /** The home takes it with changes the technician describes in the notes. */
    VIABLE_WITH_ADAPTATION,
    /** The solution cannot be installed in the home. */
    NOT_VIABLE;

    public boolean isViable() {
        return this != NOT_VIABLE;
    }
}
