package com.resolum.alivia.assistance.application;

import com.resolum.alivia.assistance.application.internal.commandservices.RelativeCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.exceptions.BusinessNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.model.commands.CreateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateRelativeCommand;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static com.resolum.alivia.assistance.AssistanceFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelativeCommandServiceImplTest {

    private static final LocalDate BIRTH = LocalDate.of(1980, 5, 5);

    private RelativeRepository relatives;
    private ExternalBusinessService business;
    private ExternalProfilesService profiles;
    private ExternalIamService iam;
    private RelativeCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        relatives = mock(RelativeRepository.class);
        business = mock(ExternalBusinessService.class);
        profiles = mock(ExternalProfilesService.class);
        iam = mock(ExternalIamService.class);
        service = new RelativeCommandServiceImpl(relatives, business, profiles, iam);
        when(relatives.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 15L));
    }

    private static CreateRelativeCommand create() {
        return new CreateRelativeCommand(1L, "rel@mail.com", "Secret123!", new byte[]{1}, "me.png",
                "51911111111", "Rosa", "Paz", "12345678", BIRTH);
    }

    @Test
    void createsTheUserProfileAndRelative() {
        when(business.existsBusinessById(1L)).thenReturn(true);
        when(iam.createRelativeUser(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(3L);
        when(profiles.createPersonProfile(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(4L);

        assertThat(service.handle(create())).isEqualTo(15L);
        verify(iam).createRelativeUser("rel@mail.com", "Secret123!", 1L, "Rosa", "Paz", "51911111111",
                "12345678", BIRTH);
    }

    @Test
    void rejectsAnUnknownBusinessOrDuplicatedIdentity() {
        assertThatThrownBy(() -> service.handle(create())).isInstanceOf(BusinessNotFoundException.class);

        when(business.existsBusinessById(1L)).thenReturn(true);
        when(profiles.existsPersonProfileByDni("12345678")).thenReturn(true);
        assertThatThrownBy(() -> service.handle(create())).isInstanceOf(DniAlreadyExistsException.class);

        when(profiles.existsPersonProfileByDni("12345678")).thenReturn(false);
        when(profiles.existsPersonProfileByEmail("rel@mail.com")).thenReturn(true);
        assertThatThrownBy(() -> service.handle(create())).isInstanceOf(EmailAddressAlreadyExistsException.class);

        when(profiles.existsPersonProfileByEmail("rel@mail.com")).thenReturn(false);
        when(iam.existsUserByEmail("rel@mail.com")).thenReturn(true);
        assertThatThrownBy(() -> service.handle(create())).isInstanceOf(EmailAddressAlreadyExistsException.class);
    }

    @Test
    void updatesTheProfileOfAnExistingRelative() {
        when(relatives.findById(1L)).thenReturn(Optional.of(relative(1L)));

        assertThat(service.handle(new UpdateRelativeCommand(1L, null, null, "51900000000", "Rosa", "Paz", BIRTH)))
                .isEqualTo(1L);
        verify(profiles).updatePersonProfile(1L, null, null, "51900000000", "Rosa", "Paz", BIRTH);
    }

    @Test
    void updateRejectsAnUnknownRelative() {
        when(relatives.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new UpdateRelativeCommand(2L, null, null, null, "R", "P", BIRTH)))
                .isInstanceOf(RelativeNotFoundException.class);
    }
}
