package com.resolum.alivia.profiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.resolum.alivia.profiles.application.acl.HouseholdAccessFacadeImpl;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseholdAccessFacadeTest {

    private HouseholdAccessService householdAccessService;
    private HouseholdAccessFacadeImpl facade;

    @BeforeEach
    void setUp() {
        householdAccessService = mock(HouseholdAccessService.class);
        facade = new HouseholdAccessFacadeImpl(householdAccessService);
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
}
