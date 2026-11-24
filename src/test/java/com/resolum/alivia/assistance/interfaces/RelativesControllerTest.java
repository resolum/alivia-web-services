package com.resolum.alivia.assistance.interfaces;

import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;
import com.resolum.alivia.assistance.domain.model.commands.UpdateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.RelativeCommandService;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.assistance.interfaces.rest.controllers.RelativesController;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateRelativeResource;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.Optional;

import static com.resolum.alivia.assistance.AssistanceFixtures.principal;
import static com.resolum.alivia.assistance.AssistanceFixtures.relative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelativesControllerTest {

    private final RelativeCommandService commands = mock(RelativeCommandService.class);
    private final RelativeQueryService queries = mock(RelativeQueryService.class);
    private final AssistanceAccessService access = mock(AssistanceAccessService.class);
    private final RelativesController controller = new RelativesController(commands, queries, access);

    @Test
    void aRelativeIsEditedBySelfOnly() {
        when(access.requireSelfRelative(1L, 7L)).thenReturn(relative(1L));
        when(access.requireSelfRelative(1L, 8L)).thenThrow(new AssistanceAccessDeniedException("no"));
        when(commands.handle(any(UpdateRelativeCommand.class))).thenReturn(1L);
        var resource = new UpdateRelativeResource("519", "Ana", "Diaz", LocalDate.of(1980, 1, 1));

        assertThat(controller.updateRelative(1L, resource, principal("7")).getBody()).isEqualTo(1L);
        assertThatThrownBy(() -> controller.updateRelative(1L, resource, principal("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(commands).handle(any(UpdateRelativeCommand.class));
    }

    @Test
    void aRelativeIsReadBySelfOrWhoTakesCareOfItsPersonsOnly() {
        when(access.requireReadRelative(1L, 7L)).thenReturn(relative(1L));
        when(access.requireReadRelative(1L, 8L)).thenThrow(new AssistanceAccessDeniedException("no"));

        assertThat(controller.getRelativeById(1L, principal("7")).getStatusCode().value()).isEqualTo(200);
        assertThatThrownBy(() -> controller.getRelativeById(1L, principal("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        assertThatThrownBy(() -> controller.getRelativeById(1L, null))
                .isInstanceOf(AssistanceAccessDeniedException.class);
    }

    @Test
    void theRelativeOfAnAccountIsReadByThatAccountOnly() {
        when(queries.handle(new GetRelativeByUserIdQuery(7L))).thenReturn(Optional.of(relative(1L)));

        assertThat(controller.getRelativeByUserId(7L, principal("7")).getStatusCode().value()).isEqualTo(200);
        assertThatThrownBy(() -> controller.getRelativeByUserId(7L, principal("8")))
                .isInstanceOf(AssistanceAccessDeniedException.class);
        verify(queries, never()).handle(new GetRelativeByUserIdQuery(8L));
    }

    @Test
    void listingTheRelativesOfABusinessIsAdministratorOnly() throws Exception {
        var rule = RelativesController.class.getMethod("getRelativesByBusinessId", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("hasRole('ADMINISTRATOR')");
    }
}
