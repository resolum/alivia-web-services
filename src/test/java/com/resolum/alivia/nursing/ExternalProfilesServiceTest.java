package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalProfilesService (Nursing ACL) Tests")
class ExternalProfilesServiceTest {

    @Mock
    private ProfilesContextFacade profilesContextFacade;

    @InjectMocks
    private ExternalProfilesService externalProfilesService;

    @Test
    @DisplayName("fetchPersonNames resolves names from ProfilesContextFacade and skips unknown profiles")
    void fetchPersonNamesDelegatesToProfilesContextFacade() {
        when(profilesContextFacade.fetchPersonProfileFullNameById(8L)).thenReturn(Optional.of("Maria Lopez"));
        when(profilesContextFacade.fetchPersonProfileFullNameById(15L)).thenReturn(Optional.empty());

        var names = externalProfilesService.fetchPersonNames(Set.of(new PersonProfileId(8L), new PersonProfileId(15L)));

        assertThat(names).containsExactly(Map.entry(new PersonProfileId(8L), "Maria Lopez"));
    }

    @Test
    @DisplayName("fetchPersonNames does not call Profiles for an empty set")
    void fetchPersonNamesSkipsProfilesForNoIds() {
        assertThat(externalProfilesService.fetchPersonNames(Set.of())).isEmpty();

        verifyNoInteractions(profilesContextFacade);
    }

    @Test
    @DisplayName("fetchStaffNames resolves staff names from ProfilesContextFacade and skips unknown profiles")
    void fetchStaffNamesDelegatesToProfilesContextFacade() {
        when(profilesContextFacade.fetchStaffProfileFullNameById(40L)).thenReturn(Optional.of("Juan Perez"));
        when(profilesContextFacade.fetchStaffProfileFullNameById(41L)).thenReturn(Optional.empty());

        assertThat(externalProfilesService.fetchStaffNames(Set.of(40L, 41L))).containsExactly(Map.entry(40L, "Juan Perez"));
    }
}
