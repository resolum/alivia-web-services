package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.application.internal.tasks.EdgeLinkWatcher;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.infrastructure.configuration.TrackingSchedulingConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The watcher of the edge links really runs: the configuration of Tracking turns the scheduling on by itself, with no help
 * from another context, and the task is the one of the watcher at the interval it declares.
 */
class TrackingSchedulingTest {

    @Configuration
    @Import(TrackingSchedulingConfiguration.class)
    static class Context {
        @Bean
        EdgeLinkWatcher watcher() {
            return new EdgeLinkWatcher(mock(EdgeStatusRepository.class), mock(DeviceEventRepository.class),
                    mock(ExternalAssetsService.class), mock(ExternalNotificationsService.class), Clock.systemUTC(),
                    new SimpleMeterRegistry(), Duration.ofMinutes(3), Duration.ofMinutes(2));
        }
    }

    @Test
    void theWatcherIsScheduledByTheConfigurationOfTrackingAlone() {
        try (var context = new AnnotationConfigApplicationContext(Context.class)) {
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();

            // The runnable of a task is wrapped by Spring, which keeps the description "Class.method" of what it runs.
            var watcher = tasks.stream().map(task -> task.getTask())
                    .filter(task -> task.getRunnable().toString().equals(EdgeLinkWatcher.class.getName() + ".run"))
                    .findFirst();

            assertThat(watcher).as("the task of the watcher").isPresent();
            assertThat(watcher.get()).isInstanceOf(FixedDelayTask.class);
            // Every 30 seconds, as the default says, after the same wait.
            var task = (FixedDelayTask) watcher.get();
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(30));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
        }
    }
}
