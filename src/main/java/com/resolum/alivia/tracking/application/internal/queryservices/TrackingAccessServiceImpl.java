package com.resolum.alivia.tracking.application.internal.queryservices;

import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssistanceService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link TrackingAccessService}.
 *
 * <p>The care network of a person is read in Assistance, which is where the relative in charge and the caregivers are
 * (caregivers are not members of the household in Profiles). The membership of the household is added on top, not
 * instead, so a member who is not in any care network can still read the home.</p>
 */
@Service
public class TrackingAccessServiceImpl implements TrackingAccessService {

    private final ExternalAssistanceService assistance;
    private final ExternalProfilesService profiles;
    private final ExternalAssetsService assets;

    public TrackingAccessServiceImpl(ExternalAssistanceService assistance, ExternalProfilesService profiles,
                                     ExternalAssetsService assets) {
        this.assistance = assistance;
        this.profiles = profiles;
        this.assets = assets;
    }

    @Override
    public Long requireReadableHouseholdOfPerson(Long userId, Long assistedPersonId) {
        if (!assistance.canReadPerson(assistedPersonId, userId)) {
            throw new AssistedPersonNotAccessible(assistedPersonId);
        }
        return assistance.fetchHouseholdOfPerson(assistedPersonId)
                .orElseThrow(() -> new AssistedPersonWithoutHousehold(assistedPersonId));
    }

    @Override
    public Long requireReadableHouseholdOfDevice(Long userId, Long deviceId) {
        var householdId = assets.fetchHouseholdOfDevice(deviceId).orElseThrow(() -> new DeviceNotAccessible(deviceId));
        if (!assistance.canReadHousehold(householdId, userId) && !profiles.canReadHousehold(householdId, userId)) {
            throw new DeviceNotAccessible(deviceId);
        }
        return householdId;
    }
}
