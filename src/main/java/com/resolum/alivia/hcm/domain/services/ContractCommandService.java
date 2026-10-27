package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.RenewContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.TerminateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;

public interface ContractCommandService {
    Contract handle(CreateContractCommand command);
    Contract handle(UpdateContractCommand command);
    Contract handle(RenewContractCommand command);
    Contract handle(TerminateContractCommand command);
}
