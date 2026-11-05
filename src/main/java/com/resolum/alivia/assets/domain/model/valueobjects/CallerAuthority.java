package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * Why the caller may change the devices of a household. Assets applies the inventory and assignment rules; who the
 * caller is for the business (a technician, a worker) belongs to HCM and to the installation or maintenance work it
 * is doing, which belongs to Operations. Assets only needs to know how the caller was authorized.
 */
public enum CallerAuthority {
    /** A member of the household: Assets checks with Profiles that the account manages it. */
    HOUSEHOLD_MEMBER,
    /** The administrator of the business: may act on any household. */
    BUSINESS_ADMINISTRATOR,
    /**
     * Another bounded context (Operations, for the work of its technicians) already decided that the caller may act;
     * Assets does not ask again.
     */
    AUTHORIZED_BY_CONTEXT
}
