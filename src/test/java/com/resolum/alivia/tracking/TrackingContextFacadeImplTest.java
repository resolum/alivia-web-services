package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.acl.TrackingContextFacadeImpl;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.repositories.TrackingStatisticsRepository;
import com.resolum.alivia.tracking.domain.services.AutonomyQueryService;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.HomeAlert;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingContextFacadeImplTest {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

    private final TrackingStatisticsRepository statistics = mock(TrackingStatisticsRepository.class);
    private final TrackingContextFacadeImpl facade =
            new TrackingContextFacadeImpl(mock(AutonomyQueryService.class), statistics);

    @Test
    void countsAreReadFromStatistics() {
        when(statistics.countAlertsBySeverity(FROM, TO)).thenReturn(Map.of("CRITICAL", 2L));
        when(statistics.countDevicesByConnectionStatus()).thenReturn(Map.of("ONLINE", 3L));

        assertThat(facade.countAlertsBySeverity(FROM, TO)).containsEntry("CRITICAL", 2L);
        assertThat(facade.countDevicesByConnectionStatus()).containsEntry("ONLINE", 3L);
    }

    @Test
    void householdAlertsAreMappedToHomeAlerts() {
        var type = EventType.values()[0];
        var event = new DeviceEvent("e1", 1L, 2L, 3L, 4L, type, EventSeverity.IMPORTANT, null, null,
                FROM, FROM, null, null, null, null, null, null);
        when(statistics.findAlertsByHousehold(3L, FROM, TO, 200)).thenReturn(List.of(event));

        assertThat(facade.fetchAlertsOfHousehold(3L, FROM, TO))
                .containsExactly(new HomeAlert(type.name(), "IMPORTANT", 4L, FROM));
    }

    @Test
    void anInvalidPeriodOrHouseholdIsRejected() {
        assertThatThrownBy(() -> facade.countAlertsBySeverity(null, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.countAlertsBySeverity(FROM, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.countAlertsBySeverity(TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.fetchAlertsOfHousehold(null, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStorageFailureIsNeverAnsweredAsEmpty() {
        when(statistics.countDevicesByConnectionStatus()).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(facade::countDevicesByConnectionStatus).isInstanceOf(TrackingStorageUnavailable.class);
    }
}
