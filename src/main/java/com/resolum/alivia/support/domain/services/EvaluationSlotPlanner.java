package com.resolum.alivia.support.domain.services;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.support.domain.model.aggregates.Appointment;
import com.resolum.alivia.support.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.support.domain.model.valueobjects.TechnicianAvailability;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Turns the technicians' shifts of a date into the windows the family can book.
 *
 * <p>The evaluation lasts 45 to 60 minutes, so the family books a two-hour arrival window. Windows are
 * cut from the start of each shift block and must fit in it whole (a 08:00 to 12:00 block gives 08:00 to
 * 10:00 and 10:00 to 12:00). A technician takes a window only when it does not overlap a visit they
 * already have. When several can take it, the one with fewer visits that day attends it, then the
 * lowest id, so the load is spread.</p>
 */
public final class EvaluationSlotPlanner {
    public static final Duration WINDOW = Duration.ofHours(2);

    private EvaluationSlotPlanner() {
    }

    /** Two-hour windows that fit whole inside the given shift blocks. */
    public static List<TimeRange> windowsWithin(List<TimeRange> shifts) {
        var windows = new ArrayList<TimeRange>();
        for (var shift : shifts) {
            var start = shift.start();
            while (!start.plus(WINDOW).isAfter(shift.end()) && start.plus(WINDOW).isAfter(start)) {
                windows.add(new TimeRange(start, start.plus(WINDOW)));
                start = start.plus(WINDOW);
            }
        }
        return windows;
    }

    /** Bookable windows of one date, in time order, each with the technician that would attend it. */
    public static List<AvailableSlot> plan(LocalDate date, List<TechnicianAvailability> technicians,
                                           List<Appointment> visitsOfTheDay) {
        var windows = new TreeSet<TimeRange>(Comparator.comparing(TimeRange::start));
        technicians.forEach(technician -> windows.addAll(windowsWithin(technician.shifts())));
        var slots = new ArrayList<AvailableSlot>();
        for (var window : windows) {
            var candidates = candidates(window, technicians, visitsOfTheDay);
            if (candidates.isEmpty()) continue;
            var chosen = candidates.getFirst();
            slots.add(new AvailableSlot(date, window, chosen.technicianId(), chosen.fullName(), candidates.size()));
        }
        return slots;
    }

    /** Technicians free for the window, least busy first. */
    public static List<TechnicianAvailability> candidates(TimeRange window, List<TechnicianAvailability> technicians,
                                                          List<Appointment> visitsOfTheDay) {
        return technicians.stream()
                .filter(technician -> windowsWithin(technician.shifts()).contains(window))
                .filter(technician -> visitsOfTheDay.stream().noneMatch(visit ->
                        visit.isAttendedBy(technician.technicianId()) && visit.window().overlaps(window)))
                .sorted(Comparator.comparingLong((TechnicianAvailability technician) -> visitsOfTheDay.stream()
                                .filter(visit -> visit.isAttendedBy(technician.technicianId())).count())
                        .thenComparing(TechnicianAvailability::technicianId))
                .toList();
    }
}
