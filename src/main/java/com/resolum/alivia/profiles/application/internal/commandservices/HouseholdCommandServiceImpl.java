package com.resolum.alivia.profiles.application.internal.commandservices;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.commands.CreateHouseholdCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateHouseholdCommand;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link HouseholdCommandService}.
 */
@Slf4j
@Service
public class HouseholdCommandServiceImpl implements HouseholdCommandService {

    private static final double DEFAULT_LATITUDE = -12.046374;
    private static final double DEFAULT_LONGITUDE = -77.042793;
    private static final String DEFAULT_PLACE_ID = "ChIJw8gA1j_JBZERSF7Yf_Q8lJc";

    private final HouseholdRepository householdRepository;

    public HouseholdCommandServiceImpl(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Transactional
    @Override
    public Long handle(CreateHouseholdCommand command) {
        Objects.requireNonNull(command, "CreateHouseholdCommand cannot be null");

        var address = resolveAddress(command);
        var phone = resolvePhoneNumber(command.contactPhone());
        var email = new EmailAddress(command.contactEmail().strip());

        var household = new Household(command.alias().strip(), address, phone, email);

        if (command.responsibleUserId() != null && command.responsibleUserId() > 0) {
            household.addMember(new UserId(command.responsibleUserId()), HouseholdResponsibility.PRINCIPAL);
        }

        var saved = householdRepository.save(household);
        log.info("Created household {} with alias '{}'", saved.getId(), saved.getAlias());
        return saved.getId();
    }

    @Transactional
    @Override
    public Long handle(UpdateHouseholdCommand command) {
        Objects.requireNonNull(command, "UpdateHouseholdCommand cannot be null");

        var household = householdRepository.findById(command.householdId())
                .orElseThrow(() -> new HouseholdNotFoundException(command.householdId()));

        var phone = resolvePhoneNumber(command.contactPhone());
        var email = new EmailAddress(command.contactEmail().strip());

        household.updateGeneralInformation(command.alias().strip(), phone, email);

        var saved = householdRepository.save(household);
        log.info("Updated household {} general information", saved.getId());
        return saved.getId();
    }

    private static Address resolveAddress(CreateHouseholdCommand command) {
        String formatted = command.formattedAddress();
        if (formatted == null || formatted.isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (command.address() != null && !command.address().isBlank()) sb.append(command.address().strip());
            if (command.district() != null && !command.district().isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(command.district().strip());
            }
            if (command.city() != null && !command.city().isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(command.city().strip());
            }
            formatted = sb.isEmpty() ? command.alias() : sb.toString();
        }

        Double latitude = command.latitude() != null ? command.latitude() : DEFAULT_LATITUDE;
        Double longitude = command.longitude() != null ? command.longitude() : DEFAULT_LONGITUDE;
        String placeId = (command.placeId() != null && !command.placeId().isBlank())
                ? command.placeId().strip()
                : DEFAULT_PLACE_ID;

        return new Address(formatted, latitude, longitude, placeId);
    }

    private static PhoneNumber resolvePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank.");
        }
        var digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() == 9 && digits.startsWith("9")) {
            digits = "51" + digits;
        }
        return new PhoneNumber(digits);
    }
}
