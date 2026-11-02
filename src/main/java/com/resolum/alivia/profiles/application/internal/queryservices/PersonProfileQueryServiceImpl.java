package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.model.aggregates.PersonProfile;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByDniQuery;
import com.resolum.alivia.profiles.domain.model.queries.ExistsPersonProfileByEmailAddressQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetPersonProfileByIdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.Dni;
import com.resolum.alivia.profiles.domain.services.PersonProfileQueryService;
import com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories.PersonProfileRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

@Service
public class PersonProfileQueryServiceImpl implements PersonProfileQueryService {

    private final PersonProfileRepository personProfileRepository;

    public PersonProfileQueryServiceImpl(PersonProfileRepository personProfileRepository) {
        this.personProfileRepository = personProfileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<PersonProfile> handle(GetPersonProfileByIdQuery query) {
        return personProfileRepository.findById(query.profileId());
    }

    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsPersonProfileByEmailAddressQuery query) {
        return personProfileRepository.existsByEmailAddress(new EmailAddress(query.emailAddress()));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean handle(ExistsPersonProfileByDniQuery query) {
        return personProfileRepository.existsByDni(new Dni(query.dni()));
    }
}
