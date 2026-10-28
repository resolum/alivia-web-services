package com.resolum.alivia.business.application.internal.outboundservices.acl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.business.domain.exceptions.BusinessNameAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.DniAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.EmailAddressAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.LegalNameAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.RucAlreadyExists;
import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outbound service acting as Anti-Corruption Layer (ACL) adapter
 * to communicate with the Profiles bounded context and Storage service.
 */
@Slf4j
@Service("businessExternalProfilesService")
public class ExternalProfilesService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ProfilesContextFacade profilesContextFacade;
    private final StorageService storageService;
    private final String defaultPhotoUrl;

    public ExternalProfilesService(
            ProfilesContextFacade profilesContextFacade,
            StorageService storageService,
            @Value("${alivia.iam.profiles.default-photo-url:}") String defaultPhotoUrl) {
        this.profilesContextFacade = profilesContextFacade;
        this.storageService = storageService;
        this.defaultPhotoUrl = defaultPhotoUrl;
    }

    public StaffProfileId createStaffProfile(
            String corporateEmail,
            byte[] photoData,
            String photoFileName,
            String firstName,
            String lastName,
            String dni,
            LocalDate birthDate,
            String phoneNumber) {

        if (profilesContextFacade.existsStaffProfileByEmailAddress(corporateEmail)) {
            throw new EmailAddressAlreadyExists(corporateEmail);
        }
        if (profilesContextFacade.existsStaffProfileByDni(dni)) {
            throw new DniAlreadyExists(dni);
        }

        String photoUrl = defaultPhotoUrl;
        String photoPublicId = "default";
        if (photoData != null && photoData.length > 0) {
            try {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    photoUrl = uploaded.getOrDefault("url", defaultPhotoUrl);
                    photoPublicId = uploaded.getOrDefault("publicId", "default");
                }
            } catch (Exception e) {
                log.warn("Photo upload failed, using default: {}", e.getMessage());
            }
        }

        try {
            Long profileId = profilesContextFacade.createStaffProfile(
                    corporateEmail, photoUrl, photoPublicId, phoneNumber, firstName, lastName, dni, birthDate);
            return new StaffProfileId(profileId);
        } catch (EmailAddressAlreadyExistsException e) {
            throw new EmailAddressAlreadyExists(corporateEmail);
        } catch (DniAlreadyExistsException e) {
            throw new DniAlreadyExists(dni);
        }
    }

    public void updateStaffProfile(
            Long staffProfileId,
            byte[] photoData,
            String photoFileName,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String phoneNumber) {

        String photoUrl = null;
        String photoPublicId = null;
        if (photoData != null && photoData.length > 0) {
            try {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    photoUrl = uploaded.get("url");
                    photoPublicId = uploaded.get("publicId");
                }
            } catch (Exception e) {
                log.warn("Photo update upload failed: {}", e.getMessage());
            }
        }

        profilesContextFacade.updateStaffProfile(
                staffProfileId, firstName, lastName, birthDate, phoneNumber, photoUrl, photoPublicId);
    }

    public BusinessProfileId createBusinessProfile(
            byte[] photoData,
            String photoFileName,
            String businessName,
            String legalName,
            Long ruc,
            String mainActivity,
            String description,
            String emailAddress,
            String phoneNumber,
            String formattedAddress,
            Double latitude,
            Double longitude,
            String placeId,
            String businessHours,
            String supportEmail,
            String emergencyPhone,
            String whatsappNum,
            String slogan) {

        if (profilesContextFacade.existsBusinessProfileByEmailAddress(emailAddress)) {
            throw new EmailAddressAlreadyExists(emailAddress);
        }
        if (profilesContextFacade.existsBusinessProfileByRuc(String.valueOf(ruc))) {
            throw new RucAlreadyExists(ruc);
        }
        if (profilesContextFacade.existsBusinessProfileByBusinessName(businessName)) {
            throw new BusinessNameAlreadyExists(businessName);
        }
        if (profilesContextFacade.existsBusinessProfileByLegalName(legalName)) {
            throw new LegalNameAlreadyExists(legalName);
        }

        String photoUrl = defaultPhotoUrl;
        String photoPublicId = "default";
        if (photoData != null && photoData.length > 0) {
            try {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    photoUrl = uploaded.getOrDefault("url", defaultPhotoUrl);
                    photoPublicId = uploaded.getOrDefault("publicId", "default");
                }
            } catch (Exception e) {
                log.warn("Photo upload failed, using default: {}", e.getMessage());
            }
        }

        List<WeeklyPeriod> periods = parseBusinessHours(businessHours);

        try {
            Long profileId = profilesContextFacade.createBusinessProfile(
                    emailAddress, photoUrl, photoPublicId, whatsappNum, String.valueOf(ruc),
                    businessName, legalName, description, mainActivity, emergencyPhone != null ? emergencyPhone : phoneNumber,
                    periods, slogan, supportEmail, formattedAddress, latitude, longitude, placeId);
            return new BusinessProfileId(profileId);
        } catch (EmailAddressAlreadyExistsException e) {
            throw new EmailAddressAlreadyExists(emailAddress);
        } catch (RucAlreadyExistsException e) {
            throw new RucAlreadyExists(ruc);
        } catch (BusinessNameAlreadyExistsException e) {
            throw new BusinessNameAlreadyExists(businessName);
        } catch (LegalNameAlreadyExistsException e) {
            throw new LegalNameAlreadyExists(legalName);
        }
    }

    public void updateBusinessProfile(
            Long businessProfileId,
            byte[] photoData,
            String photoFileName,
            String businessName,
            String legalName,
            String mainActivity,
            String description,
            String phoneNumber,
            String businessHours,
            String supportEmail,
            String emergencyPhone,
            String whatsappNum,
            String slogan) {

        String photoUrl = null;
        String photoPublicId = null;
        if (photoData != null && photoData.length > 0) {
            try {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    photoUrl = uploaded.get("url");
                    photoPublicId = uploaded.get("publicId");
                }
            } catch (Exception e) {
                log.warn("Photo update upload failed: {}", e.getMessage());
            }
        }

        List<WeeklyPeriod> periods = parseBusinessHours(businessHours);

        try {
            profilesContextFacade.updateBusinessProfile(
                    businessProfileId, photoUrl, photoPublicId, whatsappNum, businessName, legalName,
                    description, mainActivity, emergencyPhone != null ? emergencyPhone : phoneNumber,
                    periods, slogan, supportEmail);
        } catch (BusinessNameAlreadyExistsException e) {
            throw new BusinessNameAlreadyExists(businessName);
        } catch (LegalNameAlreadyExistsException e) {
            throw new LegalNameAlreadyExists(legalName);
        }
    }

    private List<WeeklyPeriod> parseBusinessHours(String businessHoursStr) {
        if (businessHoursStr == null || businessHoursStr.isBlank()) {
            return List.of(new WeeklyPeriod(
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))
            ));
        }
        try {
            return OBJECT_MAPPER.readValue(businessHoursStr, new TypeReference<List<WeeklyPeriod>>() {});
        } catch (Exception e) {
            return List.of(new WeeklyPeriod(
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    new TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))
            ));
        }
    }
}
