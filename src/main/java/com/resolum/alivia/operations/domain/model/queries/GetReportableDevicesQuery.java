package com.resolum.alivia.operations.domain.model.queries;

/** The devices installed in the home of the account, which it can report incidents about. */
public record GetReportableDevicesQuery(Long userId) {
}
