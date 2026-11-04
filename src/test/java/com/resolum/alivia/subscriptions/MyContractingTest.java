package com.resolum.alivia.subscriptions;

import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade.ContractingVisit;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.application.internal.commandservices.ContractingCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.application.internal.queryservices.ContractingTrackingQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.queries.GetMyContractingQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.MyContracting;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.MyContractingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The web opens the caregiver platform only when the family's request reached Activación. */
class MyContractingTest {
    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
    private static final UsernamePasswordAuthenticationToken FAMILY = new UsernamePasswordAuthenticationToken("42",
            null, List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")));
    private static final UsernamePasswordAuthenticationToken OTHER = new UsernamePasswordAuthenticationToken("43",
            null, List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")));

    private ConfigurationDraftRepository drafts;
    private OperationsContextFacade operations;
    private ContractingTrackingQueryServiceImpl queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        drafts = mock(ConfigurationDraftRepository.class);
        operations = mock(OperationsContextFacade.class);
        queries = new ContractingTrackingQueryServiceImpl(drafts, mock(PlanQueryService.class), new ExternalOperationsService(operations));
        var commands = new ContractingCommandServiceImpl(drafts, queries, new ExternalOperationsService(operations),
                Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new MyContractingController(queries, commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void aDraftIsClaimedOnceAndOnlyByItsFirstAccount() {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        assertThrows(IllegalArgumentException.class, () -> draft.claimBy(null));
        assertTrue(draft.claimBy(42L));
        assertTrue(draft.claimBy(42L));
        assertFalse(draft.claimBy(43L));
        assertEquals(42L, draft.getOwnerUserId());
        assertFalse(draft.isPaid());
    }

    @Test
    void theFamilyLinksItsRequestAndFollowsItsStageUntilActivation() throws Exception {
        var draft = paidDraft();
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(drafts.findById("missing")).thenReturn(Optional.empty());
        when(drafts.findByOwnerUserIdOrderByUpdatedAtDesc(42L)).thenAnswer(invocation ->
                draft.getOwnerUserId() == null ? List.of() : List.of(draft));
        when(operations.fetchContractingVisitsOfDraft(draft.getId()))
                .thenReturn(List.of(visit("EVALUATION", "COMPLETED", "VIABLE")))
                .thenReturn(List.of(visit("EVALUATION", "COMPLETED", "VIABLE"), visit("INSTALLATION", "COMPLETED", "APPROVED")));

        mvc.perform(get("/api/v1/configuration-drafts/mine").principal(FAMILY)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/configuration-drafts/{id}/owner", draft.getId()).principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draft.getId()))
                .andExpect(jsonPath("$.stage").value("CONFIRMATION"))
                .andExpect(jsonPath("$.planCode").value("MINIMUM"))
                .andExpect(jsonPath("$.billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.active").value(false));
        verify(drafts).save(draft);
        // The approved installation captured the payment: the service is active.
        ReflectionTestUtils.setField(draft, "paymentStatus", PaymentStatus.SUCCEEDED);
        mvc.perform(get("/api/v1/configuration-drafts/mine").principal(FAMILY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("ACTIVATION"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(put("/api/v1/configuration-drafts/{id}/owner", draft.getId()).principal(OTHER))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/configuration-drafts/missing/owner").principal(FAMILY))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnpaidRequestIsStillInConfiguration() {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        draft.claimBy(42L);
        when(drafts.findByOwnerUserIdOrderByUpdatedAtDesc(42L)).thenReturn(List.of(draft));

        var mine = queries.handle(new GetMyContractingQuery(42L)).orElseThrow();

        assertEquals(MyContracting.CONFIGURATION, mine.stage());
        assertFalse(mine.isActive());
        assertTrue(queries.handle(new GetMyContractingQuery(null)).isEmpty());
        verifyNoInteractions(operations);
    }

    @Test
    void anUnauthenticatedCallIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MyContractingController(queries, null).mine(null));
    }

    @Test
    void theMostAdvancedRequestDecidesOverANewerUnpaidOne() {
        var active = paidDraft();
        ReflectionTestUtils.setField(active, "paymentStatus", PaymentStatus.SUCCEEDED);
        var unpaid = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        when(drafts.findByOwnerUserIdOrderByUpdatedAtDesc(42L)).thenReturn(List.of(unpaid, active));
        when(operations.fetchContractingVisitsOfDraft(active.getId()))
                .thenReturn(List.of(visit("EVALUATION", "COMPLETED", "VIABLE"), visit("INSTALLATION", "COMPLETED", "APPROVED")))
                .thenReturn(List.of(visit("EVALUATION", "SCHEDULED", null)))
                .thenReturn(List.of(visit("EVALUATION", "COMPLETED", "NOT_VIABLE")));

        var mine = queries.handle(new GetMyContractingQuery(42L)).orElseThrow();
        assertEquals(active.getId(), mine.draftId());
        assertTrue(mine.isActive());

        // A paid request still in evaluation also wins over the newer draft.
        ReflectionTestUtils.setField(active, "paymentStatus", PaymentStatus.PREAUTHORIZED);
        assertEquals("EVALUATION", queries.handle(new GetMyContractingQuery(42L)).orElseThrow().stage());

        // A home that was not viable leaves the newer draft as the request to continue.
        var afterRejection = queries.handle(new GetMyContractingQuery(42L)).orElseThrow();
        assertEquals(unpaid.getId(), afterRejection.draftId());
        assertEquals(MyContracting.CONFIGURATION, afterRejection.stage());
    }

    @Test
    void theApprovedInstallationSettlesThePaymentAsSoonAsStripeCapturesIt() {
        var gateway = mock(PaymentAuthorizationGateway.class);
        var facade = new SubscriptionsContextFacadeImpl(drafts, mock(SubscriptionRepository.class), gateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var charged = paidDraft();
        ReflectionTestUtils.setField(charged, "paymentStatus", PaymentStatus.AWAITING_CONFIRMATION);
        ReflectionTestUtils.setField(charged, "stripePaymentIntentId", "pi_charged");
        var pending = paidDraft();
        ReflectionTestUtils.setField(pending, "paymentStatus", PaymentStatus.PREAUTHORIZED);
        ReflectionTestUtils.setField(pending, "stripePaymentIntentId", "pi_pending");
        when(drafts.findById(charged.getId())).thenReturn(Optional.of(charged));
        when(drafts.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(gateway.capture(new StripePaymentIntentId("pi_charged"))).thenReturn(PaymentStatus.SUCCEEDED);
        when(gateway.capture(new StripePaymentIntentId("pi_pending"))).thenReturn(PaymentStatus.AWAITING_CONFIRMATION);

        assertTrue(facade.captureConfigurationDraftPayment(charged.getId()));
        assertEquals(PaymentStatus.SUCCEEDED, charged.getPaymentStatus());
        assertTrue(facade.captureConfigurationDraftPayment(pending.getId()));
        assertEquals(PaymentStatus.AWAITING_CONFIRMATION, pending.getPaymentStatus());
    }

    private static ConfigurationDraft paidDraft() {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        ReflectionTestUtils.setField(draft, "preauthorizedAt", NOW);
        return draft;
    }

    private static ContractingVisit visit(String type, String status, String result) {
        return new ContractingVisit(type, status, LocalDate.of(2026, 11, 4), LocalTime.of(8, 0), LocalTime.of(10, 0),
                "Elena Vega", null, result, null);
    }
}
