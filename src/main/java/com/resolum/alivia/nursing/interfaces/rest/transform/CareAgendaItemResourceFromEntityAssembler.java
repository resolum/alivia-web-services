package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.interfaces.rest.resources.CareAgendaItemResource;

/**
 * Assembles {@link CareAgendaItemResource} instances from agenda items.
 */
public final class CareAgendaItemResourceFromEntityAssembler {

    private CareAgendaItemResourceFromEntityAssembler() {
    }

    /**
     * Converts an agenda item to its resource.
     *
     * @param item the agenda item
     * @return the resource
     */
    public static CareAgendaItemResource toResourceFromEntity(AgendaItem item) {
        var contact = item.contact();
        return new CareAgendaItemResource(
                new CareAgendaItemResource.ActivityResource(item.careActivityId(), item.title(), item.type()),
                item.assignmentId(),
                new CareAgendaItemResource.AssistedPersonResource(
                        item.assistedPersonProfileId().personProfileId(), contact.assistedPersonName()),
                item.date(), item.timeRange().start(), item.timeRange().end(),
                item.startsAt(), item.endsAt(),
                new CareAgendaItemResource.ResponsibleResource(
                        item.caregiverId() == null ? CareRole.RELATIVE : CareRole.CAREGIVER,
                        item.caregiverId(),
                        contact.caregiverProfileId() == null ? null : contact.caregiverProfileId().personProfileId(),
                        contact.caregiverName(), contact.caregiverEmail()),
                item.recurring(), item.status(), item.priority());
    }
}
