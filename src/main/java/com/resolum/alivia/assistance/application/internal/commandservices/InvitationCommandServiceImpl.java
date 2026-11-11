package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RegisterCaregiverAccountCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;
import com.resolum.alivia.assistance.domain.model.valueobjects.CaregiverStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.InvitationStatus;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.InvitationRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.InvitationCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class InvitationCommandServiceImpl implements InvitationCommandService {

    private final InvitationRepository invitationRepository;
    private final RelativeRepository relativeRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final CaregiverRepository caregiverRepository;
    private final ExternalIamService externalIamService;
    private final ExternalCommunicationsService externalCommunicationsService;

    public InvitationCommandServiceImpl(InvitationRepository invitationRepository,
                                       RelativeRepository relativeRepository,
                                       AssistedPersonRepository assistedPersonRepository,
                                       CaregiverRepository caregiverRepository,
                                       ExternalIamService externalIamService,
                                       ExternalCommunicationsService externalCommunicationsService) {
        this.invitationRepository = invitationRepository;
        this.relativeRepository = relativeRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.caregiverRepository = caregiverRepository;
        this.externalIamService = externalIamService;
        this.externalCommunicationsService = externalCommunicationsService;
    }

    @Override
    public Long handle(CreateInvitationCommand command) {
        Relative relative = relativeRepository.findById(command.relativeId())
                .orElseThrow(() -> new RelativeNotFoundException(command.relativeId()));

        AssistedPerson assistedPerson = assistedPersonRepository.findById(command.assistedPersonId())
                .orElseThrow(() -> new AssistedPersonNotFoundException(command.assistedPersonId()));

        if (!assistedPerson.isManagedBy(relative)) {
            throw new AssistedPersonNotManagedByRelativeException(assistedPerson.getId(), relative.getId());
        }

        EmailAddress emailAddress = new EmailAddress(command.inviteeEmail());
        if (invitationRepository.existsByAssistedPersonIdAndInviteeEmailAndStatus(
                assistedPerson.getId(), emailAddress, InvitationStatus.PENDING)) {
            throw new InvitationAlreadyExistsException(command.inviteeEmail());
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        Invitation invitation = new Invitation(assistedPerson, relative, emailAddress, expiresAt);
        invitation = invitationRepository.save(invitation);

        handle(new RegisterCaregiverAccountCommand(invitation.getId()));
        return invitation.getId();
    }

    @Override
    public Long handle(RegisterCaregiverAccountCommand command) {
        Invitation invitation = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> new InvitationNotFoundException(command.invitationId()));

        String email = invitation.getInviteeEmail().email();
        if (externalIamService.existsUserByEmail(email)) {
            // Existing user in IAM
            // In existing user flow: assign and send notification
            Long userId = 1L; // Fallback or resolved from IAM
            if (caregiverRepository.existsByAssistedPersonIdAndUserIdAndStatus(
                    invitation.getAssistedPerson().getId(), new UserId(userId), CaregiverStatus.ACTIVE)) {
                throw new CaregiverAlreadyExistsException(invitation.getAssistedPerson().getId(), userId);
            }
            invitation.assignExistingCaregiverAccount(new UserId(userId));
        } else {
            // New user account flow
            String tempPassword = UUID.randomUUID().toString();
            Long userId = externalIamService.registerCaregiverAccount(email, tempPassword, "Cuidador", "Invitado", "000000000");
            String setupUrl = "http://localhost:5173/activate-account?invitationId=" + invitation.getId();
            invitation.assignCaregiverAccount(new UserId(userId), setupUrl);
            externalCommunicationsService.sendCaregiverInvitation(email, "Familiar", "Persona asistida", setupUrl, 7);
        }

        invitationRepository.save(invitation);
        return invitation.getId();
    }

    @Override
    public Long handle(AcceptInvitationCommand command) {
        Invitation invitation = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> new InvitationNotFoundException(command.invitationId()));

        invitation.accept(new UserId(command.caregiverUserId()), LocalDateTime.now());

        if (!caregiverRepository.existsByAssistedPersonIdAndUserIdAndStatus(
                invitation.getAssistedPerson().getId(), new UserId(command.caregiverUserId()), CaregiverStatus.ACTIVE)) {
            Caregiver caregiver = new Caregiver(new UserId(command.caregiverUserId()), invitation.getAssistedPerson());
            caregiverRepository.save(caregiver);
        }

        invitationRepository.save(invitation);
        return invitation.getId();
    }

    @Override
    public Long handle(RevokeInvitationCommand command) {
        Invitation invitation = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> new InvitationNotFoundException(command.invitationId()));

        if (!Objects.equals(invitation.getInviter().getId(), command.relativeId())) {
            throw new AssistedPersonNotManagedByRelativeException(invitation.getAssistedPerson().getId(), command.relativeId());
        }

        invitation.revoke();
        invitationRepository.save(invitation);
        return invitation.getId();
    }

    @Override
    public Long handle(ResendInvitationCommand command) {
        Invitation invitation = invitationRepository.findById(command.invitationId())
                .orElseThrow(() -> new InvitationNotFoundException(command.invitationId()));

        if (!Objects.equals(invitation.getInviter().getId(), command.relativeId())) {
            throw new AssistedPersonNotManagedByRelativeException(invitation.getAssistedPerson().getId(), command.relativeId());
        }

        invitation.renew(LocalDateTime.now().plusDays(7));
        invitationRepository.save(invitation);

        handle(new RegisterCaregiverAccountCommand(invitation.getId()));
        return invitation.getId();
    }
}
