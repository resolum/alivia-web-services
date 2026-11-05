package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.domain.model.valueobjects.ContractedActuators;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Optional;

/** Assets' view of Subscriptions: what the household's active subscription includes. */
@Service("assetsExternalSubscriptionsService")
public class ExternalSubscriptionsService {
    private final SubscriptionsContextFacade subscriptionsContextFacade;

    public ExternalSubscriptionsService(SubscriptionsContextFacade subscriptionsContextFacade) {
        this.subscriptionsContextFacade = subscriptionsContextFacade;
    }

    /**
     * Actuators contracted by the active subscription of the household; empty when it has none.
     * Subscriptions identifies households by text, Profiles by number: the number is sent as text.
     */
    public Optional<ContractedActuators> fetchContractedActuators(Long householdId) {
        return subscriptionsContextFacade.fetchContractedActuatorsOfHousehold(String.valueOf(householdId))
                .map(contracted -> {
                    var quantities = new EnumMap<DeviceType, Integer>(DeviceType.class);
                    contracted.quantityByType().forEach((type, quantity) -> {
                        try {
                            quantities.put(DeviceType.valueOf(type), quantity);
                        } catch (IllegalArgumentException unknownType) {
                            // A type this context does not manage cannot be assigned here.
                        }
                    });
                    return new ContractedActuators(quantities);
                });
    }
}
