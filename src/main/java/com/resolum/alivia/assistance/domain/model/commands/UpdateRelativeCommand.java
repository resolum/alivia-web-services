package com.resolum.alivia.assistance.domain.model.commands;

import java.time.LocalDate;

public record UpdateRelativeCommand(
        Long relativeId,
        byte[] photoData,
        String photoFileName,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthDate
) {}
