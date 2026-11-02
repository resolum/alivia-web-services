package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.aggregates.StaffProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsStaffProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetStaffProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.services.StaffProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.StaffProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

@Service
public class StaffProfileQueryServiceImpl implements StaffProfileQueryService {

    private final StaffProfileRepository staffProfileRepository;

    public StaffProfileQueryServiceImpl(StaffProfileRepository staffProfileRepository) {
        this.staffProfileRepository = staffProfileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<StaffProfile> handle(GetStaffProfileByIdQuery query) {
        return staffProfileRepository.findById(query.profileId());
    }

    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsStaffProfileByEmailAddressQuery query) {
        return staffProfileRepository.existsByEmailAddress(new EmailAddress(query.emailAddress()));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsStaffProfileByDniQuery query) {
        return staffProfileRepository.existsByDni(new Dni(query.dni()));
    }
}
