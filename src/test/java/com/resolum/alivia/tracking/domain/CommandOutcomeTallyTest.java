package com.resolum.alivia.tracking.domain;

import com.resolum.alivia.tracking.domain.model.valueobjects.AutonomyCounts;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandOutcomeTally;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceAutonomy;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** How commands are counted from the events about them: once each, in exactly one outcome. */
class CommandOutcomeTallyTest {

    private static final Instant T = Instant.parse("2026-11-24T15:00:00Z");

    private static CommandFact requested(String command, long device, int seconds) {
        return new CommandFact(command, EventType.COMMAND_REQUESTED, null, EventOrigin.LIVE, T.plusSeconds(seconds), device);
    }

    private static CommandFact result(String command, long device, EventResult result, int seconds, EventOrigin origin) {
        return new CommandFact(command, EventType.COMMAND_RESULT, result, origin, T.plusSeconds(seconds), device);
    }

    private static CommandFact rejected(String command, Long device, EventResult result, int seconds) {
        return new CommandFact(command, EventType.COMMAND_REJECTED, result, EventOrigin.LIVE, T.plusSeconds(seconds), device);
    }

    private static CommandOutcomeTally tally(CommandFact... facts) {
        var tally = new CommandOutcomeTally();
        for (var fact : facts) tally.add(fact);
        return tally;
    }

    private static int sumOfOutcomes(AutonomyCounts c) {
        return c.succeeded() + c.failed() + c.rejectedLowConfidence() + c.rejectedUnauthorized() + c.noConfirmation()
                + c.inProgress();
    }

    @Test
    void aCommandWithItsRequestAndItsResultIsOneCommand() {
        var total = tally(requested("c1", 10, 0), result("c1", 10, EventResult.SUCCESS, 2, EventOrigin.LIVE)).total();

        assertThat(total.requested()).isEqualTo(1);
        assertThat(total.succeeded()).isEqualTo(1);
        assertThat(sumOfOutcomes(total)).isEqualTo(total.requested());
    }

    @Test
    void anEventSentTwiceIsStillOneCommand() {
        var request = requested("c1", 10, 0);
        var done = result("c1", 10, EventResult.SUCCESS, 2, EventOrigin.LIVE);

        var total = tally(request, done, request, done, done).total();

        assertThat(total.requested()).isEqualTo(1);
        assertThat(total.succeeded()).isEqualTo(1);
    }

    @Test
    void theFourOutcomesOfTheStoryAreClassified() {
        var total = tally(
                requested("ok", 10, 0), result("ok", 10, EventResult.SUCCESS, 1, EventOrigin.LIVE),
                requested("bad", 10, 0), result("bad", 10, EventResult.FAILED, 1, EventOrigin.LIVE),
                requested("late", 11, 0), result("late", 11, EventResult.FAILED_TIMEOUT, 1, EventOrigin.LIVE),
                rejected("low", 10L, EventResult.REJECTED_LOW_CONFIDENCE, 0),
                requested("silent", 11, 0), result("silent", 11, EventResult.NO_CONFIRMATION, 5, EventOrigin.LIVE)).total();

        assertThat(total).isEqualTo(new AutonomyCounts(5, 1, 2, 1, 0, 1, 0, 0));
        assertThat(sumOfOutcomes(total)).isEqualTo(total.requested());
    }

    @Test
    void anUnauthorizedRejectionIsCountedApartFromTheLowConfidenceOne() {
        var total = tally(rejected("a", 10L, EventResult.REJECTED_UNAUTHORIZED, 0),
                rejected("b", 10L, EventResult.REJECTED_LOW_CONFIDENCE, 0)).total();

        assertThat(total.rejectedUnauthorized()).isEqualTo(1);
        assertThat(total.rejectedLowConfidence()).isEqualTo(1);
    }

    @Test
    void aCommandWithOnlyItsRequestIsInProgress() {
        var total = tally(requested("c1", 10, 0)).total();

        assertThat(total.requested()).isEqualTo(1);
        assertThat(total.inProgress()).isEqualTo(1);
    }

    @Test
    void theActuatorResultBeatsTheNoConfirmationWhicheverArrivesFirst() {
        var silent = result("c1", 10, EventResult.NO_CONFIRMATION, 5, EventOrigin.LIVE);
        var done = result("c1", 10, EventResult.SUCCESS, 9, EventOrigin.SYNCED);

        assertThat(tally(silent, done).total().succeeded()).isEqualTo(1);
        assertThat(tally(done, silent).total().succeeded()).isEqualTo(1);
        assertThat(tally(done, silent).total().noConfirmation()).isZero();
    }

    @Test
    void ALateSuccessBeatsAnEarlierTimeoutAndASimultaneousOneWins() {
        var timeout = result("c1", 10, EventResult.FAILED_TIMEOUT, 5, EventOrigin.LIVE);
        var success = result("c1", 10, EventResult.SUCCESS, 8, EventOrigin.LIVE);
        var simultaneous = result("c1", 10, EventResult.FAILED, 8, EventOrigin.LIVE);

        assertThat(tally(timeout, success).total().succeeded()).isEqualTo(1);
        assertThat(tally(success, timeout).total().succeeded()).isEqualTo(1);
        assertThat(tally(success, simultaneous).total().succeeded()).isEqualTo(1);
        assertThat(tally(simultaneous, success).total().succeeded()).isEqualTo(1);
        // The other way round: a failure after a success is what happened last.
        assertThat(tally(success, result("c1", 10, EventResult.FAILED, 9, EventOrigin.LIVE)).total().failed()).isEqualTo(1);
    }

    @Test
    void theOrderTheEventsArriveInDoesNotChangeTheCount() {
        var facts = new ArrayList<CommandFact>(List.of(
                requested("a", 10, 0), result("a", 10, EventResult.SUCCESS, 2, EventOrigin.SYNCED),
                requested("b", 11, 0), result("b", 11, EventResult.NO_CONFIRMATION, 5, EventOrigin.LIVE),
                result("b", 11, EventResult.FAILED, 7, EventOrigin.SYNCED),
                rejected("c", null, EventResult.REJECTED_LOW_CONFIDENCE, 1), requested("d", 10, 3)));
        var expected = tally(facts.toArray(new CommandFact[0])).total();

        for (int seed = 0; seed < 25; seed++) {
            Collections.shuffle(facts, new Random(seed));
            assertThat(tally(facts.toArray(new CommandFact[0])).total()).as("seed " + seed).isEqualTo(expected);
        }
        assertThat(expected).isEqualTo(new AutonomyCounts(4, 1, 1, 1, 0, 0, 1, 2));
    }

    @Test
    void onlyWhatTheActuatorRanAndReachedTheBackendLaterIsExecutedLocally() {
        var total = tally(
                requested("a", 10, 0), result("a", 10, EventResult.SUCCESS, 2, EventOrigin.SYNCED),
                requested("b", 10, 0), result("b", 10, EventResult.FAILED, 2, EventOrigin.SYNCED),
                requested("c", 10, 0), result("c", 10, EventResult.SUCCESS, 2, EventOrigin.LIVE),
                requested("d", 10, 0), result("d", 10, EventResult.NO_CONFIRMATION, 2, EventOrigin.SYNCED)).total();

        assertThat(total.executedLocally()).isEqualTo(2);
        assertThat(total.requested()).isEqualTo(4);
    }

    @Test
    void theCommandsAreGroupedByDeviceAndTheOnesWithoutDeviceAreApart() {
        var tally = tally(
                requested("a", 11, 0), result("a", 11, EventResult.SUCCESS, 1, EventOrigin.LIVE),
                requested("b", 10, 0), result("b", 10, EventResult.FAILED, 1, EventOrigin.LIVE),
                requested("c", 10, 0), result("c", 10, EventResult.SUCCESS, 1, EventOrigin.LIVE),
                rejected("d", null, EventResult.REJECTED_LOW_CONFIDENCE, 0));

        assertThat(tally.byDevice()).extracting(DeviceAutonomy::deviceId).containsExactly(10L, 11L);
        assertThat(tally.byDevice().get(0).counts()).isEqualTo(new AutonomyCounts(2, 1, 1, 0, 0, 0, 0, 0));
        assertThat(tally.byDevice().get(1).counts().succeeded()).isEqualTo(1);
        assertThat(tally.withoutDevice()).isEqualTo(new AutonomyCounts(1, 0, 0, 1, 0, 0, 0, 0));
        assertThat(tally.total().requested()).isEqualTo(4);
    }

    @Test
    void aCommandTakesTheDeviceFromWhicheverOfItsEventsNamesOne() {
        var tally = tally(rejected("a", null, EventResult.REJECTED_LOW_CONFIDENCE, 0),
                requested("a", 12, 1));

        assertThat(tally.byDevice()).extracting(DeviceAutonomy::deviceId).containsExactly(12L);
        assertThat(tally.withoutDevice().requested()).isZero();
    }

    @Test
    void anEventWithoutACommandIsNotACommand() {
        var tally = tally(new CommandFact(null, EventType.COMMAND_RESULT, EventResult.SUCCESS, EventOrigin.LIVE, T, 10L));

        assertThat(tally.total()).isEqualTo(AutonomyCounts.NONE);
    }

    @Test
    void noEventsIsAllZeros() {
        var tally = new CommandOutcomeTally();

        assertThat(tally.total()).isEqualTo(AutonomyCounts.NONE);
        assertThat(tally.byDevice()).isEmpty();
        assertThat(tally.withoutDevice()).isEqualTo(AutonomyCounts.NONE);
    }
}
