package com.resolum.alivia.communications.infrastructure.adapters;

import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.communications.infrastructure.persistence.jpa.repositories.PushDeviceJpaRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PushDeviceRepositoryImpl implements PushDeviceRepository {

    private final PushDeviceJpaRepository jpaRepository;

    public PushDeviceRepositoryImpl(PushDeviceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PushDevice save(PushDevice pushDevice) {
        return jpaRepository.save(pushDevice);
    }

    @Override
    public Optional<PushDevice> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public List<PushDevice> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public void delete(PushDevice pushDevice) {
        jpaRepository.delete(pushDevice);
    }
}
