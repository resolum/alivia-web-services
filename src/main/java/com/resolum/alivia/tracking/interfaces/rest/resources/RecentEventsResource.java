package com.resolum.alivia.tracking.interfaces.rest.resources;

import java.util.List;

/**
 * The latest events of the home of an assisted person, newest first.
 *
 * @param limit how many were asked for
 */
public record RecentEventsResource(Long personId, int limit, List<RecentEventResource> items) {
}
