package com.resolum.alivia.profiles.application.acl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;
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
import com.resolum.alivia.profiles.domain.model.valueobjects.BirthDate;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.PersonName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.domain.services.BusinessProfileQueryService;
import com.resolum.alivia.profiles.domain.services.PersonProfileCommandService;
import com.resolum.alivia.profiles.domain.services.PersonProfileQueryService;
import com.resolum.alivia.profiles.domain.services.ProfileQueryService;
import com.resolum.alivia.profiles.domain.services.StaffProfileCommandService;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;
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
    private final PersonProfileRepository personProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BusinessProfileRepository businessProfileRepository;

    public ProfilesContextFacadeImpl(
            PersonProfileCommandService personProfileCommandService,
            PersonProfileQueryService personProfileQueryService,
            StaffProfileCommandService staffProfileCommandService,
            StaffProfileQueryService staffProfileQueryService,
            BusinessProfileCommandService businessProfileCommandService,
            BusinessProfileQueryService businessProfileQueryService,
            ProfileQueryService profileQueryService) {
        this(personProfileCommandService, personProfileQueryService, staffProfileCommandService,
                staffProfileQueryService, businessProfileCommandService, businessProfileQueryService,
                profileQueryService, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProfilesContextFacadeImpl(
            PersonProfileCommandService personProfileCommandService,
            PersonProfileQueryService personProfileQueryService,
            StaffProfileCommandService staffProfileCommandService,
            StaffProfileQueryService staffProfileQueryService,
            BusinessProfileCommandService businessProfileCommandService,
            BusinessProfileQueryService businessProfileQueryService,
            ProfileQueryService profileQueryService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PersonProfileRepository personProfileRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StaffProfileRepository staffProfileRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) BusinessProfileRepository businessProfileRepository) {
        this.personProfileCommandService = personProfileCommandService;
        this.personProfileQueryService = personProfileQueryService;
        this.staffProfileCommandService = staffProfileCommandService;
        this.staffProfileQueryService = staffProfileQueryService;
        this.businessProfileCommandService = businessProfileCommandService;
        this.businessProfileQueryService = businessProfileQueryService;
        this.profileQueryService = profileQueryService;
        this.personProfileRepository = personProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.businessProfileRepository = businessProfileRepository;
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
        if (personProfileRepository != null) {
            var emailVo = new EmailAddress(emailAddress);
            if (personProfileRepository.existsByEmailAddress(emailVo)) {
                throw new EmailAddressAlreadyExistsException(emailVo);
            }
            var dniVo = new Dni(dni);
            if (personProfileRepository.existsByDni(dniVo)) {
                throw new DniAlreadyExistsException(dniVo);
            }
            var personProfile = new PersonProfile(
                    emailVo,
                    new Photo(photoUrl, photoPublicId),
                    new PhoneNumber(phoneNumber),
                    new PersonName(firstName, lastName),
                    dniVo,
                    new BirthDate(birthDate)
            );
            return personProfileRepository.save(personProfile).getId();
        }
        return createPersonProfileWithPhoto(
                emailAddress, new byte[]{1}, "default.jpg", phoneNumber, firstName, lastName, dni, birthDate);
    }

    @Override
    @Transactional
    public Long createPersonProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                            String phoneNumber, String firstName, String lastName,
                                            String dni, LocalDate birthDate) {
        log.info("Creating person profile via ACL with photo data for email: {}", emailAddress);
        var command = new CreatePersonProfileCommand(
                emailAddress, photoData, photoFileName, phoneNumber, firstName, lastName, dni, birthDate);
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
        if (staffProfileRepository != null) {
            var emailVo = new EmailAddress(emailAddress);
            if (staffProfileRepository.existsByEmailAddress(emailVo)) {
                throw new EmailAddressAlreadyExistsException(emailVo);
            }
            var dniVo = new Dni(dni);
            if (staffProfileRepository.existsByDni(dniVo)) {
                throw new DniAlreadyExistsException(dniVo);
            }
            var staffProfile = new StaffProfile(
                    emailVo,
                    new Photo(photoUrl, photoPublicId),
                    new PhoneNumber(phoneNumber),
                    new PersonName(firstName, lastName),
                    dniVo,
                    new BirthDate(birthDate)
            );
            return staffProfileRepository.save(staffProfile).getId();
        }
        return createStaffProfileWithPhoto(
                emailAddress, new byte[]{1}, "default.jpg", phoneNumber, firstName, lastName, dni, birthDate);
    }

    @Override
    @Transactional
    public Long createStaffProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                           String phoneNumber, String firstName, String lastName,
                                           String dni, LocalDate birthDate) {
        log.info("Creating staff profile via ACL with photo data for email: {}", emailAddress);
        var command = new CreateStaffProfileCommand(
                emailAddress, photoData, photoFileName, phoneNumber, firstName, lastName, dni, birthDate);
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
        if (staffProfileRepository != null) {
            var staffProfile = staffProfileRepository.findById(profileId)
                    .orElseThrow(() -> new StaffProfileNotFoundException(profileId));
            PhoneNumber phoneVo = (phoneNumber != null && !phoneNumber.isBlank()) ? new PhoneNumber(phoneNumber) : staffProfile.getPhoneNumber();
            Photo photo = (photoUrl != null && !photoUrl.isBlank()) ? new Photo(photoUrl, photoPublicId) : staffProfile.getPhoto();
            staffProfile.updatePersonalInformation(
                    new PersonName(firstName, lastName),
                    new BirthDate(birthDate),
                    phoneVo,
                    photo
            );
            return staffProfileRepository.save(staffProfile).getId();
        }
        return updateStaffProfileWithPhoto(
                profileId, firstName, lastName, birthDate, phoneNumber, null, null);
    }

    @Override
    @Transactional
    public Long updateStaffProfileWithPhoto(Long profileId, String firstName, String lastName,
                                           LocalDate birthDate, String phoneNumber, byte[] photoData,
                                           String photoFileName) {
        return staffProfileCommandService.handle(new UpdateStaffProfileCommand(
                profileId, firstName, lastName, birthDate, phoneNumber, photoData, photoFileName));
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
        if (businessProfileRepository != null) {
            var emailVo = new EmailAddress(emailAddress);
            if (businessProfileRepository.existsByEmailAddress(emailVo)) {
                throw new EmailAddressAlreadyExistsException(emailVo);
            }
            var rucVo = new Ruc(ruc);
            if (businessProfileRepository.existsByRuc(rucVo)) {
                throw new RucAlreadyExistsException(rucVo);
            }
            var bNameVo = new BusinessName(businessName);
            if (businessProfileRepository.existsByBusinessName(bNameVo)) {
                throw new BusinessNameAlreadyExistsException(bNameVo);
            }
            var lNameVo = new LegalName(legalName);
            if (businessProfileRepository.existsByLegalName(lNameVo)) {
                throw new LegalNameAlreadyExistsException(lNameVo);
            }
            var hoursVo = businessHours != null ? new BusinessHours(businessHours) : null;
            var address = new Address(streetAddress, latitude, longitude, placeId);
            PhoneNumber emergencyVo = (emergencyPhone != null && !emergencyPhone.isBlank()) ? new PhoneNumber(emergencyPhone) : null;
            EmailAddress supportVo = (supportEmail != null && !supportEmail.isBlank()) ? new EmailAddress(supportEmail) : null;
            PhoneNumber contactVo = (whatsappNum != null && !whatsappNum.isBlank()) ? new PhoneNumber(whatsappNum) : null;
            var businessProfile = new BusinessProfile(
                    emailVo,
                    new Photo(photoUrl, photoPublicId),
                    contactVo,
                    rucVo,
                    bNameVo,
                    lNameVo,
                    description,
                    mainActivity,
                    emergencyVo,
                    hoursVo,
                    slogan,
                    supportVo,
                    address
            );
            return businessProfileRepository.save(businessProfile).getId();
        }
        return createBusinessProfileWithPhoto(
                emailAddress, new byte[]{1}, "default.jpg", whatsappNum, ruc, businessName,
                legalName, description, mainActivity, emergencyPhone, businessHours,
                slogan, supportEmail, streetAddress, latitude, longitude, placeId);
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

    @Override
    @Transactional
    public Long createBusinessProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                              String whatsappNum, String ruc, String businessName,
                                              String legalName, String description, String mainActivity,
                                              String emergencyPhone, List<WeeklyPeriod> businessHours,
                                              String slogan, String supportEmail, String streetAddress,
                                              Double latitude, Double longitude, String placeId) {
        log.info("Creating business profile via ACL with photo data for business: {}", businessName);
        var hoursVo = businessHours != null ? new BusinessHours(businessHours) : null;
        var command = new CreateBusinessProfileCommand(
                emailAddress, photoData, photoFileName, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, hoursVo, slogan, supportEmail,
                streetAddress, latitude, longitude, placeId);
        Long profileId = businessProfileCommandService.handle(command);
        log.info("Business profile created via ACL with id: {}", profileId);
        return profileId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long updateBusinessProfile(Long profileId, String photoUrl, String photoPublicId,
                                      String whatsappNum, String businessName, String legalName,
                                      String description, String mainActivity, String emergencyPhone,
                                      List<WeeklyPeriod> businessHours, String slogan, String supportEmail) {
        if (businessProfileRepository != null) {
            var existing = businessProfileRepository.findById(profileId)
                    .orElseThrow(() -> new BusinessProfileNotFoundException(profileId));
            var bNameVo = new BusinessName(businessName);
            var lNameVo = new LegalName(legalName);
            var hoursVo = businessHours != null ? new BusinessHours(businessHours) : existing.getBusinessHours();
            PhoneNumber emergencyVo = (emergencyPhone != null && !emergencyPhone.isBlank()) ? new PhoneNumber(emergencyPhone) : existing.getBusinessPhone();
            EmailAddress supportVo = (supportEmail != null && !supportEmail.isBlank()) ? new EmailAddress(supportEmail) : existing.getSupportEmail();
            PhoneNumber contactVo = (whatsappNum != null && !whatsappNum.isBlank()) ? new PhoneNumber(whatsappNum) : existing.getPhoneNumber();
            existing.updateInformation(
                    bNameVo,
                    lNameVo,
                    description,
                    mainActivity,
                    emergencyVo,
                    hoursVo,
                    slogan,
                    supportVo,
                    existing.getAddress()
            );
            Photo photo = (photoUrl != null && !photoUrl.isBlank()) ? new Photo(photoUrl, photoPublicId) : existing.getPhoto();
            existing.updateContactInformation(contactVo, photo);
            return businessProfileRepository.save(existing).getId();
        }
        return updateBusinessProfileWithPhoto(
                profileId, null, null, whatsappNum, businessName, legalName,
                description, mainActivity, emergencyPhone, businessHours, slogan, supportEmail);
    }

    @Override
    @Transactional
    public Long updateBusinessProfileWithPhoto(Long profileId, byte[] photoData, String photoFileName,
                                              String whatsappNum, String businessName, String legalName,
                                              String description, String mainActivity, String emergencyPhone,
                                              List<WeeklyPeriod> businessHours, String slogan, String supportEmail) {
        log.info("Updating business profile via ACL with photo data for id: {}", profileId);
        var existing = businessProfileQueryService.handle(new GetBusinessProfileByIdQuery(profileId))
                .orElseThrow(() -> new BusinessProfileNotFoundException(profileId));

        var command = new UpdateBusinessProfileCommand(
                profileId,
                existing.getEmailAddress() != null ? existing.getEmailAddress().email() : "",
                photoData,
                photoFileName,
                whatsappNum,
                existing.getRuc() != null ? String.valueOf(existing.getRuc().ruc()) : "",
                businessName,
                legalName,
                description,
                mainActivity,
                emergencyPhone,
                businessHours != null ? new BusinessHours(businessHours) : existing.getBusinessHours(),
                slogan,
                supportEmail,
                existing.getAddress()
        );
        Long updatedId = businessProfileCommandService.handle(command);
        log.info("Business profile updated via ACL with id: {}", updatedId);
        return updatedId;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBusinessProfileByRuc(String ruc) {
        if (ruc == null || ruc.isBlank() || businessProfileRepository == null) {
            return false;
        }
        try {
            return businessProfileRepository.existsByRuc(new Ruc(ruc));
        } catch (IllegalArgumentException e) {
            log.debug("Validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBusinessProfileByBusinessName(String businessName) {
        if (businessName == null || businessName.isBlank() || businessProfileRepository == null) {
            return false;
        }
        try {
            return businessProfileRepository.existsByBusinessName(new BusinessName(businessName));
        } catch (IllegalArgumentException e) {
            log.debug("Validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBusinessProfileByLegalName(String legalName) {
        if (legalName == null || legalName.isBlank() || businessProfileRepository == null) {
            return false;
        }
        try {
            return businessProfileRepository.existsByLegalName(new LegalName(legalName));
        } catch (IllegalArgumentException e) {
            log.debug("Validation failed: {}", e.getMessage());
            return false;
        }
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
