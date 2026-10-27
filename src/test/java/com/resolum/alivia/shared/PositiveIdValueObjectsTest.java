package com.resolum.alivia.shared;


import com.resolum.alivia.shared.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cubre todos los value objects que envuelven un Long positivo:
 * BusinessId, DeviceId, EdgeId, EmployeeId, InstallationId, PersonProfileId, StaffProfileId y UserId.
 */
class PositiveIdValueObjectsTest {

    record IdCase(String name, Function<Long, Object> factory, Function<Object, Long> accessor, String message) {
        @SuppressWarnings("unchecked")
        static <T> IdCase of(String name, Function<Long, T> factory, Function<T, Long> accessor, String message) {
            return new IdCase(name, factory::apply, o -> accessor.apply((T) o), message);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<IdCase> cases() {
        return Stream.of(
                IdCase.of("BusinessId", BusinessId::new, BusinessId::businessId,
                        "Business ID must be a positive number."),
                IdCase.of("DeviceId", DeviceId::new, DeviceId::deviceId,
                        "Device ID must be a positive number."),
                IdCase.of("EdgeId", EdgeId::new, EdgeId::edgeId,
                        "Edge ID must be a positive number."),
                IdCase.of("EmployeeId", EmployeeId::new, EmployeeId::employeeId,
                        "Employee ID must be a positive number."),
                IdCase.of("InstallationId", InstallationId::new, InstallationId::installationId,
                        "Installation ID must be a positive number."),
                IdCase.of("PersonProfileId", PersonProfileId::new, PersonProfileId::personProfileId,
                        "person profile id cannot be null"),
                IdCase.of("StaffProfileId", StaffProfileId::new, StaffProfileId::staffProfileId,
                        "Staff profile ID must be a positive number."),
                IdCase.of("UserId", UserId::new, UserId::userId,
                        "user id cannot be null or less than or equal to zero")
        );
    }

    static Stream<Arguments> nonPositiveValues() {
        return cases().flatMap(c -> Stream.of(0L, -1L, Long.MIN_VALUE).map(v -> Arguments.of(c, v)));
    }

    static Stream<Arguments> positiveValues() {
        return cases().flatMap(c -> Stream.of(1L, 42L, Long.MAX_VALUE).map(v -> Arguments.of(c, v)));
    }

    @ParameterizedTest(name = "{0} rechaza null")
    @MethodSource("cases")
    void rejectsNull(IdCase idCase) {
        var ex = assertThrows(IllegalArgumentException.class, () -> idCase.factory().apply(null));

        assertEquals(idCase.message(), ex.getMessage());
    }

    @ParameterizedTest(name = "{0} rechaza {1}")
    @MethodSource("nonPositiveValues")
    void rejectsZeroAndNegativeValues(IdCase idCase, Long value) {
        var ex = assertThrows(IllegalArgumentException.class, () -> idCase.factory().apply(value));

        assertEquals(idCase.message(), ex.getMessage());
    }

    @ParameterizedTest(name = "{0} acepta {1}")
    @MethodSource("positiveValues")
    void acceptsPositiveValuesAndExposesThem(IdCase idCase, Long value) {
        Object id = assertDoesNotThrow(() -> idCase.factory().apply(value));

        assertEquals(value, idCase.accessor().apply(id));
    }

    @ParameterizedTest(name = "{0} compara por valor")
    @MethodSource("cases")
    void equalityIsBasedOnValue(IdCase idCase) {
        Object a = idCase.factory().apply(5L);
        Object b = idCase.factory().apply(5L);
        Object other = idCase.factory().apply(6L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, other);
    }

    @Test
    void differentIdTypesWithSameValueAreNotEqual() {
        assertNotEquals(new UserId(1L), new DeviceId(1L));
    }
}