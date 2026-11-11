package com.resolum.alivia.communications.domain.model.commands;

public record CreateAlertCommand(Long recipientUserId, String helpRequestId, String title, String body) {
}
