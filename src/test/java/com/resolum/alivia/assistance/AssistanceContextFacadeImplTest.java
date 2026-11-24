package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.application.acl.AssistanceContextFacadeImpl;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
