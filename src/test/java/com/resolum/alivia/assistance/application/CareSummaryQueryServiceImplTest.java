package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.queryservices.CareSummaryQueryServiceImpl;
import com.resolum.alivia.assistance.domain.exceptions.InvalidSummaryRangeException;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareSummaryQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary.Kind;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.repositories.CareNoteRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TS-17: the continuity summary puts activities, medications, alerts and notes in one chronological list. */
class CareSummaryQueryServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final AssistanceAccessService access = mock(AssistanceAccessService.class);
    private final CareActivityQueryService activities = mock(CareActivityQueryService.class);
    private final CareNoteRepository notes = mock(CareNoteRepository.class);
    private final TrackingContextFacade tracking = mock(TrackingContextFacade.class);
    private final AssistedPerson person = mock(AssistedPerson.class);
    private CareSummaryQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CareSummaryQueryServiceImpl(access, activities, notes, tracking,
                Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC));
        when(person.getId()).thenReturn(5L);
        when(person.getHouseholdId()).thenReturn(30L);
        when(access.requireRead(5L, 7L)).thenReturn(person);
    }

    private static AgendaItem item(String title, CareActivityType type, int hour, OccurrenceStatus status) {
        var at = DAY.atTime(hour, 0);
        return new AgendaItem(1L, 2L, title, type, null, DAY, null, at, at.plusHours(1), null, true, status, null);
    }

    @Test
    void entriesAreChronologicalAndCountAttendedPendingAndMissed() {
        when(activities.handle(new GetAgendaByAssistedPersonIdQuery(5L, DAY, DAY))).thenReturn(List.of(
                item("Paracetamol", CareActivityType.MEDICATION, 9, OccurrenceStatus.COMPLETED),
                item("Paseo", CareActivityType.EXERCISE, 8, OccurrenceStatus.MISSED),
                item("Cena", CareActivityType.MEAL, 20, OccurrenceStatus.SCHEDULED)));
        var note = mock(CareNote.class);
        // Auditing writes UTC wall time (the clock of the test is UTC); the summary shows it in the agenda zone.
        when(note.getCreatedAt()).thenReturn(LocalDateTime.ofInstant(
                DAY.atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant(), ZoneOffset.UTC));
        when(note.getCategory()).thenReturn(CareNoteCategory.MOOD);
        when(note.getContent()).thenReturn("Estuvo tranquilo.");
        when(note.getAuthorUserId()).thenReturn(7L);
        when(notes.findAllByAssistedPersonIdBetween(anyLong(), any(), any())).thenReturn(List.of(note));
        // The alert happened at 10:00 in the zone the notes and the agenda use.
        var alertAt = DAY.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant();
        when(tracking.fetchAlertsOfHousehold(anyLong(), any(), any())).thenReturn(List.of(
                new TrackingContextFacade.HomeAlert("HELP_REQUESTED", "CRITICAL", null, alertAt)));

        var summary = service.handle(new GetCareSummaryQuery(5L, 7L, DAY, DAY));

        assertThat(summary.attended()).isEqualTo(1);
        assertThat(summary.pending()).isEqualTo(1);
        assertThat(summary.missed()).isEqualTo(1);
        assertThat(summary.entries()).extracting(e -> e.kind())
                .containsExactly(Kind.ACTIVITY, Kind.MEDICATION, Kind.ALERT, Kind.NOTE, Kind.ACTIVITY);
        assertThat(summary.entries().get(2).at()).isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 0));
        assertThat(summary.incompleteSources()).isEmpty();
    }

    @Test
    void alertsThatCannotBeReadAreNamedInsteadOfFailingTheSummary() {
        when(activities.handle(any(GetAgendaByAssistedPersonIdQuery.class))).thenReturn(List.of());
        when(tracking.fetchAlertsOfHousehold(anyLong(), any(), any())).thenThrow(new IllegalStateException("down"));

        var summary = service.handle(new GetCareSummaryQuery(5L, 7L, DAY, DAY));

        assertThat(summary.incompleteSources()).containsExactly("alerts");
    }

    @Test
    void anInvalidRangeIsRefusedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.handle(new GetCareSummaryQuery(5L, 7L, DAY.plusDays(1), DAY)))
                .isInstanceOf(InvalidSummaryRangeException.class);
        assertThatThrownBy(() -> service.handle(new GetCareSummaryQuery(5L, 7L, null, DAY)))
                .isInstanceOf(InvalidSummaryRangeException.class);
        assertThatThrownBy(() -> service.handle(new GetCareSummaryQuery(5L, 7L, DAY.minusDays(31), DAY)))
                .isInstanceOf(InvalidSummaryRangeException.class);
        verifyNoInteractions(access, activities, tracking);
    }

    @Test
    void aNoteKeepsItsTrimmedContentAndRefusesAnEmptyOne() {
        var note = new CareNote(5L, 7L, CareNoteCategory.HEALTH, "  Tomó agua.  ");
        assertThat(note.getContent()).isEqualTo("Tomó agua.");
        assertThatThrownBy(() -> new CareNote(5L, 7L, CareNoteCategory.HEALTH, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
