package com.resolum.alivia.nursing.domain.services;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.commands.ChangeMedicationResponsibleCommand;
import com.resolum.alivia.nursing.domain.model.commands.CreateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.SuspendMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.UpdateMedicationScheduleCommand;

/**
 * Domain service for managing the lifecycle of medication schedules.
 */
public interface MedicationScheduleCommandService {

    /**
     * Schedules a new medication for an assisted person.
     *
     * @param command the command
     * @return the created care activity
     */
    CareActivity handle(CreateMedicationScheduleCommand command);

    /**
     * Updates an existing medication schedule with a new indication or timing.
     *
     * @param command the command
     * @return the updated care activity
     */
    CareActivity handle(UpdateMedicationScheduleCommand command);

    /**
     * Suspends a scheduled medication.
     *
     * @param command the command
     * @return the suspended care activity
     */
    CareActivity handle(SuspendMedicationScheduleCommand command);

    /**
     * Changes the caregiver responsible for administering the medication.
     *
     * @param command the command
     * @return the updated care activity
     */
    CareActivity handle(ChangeMedicationResponsibleCommand command);
}
