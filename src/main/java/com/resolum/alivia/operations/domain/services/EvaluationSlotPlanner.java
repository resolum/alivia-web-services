package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianOccupancy;

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
 * already have or a window another family is holding for them. The family sees every free technician of
 * each window and picks one; when the backend picks instead, the one with fewer visits that day goes,
 * then the lowest id, so the load is spread.</p>
 *
 * <p>Technical orders use the same rules with the window of their {@link ServiceType}: a four-hour
 * installation only fits a block of at least four hours.</p>
 */
public final class EvaluationSlotPlanner {
    public static final Duration WINDOW = Duration.ofHours(2);

    private EvaluationSlotPlanner() {
    }

    /** Two-hour windows that fit whole inside the given shift blocks. */
    public static List<TimeRange> windowsWithin(List<TimeRange> shifts) {
        return windowsWithin(shifts, WINDOW);
    }

    /** Windows of {@code length} that fit whole inside the given shift blocks. */
    public static List<TimeRange> windowsWithin(List<TimeRange> shifts, Duration length) {
        var windows = new ArrayList<TimeRange>();
        for (var shift : shifts) {
            var start = shift.start();
            while (!start.plus(length).isAfter(shift.end()) && start.plus(length).isAfter(start)) {
                windows.add(new TimeRange(start, start.plus(length)));
                start = start.plus(length);
            }
        }
        return windows;
    }

    /**
     * Bookable windows of one date, in time order, once per free technician (least busy first), so the
     * family chooses who visits them.
     */
    public static List<AvailableSlot> plan(LocalDate date, List<TechnicianAvailability> technicians,
                                           List<? extends TechnicianOccupancy> visitsOfTheDay) {
        var windows = new TreeSet<TimeRange>(Comparator.comparing(TimeRange::start));
        technicians.forEach(technician -> windows.addAll(windowsWithin(technician.shifts())));
        var slots = new ArrayList<AvailableSlot>();
        for (var window : windows) {
            var candidates = candidates(window, technicians, visitsOfTheDay);
            for (var technician : candidates) {
                slots.add(new AvailableSlot(date, window, technician.technicianId(), technician.fullName(),
                        candidates.size()));
            }
        }
        return slots;
    }

    /**
     * Blocks of one date for {@code serviceType}, in time order, with how many orders each can still take.
     * The technicians are left out on purpose: the family and the commercial team only see capacity.
     */
    public static List<AvailableBlock> blocks(LocalDate date, ServiceType serviceType,
                                              List<TechnicianAvailability> technicians,
                                              List<? extends TechnicianOccupancy> visitsOfTheDay) {
        var windows = new TreeSet<TimeRange>(Comparator.comparing(TimeRange::start));
        technicians.forEach(technician -> windows.addAll(windowsWithin(technician.shifts(), serviceType.window())));
        var blocks = new ArrayList<AvailableBlock>();
        for (var window : windows) {
            var capacity = candidates(window, technicians, visitsOfTheDay).size();
            if (capacity > 0) blocks.add(new AvailableBlock(new SlotId(date, window), serviceType, capacity));
        }
        return blocks;
    }

    /** Technicians free for the window, least busy first. */
    public static List<TechnicianAvailability> candidates(TimeRange window, List<TechnicianAvailability> technicians,
                                                          List<? extends TechnicianOccupancy> visitsOfTheDay) {
        return technicians.stream()
                .filter(technician -> windowsWithin(technician.shifts(), window.duration()).contains(window))
                .filter(technician -> visitsOfTheDay.stream().noneMatch(visit ->
                        visit.isAttendedBy(technician.technicianId()) && visit.window().overlaps(window)))
                .sorted(Comparator.comparingLong((TechnicianAvailability technician) -> visitsOfTheDay.stream()
                                .filter(visit -> visit.isAttendedBy(technician.technicianId())).count())
                        .thenComparing(TechnicianAvailability::technicianId))
                .toList();
    }
}
