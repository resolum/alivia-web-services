package com.resolum.alivia.profiles.domain.model.commands;

/**
 * Command to update general/contact information of an existing household.
 *
 * @param householdId  the household identifier
 * @param alias        the new alias
 * @param contactPhone the new contact phone
 * @param contactEmail the new contact email
 */
public record UpdateHouseholdCommand(
        Long householdId,
        String alias,
        String contactPhone,
        String contactEmail
) {
}
