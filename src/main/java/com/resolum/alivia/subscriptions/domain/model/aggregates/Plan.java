package com.resolum.alivia.subscriptions.domain.model.aggregates;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidPlan;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PriceQualifier;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OrderColumn;
import lombok.AccessLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 10)
    private PlanCode code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanStatus status;

    @Column(name = "base_price_amount", nullable = false, precision = 10, scale = 2)
    @Getter(AccessLevel.NONE)
    private BigDecimal basePriceAmount;

    @Column(nullable = false, length = 3)
    @Getter(AccessLevel.NONE)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_qualifier", nullable = false, length = 20)
    private PriceQualifier priceQualifier;

    @Column(name = "additional_room_monthly_amount", precision = 10, scale = 2)
    @Getter(AccessLevel.NONE)
    private BigDecimal additionalRoomMonthlyAmount;

    @Column(name = "minimum_rooms", nullable = false)
    private int minimumRooms;

    @Column(name = "min_actuators", nullable = false)
    private int minActuators;

    @Column(name = "max_actuators", nullable = false)
    private int maxActuators;

    @Column(name = "included_actuators", nullable = false)
    private int includedActuators;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(joinColumns = @JoinColumn(name = "plan_id"))
    @MapKeyColumn(name = "device_type", length = 10)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "monthly_price_amount", nullable = false, precision = 10, scale = 2)
    private Map<ActuatorType, BigDecimal> deviceMonthlyPrices = new EnumMap<>(ActuatorType.class);

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "feature", nullable = false, length = 300)
    private List<String> features = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "device", nullable = false, length = 80)
    private List<String> includedDevices = new ArrayList<>();

    @Column(name = "stripe_base_price_id", length = 100)
    @Getter(AccessLevel.NONE)
    private String stripeBasePriceId;

    @Column(name = "stripe_actuator_price_id", length = 100)
    @Getter(AccessLevel.NONE)
    private String stripeActuatorPriceId;

    protected Plan() {
    }

    public Plan(PlanCode code, String name, String description, Money basePrice,
                PriceQualifier priceQualifier, Money additionalRoomPrice, int minimumRooms,
                int minActuators, int maxActuators, int includedActuators,
                Map<ActuatorType, Money> devicePrices, List<String> features,
                List<String> includedDevices) {
        if (code == null || name == null || name.isBlank() || description == null || description.isBlank()
                || basePrice == null || priceQualifier == null || devicePrices == null
                || features == null || includedDevices == null || minimumRooms < 1
                || minActuators < 1 || maxActuators < minActuators || maxActuators > 4
                || includedActuators < 0 || includedActuators > minActuators) {
            throw new InvalidPlan("Los datos o límites del plan son inválidos.");
        }
        if (additionalRoomPrice != null && !basePrice.currency().equals(additionalRoomPrice.currency())) {
            throw new InvalidPlan("El recargo por habitación debe estar en PEN.");
        }
        if (devicePrices.values().stream().anyMatch(price -> price == null || !basePrice.currency().equals(price.currency()))) {
            throw new InvalidPlan("Las tarifas de dispositivos deben estar en PEN.");
        }
        if (code == PlanCode.MINIMUM && (minimumRooms != 1 || minActuators != 1 || maxActuators != 1
                || includedActuators != 1 || additionalRoomPrice != null)) {
            throw new InvalidPlan("El Plan Base permite una habitación y un actuador incluido.");
        }
        if (code == PlanCode.CUSTOM && (minimumRooms < 2 || includedActuators != 0 || additionalRoomPrice == null
                || devicePrices.size() != ActuatorType.values().length)) {
            throw new InvalidPlan("El Plan Personalizado requiere recargo por habitación y tarifas por actuador.");
        }
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = PlanStatus.ACTIVE;
        this.basePriceAmount = basePrice.amount();
        this.currency = basePrice.currency();
        this.priceQualifier = priceQualifier;
        this.additionalRoomMonthlyAmount = additionalRoomPrice == null ? null : additionalRoomPrice.amount();
        this.minimumRooms = minimumRooms;
        this.minActuators = minActuators;
        this.maxActuators = maxActuators;
        this.includedActuators = includedActuators;
        this.deviceMonthlyPrices = new EnumMap<>(ActuatorType.class);
        devicePrices.forEach((type, price) -> this.deviceMonthlyPrices.put(type, price.amount()));
        this.features = new ArrayList<>(features);
        this.includedDevices = new ArrayList<>(includedDevices);
    }

    public boolean allows(ActuatorSelection selection) {
        return selection != null && selection.total() >= minActuators && selection.total() <= maxActuators;
    }

    public Money monthlyPriceFor(ActuatorSelection selection) {
        return new Money(quoteMonthly(minimumRooms, selection).totalMonthlyAmount(), currency);
    }

    public PlanQuote quoteMonthly(int rooms, ActuatorSelection selection) {
        if (rooms < minimumRooms || (code == PlanCode.MINIMUM && rooms != 1)) {
            throw new InvalidActuatorSelection("La cantidad de habitaciones no es válida para este plan.");
        }
        if (!allows(selection)) {
            throw new InvalidActuatorSelection("El plan permite entre " + minActuators + " y " + maxActuators + " actuador(es).");
        }
        Money base = getBasePrice();
        Money roomsAmount = new Money(BigDecimal.ZERO, currency);
        if (additionalRoomMonthlyAmount != null) {
            roomsAmount = new Money(additionalRoomMonthlyAmount, currency).times(rooms - minimumRooms);
        }
        Money devicesAmount = new Money(BigDecimal.ZERO, currency);
        var quantities = new EnumMap<ActuatorType, Integer>(ActuatorType.class);
        for (ActuatorQuantity item : selection.items()) {
            quantities.put(item.type(), item.quantity());
            if (code == PlanCode.CUSTOM) {
                BigDecimal unitPrice = deviceMonthlyPrices.get(item.type());
                if (unitPrice == null) {
                    throw new InvalidActuatorSelection("El tipo de actuador no está disponible.");
                }
                devicesAmount = devicesAmount.plus(new Money(unitPrice, currency).times(item.quantity()));
            }
        }
        return new PlanQuote(code, currency, rooms, Map.copyOf(quantities), base.amount(),
                roomsAmount.amount(), devicesAmount.amount(),
                base.plus(roomsAmount).plus(devicesAmount).amount());
    }

    public Money minimumMonthlyPrice() {
        if (code == PlanCode.MINIMUM) return getBasePrice();
        BigDecimal cheapest = deviceMonthlyPrices.values().stream().min(BigDecimal::compareTo)
                .orElseThrow(() -> new InvalidPlan("El plan no tiene tarifas de actuadores."));
        return getBasePrice().plus(new Money(cheapest, currency).times(minActuators));
    }

    public Money getBasePrice() { return new Money(basePriceAmount, currency); }
    public Money getAdditionalRoomPrice() { return additionalRoomMonthlyAmount == null ? null : new Money(additionalRoomMonthlyAmount, currency); }
    public Map<ActuatorType, BigDecimal> getDeviceMonthlyPrices() { return Map.copyOf(deviceMonthlyPrices); }
    public List<String> getFeatures() { return List.copyOf(features); }
    public List<String> getIncludedDevices() { return List.copyOf(includedDevices); }
}
