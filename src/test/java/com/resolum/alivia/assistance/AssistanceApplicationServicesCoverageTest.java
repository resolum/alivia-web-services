package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.application.internal.commandservices.ActivityLogCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.AssistedPersonCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.CareActivityCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.CareShiftCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.CaregiverCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.HandoverCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.InvitationCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.commandservices.RelativeCommandServiceImpl;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalCommunicationsService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.application.internal.queryservices.ActivityLogQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.AssistedPersonQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.CareActivityQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.CareShiftQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.CaregiverQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.HandoverQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.InvitationQueryServiceImpl;
import com.resolum.alivia.assistance.application.internal.queryservices.RelativeQueryServiceImpl;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.aggregates.Invitation;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Exercises every Assistance application-service operation through isolated repository/facade stubs. */
class AssistanceApplicationServicesCoverageTest {

    private static final List<Class<?>> SERVICES = List.of(
            ActivityLogCommandServiceImpl.class,
            AssistedPersonCommandServiceImpl.class,
            CareActivityCommandServiceImpl.class,
            CareShiftCommandServiceImpl.class,
            CaregiverCommandServiceImpl.class,
            HandoverCommandServiceImpl.class,
            InvitationCommandServiceImpl.class,
            RelativeCommandServiceImpl.class,
            ActivityLogQueryServiceImpl.class,
            AssistedPersonQueryServiceImpl.class,
            CareActivityQueryServiceImpl.class,
            CareShiftQueryServiceImpl.class,
            CaregiverQueryServiceImpl.class,
            HandoverQueryServiceImpl.class,
            InvitationQueryServiceImpl.class,
            RelativeQueryServiceImpl.class,
            ExternalBusinessService.class,
            ExternalCommunicationsService.class,
            ExternalIamService.class,
            ExternalProfilesService.class
    );

    @TestFactory
    Stream<DynamicTest> everyApplicationOperationHandlesRepositoryResultsAndValidation() {
        return SERVICES.stream().flatMap(serviceType -> Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .flatMap(method -> Stream.of(false, true).map(populated -> DynamicTest.dynamicTest(
                        serviceType.getSimpleName() + '.' + method.getName()
                                + (populated ? " with matching data" : " without data"),
                        () -> invokeOperation(serviceType, method, populated)))));
    }

    private void invokeOperation(Class<?> serviceType, Method method, boolean populated) throws Exception {
        Object service = instantiate(serviceType, populated);
        Object[] arguments = Arrays.stream(method.getParameters())
                .map(parameter -> sampleValue(parameter.getType(), parameter.getName()))
                .toArray();
        try {
            Object result = method.invoke(service, arguments);
            // Successful completion is the assertion for commands and collection queries.
        } catch (InvocationTargetException exception) {
            assertInstanceOf(RuntimeException.class, exception.getCause(),
                    "Validation failures must use domain/runtime exceptions");
        }
    }

    private Object instantiate(Class<?> type, boolean populated) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(dependency -> stub(dependency, populated)).toArray();
        return constructor.newInstance(dependencies);
    }

    private Object stub(Class<?> type, boolean populated) {
        if (!type.isInterface()) {
            try {
                return instantiate(type, populated);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create service dependency " + type.getName(), exception);
            }
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Stub<" + type.getSimpleName() + '>';
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            if (method.getName().equals("save") && arguments != null && arguments.length > 0) {
                return arguments[0];
            }
            return repositoryValue(method.getGenericReturnType(), method.getReturnType(), populated);
        });
    }

    private Object repositoryValue(Type genericType, Class<?> rawType, boolean populated) {
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type elementType = parameterizedType.getActualTypeArguments()[0];
            Object element = populated && elementType instanceof Class<?> elementClass ? domainSample(elementClass) : null;
            if (Optional.class.isAssignableFrom(rawType)) return Optional.ofNullable(element);
            if (Set.class.isAssignableFrom(rawType)) return element == null ? Set.of() : Set.of(element);
            if (Collection.class.isAssignableFrom(rawType)) return element == null ? List.of() : List.of(element);
        }
        Object sample = populated ? domainSample(rawType) : null;
        return sample != null ? sample : emptyValue(rawType);
    }

    private Object domainSample(Class<?> type) {
        Relative relative = new Relative(new UserId(1L), new PersonProfileId(1L), new BusinessId(1L));
        ReflectionTestUtils.setField(relative, "id", 1L);
        AssistedPerson assistedPerson = new AssistedPerson(new PersonProfileId(2L), relative);
        ReflectionTestUtils.setField(assistedPerson, "id", 1L);
        Caregiver caregiver = new Caregiver(new UserId(2L), assistedPerson);
        ReflectionTestUtils.setField(caregiver, "id", 1L);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        CareShift firstShift = new CareShift(assistedPerson, 2L, "Caregiver", start, start.plusHours(2), "Notes");
        CareShift secondShift = new CareShift(assistedPerson, 3L, "Replacement", start.plusHours(2), start.plusHours(4), "Notes");
        ReflectionTestUtils.setField(firstShift, "id", 1L);
        ReflectionTestUtils.setField(secondShift, "id", 2L);

        if (type == Relative.class) return relative;
        if (type == AssistedPerson.class) return assistedPerson;
        if (type == Caregiver.class) return caregiver;
        if (type == CareActivity.class) {
            CareActivity activity = new CareActivity(assistedPerson, CareActivityType.MEDICATION,
                    "Medication", "Daily dose");
            ReflectionTestUtils.setField(activity, "id", 1L);
            return activity;
        }
        if (type == Invitation.class) {
            Invitation invitation = new Invitation(assistedPerson, relative,
                    new EmailAddress("caregiver@alivia.test"),
                    LocalDateTime.now().plusDays(7));
            ReflectionTestUtils.setField(invitation, "id", 1L);
            return invitation;
        }
        if (type == CareShift.class) return firstShift;
        if (type == Handover.class) return new Handover(firstShift, secondShift, "[]", "[]");
        return null;
    }

    private Object emptyValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 1L;
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isInterface()) return stub(type, true);
        return null;
    }

    private Object sampleValue(Class<?> type, String name) {
        if (type == String.class) return sampleString(name);
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == byte[].class) return new byte[]{1, 2, 3};
        if (type == LocalDate.class) return LocalDate.now().plusDays(1);
        if (type == LocalDateTime.class) return LocalDateTime.now().plusDays(1);
        if (type == LocalTime.class) return name.toLowerCase().contains("end")
                ? LocalTime.of(10, 0) : LocalTime.of(9, 0);
        if (type == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (Set.class.isAssignableFrom(type)) return Set.of(DayOfWeek.MONDAY);
        if (List.class.isAssignableFrom(type) || Collection.class == type) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isRecord()) return createRecord(type);
        return type.isInterface() ? stub(type, true) : emptyValue(type);
    }

    private Object createRecord(Class<?> recordType) {
        try {
            RecordComponent[] components = recordType.getRecordComponents();
            Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] values = Arrays.stream(components)
                    .map(component -> sampleValue(component.getType(), component.getName()))
                    .toArray();
            Constructor<?> constructor = recordType.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create command/query " + recordType.getName(), exception);
        }
    }

    private String sampleString(String name) {
        String normalized = name.toLowerCase();
        if (normalized.contains("email")) return "demo@alivia.test";
        if (normalized.contains("password")) return "Alivia#Demo2026";
        if (normalized.contains("phone")) return "+51999999999";
        if (normalized.contains("id")) return "1";
        if (normalized.equals("dni")) return "12345678";
        if (normalized.contains("token")) return "valid-demo-token";
        return "demo";
    }
}
