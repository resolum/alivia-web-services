package com.resolum.alivia.iam.application.internal.queryservices;

import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.iam.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.queries.GetUserByAccountIdQuery;
import com.resolum.alivia.iam.domain.model.queries.GetUsersByBusinessIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.UserAccount;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;
    private final ExternalHcmService externalHcmService;
    private final ExternalProfilesService externalProfilesService;

    public UserQueryServiceImpl(UserRepository userRepository, ExternalHcmService externalHcmService,
                                ExternalProfilesService externalProfilesService) {
        this.userRepository = userRepository;
        this.externalHcmService = externalHcmService;
        this.externalProfilesService = externalProfilesService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> handle(GetUserByAccountIdQuery query) {
        return userRepository.findByAccountId(query.accountId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> handle(GetUsersByBusinessIdQuery query) {
        // shortcut: one profile lookup per user, fine for a business's staff; batch it if lists grow to thousands.
        return userRepository.findAllByIds(externalHcmService.fetchUserIdsOfBusiness(query.businessId())).stream()
                .sorted(Comparator.comparing(User::getId))
                .map(user -> new UserAccount(user,
                        externalProfilesService.fetchStaffFullName(user.getProfileId()).orElse(null)))
                .toList();
    }
}
