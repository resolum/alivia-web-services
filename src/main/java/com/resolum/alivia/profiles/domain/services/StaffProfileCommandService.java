package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateStaffProfileCommand;

public interface StaffProfileCommandService {
    Long handle(CreateStaffProfileCommand command);

    Long handle(UpdateStaffProfileCommand command);
}
