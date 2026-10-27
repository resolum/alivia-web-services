package com.resolum.alivia.profiles.application.acl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.queries.ExistsBusinessProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetBusinessProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.domain.services.PersonProfileCommandService;
import com.resolum.alivia.profiles.domain.services.PersonProfileQueryService;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.profiles.domain.services.StaffProfileCommandService;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the Profiles anti-corruption layer facade.
 *
 * <p>Delegates cross-context requests to internal command/query services and returns primitive
 * or standard Java types so consumers do not depend on Profiles aggregates.</p>
 */
@Slf4j
@Service
public class ProfilesContextFacadeImpl implements ProfilesContextFacade {

    private final PersonProfileCommandService personProfileCommandService;
    private final PersonProfileQueryService personProfileQueryService;
    private final StaffProfileCommandService staffProfileCommandService;
    private final StaffProfileQueryService staffProfileQueryService;
    private final BusinessProfileCommandService businessProfileCommandService;
    private final BusinessProfileQueryService businessProfileQueryService;
    private final ProfileQueryService profileQueryService;

    public ProfilesContextFacadeImpl(
            PersonProfileCommandService personProfileCommandService,
            PersonProfileQueryService personProfileQueryService,
            StaffProfileCommandService staffProfileCommandService,
            StaffProfileQueryService staffProfileQueryService,
            BusinessProfileCommandService businessProfileCommandService,
            BusinessProfileQueryService businessProfileQueryService,
            ProfileQueryService profileQueryService) {
        this.personProfileCommandService = personProfileCommandService;
        this.personProfileQueryService = personProfileQueryService;
        this.staffProfileCommandService = staffProfileCommandService;
        this.staffProfileQueryService = staffProfileQueryService;
        this.businessProfileCommandService = businessProfileCommandService;
        this.businessProfileQueryService = businessProfileQueryService;
        this.profileQueryService = profileQueryService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long createPersonProfile(String emailAddress, String photoUrl, String photoPublicId,
                                    String phoneNumber, String firstName, String lastName,
                                    String dni, LocalDate birthDate) {
        log.info("Creating person profile via ACL for email: {}", emailAddress);
        var command = new CreatePersonProfileCommand(
                emailAddress, photoUrl, photoPublicId, phoneNumber, firstName, lastName, dni, birthDate);
        Long profileId = personProfileCommandService.handle(command);
        log.info("Person profile created via ACL with id: {}", profileId);
        return profileId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long createStaffProfile(String emailAddress, String photoUrl, String photoPublicId,
                                   String phoneNumber, String firstName, String lastName,
                                   String dni, LocalDate birthDate) {
        log.info("Creating staff profile via ACL for email: {}", emailAddress);
        var command = new CreateStaffProfileCommand(
                emailAddress, photoUrl, photoPublicId, phoneNumber, firstName, lastName, dni, birthDate);
        Long profileId = staffProfileCommandService.handle(command);
        log.info("Staff profile created via ACL with id: {}", profileId);
        return profileId;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfilesContextFacade.StaffProfileData> fetchStaffProfileById(Long profileId) {
        if (profileId == null || profileId <= 0) return Optional.empty();
        return staffProfileQueryService.handle(new GetStaffProfileByIdQuery(profileId))
                .map(profile -> new ProfilesContextFacade.StaffProfileData(
                        profile.getId(), profile.getEmailAddress().email(),
                        profile.getPhoto().photoUrl(), profile.getPhoto().photoPublicId(),
                        profile.getPhoneNumber().phoneNumber(), profile.getPersonName().firstName(),
                        profile.getPersonName().lastName(), profile.getDni().dni(),
                        profile.getBirthDate().date()));
    }

    @Override
    @Transactional
    public Long updateStaffProfile(Long profileId, String firstName, String lastName,
                                   LocalDate birthDate, String phoneNumber, String photoUrl,
                                   String photoPublicId) {
        return staffProfileCommandService.handle(new UpdateStaffProfileCommand(
                profileId, firstName, lastName, birthDate, phoneNumber, photoUrl, photoPublicId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long createBusinessProfile(String emailAddress, String photoUrl, String photoPublicId,
                                      String whatsappNum, String ruc, String businessName,
                                      String legalName, String description, String mainActivity,
                                      String emergencyPhone, List<WeeklyPeriod> businessHours,
                                      String slogan, String supportEmail, String streetAddress,
                                      Double latitude, Double longitude, String placeId) {
        log.info("Creating business profile via ACL for business: {}", businessName);
        var hoursVo = businessHours != null ? new BusinessHours(businessHours) : null;
        var command = new CreateBusinessProfileCommand(
                emailAddress, photoUrl, photoPublicId, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, hoursVo, slogan, supportEmail,
                streetAddress, latitude, longitude, placeId);
        Long profileId = businessProfileCommandService.handle(command);
        log.info("Business profile created via ACL with id: {}", profileId);
        return profileId;
    }

    /**
     * Convenience overload for creating a business profile using {@link BusinessHours}.
     */
    @Transactional
    public Long createBusinessProfile(String emailAddress, String photoUrl, String photoPublicId,
                                      String whatsappNum, String ruc, String businessName,
                                      String legalName, String description, String mainActivity,
                                      String emergencyPhone, BusinessHours businessHours,
                                      String slogan, String supportEmail, String streetAddress,
                                      Double latitude, Double longitude, String placeId) {
        return createBusinessProfile(
                emailAddress, photoUrl, photoPublicId, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone,
                businessHours != null ? businessHours.periods() : null,
                slogan, supportEmail, streetAddress, latitude, longitude, placeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsPersonProfileByEmailAddress(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            log.debug("Person profile email existence check ignored: email is empty");
            return false;
        }
        try {
            var emailVo = new EmailAddress(emailAddress);
            return personProfileQueryService.handle(new ExistsPersonProfileByEmailAddressQuery(emailVo));
        } catch (IllegalArgumentException exception) {
            log.warn("Person profile email check rejected: invalid email format");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsPersonProfileByDni(String dni) {
        if (dni == null || dni.isBlank()) {
            log.debug("Person profile DNI existence check ignored: DNI is empty");
            return false;
        }
        try {
            var dniVo = new Dni(dni);
            return personProfileQueryService.handle(new ExistsPersonProfileByDniQuery(dniVo));
        } catch (IllegalArgumentException exception) {
            log.warn("Person profile DNI check rejected: invalid DNI format");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsStaffProfileByEmailAddress(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            log.debug("Staff profile email existence check ignored: email is empty");
            return false;
        }
        try {
            var emailVo = new EmailAddress(emailAddress);
            return staffProfileQueryService.handle(new ExistsStaffProfileByEmailAddressQuery(emailVo));
        } catch (IllegalArgumentException exception) {
            log.warn("Staff profile email check rejected: invalid email format");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsStaffProfileByDni(String dni) {
        if (dni == null || dni.isBlank()) {
            log.debug("Staff profile DNI existence check ignored: DNI is empty");
            return false;
        }
        try {
            var dniVo = new Dni(dni);
            return staffProfileQueryService.handle(new ExistsStaffProfileByDniQuery(dniVo));
        } catch (IllegalArgumentException exception) {
            log.warn("Staff profile DNI check rejected: invalid DNI format");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsBusinessProfileByEmailAddress(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            log.debug("Business profile email existence check ignored: email is empty");
            return false;
        }
        try {
            var emailVo = new EmailAddress(emailAddress);
            return businessProfileQueryService.handle(new ExistsBusinessProfileByEmailAddressQuery(emailVo));
        } catch (IllegalArgumentException exception) {
            log.warn("Business profile email check rejected: invalid email format");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsProfileById(Long profileId) {
        if (profileId == null || profileId <= 0) {
            log.debug("Profile ID existence check ignored: ID is null or non-positive");
            return false;
        }
        try {
            return profileQueryService.handle(new ExistsProfileByIdQuery(profileId));
        } catch (IllegalArgumentException exception) {
            log.warn("Profile ID check rejected: invalid profile ID");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> fetchPersonProfileFullNameById(Long profileId) {
        if (profileId == null || profileId <= 0) {
            return Optional.empty();
        }
        return personProfileQueryService.handle(new GetPersonProfileByIdQuery(profileId))
                .map(profile -> profile.getPersonName().getFullName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> fetchStaffProfileFullNameById(Long profileId) {
        if (profileId == null || profileId <= 0) {
            return Optional.empty();
        }
        return staffProfileQueryService.handle(new GetStaffProfileByIdQuery(profileId))
                .map(profile -> profile.getPersonName().getFullName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> fetchBusinessProfileNameById(Long profileId) {
        if (profileId == null || profileId <= 0) {
            return Optional.empty();
        }
        return businessProfileQueryService.handle(new GetBusinessProfileByIdQuery(profileId))
                .map(profile -> profile.getBusinessName().businessName());
    }
}
