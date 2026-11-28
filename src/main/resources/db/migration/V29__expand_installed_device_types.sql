ALTER TABLE appointment_installed_devices
    DROP CONSTRAINT IF EXISTS appointment_installed_devices_device_type_check;

ALTER TABLE appointment_installed_devices
    ADD CONSTRAINT appointment_installed_devices_device_type_check
        CHECK (device_type IN (
            'MICROPHONE_EDGE',
            'ACTUATOR_EDGE',
            'MICROPHONE',
            'DOOR',
            'WINDOW',
            'CURTAIN',
            'LIGHT',
            'ACTUATOR',
            'SENSOR',
            'HUB'
        ));
