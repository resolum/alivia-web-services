-- V10: an installation is confirmed by the technicians of the business, and the network keeps no device columns
-- (AP-376 follow-up).
--
-- 1. The network of an installation now starts PENDING_CONFIRMATION, while the technicians place the devices, and
--    becomes ACTIVE when they confirm it. The status check accepts the new value, and the rule "one network per
--    household" now covers both open statuses: a household cannot have a pending and an active network at once.
--
-- 2. The devices of an installation are the ones with an active assignment to it (device_assignments), whatever their
--    type. The columns of the first model that were never filled are removed: device_networks.microphone_id and the
--    network_actuators join table. The edge columns (microphone_edge, actuator_id) stay: they point to the edge
--    services that receive the synchronization of the household.
--
-- Nothing in the application ever wrote to the removed objects, so no data is lost in a database built by these
-- migrations. IF EXISTS keeps databases where Hibernate created a different shape from failing.

ALTER TABLE device_networks
    DROP CONSTRAINT IF EXISTS device_networks_network_status_check;

ALTER TABLE device_networks
    ADD CONSTRAINT device_networks_network_status_check
        CHECK (network_status IN ('PENDING_CONFIRMATION', 'ACTIVE', 'DEACTIVATED', 'DISMANTLED'));

DROP INDEX IF EXISTS uk_device_networks_active_household;

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_networks_open_household
    ON device_networks (household_id)
    WHERE network_status IN ('PENDING_CONFIRMATION', 'ACTIVE');

DROP TABLE IF EXISTS network_actuators;

ALTER TABLE device_networks
    DROP COLUMN IF EXISTS microphone_id;
