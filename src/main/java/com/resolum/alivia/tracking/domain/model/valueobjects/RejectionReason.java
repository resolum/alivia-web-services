package com.resolum.alivia.tracking.domain.model.valueobjects;

/** Why a reported event or state is not accepted. The code is what the edge reads in the answer. */
public enum RejectionReason {
    INVALID_EVENT_ID("invalid_event_id"),
    TYPE_NOT_ALLOWED("type_not_allowed"),
    UNKNOWN_DEVICE("unknown_device"),
    DEVICE_REQUIRED("device_required"),
    COMMAND_DATA_REQUIRED("command_data_required"),
    INVALID_ACTION("invalid_action"),
    INVALID_RESULT("invalid_result"),
    CONFIDENCE_OUT_OF_RANGE("confidence_out_of_range"),
    OCCURRED_IN_FUTURE("occurred_in_future"),
    INVALID_TIMESTAMP("invalid_timestamp"),
    DETAIL_TOO_LONG("detail_too_long"),
    ATTRIBUTES_INVALID("attributes_invalid"),
    FORBIDDEN_ATTRIBUTE("forbidden_attribute"),
    BATTERY_OUT_OF_RANGE("battery_out_of_range"),
    INVALID_STATUS("invalid_status");

    private final String code;

    RejectionReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
