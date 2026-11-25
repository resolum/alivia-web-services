package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts commands from the events about them. The events of a command are one to three (the request, the result, the
 * rejection, the timeout) and may arrive in any order and more than once; the command is what is counted.
 *
 * <p>How a command is classified, in this order of precedence:</p>
 * <ol>
 *   <li>the result the actuator reported ({@code SUCCESS}, {@code FAILED}, {@code FAILED_TIMEOUT}); if there are several,
 *       the latest one, and {@code SUCCESS} when they are simultaneous. A late real result beats a timeout, as the voice
 *       edge's {@code NO_CONFIRMATION} is only what it knew when it gave up;</li>
 *   <li>a rejection ({@code REJECTED_LOW_CONFIDENCE} or {@code REJECTED_UNAUTHORIZED});</li>
 *   <li>{@code NO_CONFIRMATION};</li>
 *   <li>otherwise it is in progress.</li>
 * </ol>
 */
public final class CommandOutcomeTally {

    private enum Outcome { SUCCEEDED, FAILED, REJECTED_LOW_CONFIDENCE, REJECTED_UNAUTHORIZED, NO_CONFIRMATION, IN_PROGRESS }

    /** What is known of one command while its events are read. */
    private static final class Command {
        Long deviceId;
        EventResult ranResult;
        Instant ranAt;
        EventOrigin ranOrigin;
        EventResult rejection;
        boolean noConfirmation;

        void add(CommandFact fact) {
            if (deviceId == null) deviceId = fact.deviceId();
            var result = fact.result();
            if (fact.type() == EventType.COMMAND_RESULT && result != null) {
                switch (result) {
                    case SUCCESS, FAILED, FAILED_TIMEOUT -> keepIfLater(fact);
                    case NO_CONFIRMATION -> noConfirmation = true;
                    default -> {
                    }
                }
            } else if (fact.type() == EventType.COMMAND_REJECTED && result != null) {
                rejection = result;
            }
        }

        private void keepIfLater(CommandFact fact) {
            boolean later = ranAt == null || fact.occurredAt().isAfter(ranAt)
                    || (fact.occurredAt().equals(ranAt) && fact.result() == EventResult.SUCCESS);
            if (later) {
                ranResult = fact.result();
                ranAt = fact.occurredAt();
                ranOrigin = fact.origin();
            }
        }

        Outcome outcome() {
            if (ranResult != null) return ranResult == EventResult.SUCCESS ? Outcome.SUCCEEDED : Outcome.FAILED;
            if (rejection == EventResult.REJECTED_LOW_CONFIDENCE) return Outcome.REJECTED_LOW_CONFIDENCE;
            if (rejection == EventResult.REJECTED_UNAUTHORIZED) return Outcome.REJECTED_UNAUTHORIZED;
            if (noConfirmation) return Outcome.NO_CONFIRMATION;
            return Outcome.IN_PROGRESS;
        }
    }

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /** Adds an event about a command; one with no command id is not about any command and is ignored. */
    public void add(CommandFact fact) {
        if (fact.commandId() == null) return;
        commands.computeIfAbsent(fact.commandId(), id -> new Command()).add(fact);
    }

    public AutonomyCounts total() {
        return count(commands.values());
    }

    /** The counts of each device that had commands, by device id; the commands that name no device are not here. */
    public List<DeviceAutonomy> byDevice() {
        var grouped = new TreeMap<Long, List<Command>>();
        commands.values().stream().filter(c -> c.deviceId != null)
                .forEach(c -> grouped.computeIfAbsent(c.deviceId, id -> new java.util.ArrayList<>()).add(c));
        return grouped.entrySet().stream().map(e -> new DeviceAutonomy(e.getKey(), count(e.getValue()))).toList();
    }

    public AutonomyCounts withoutDevice() {
        return count(commands.values().stream().filter(c -> c.deviceId == null).toList());
    }

    private static AutonomyCounts count(Iterable<Command> group) {
        int requested = 0, succeeded = 0, failed = 0, lowConfidence = 0, unauthorized = 0, noConfirmation = 0,
                inProgress = 0, local = 0;
        for (var command : group) {
            requested++;
            switch (command.outcome()) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case REJECTED_LOW_CONFIDENCE -> lowConfidence++;
                case REJECTED_UNAUTHORIZED -> unauthorized++;
                case NO_CONFIRMATION -> noConfirmation++;
                case IN_PROGRESS -> inProgress++;
            }
            if (command.ranResult != null && command.ranOrigin == EventOrigin.SYNCED) local++;
        }
        return new AutonomyCounts(requested, succeeded, failed, lowConfidence, unauthorized, noConfirmation, inProgress,
                local);
    }
}
