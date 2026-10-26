package com.resolum.alivia.profiles.domain.model.aggregates;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;
import com.resolum.alivia.profiles.domain.model.valueobjects.LegalName;
import com.resolum.alivia.profiles.domain.model.valueobjects.Ruc;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.Photo;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class BusinessProfile extends Profile {

    @Embedded
    private Ruc ruc;

    @Embedded
    private BusinessName businessName;

    @Embedded
    private LegalName legalName;

    private String description;

    private String mainActivity;

    @Embedded
    @AttributeOverride(name = "phoneNumber", column = @Column(name = "business_phone"))
    private PhoneNumber businessPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours")
    private BusinessHours businessHours;

    private String slogan;

    @Embedded
    @AttributeOverride(name = "email", column = @Column(name = "support_email"))
    private EmailAddress supportEmail;

    @Embedded
    private Address address;

    public BusinessProfile(EmailAddress emailAddress, Photo photo, PhoneNumber whatsappNum, Ruc ruc,
            BusinessName businessName, LegalName legalName, String description, String mainActivity,
            PhoneNumber businessPhone, BusinessHours businessHours, String slogan,
            EmailAddress supportEmail, Address address) {
        super(emailAddress, photo, whatsappNum);
        this.ruc = ruc;
        this.businessName = businessName;
        this.legalName = legalName;
        this.description = description;
        this.mainActivity = mainActivity;
        this.businessPhone = businessPhone;
        this.businessHours = businessHours;
        this.slogan = slogan;
        this.supportEmail = supportEmail;
        this.address = address;
    }

    public void updateInformation(BusinessName businessName, LegalName legalName, String description,
            String mainActivity, PhoneNumber businessPhone, BusinessHours businessHours, String slogan,
            EmailAddress supportEmail, Address address) {
        this.businessName = businessName;
        this.legalName = legalName;
        this.description = description;
        this.mainActivity = mainActivity;
        this.businessPhone = businessPhone;
        this.businessHours = businessHours;
        this.slogan = slogan;
        this.supportEmail = supportEmail;
        this.address = address;
    }

    public boolean isOpenAt(DayOfWeek day, LocalTime time) {
        return businessHours.isOpenAt(day, time);
    }
}
