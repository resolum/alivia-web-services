package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareAgendaItemResource;

public final class CareAgendaItemResourceAssembler {

    private CareAgendaItemResourceAssembler() {}

    public static CareAgendaItemResource toResourceFromEntity(AgendaItem item) {
        var contact = item.contact();
        Long assistedPersonProfileIdVal = item.assistedPersonProfileId() != null
                ? item.assistedPersonProfileId().personProfileId() : null;
        Long caregiverProfileIdVal = (contact != null && contact.caregiverProfileId() != null)
                ? contact.caregiverProfileId().personProfileId() : null;

        return new CareAgendaItemResource(
                new CareAgendaItemResource.ActivityResource(item.careActivityId(), item.title(), item.type()),
                item.assignmentId(),
                new CareAgendaItemResource.AssistedPersonResource(
                        assistedPersonProfileIdVal,
                        contact != null ? contact.assistedPersonName() : null),
                item.date(),
                item.timeRange() != null ? item.timeRange().start() : null,
                item.timeRange() != null ? item.timeRange().end() : null,
                item.startsAt(),
                item.endsAt(),
                new CareAgendaItemResource.ResponsibleResource(
                        item.caregiverId() == null ? "RELATIVE" : "CAREGIVER",
                        item.caregiverId(),
                        caregiverProfileIdVal,
                        contact != null ? contact.caregiverName() : null,
                        contact != null ? contact.caregiverEmail() : null),
                item.recurring(),
                item.status(),
                item.priority()
        );
    }
}
