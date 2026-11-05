package com.resolum.alivia.operations.domain.model.valueobjects;

/** What the family says is wrong with the device, as the report form offers it. */
public enum IncidentProblemType {
    BATTERY(IncidentSeverity.MEDIUM),
    NO_RESPONSE(IncidentSeverity.HIGH),
    FALSE_ALARM(IncidentSeverity.MEDIUM),
    CONNECTIVITY(IncidentSeverity.MEDIUM),
    PHYSICAL_DAMAGE(IncidentSeverity.HIGH),
    OTHER(IncidentSeverity.LOW);

    private final IncidentSeverity severity;

    IncidentProblemType(IncidentSeverity severity) {
        this.severity = severity;
    }

    /** Severity the support team starts from for this kind of problem. */
    public IncidentSeverity severity() {
        return severity;
    }
}
