package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAgendaByCaregiverIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.AgendaController;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.activity;
import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.caregiver;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendaControllerTest {

    private final LocalDate from = LocalDate.of(2026, 11, 10);
    private final LocalDate to = from.plusDays(1);
    private CareActivityQueryService queries;
    private CareActivityRepository activities;
    private AssistedPersonRepository persons;
    private RelativeRepository relatives;
    private CaregiverRepository caregivers;
    private AgendaController controller;
    private Relative relative;
    private AssistedPerson person;

    @BeforeEach
    void setUp() {
        queries = mock(CareActivityQueryService.class);
        activities = mock(CareActivityRepository.class);
        persons = mock(AssistedPersonRepository.class);
        relatives = mock(RelativeRepository.class);
        caregivers = mock(CaregiverRepository.class);
        controller = new AgendaController(queries, activities, persons, relatives, caregivers);
        relative = relative(1L);
        person = assistedPerson(2L, relative);
    }

    private AgendaItem item(int hour, CareActivityType type, OccurrenceStatus status, Long caregiverId) {
        return new AgendaItem(5L, 30L, "Item", type, new PersonProfileId(9L), from,
                new TimeRange(LocalTime.of(hour, 0), LocalTime.of(hour, 30)), from.atTime(hour, 0),
                from.atTime(hour, 30), caregiverId, true, status,
                new AgendaItem.Contact("Ana", new PersonProfileId(4L), "Carla", "c@mail.com"));
    }

    @Test
    void filtersAndSortsTheAgendaOfAnAssistedPerson() {
        when(queries.handle(new GetAgendaByAssistedPersonIdQuery(2L, from, to))).thenReturn(List.of(
                item(10, CareActivityType.MEDICATION, OccurrenceStatus.SCHEDULED, 3L),
                item(8, CareActivityType.MEDICATION, OccurrenceStatus.SCHEDULED, null),
                item(9, CareActivityType.MEAL, OccurrenceStatus.SCHEDULED, null),
                item(7, CareActivityType.MEDICATION, OccurrenceStatus.COMPLETED, null)));

        var body = controller.getCareAgenda(null, from, to, 2L, null, OccurrenceStatus.SCHEDULED,
                ReminderPriority.HIGH).getBody();

        assertThat(body).hasSize(2);
        assertThat(body.get(0).startsAt().getHour()).isEqualTo(8);
        assertThat(body.get(0).responsible().type()).isEqualTo("RELATIVE");
        assertThat(body.get(1).responsible().type()).isEqualTo("CAREGIVER");
    }

    @Test
    void readsTheAgendaOfACaregiver() {
        when(queries.handle(new GetAgendaByCaregiverIdQuery(3L, from, to)))
                .thenReturn(List.of(item(8, CareActivityType.BATH, OccurrenceStatus.MISSED, 3L)));

        assertThat(controller.getCareAgenda(null, from, to, null, 3L, null, null).getBody()).hasSize(1);
        assertThat(controller.getAgendaByCaregiverId(3L, from, to).getBody()).hasSize(1);
    }

    @Test
    void resolvesTheAgendaOfARelativeCaller() {
        when(relatives.findByUserId(new UserId(1L))).thenReturn(Optional.of(relative));
        when(persons.findAllByRelativeId(1L)).thenReturn(List.of(person));
        when(queries.handle(new GetAgendaByAssistedPersonIdQuery(2L, from, to)))
                .thenReturn(List.of(item(8, CareActivityType.THERAPY, OccurrenceStatus.SCHEDULED, null)));

        assertThat(controller.getCareAgenda(principal("1"), from, to, null, null, null, null).getBody()).hasSize(1);
        assertThat(controller.getAgendaByAssistedPersonId(2L, from, to).getBody()).hasSize(1);
    }

    @Test
    void resolvesTheAgendaOfACaregiverCaller() {
        when(relatives.findByUserId(new UserId(6L))).thenReturn(Optional.empty());
        when(caregivers.findAllByUserId(new UserId(6L))).thenReturn(List.of(caregiver(3L, 6L, person)));
        when(queries.handle(new GetAgendaByCaregiverIdQuery(3L, from, to)))
                .thenReturn(List.of(item(8, CareActivityType.HYGIENE, OccurrenceStatus.SCHEDULED, 3L)));

        assertThat(controller.getCareAgenda(principal("6"), from, to, null, null, null, null).getBody()).hasSize(1);
    }

    @Test
    void fallsBackToEveryActiveActivityWhenTheCallerHasNothing() {
        when(activities.findAllActive()).thenReturn(List.of(activity(5L, person, null), activity(6L, person, null)));
        when(queries.handle(any(GetAgendaByAssistedPersonIdQuery.class)))
                .thenReturn(List.of(item(8, CareActivityType.APPOINTMENT, OccurrenceStatus.SCHEDULED, null)));

        assertThat(controller.getCareAgenda(principal("not-a-user"), from, to, null, null, null, null).getBody())
                .hasSize(1);
        assertThat(controller.getCareAgenda(null, from, to, null, null, null, null).getBody()).hasSize(1);
    }
}
