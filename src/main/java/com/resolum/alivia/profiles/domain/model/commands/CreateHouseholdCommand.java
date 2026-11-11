package com.resolum.alivia.profiles.domain.model.commands;

/**
 * Command to create a household account.
 *
 * @param alias               the household alias/name
 * @param contactPhone        the contact phone number
 * @param contactEmail        the contact email address
 * @param address             the street address
 * @param district            the district/zone
 * @param city                the city
 * @param formattedAddress     the full formatted address
 * @param latitude            the latitude
 * @param longitude           the longitude
 * @param placeId             the Google Maps place id
 * @param responsibleUserId   the user id of the principal member responsible for the household
 */
public record CreateHouseholdCommand(
        String alias,
        String contactPhone,
        String contactEmail,
        String address,
        String district,
        String city,
        String formattedAddress,
        Double latitude,
        Double longitude,
        String placeId,
        Long responsibleUserId
) {
}
