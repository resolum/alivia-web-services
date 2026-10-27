package com.resolum.alivia.profiles.application.internal.commandservices;

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
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

@Service
public class BusinessProfileCommandServiceImpl implements BusinessProfileCommandService {

    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfileCommandServiceImpl(BusinessProfileRepository businessProfileRepository) {
        this.businessProfileRepository = businessProfileRepository;
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

        var photo = new Photo(command.photoUrl(), command.photoPublicId());
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

        businessProfile.updateContactInformation(
                new PhoneNumber(command.whatsappNum()),
                new Photo(command.photoUrl(), command.photoPublicId())
        );

        var updatedProfile = businessProfileRepository.save(businessProfile);
        return updatedProfile.getId();
    }
}
