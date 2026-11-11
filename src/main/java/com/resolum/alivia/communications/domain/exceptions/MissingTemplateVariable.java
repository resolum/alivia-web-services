package com.resolum.alivia.communications.domain.exceptions;

import com.resolum.alivia.communications.domain.model.valueobjects.EmailTemplate;

public class MissingTemplateVariable extends RuntimeException {
    public MissingTemplateVariable(EmailTemplate template, String variable) {
        super("Missing required variable '" + variable + "' for email template " + template);
    }
}
