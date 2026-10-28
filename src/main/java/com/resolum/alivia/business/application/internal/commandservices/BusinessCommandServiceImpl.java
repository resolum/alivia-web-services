package com.resolum.alivia.business.application.internal.commandservices;

import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BusinessAlreadyExistsForAdministrator;
import com.resolum.alivia.business.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.business.domain.exceptions.UnauthorizedAdministratorException;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.commands.CreateBusinessCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateBusinessCommand;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.domain.repositories.BusinessRepository;
import com.resolum.alivia.business.domain.services.BusinessCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BusinessCommandServiceImpl implements BusinessCommandService {

    private final BusinessRepository businessRepository;
    private final AdministratorRepository administratorRepository;
    private final ExternalProfilesService externalProfilesService;

    public BusinessCommandServiceImpl(
            BusinessRepository businessRepository,
            AdministratorRepository administratorRepository,
            ExternalProfilesService externalProfilesService) {
        this.businessRepository = businessRepository;
        this.administratorRepository = administratorRepository;
        this.externalProfilesService = externalProfilesService;
    }

    @Override
    @Transactional
    public Long handle(CreateBusinessCommand command) {
        log.info("Handling CreateBusinessCommand for businessName: {}", command.businessName());

        Administrator administrator = administratorRepository.findById(command.administratorId())
                .orElseThrow(() -> new AdministratorNotFound(command.administratorId()));

        if (businessRepository.existsByAdministratorId(administrator.getId())) {
            throw new BusinessAlreadyExistsForAdministrator(administrator.getId());
        }

        BusinessProfileId businessProfileId = externalProfilesService.createBusinessProfile(
                command.photoData(),
                command.photoFileName(),
                command.businessName(),
                command.legalName(),
                command.ruc(),
                command.mainActivity(),
                command.description(),
                command.emailAddress(),
                command.phoneNumber(),
                command.formattedAddress(),
                command.latitude(),
                command.longitude(),
                command.placeId(),
                command.businessHours(),
                command.supportEmail(),
                command.emergencyPhone(),
                command.whatsappNum(),
                command.slogan());

        Business business = new Business(businessProfileId, administrator);
        Business savedBusiness = businessRepository.save(business);

        log.info("Business created successfully with ID: {}", savedBusiness.getId());
        return savedBusiness.getId();
    }

    @Override
    @Transactional
    public Long handle(UpdateBusinessCommand command) {
        log.info("Handling UpdateBusinessCommand for business ID: {}", command.businessId());

        Business business = businessRepository.findById(command.businessId())
                .orElseThrow(() -> new BusinessNotFound(command.businessId()));

        Administrator administrator = administratorRepository.findById(command.administratorId())
                .orElseThrow(() -> new AdministratorNotFound(command.administratorId()));

        if (!business.isManagedBy(administrator)) {
            throw new UnauthorizedAdministratorException("Administrator is not authorized to manage this business.");
        }

        externalProfilesService.updateBusinessProfile(
                business.getBusinessProfileId().businessProfileId(),
                command.photoData(),
                command.photoFileName(),
                command.businessName(),
                command.legalName(),
                command.mainActivity(),
                command.description(),
                command.phoneNumber(),
                command.businessHours(),
                command.supportEmail(),
                command.emergencyPhone(),
                command.whatsappNum(),
                command.slogan());

        log.info("Business updated successfully with ID: {}", business.getId());
        return business.getId();
    }
}
