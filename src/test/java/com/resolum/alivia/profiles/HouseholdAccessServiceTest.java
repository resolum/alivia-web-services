package com.resolum.alivia.profiles;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.resolum.alivia.profiles.application.internal.queryservices.HouseholdAccessServiceImpl;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMemberStatus;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HouseholdAccessServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long OUTSIDER_ID = 99L;

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");
    private static final PhoneNumber PHONE = new PhoneNumber("51987654321");
    private static final EmailAddress EMAIL = new EmailAddress("hogar@alivia.com");

    private HouseholdRepository householdRepository;
    private HouseholdAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        service = new HouseholdAccessServiceImpl(householdRepository);
    }

    static Stream<Arguments> decisionMatrix() {
        return Stream.of(
                Arguments.of(HouseholdResponsibility.PRINCIPAL, HouseholdMemberStatus.ACTIVE, true, true),
                Arguments.of(HouseholdResponsibility.CAREGIVER, HouseholdMemberStatus.ACTIVE, true, false),
                Arguments.of(HouseholdResponsibility.GUEST, HouseholdMemberStatus.ACTIVE, true, false),
                Arguments.of(HouseholdResponsibility.PRINCIPAL, HouseholdMemberStatus.REVOKED, false, false),
                Arguments.of(HouseholdResponsibility.CAREGIVER, HouseholdMemberStatus.REVOKED, false, false),
                Arguments.of(HouseholdResponsibility.GUEST, HouseholdMemberStatus.REVOKED, false, false));
    }

    @ParameterizedTest(name = "{0} {1} -> read={2}, manage={3}")
    @MethodSource("decisionMatrix")
    void appliesTheResponsibilityMatrix(HouseholdResponsibility responsibility,
                                        HouseholdMemberStatus status,
                                        boolean canRead,
                                        boolean canManage) {
        var household = householdWithMember(MEMBER_ID, responsibility, status);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household));

        assertDecision(canRead, canManage);
    }

    @Test
    void deniesBothOperationsToANonMember() {
        var household = householdWithMember(MEMBER_ID, HouseholdResponsibility.PRINCIPAL,
                HouseholdMemberStatus.ACTIVE);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household));

        assertFalse(service.canRead(HOUSEHOLD_ID, OUTSIDER_ID));
        assertFalse(service.canManage(HOUSEHOLD_ID, OUTSIDER_ID));
        assertThrows(HouseholdAccessDeniedException.class,
                () -> service.assertCanRead(HOUSEHOLD_ID, OUTSIDER_ID));
        assertThrows(HouseholdAccessDeniedException.class,
                () -> service.assertCanManage(HOUSEHOLD_ID, OUTSIDER_ID));
    }

    @Test
    void deniesBothOperationsWhenTheHouseholdDoesNotExist() {
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.empty());

        assertFalse(service.canRead(HOUSEHOLD_ID, MEMBER_ID));
        assertFalse(service.canManage(HOUSEHOLD_ID, MEMBER_ID));
        assertThrows(HouseholdNotFoundException.class,
                () -> service.assertCanRead(HOUSEHOLD_ID, MEMBER_ID));
        assertThrows(HouseholdNotFoundException.class,
                () -> service.assertCanManage(HOUSEHOLD_ID, MEMBER_ID));
    }

    @Test
    void assertCanManageRejectsACaregiver() {
        var household = householdWithMember(MEMBER_ID, HouseholdResponsibility.CAREGIVER,
                HouseholdMemberStatus.ACTIVE);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household));

        assertDoesNotThrow(() -> service.assertCanRead(HOUSEHOLD_ID, MEMBER_ID));
        assertThrows(HouseholdAccessDeniedException.class,
                () -> service.assertCanManage(HOUSEHOLD_ID, MEMBER_ID));
    }

    @Test
    void assertCanManageAcceptsThePrincipal() {
        var household = householdWithMember(MEMBER_ID, HouseholdResponsibility.PRINCIPAL,
                HouseholdMemberStatus.ACTIVE);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household));

        assertDoesNotThrow(() -> service.assertCanRead(HOUSEHOLD_ID, MEMBER_ID));
        assertDoesNotThrow(() -> service.assertCanManage(HOUSEHOLD_ID, MEMBER_ID));
    }

    @Test
    void logsTheDeniedAttemptWithIdentifiersOnly() {
        var household = householdWithMember(MEMBER_ID, HouseholdResponsibility.GUEST,
                HouseholdMemberStatus.ACTIVE);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household));

        var logger = (Logger) LoggerFactory.getLogger(HouseholdAccessServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(HouseholdAccessDeniedException.class,
                    () -> service.assertCanManage(HOUSEHOLD_ID, MEMBER_ID));
        } finally {
            logger.detachAppender(appender);
        }

        var denied = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertEquals(1, denied.size());
        assertTrue(denied.get(0).contains("operation=manage"));
        assertTrue(denied.get(0).contains("householdId=1"));
        assertTrue(denied.get(0).contains("userId=10"));
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.canRead(null, MEMBER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.canRead(HOUSEHOLD_ID, null));
        assertThrows(IllegalArgumentException.class, () -> service.canRead(0L, MEMBER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.canRead(HOUSEHOLD_ID, -1L));
        assertThrows(IllegalArgumentException.class, () -> service.assertCanRead(null, MEMBER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.assertCanManage(HOUSEHOLD_ID, 0L));
    }

    private void assertDecision(boolean canRead, boolean canManage) {
        assertEquals(canRead, service.canRead(HOUSEHOLD_ID, MEMBER_ID));
        assertEquals(canManage, service.canManage(HOUSEHOLD_ID, MEMBER_ID));
    }

    private static Household householdWithMember(Long userId, HouseholdResponsibility responsibility,
                                                 HouseholdMemberStatus status) {
        var household = new Household("Casa", ADDRESS, PHONE, EMAIL);
        var member = household.addMember(new UserId(userId), responsibility);
        if (status == HouseholdMemberStatus.REVOKED) {
            member.revoke();
        }
        return household;
    }
}
