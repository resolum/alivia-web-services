package com.resolum.alivia.operations.domain.model.valueobjects;

import java.time.Duration;

/**
 * Kind of technical order that sends a technician to a home.
 *
 * <p>Each kind books a different arrival window: the evaluation takes 45 to 60 minutes, so the family
 * gets a two-hour window; the installation of the edges, the microphone and the actuators takes most of
 * a morning or an afternoon, so it books four hours.</p>
 *
 * <p>{@code daysAhead} is how far ahead the order can be booked, counted from tomorrow. The evaluation
 * must happen while the card hold lasts (seven days); the installation follows it, within two weeks.</p>
 */
public enum ServiceType {
    EVALUATION(Duration.ofHours(2), 7),
    INSTALLATION(Duration.ofHours(4), 14);

    private final Duration window;
    private final int daysAhead;

    ServiceType(Duration window, int daysAhead) {
        this.window = window;
        this.daysAhead = daysAhead;
    }

    public Duration window() {
        return window;
    }

    public int daysAhead() {
        return daysAhead;
    }
}
