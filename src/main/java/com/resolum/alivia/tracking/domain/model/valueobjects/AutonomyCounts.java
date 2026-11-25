package com.resolum.alivia.tracking.domain.model.valueobjects;

/**
 * How many commands there were and how each one ended. Every command is in exactly one of the six outcomes, so
 * {@code requested} is their sum.
 *
 * @param requested             the commands asked for, each counted once whatever the number of events about it
 * @param succeeded             the actuator did it
 * @param failed                the actuator failed or did not finish in time
 * @param rejectedLowConfidence the voice was not recognised well enough and the command was not run
 * @param rejectedUnauthorized  the command was not run because the person is not allowed to
 * @param noConfirmation        the actuator did not confirm before the deadline and nothing says what happened
 * @param inProgress            asked for, and no result, rejection or timeout yet
 * @param executedLocally       of the commands the actuator ran (succeeded or failed), those that were executed without
 *                              connection and reached the backend later
 */
public record AutonomyCounts(int requested, int succeeded, int failed, int rejectedLowConfidence,
                             int rejectedUnauthorized, int noConfirmation, int inProgress, int executedLocally) {

    public static final AutonomyCounts NONE = new AutonomyCounts(0, 0, 0, 0, 0, 0, 0, 0);
}
