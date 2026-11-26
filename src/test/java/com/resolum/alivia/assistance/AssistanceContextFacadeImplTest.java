package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.application.acl.AssistanceContextFacadeImpl;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistanceContextFacadeImplTest {

    private final AssistedPersonRepository people = mock(AssistedPersonRepository.class);
    private final AssistanceAccessService access = mock(AssistanceAccessService.class);
    private final AssistanceContextFacadeImpl facade = new AssistanceContextFacadeImpl(people, access);

    @Test
    void aPersonProfileIsReadableOrOperableWhenTheAccessServiceSaysSo() {
        var person = assistedPerson(2L, relative(1L));
        when(people.findAllByPersonProfileId(100L)).thenReturn(List.of(person));
        when(access.canRead(person, 5L)).thenReturn(true);
        when(access.canOperate(person, 5L)).thenReturn(false);

        assertThat(facade.canReadPersonProfile(100L, 5L)).isTrue();
        assertThat(facade.canOperatePersonProfile(100L, 5L)).isFalse();
        assertThat(facade.canReadPersonProfile(100L, 6L)).isFalse();
    }

    @Test
    void aProfileOfNoAssistedPersonOrWithoutIdsAnswersFalse() {
        when(people.findAllByPersonProfileId(101L)).thenReturn(List.of());

        assertThat(facade.canReadPersonProfile(101L, 5L)).isFalse();
        assertThat(facade.canReadPersonProfile(null, 5L)).isFalse();
        assertThat(facade.canOperatePersonProfile(101L, null)).isFalse();
    }

    @Test
    void anAssistedPersonIsReadableWhenTheAccessServiceSaysSo() {
        var person = assistedPerson(2L, relative(1L));
        when(people.findById(2L)).thenReturn(Optional.of(person));
        when(access.canRead(person, 5L)).thenReturn(true);

        assertThat(facade.canReadAssistedPerson(2L, 5L)).isTrue();
        assertThat(facade.canReadAssistedPerson(2L, 6L)).isFalse();
    }

    @Test
    void aMissingPersonIsNotReadableAndAnswersTheSameAsADeniedOne() {
        when(people.findById(3L)).thenReturn(Optional.empty());

        assertThat(facade.canReadAssistedPerson(3L, 5L)).isFalse();
        assertThat(facade.canReadAssistedPerson(null, 5L)).isFalse();
        assertThat(facade.canReadAssistedPerson(2L, null)).isFalse();
    }

    @Test
    void theHouseholdOfAPersonIsEmptyWhenThePersonHasNoneOrDoesNotExist() {
        var placed = assistedPerson(2L, relative(1L));
        placed.updateCareDetails(30L, null, null, null);
        var unplaced = assistedPerson(4L, relative(1L));
        when(people.findById(2L)).thenReturn(Optional.of(placed));
        when(people.findById(4L)).thenReturn(Optional.of(unplaced));
        when(people.findById(5L)).thenReturn(Optional.empty());

        assertThat(facade.fetchHouseholdIdOfAssistedPerson(2L)).contains(30L);
        assertThat(facade.fetchHouseholdIdOfAssistedPerson(4L)).isEmpty();
        assertThat(facade.fetchHouseholdIdOfAssistedPerson(5L)).isEmpty();
        assertThat(facade.fetchHouseholdIdOfAssistedPerson(null)).isEmpty();
    }

    @Test
    void aHouseholdIsReadableWhenTheUserMayReadAnyoneWhoLivesThere() {
        var first = assistedPerson(2L, relative(1L));
        var second = assistedPerson(3L, relative(2L));
        when(people.findAllByHouseholdId(30L)).thenReturn(List.of(first, second));
        when(people.findAllByHouseholdId(31L)).thenReturn(List.of());
        when(access.canRead(first, 5L)).thenReturn(false);
        when(access.canRead(second, 5L)).thenReturn(true);

        assertThat(facade.canReadHousehold(30L, 5L)).isTrue();
        assertThat(facade.canReadHousehold(30L, 6L)).isFalse();
        assertThat(facade.canReadHousehold(31L, 5L)).isFalse();
        assertThat(facade.canReadHousehold(null, 5L)).isFalse();
        assertThat(facade.canReadHousehold(30L, null)).isFalse();
    }
}
