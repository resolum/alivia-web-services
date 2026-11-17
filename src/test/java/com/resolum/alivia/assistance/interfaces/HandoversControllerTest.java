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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.resolum.alivia.assistance.AssistanceFixtures.assistedPerson;
import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HandoversControllerTest {

    @Test
    void proposeSubstituteAndAnswerHandoverParseNonNumericIdsSafely() {
        var commandService = mock(HandoverCommandService.class);
        var queryService = mock(HandoverQueryService.class);
        var controller = new HandoversController(commandService, queryService);

        Relative rel = relative(1L);
        AssistedPerson person = assistedPerson(2L, rel);
        CareShift fromShift = new CareShift(person, 3L, "Ana", LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(16, 0));
        CareShift toShift = new CareShift(person, 4L, "Luis", LocalDate.now(), LocalTime.of(16, 0), LocalTime.of(23, 0));
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
                new AnswerHandoverResource(true, "cg-4", "Luis", List.of(), List.of()));
        assertThat(ans.getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(commandService).handle(new ProposeSubstituteCommand(1L, 7L, "Dayro", 2L));
        verify(commandService).handle(new ProposeSubstituteCommand(1L, 5L, "Dayro", null));
        verify(commandService).handle(new AnswerHandoverCommand(1L, true, 4L, "Luis", List.of(), List.of()));
    }
}
