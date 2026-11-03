package com.resolum.alivia.operations.domain.model.valueobjects;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;

/** Something that keeps a technician busy during a window: a scheduled order or a family's slot hold. */
public interface TechnicianOccupancy {
    boolean isAttendedBy(Long technicianId);

    TimeRange window();
}
