package com.resolum.alivia.assistance.application.internal.tasks;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.repositories.CareActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Reminds the relative of an assisted person that a care activity, a medication included, is about to start.
 *
 * <p>Every minute it looks for the occurrences that start within the lead time, in the time of the homes
 * (America/Lima). Communications keeps one reminder per occurrence, so a later run or another instance does not
 * repeat it.</p>
 */
@Slf4j
@Component
public class CareActivityReminderScheduler {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CareActivityRepository activities;
    private final ExternalNotificationsService notifications;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final ZoneId zone;
    private final Duration lead;

    public CareActivityReminderScheduler(CareActivityRepository activities, ExternalNotificationsService notifications,
                                         TransactionOperations transactions, Clock clock,
                                         @Value("${alivia.assistance.reminders.zone:America/Lima}") String zone,
                                         @Value("${alivia.assistance.reminders.lead-minutes:15}") long leadMinutes) {
        this.activities = activities;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.lead = Duration.ofMinutes(leadMinutes);
    }

    @Scheduled(fixedDelayString = "${alivia.assistance.reminders.interval-ms:60000}",
            initialDelayString = "${alivia.assistance.reminders.interval-ms:60000}")
    public void run() {
        try {
            remindDueActivities();
        } catch (RuntimeException e) {
            log.error("Care activity reminders failed: {}", e.getMessage());
        }
    }

    /**
     * Sends the reminders of the occurrences that start from now until the lead time ends. The activities are read in
     * one transaction (their assisted person and relative load lazily); each reminder is saved in its own.
     *
     * @return how many reminders were sent
     */
    public int remindDueActivities() {
        return transactions.execute(status -> remindDueActivitiesNow());
    }

    private int remindDueActivitiesNow() {
        var now = LocalDateTime.now(clock.withZone(zone));
        var until = now.plus(lead);
        int sent = 0;
        for (CareActivity activity : activities.findAllActive()) {
            for (var date = now.toLocalDate(); !date.isAfter(until.toLocalDate()); date = date.plusDays(1)) {
                for (ActivityAssignment occurrence : activity.occurrencesOn(date)) {
                    var start = date.atTime(occurrence.getStartTime());
                    if (start.isBefore(now) || start.isAfter(until)) continue;
                    if (remind(activity, occurrence, start)) sent++;
                }
            }
        }
        return sent;
    }

    private boolean remind(CareActivity activity, ActivityAssignment occurrence, LocalDateTime start) {
        var relative = activity.getAssistedPerson() == null ? null : activity.getAssistedPerson().getRelative();
        if (relative == null || relative.getUserId() == null) return false;
        var title = activity.getType() == CareActivityType.MEDICATION ? "Hora del medicamento" : "Recordatorio de actividad";
        var body = activity.getTitle() + " a las " + start.toLocalTime().format(TIME) + ".";
        var occurrenceId = "activity-" + occurrence.getId() + "-" + start.toLocalDate();
        try {
            return notifications.remindCareActivity(relative.getUserId().userId(), occurrenceId, title, body);
        } catch (RuntimeException e) {
            log.warn("Could not remind occurrence {}: {}", occurrenceId, e.getMessage());
            return false;
        }
    }
}
