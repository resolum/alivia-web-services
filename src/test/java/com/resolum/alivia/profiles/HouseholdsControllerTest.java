package com.resolum.alivia.profiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;
import com.resolum.alivia.profiles.interfaces.rest.controllers.HouseholdsController;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HouseholdsControllerTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    private HouseholdQueryService queryService;
    private HouseholdAccessService accessService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queryService = mock(HouseholdQueryService.class);
        accessService = mock(HouseholdAccessService.class);
        mvc = MockMvcBuilders.standaloneSetup(new HouseholdsController(queryService, accessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getHouseholdAuthorizesTheAuthenticatedUserAndReturnsTheHousehold() throws Exception {
        var household = household();
        when(queryService.handle(new GetHouseholdQuery(1L))).thenReturn(household);

        mvc.perform(get("/api/v1/households/1").principal(() -> "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.alias").value("Casa"))
                .andExpect(jsonPath("$.formattedAddress").value(ADDRESS.formattedAddress()))
                .andExpect(jsonPath("$.contactPhone").value("51987654321"))
                .andExpect(jsonPath("$.contactEmail").value("hogar@alivia.com"))
                .andExpect(jsonPath("$.members[0].userId").value(10))
                .andExpect(jsonPath("$.members[0].responsibility").value("PRINCIPAL"))
                .andExpect(jsonPath("$.members[0].status").value("ACTIVE"));

        verify(accessService).assertCanRead(1L, 10L);
    }

    @Test
    void getHouseholdMembersAuthorizesTheAuthenticatedUserAndReturnsTheMembers() throws Exception {
        var household = household();
        when(queryService.handle(new GetHouseholdMembersQuery(1L))).thenReturn(household.getMembers());

        mvc.perform(get("/api/v1/households/1/members").principal(() -> "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].responsibility").value("PRINCIPAL"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(accessService).assertCanRead(1L, 10L);
    }

    @Test
    void getHouseholdReturns403ForANonMemberWithoutReadingTheHousehold() throws Exception {
        doThrow(new HouseholdAccessDeniedException(1L, 10L))
                .when(accessService).assertCanRead(1L, 10L);

        mvc.perform(get("/api/v1/households/1").principal(() -> "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("household_access_denied"));

        verify(queryService, never()).handle(any(GetHouseholdQuery.class));
    }

    @Test
    void getHouseholdMembersReturns403ForANonMemberWithoutReadingTheMembers() throws Exception {
        doThrow(new HouseholdAccessDeniedException(1L, 10L))
                .when(accessService).assertCanRead(1L, 10L);

        mvc.perform(get("/api/v1/households/1/members").principal(() -> "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("household_access_denied"));

        verify(queryService, never()).handle(any(GetHouseholdMembersQuery.class));
    }

    @Test
    void getHouseholdReturns404WhenItDoesNotExist() throws Exception {
        doThrow(new HouseholdNotFoundException(99L))
                .when(accessService).assertCanRead(99L, 10L);

        mvc.perform(get("/api/v1/households/99").principal(() -> "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("household_not_found"));

        verify(queryService, never()).handle(any(GetHouseholdQuery.class));
    }

    @Test
    void getHouseholdMembersReturns404WhenTheHouseholdDoesNotExist() throws Exception {
        doThrow(new HouseholdNotFoundException(99L))
                .when(accessService).assertCanRead(99L, 10L);

        mvc.perform(get("/api/v1/households/99/members").principal(() -> "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("household_not_found"));

        verify(queryService, never()).handle(any(GetHouseholdMembersQuery.class));
    }

    private static Household household() {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        var member = household.addMember(new UserId(10L), HouseholdResponsibility.PRINCIPAL);
        setId(household, 1L);
        setId(member, 100L);
        return household;
    }

    private static void setId(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }
}
