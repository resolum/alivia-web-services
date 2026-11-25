package com.resolum.alivia.tracking.domain.model.valueobjects;

import java.time.Instant;

/**
 * The period and the page of a history once the request has been checked and its defaults filled in.
 *
 * @param from the start of the period, included
 * @param to   the end of the period, excluded
 * @param page the number of the page, from 0
 * @param size the size of the page
 */
public record HistoryWindow(Instant from, Instant to, int page, int size) {
}
