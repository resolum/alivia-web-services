package com.resolum.alivia.business.domain.model.aggregates;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
public class Administrator extends AuditableAbstractAggregateRoot<Administrator> {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private UserId userId;

    @Embedded
    @AttributeOverride(name = "staffProfileId", column = @Column(name = "staff_profile_id", nullable = false))
    private StaffProfileId staffProfileId;

    @Embedded
    @AttributeOverride(name = "email", column = @Column(name = "backup_personal_email"))
    private EmailAddress backupPersonalEmail;

    /**
     * Required by JPA.
     */
    protected Administrator() {
    }

    public Administrator(UserId userId, StaffProfileId staffProfileId, EmailAddress backupPersonalEmail) {
        if (userId == null) {
            throw new IllegalArgumentException("User identifier is required.");
        }
        if (staffProfileId == null) {
            throw new IllegalArgumentException("Staff profile identifier is required.");
        }
        this.userId = userId;
        this.staffProfileId = staffProfileId;
        this.backupPersonalEmail = backupPersonalEmail;
    }

    public void updateBackupPersonalEmail(EmailAddress backupPersonalEmail) {
        this.backupPersonalEmail = backupPersonalEmail;
    }
}
