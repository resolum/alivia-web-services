-- V11: the network of an installation has no "pending confirmation" status (AP-376 follow-up).
--
-- V10 added PENDING_CONFIRMATION, thinking that Assets had to know whether the technicians had confirmed the
-- installation. That work (who does it, when it is confirmed) belongs to Operations, and the workers to HCM; Assets
-- only keeps the inventory and the assignment of devices. This migration returns the status check and the
-- "one network per household" rule to what they were before: ACTIVE, DEACTIVATED or DISMANTLED, and one ACTIVE network
-- per household.
--
-- V10 is already recorded in the history, so it is not edited. A network that was still pending becomes ACTIVE: it
-- was already the only open network of its household, so the unique index keeps holding. The removal of the unused
-- device columns made by V10 (network_actuators, microphone_id) stays.

UPDATE device_networks
    SET network_status = 'ACTIVE'
    WHERE network_status = 'PENDING_CONFIRMATION';

ALTER TABLE device_networks
    DROP CONSTRAINT IF EXISTS device_networks_network_status_check;

ALTER TABLE device_networks
    ADD CONSTRAINT device_networks_network_status_check
        CHECK (network_status IN ('ACTIVE', 'DEACTIVATED', 'DISMANTLED'));

DROP INDEX IF EXISTS uk_device_networks_open_household;

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_networks_active_household
    ON device_networks (household_id)
    WHERE network_status = 'ACTIVE';
