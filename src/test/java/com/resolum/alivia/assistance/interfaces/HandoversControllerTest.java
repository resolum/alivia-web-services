package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.commands.AnswerHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ProposeSubstituteCommand;
import com.resolum.alivia.assistance.domain.services.HandoverCommandService;
import com.resolum.alivia.assistance.domain.services.HandoverQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.HandoversController;
import com.resolum.alivia.assistance.interfaces.rest.resources.AnswerHandoverResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.ProposeSubstituteResource;
import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HandoversControllerTest {

    @Test
    void proposeSubstituteAndAnswerHandoverParseNonNumericIdsSafely() {
        var commandService = mock(HandoverCommandService.class);
        var queryService = mock(HandoverQueryService.class);
        var access = mock(AssistanceAccessService.class);
        var controller = new HandoversController(commandService, queryService, access);

        Relative rel = relative(1L);
        AssistedPerson person = assistedPerson(2L, rel);
        LocalDateTime base = LocalDateTime.of(2026, 11, 15, 8, 0);
        CareShift fromShift = new CareShift(person, 3L, "Ana", base, base.plusHours(8), "");
        CareShift toShift = new CareShift(person, 4L, "Luis", base.plusHours(8), base.plusHours(16), "");
        ReflectionTestUtils.setField(fromShift, "id", 10L);
        ReflectionTestUtils.setField(toShift, "id", 11L);
        Handover handover = new Handover(fromShift, toShift, "[]", "[]");
        ReflectionTestUtils.setField(handover, "id", 1L);

        when(commandService.handle(any(ProposeSubstituteCommand.class))).thenReturn(handover);
        when(commandService.handle(any(AnswerHandoverCommand.class))).thenReturn(handover);

        var sub1 = controller.proposeSubstitute(1L,
                new ProposeSubstituteResource("principal-7", "Dayro", "relative-2"), principal("1"));
        assertThat(sub1.getStatusCode()).isEqualTo(HttpStatus.OK);

        var sub2 = controller.proposeSubstitute(1L,
                new ProposeSubstituteResource("principal", "Dayro", ""), principal("5"));
        assertThat(sub2.getStatusCode()).isEqualTo(HttpStatus.OK);

        var ans = controller.answerHandover(1L,
                new AnswerHandoverResource(true, "cg-4", "Luis", List.of(), List.of()), principal("4"));
        assertThat(ans.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(access).requireOperateHandover(1L, 1L);
        verify(access).requireOperateHandover(1L, 5L);
        verify(access).requireOperateHandover(1L, 4L);

        verify(commandService).handle(new ProposeSubstituteCommand(1L, 7L, "Dayro", 2L));
        verify(commandService).handle(new ProposeSubstituteCommand(1L, 5L, "Dayro", null));
        verify(commandService).handle(new AnswerHandoverCommand(1L, true, 4L, "Luis", List.of(), List.of()));
    }

    @Test
    void refusesEveryHandoverOperationOfACallerOutsideTheCareNetwork() {
        var commandService = mock(HandoverCommandService.class);
        var queryService = mock(HandoverQueryService.class);
        var access = mock(AssistanceAccessService.class);
        var controller = new HandoversController(commandService, queryService, access);
        var denied = new AssistanceAccessDeniedException("no");
        when(access.requireOperateHandover(1L, 9L)).thenThrow(denied);
        when(access.requireOperateShift(10L, 9L)).thenThrow(denied);
        when(access.requireRead(2L, 9L)).thenThrow(denied);

        assertThatThrownBy(() -> controller.answerHandover(1L,
                new AnswerHandoverResource(true, "4", "Luis", List.of(), List.of()), principal("9")))
                .isSameAs(denied);
        assertThatThrownBy(() -> controller.proposeSubstitute(1L,
                new ProposeSubstituteResource("3", "Dayro", null), principal("9"))).isSameAs(denied);
        assertThatThrownBy(() -> controller.escalateHandover(1L, principal("9"))).isSameAs(denied);
        assertThatThrownBy(() -> controller.requestHandover(
                new com.resolum.alivia.assistance.interfaces.rest.resources.RequestHandoverResource("10", "11", List.of(), List.of()),
                principal("9"))).isSameAs(denied);
        assertThatThrownBy(() -> controller.getHandovers(2L, null, principal("9"))).isSameAs(denied);
        verifyNoInteractions(commandService);
    }

    @Test
    void withoutAPersonTheListIsTheOneOfTheCallerAndEmptyWhenThereIsNone() {
        var queryService = mock(HandoverQueryService.class);
        var access = mock(AssistanceAccessService.class);
        var controller = new HandoversController(mock(HandoverCommandService.class), queryService, access);
        when(access.readableBy(8L)).thenReturn(List.of());

        assertThat(controller.getHandovers(null, null, principal("8")).getBody()).isEmpty();
        verifyNoInteractions(queryService);
    }
}
