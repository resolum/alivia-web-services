package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.services.RoleParser;
import com.resolum.alivia.iam.domain.model.valueobjects.UserAccount;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.interfaces.rest.resources.UserAccountResource;

public final class UserAccountResourceFromEntityAssembler {

    private UserAccountResourceFromEntityAssembler() {
    }

    public static UserAccountResource toResourceFromEntity(UserAccount account) {
        var user = account.user();
        var status = user.getStatus();
        // The front knows a single "not allowed in" state: blocked and inactive accounts both show as suspended.
        var apiStatus = status == UserStatus.ACTIVE || status == UserStatus.PENDING_VERIFICATION
                ? status.name() : "SUSPENDED";
        return new UserAccountResource(user.getAccountId(), user.getEmail().email(), account.fullName(), apiStatus,
                user.getRoles().stream().map(RoleParser::toApiName).sorted().toList());
    }
}
