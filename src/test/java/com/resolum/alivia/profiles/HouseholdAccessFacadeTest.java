package com.resolum.alivia.profiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.resolum.alivia.profiles.application.acl.HouseholdAccessFacadeImpl;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseholdAccessFacadeTest {

    private HouseholdAccessService householdAccessService;
    private HouseholdRepository householdRepository;
    private HouseholdAccessFacadeImpl facade;

    @BeforeEach
    void setUp() {
        householdAccessService = mock(HouseholdAccessService.class);
        householdRepository = mock(HouseholdRepository.class);
        facade = new HouseholdAccessFacadeImpl(householdAccessService, householdRepository);
    }

    @Test
    void canReadHouseholdDelegatesToTheDomainService() {
        when(householdAccessService.canRead(1L, 10L)).thenReturn(true);

        assertTrue(facade.canReadHousehold(1L, 10L));
        verify(householdAccessService).canRead(1L, 10L);
    }

    @Test
    void canManageHouseholdDelegatesToTheDomainService() {
        when(householdAccessService.canManage(1L, 10L)).thenReturn(false);

        assertFalse(facade.canManageHousehold(1L, 10L));
        verify(householdAccessService).canManage(1L, 10L);
    }

    @Test
    void fetchHouseholdAliasReturnsAliasWhenFound() {
        var household = mock(Household.class);
        when(household.getAlias()).thenReturn("Casa Los Rosales");
        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));

        var result = facade.fetchHouseholdAlias(1L);

        assertTrue(result.isPresent());
        assertEquals("Casa Los Rosales", result.get());
    }
}
