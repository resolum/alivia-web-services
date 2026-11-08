package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.domain.exceptions.PushDeviceNotFound;
import com.resolum.alivia.communications.domain.exceptions.PushDeviceNotForUser;
import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.communications.domain.model.commands.RegisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.commands.UnregisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.repositories.PushDeviceRepository;
import com.resolum.alivia.communications.domain.services.PushDeviceCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class PushDeviceCommandServiceImpl implements PushDeviceCommandService {

    private final PushDeviceRepository pushDeviceRepository;

    public PushDeviceCommandServiceImpl(PushDeviceRepository pushDeviceRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
    }

    @Override
    public Long handle(RegisterPushDeviceCommand command) {
        UserId userId = new UserId(command.userId());
        Optional<PushDevice> existing = pushDeviceRepository.findByToken(command.token());
        if (existing.isPresent()) {
            PushDevice device = existing.get();
            device.reassignTo(userId, command.platform());
            return pushDeviceRepository.save(device).getId();
        } else {
            PushDevice newDevice = new PushDevice(userId, command.token(), command.platform());
            return pushDeviceRepository.save(newDevice).getId();
        }
    }

    @Override
    public void handle(UnregisterPushDeviceCommand command) {
        UserId userId = new UserId(command.userId());
        PushDevice device = pushDeviceRepository.findByToken(command.token())
                .orElseThrow(() -> new PushDeviceNotFound(command.token()));

        if (!device.belongsTo(userId)) {
            throw new PushDeviceNotForUser(command.token());
        }

        pushDeviceRepository.delete(device);
    }
}
