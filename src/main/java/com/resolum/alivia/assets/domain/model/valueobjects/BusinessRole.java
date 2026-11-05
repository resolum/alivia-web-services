package com.resolum.alivia.assets.domain.model.valueobjects;

/**
 * What the account that changes the devices of an installation is to the business (Alivia). A household member
 * has no role in the business: its permission comes from managing the household.
 */
public enum BusinessRole {
    NONE,
    TECHNICIAN,
    ADMINISTRATOR
}
