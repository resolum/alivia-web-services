package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.ActivateAccountCommand;
import com.resolum.alivia.iam.domain.model.commands.InviteCompanyUserCommand;
import com.resolum.alivia.iam.domain.model.queries.GetAccountActivationQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccountActivation;

/** Staff accounts are created by invitation: no password until its owner activates it. */
public interface AccountActivationService {
    User handle(InviteCompanyUserCommand command);
    User handle(ActivateAccountCommand command);
    AccountActivation handle(GetAccountActivationQuery query);
}
