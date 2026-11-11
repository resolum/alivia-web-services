package com.resolum.alivia.communications.domain.model.commands;

import com.resolum.alivia.communications.domain.model.valueobjects.EmailTemplate;
import java.util.Map;

public record SendEmailCommand(String emailAddress, EmailTemplate template, Map<String, String> variables) {
}
