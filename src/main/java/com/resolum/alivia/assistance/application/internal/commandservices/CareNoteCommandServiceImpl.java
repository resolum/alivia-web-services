package com.resolum.alivia.assistance.application.internal.commandservices;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareNoteCommand;
import com.resolum.alivia.assistance.domain.repositories.CareNoteRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.CareNoteCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareNoteCommandServiceImpl implements CareNoteCommandService {

    private final CareNoteRepository notes;
    private final AssistanceAccessService access;

    public CareNoteCommandServiceImpl(CareNoteRepository notes, AssistanceAccessService access) {
        this.notes = notes;
        this.access = access;
    }

    /** Only the care network that may operate the person (relative in charge, active caregiver) writes notes. */
    @Override
    @Transactional
    public CareNote handle(CreateCareNoteCommand command) {
        access.requireOperate(command.assistedPersonId(), command.authorUserId());
        return notes.save(new CareNote(command.assistedPersonId(), command.authorUserId(), command.category(),
                command.content()));
    }
}
