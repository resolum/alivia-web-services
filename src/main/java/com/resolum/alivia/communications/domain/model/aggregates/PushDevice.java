package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidPushToken;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "push_devices")
public class PushDevice extends AuditableAbstractAggregateRoot<PushDevice> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private UserId userId;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private PushPlatform platform;

    protected PushDevice() {
        // JPA required
    }

    public PushDevice(UserId userId, String token, PushPlatform platform) {
        if (userId == null) {
            throw new IllegalArgumentException("User id cannot be null");
        }
        if (token == null || token.isBlank()) {
            throw new InvalidPushToken();
        }
        this.userId = userId;
        this.token = token.trim();
        this.platform = platform != null ? platform : PushPlatform.ANDROID;
    }

    public void reassignTo(UserId userId, PushPlatform platform) {
        if (userId == null) {
            throw new IllegalArgumentException("User id cannot be null");
        }
        this.userId = userId;
        if (platform != null) {
            this.platform = platform;
        }
    }

    public boolean belongsTo(UserId userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}
