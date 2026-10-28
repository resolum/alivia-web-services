package com.resolum.alivia.profiles.application.internal.commandservices;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.model.aggregates.BusinessProfile;
import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.profiles.domain.services.BusinessProfileCommandService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.BusinessProfileRepository;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

@Service
public class BusinessProfileCommandServiceImpl implements BusinessProfileCommandService {

    private final BusinessProfileRepository businessProfileRepository;
    private final StorageService storageService;

    public BusinessProfileCommandServiceImpl(BusinessProfileRepository businessProfileRepository) {
        this(businessProfileRepository, null);
    }

    @Autowired
    public BusinessProfileCommandServiceImpl(BusinessProfileRepository businessProfileRepository,
                                            @Autowired(required = false) StorageService storageService) {
        this.businessProfileRepository = businessProfileRepository;
        this.storageService = storageService;
    }

    @Transactional
    @Override
    public Long handle(CreateBusinessProfileCommand command) {
        var emailAddress = new EmailAddress(command.emailAddress());
        if (businessProfileRepository.existsByEmailAddress(emailAddress)) {
            throw new EmailAddressAlreadyExistsException(emailAddress);
        }

        var ruc = new Ruc(command.ruc());
        if (businessProfileRepository.existsByRuc(ruc)) {
            throw new RucAlreadyExistsException(ruc);
        }

        var businessName = new BusinessName(command.businessName());
        if (businessProfileRepository.existsByBusinessName(businessName)) {
            throw new BusinessNameAlreadyExistsException(businessName);
        }

        var legalName = new LegalName(command.legalName());
        if (businessProfileRepository.existsByLegalName(legalName)) {
            throw new LegalNameAlreadyExistsException(legalName);
        }

        Photo photo = resolvePhoto(command.photoData(), command.photoFileName(), null);
        var whatsappNum = new PhoneNumber(command.whatsappNum());
        var businessPhone = new PhoneNumber(command.emergencyPhone());
        var supportEmail = new EmailAddress(command.supportEmail());
        var address = new Address(
                command.streetAddress(),
                command.latitude(),
                command.longitude(),
                command.placeId()
        );

        var businessProfile = new BusinessProfile(
                emailAddress,
                photo,
                whatsappNum,
                ruc,
                businessName,
                legalName,
                command.description(),
                command.mainActivity(),
                businessPhone,
                command.businessHours(),
                command.slogan(),
                supportEmail,
                address
        );

        var savedProfile = businessProfileRepository.save(businessProfile);
        return savedProfile.getId();
    }

    @Transactional
    @Override
    public Long handle(UpdateBusinessProfileCommand command) {
        var businessProfile = businessProfileRepository.findById(command.profileId())
                .orElseThrow(() -> new BusinessProfileNotFoundException(command.profileId()));

        var businessName = new BusinessName(command.businessName());
        var legalName = new LegalName(command.legalName());
        var businessPhone = new PhoneNumber(command.emergencyPhone());
        var supportEmail = new EmailAddress(command.supportEmail());
        var address = new Address(
                command.streetAddress(),
                command.latitude(),
                command.longitude(),
                command.placeId()
        );

        businessProfile.updateInformation(
                businessName,
                legalName,
                command.description(),
                command.mainActivity(),
                businessPhone,
                command.businessHours(),
                command.slogan(),
                supportEmail,
                address
        );

        Photo photo = resolvePhoto(command.photoData(), command.photoFileName(), businessProfile.getPhoto());
        businessProfile.updateContactInformation(
                new PhoneNumber(command.whatsappNum()),
                photo
        );

        var updatedProfile = businessProfileRepository.save(businessProfile);
        return updatedProfile.getId();
    }

    private Photo resolvePhoto(byte[] photoData, String photoFileName, Photo existingPhoto) {
        if (photoData != null && photoData.length > 0) {
            if (storageService != null) {
                Map<String, String> uploaded = storageService.upload(photoData, photoFileName);
                if (uploaded != null) {
                    String url = uploaded.get("url") != null ? uploaded.get("url") : uploaded.get("secure_url");
                    String publicId = uploaded.get("publicId") != null ? uploaded.get("publicId") : uploaded.get("public_id");
                    if (url != null && publicId != null) {
                        return new Photo(url, publicId);
                    }
                }
            }
            return new Photo("https://res.cloudinary.com/placeholder", "placeholder");
        }
        return existingPhoto != null ? existingPhoto : new Photo("https://res.cloudinary.com/placeholder", "placeholder");
    }
}
