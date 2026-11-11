package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftConflictException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftHasPendingHandoverException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeleteShiftCommand;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareShiftCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CareShiftCommandServiceImpl implements CareShiftCommandService {

    private final CareShiftRepository careShiftRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final HandoverRepository handoverRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public CareShiftCommandServiceImpl(CareShiftRepository careShiftRepository,
            AssistedPersonRepository assistedPersonRepository,
            HandoverRepository handoverRepository,
            RelativeRepository relativeRepository,
            CaregiverRepository caregiverRepository) {
        this.careShiftRepository = careShiftRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.handoverRepository = handoverRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    private Long resolveAssistedPersonId(Long assistedPersonId, UserId userId) {
        if (assistedPersonId != null)
            return assistedPersonId;
        if (userId != null) {
            var relativeOpt = relativeRepository.findByUserId(userId);
            if (relativeOpt.isPresent()) {
                var persons = assistedPersonRepository.findAllByRelativeId(relativeOpt.get().getId());
                if (!persons.isEmpty())
                    return persons.getFirst().getId();
            } else {
                var caregivers = caregiverRepository.findAllByUserId(userId);
                if (!caregivers.isEmpty() && caregivers.getFirst().getAssistedPerson() != null) {
                    return caregivers.getFirst().getAssistedPerson().getId();
                }
            }
        }
        var persons = assistedPersonRepository.findAll();
        if (!persons.isEmpty())
            return persons.getFirst().getId();
        return 1L;
    }

    @Override
    public CareShift handle(AssignShiftCommand command) {
        Long targetAssistedPersonId = resolveAssistedPersonId(command.assistedPersonId(), command.userId());
        AssistedPerson assistedPerson = assistedPersonRepository.findById(targetAssistedPersonId)
                .orElseThrow(() -> new AssistedPersonNotFoundException(targetAssistedPersonId));

        if (!command.allowConflict()) {
            List<CareShift> conflicts = careShiftRepository.findConflictingShifts(
                    targetAssistedPersonId,
                    command.startsAt(),
                    command.endsAt(),
                    command.shiftId());
            if (!conflicts.isEmpty()) {
                throw new ShiftConflictException("Shift coincides with an existing scheduled shift.");
            }
        }

        if (command.shiftId() != null) {
            CareShift existing = careShiftRepository.findById(command.shiftId())
                    .orElseThrow(() -> new CareShiftNotFoundException(command.shiftId()));
            existing.update(
                    command.caregiverId(),
                    command.caregiverName(),
                    command.startsAt(),
                    command.endsAt(),
                    command.notes());
            return careShiftRepository.save(existing);
        } else {
            CareShift shift = new CareShift(
                    assistedPerson,
                    command.caregiverId(),
                    command.caregiverName(),
                    command.startsAt(),
                    command.endsAt(),
                    command.notes());
            return careShiftRepository.save(shift);
        }
    }

    @Override
    public void handle(DeleteShiftCommand command) {
        CareShift shift = careShiftRepository.findById(command.shiftId())
                .orElseThrow(() -> new CareShiftNotFoundException(command.shiftId()));

        var openHandovers = handoverRepository.findOpenHandoversByShiftId(command.shiftId());
        if (!openHandovers.isEmpty()) {
            throw new ShiftHasPendingHandoverException(command.shiftId());
        }

        careShiftRepository.delete(shift);
    }
}
