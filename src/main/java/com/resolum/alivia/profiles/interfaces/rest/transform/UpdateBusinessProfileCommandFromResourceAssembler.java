package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateBusinessProfileResource;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class UpdateBusinessProfileCommandFromResourceAssembler {

    private static final BusinessHours DEFAULT_BUSINESS_HOURS = new BusinessHours(
            List.of(new WeeklyPeriod(
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))
            ))
    );

    private UpdateBusinessProfileCommandFromResourceAssembler() {
    }

    public static UpdateBusinessProfileCommand toCommandFromResource(Long profileId, UpdateBusinessProfileResource resource) {
        if (resource == null) return null;
        byte[] photoData = null;
        String photoFileName = null;
        if (resource.photo() != null && !resource.photo().isEmpty()) {
            try {
                photoData = resource.photo().getBytes();
                photoFileName = resource.photo().getOriginalFilename();
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid photo file", e);
            }
        }
        var hours = resource.businessHours() != null ? resource.businessHours() : DEFAULT_BUSINESS_HOURS;
        return new UpdateBusinessProfileCommand(
                profileId,
                resource.emailAddress(),
                photoData,
                photoFileName,
                resource.whatsappNum(),
                resource.ruc(),
                resource.businessName(),
                resource.legalName(),
                resource.description(),
                resource.mainActivity(),
                resource.emergencyPhone(),
                hours,
                resource.slogan(),
                resource.supportEmail(),
                resource.streetAddress(),
                resource.latitude(),
                resource.longitude(),
                resource.placeId()
        );
    }
}
