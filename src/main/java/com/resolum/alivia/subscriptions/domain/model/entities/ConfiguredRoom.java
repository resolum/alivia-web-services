package com.resolum.alivia.subscriptions.domain.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class ConfiguredRoom {
    @Column(name = "room_key", nullable = false, length = 60)
    private String key;
    @Column(name = "room_name", nullable = false, length = 80)
    private String name;
    @Column(name = "light_quantity", nullable = false)
    private int lights;
    @Column(name = "door_quantity", nullable = false)
    private int doors;
    @Column(name = "window_quantity", nullable = false)
    private int windows;
    @Column(name = "curtain_quantity", nullable = false)
    private int curtains;
    @Column(name = "microphone_quantity", nullable = false)
    private int microphones;

    protected ConfiguredRoom() {
    }

    public ConfiguredRoom(String key, String name, int lights, int doors, int windows,
                          int curtains, int microphones) {
        if (key == null || key.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("La habitación requiere identificador y nombre.");
        }
        if (lights < 0 || doors < 0 || windows < 0 || curtains < 0 || microphones != 1) {
            throw new IllegalArgumentException("Las cantidades de dispositivos de la habitación son inválidas.");
        }
        this.key = key.trim();
        this.name = name.trim();
        this.lights = lights;
        this.doors = doors;
        this.windows = windows;
        this.curtains = curtains;
        this.microphones = microphones;
    }

    public int actuatorCount() {
        return lights + doors + windows + curtains;
    }
}
