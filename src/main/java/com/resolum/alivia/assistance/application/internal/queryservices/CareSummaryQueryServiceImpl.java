package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.exceptions.InvalidSummaryRangeException;
import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareNotesQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareSummaryQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary.Entry;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary.Kind;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.repositories.CareNoteRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.domain.services.CareSummaryQueryService;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CareSummaryQueryServiceImpl implements CareSummaryQueryService {

    /** The agenda is computed day by day, so the period is bounded. */
    static final int MAX_DAYS = 31;
    /** The agenda, the logs and the notes use the zone of the JVM (LocalDateTime.now, JPA auditing): alerts too. */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final AssistanceAccessService access;
    private final CareActivityQueryService activities;
    private final CareNoteRepository notes;
    private final TrackingContextFacade tracking;
    private final Clock clock;

    public CareSummaryQueryServiceImpl(AssistanceAccessService access, CareActivityQueryService activities,
                                       CareNoteRepository notes, TrackingContextFacade tracking, Clock clock) {
        this.access = access;
        this.activities = activities;
        this.notes = notes;
        this.tracking = tracking;
        this.clock = clock;
    }

    @Override
    public List<CareNote> handle(GetCareNotesQuery query) {
        access.requireRead(query.assistedPersonId(), query.userId());
        return notes.findAllByAssistedPersonId(query.assistedPersonId());
    }

    @Override
    public CareSummary handle(GetCareSummaryQuery query) {
        // The range is checked before reading anything, so an invalid one answers 400 whoever asks.
        var from = query.from();
        var to = query.to() == null ? LocalDate.now(clock.withZone(ZONE)) : query.to();
        if (from == null) throw new InvalidSummaryRangeException("The start date (from) is required.");
        if (from.isAfter(to)) throw new InvalidSummaryRangeException("The start date is after the end date.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidSummaryRangeException("The period is longer than " + MAX_DAYS + " days.");
        }

        var person = access.requireRead(query.assistedPersonId(), query.userId());
        var entries = new ArrayList<Entry>();
        int attended = 0;
        int pending = 0;
        int missed = 0;

        for (AgendaItem item : activities.handle(new GetAgendaByAssistedPersonIdQuery(person.getId(), from, to))) {
            var kind = item.type() == CareActivityType.MEDICATION ? Kind.MEDICATION : Kind.ACTIVITY;
            entries.add(new Entry(item.startsAt(), kind, item.title(), item.status().name(), item.careActivityId(),
                    null, null));
            if (item.status() == OccurrenceStatus.COMPLETED) attended++;
            else if (item.isPending()) pending++;
            else if (item.status() == OccurrenceStatus.MISSED || item.status() == OccurrenceStatus.OMITTED) missed++;
        }

        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();
        // Auditing writes createdAt with the shared clock (UTC), not in the zone of the agenda.
        var audit = clock.getZone();
        for (CareNote note : notes.findAllByAssistedPersonIdBetween(person.getId(),
                start.atZone(ZONE).withZoneSameInstant(audit).toLocalDateTime(),
                end.atZone(ZONE).withZoneSameInstant(audit).toLocalDateTime())) {
            var at = note.getCreatedAt().atZone(audit).withZoneSameInstant(ZONE).toLocalDateTime();
            entries.add(new Entry(at, Kind.NOTE, note.getCategory().name(), note.getCategory().name(),
                    note.getId(), note.getAuthorUserId(), note.getContent()));
        }

        var incomplete = new ArrayList<String>();
        if (person.getHouseholdId() != null) {
            try {
                for (var alert : tracking.fetchAlertsOfHousehold(person.getHouseholdId(),
                        start.atZone(ZONE).toInstant(), end.atZone(ZONE).toInstant())) {
                    entries.add(new Entry(LocalDateTime.ofInstant(alert.occurredAt(), ZONE), Kind.ALERT, alert.type(),
                            alert.severity(), alert.deviceId(), null, null));
                }
            } catch (RuntimeException unavailable) {
                // The rest of the summary is still useful: the alerts are reported as missing instead of empty.
                log.warn("Care summary of assisted person {} without alerts: {}", person.getId(), unavailable.toString());
                incomplete.add("alerts");
            }
        }

        entries.sort(Comparator.comparing(Entry::at, Comparator.nullsLast(Comparator.naturalOrder())));
        return new CareSummary(person.getId(), from, to, attended, pending, missed, entries, incomplete);
    }
}
