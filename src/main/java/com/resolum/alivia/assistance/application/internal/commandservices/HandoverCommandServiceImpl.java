package com.resolum.alivia.assistance.application.internal.commandservices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotForCaregiverException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotPendingException;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.commands.AnswerHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.EscalateHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ProposeSubstituteCommand;
import com.resolum.alivia.assistance.domain.model.commands.RequestHandoverCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.HandoverStatus;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.services.HandoverCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class HandoverCommandServiceImpl implements HandoverCommandService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final HandoverRepository handoverRepository;
    private final CareShiftRepository careShiftRepository;

    public HandoverCommandServiceImpl(HandoverRepository handoverRepository,
            CareShiftRepository careShiftRepository) {
        this.handoverRepository = handoverRepository;
        this.careShiftRepository = careShiftRepository;
    }

    private String toJson(List<String> items) {
        if (items == null)
            return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public Handover handle(RequestHandoverCommand command) {
        // Idempotent: return existing handover if already requested for this shift
        Optional<Handover> existing = handoverRepository.findByFromShiftId(command.fromShiftId());
        if (existing.isPresent()) {
            return existing.get();
        }

        CareShift fromShift = careShiftRepository.findById(command.fromShiftId())
                .orElseThrow(() -> new CareShiftNotFoundException(command.fromShiftId()));
        CareShift toShift = careShiftRepository.findById(command.toShiftId())
                .orElseThrow(() -> new CareShiftNotFoundException(command.toShiftId()));

        Handover handover = new Handover(
                fromShift,
                toShift,
                toJson(command.attended()),
                toJson(command.pending()));
        return handoverRepository.save(handover);
    }

    @Override
    public Handover handle(AnswerHandoverCommand command) {
        Handover handover = handoverRepository.findById(command.handoverId())
                .orElseThrow(() -> new HandoverNotFoundException(command.handoverId()));

        if (command.actorCaregiverId() != null &&
                !Objects.equals(handover.getToCaregiverId(), command.actorCaregiverId())) {
            throw new HandoverNotForCaregiverException(command.handoverId(), command.actorCaregiverId());
        }

        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new HandoverNotPendingException(command.handoverId());
        }

        if (command.accept()) {
            if (handover.isSubstitute()) {
                Long newCaregiverId = command.actorCaregiverId() != null
                        ? command.actorCaregiverId()
                        : handover.getToCaregiverId();
                String newCaregiverName = (command.actorCaregiverName() != null
                        && !command.actorCaregiverName().isBlank())
                                ? command.actorCaregiverName()
                                : handover.getToCaregiverName();

                CareShift toShift = handover.getToShift();
                toShift.reassignCaregiver(newCaregiverId, newCaregiverName);
                careShiftRepository.save(toShift);
            }
            handover.accept(toJson(command.attended()), toJson(command.pending()));
        } else {
            handover.decline();
        }

        return handoverRepository.save(handover);
    }

    @Override
    public Handover handle(ProposeSubstituteCommand command) {
        Handover handover = handoverRepository.findById(command.handoverId())
                .orElseThrow(() -> new HandoverNotFoundException(command.handoverId()));

        if (!handover.isOpen()) {
            throw new HandoverNotPendingException(command.handoverId());
        }

        handover.proposeSubstitute(command.substituteCaregiverId(), command.substituteCaregiverName());
        return handoverRepository.save(handover);
    }

    @Override
    public Handover handle(EscalateHandoverCommand command) {
        Handover handover = handoverRepository.findById(command.handoverId())
                .orElseThrow(() -> new HandoverNotFoundException(command.handoverId()));

        handover.markEscalated();
        return handoverRepository.save(handover);
    }
}
