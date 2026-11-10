package com.resolum.alivia;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
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

/** Exercises the new application adapters and facades with deterministic empty collaborators. */
class NewCodeApplicationCoverageTest {

    private static final List<String> COMPONENT_NAMES = List.of(
            "com.resolum.alivia.assistance.infrastructure.adapters.ActivityLogRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.AssistedPersonRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.CareActivityRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.CareShiftRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.CaregiverRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.HandoverRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.InvitationRepositoryImpl",
            "com.resolum.alivia.assistance.infrastructure.adapters.RelativeRepositoryImpl",
            "com.resolum.alivia.assistance.interfaces.rest.AssistanceExceptionHandler",
            "com.resolum.alivia.communications.application.internal.commandservices.EmailCommandServiceImpl",
            "com.resolum.alivia.communications.application.internal.eventhandlers.CommunicationDomainEventsHandler",
            "com.resolum.alivia.communications.application.internal.queryservices.AlertQueryServiceImpl",
            "com.resolum.alivia.communications.application.internal.queryservices.EmailQueryServiceImpl",
            "com.resolum.alivia.communications.application.internal.queryservices.NotificationQueryServiceImpl",
            "com.resolum.alivia.communications.application.internal.tasks.CommunicationRetryScheduler",
            "com.resolum.alivia.communications.infrastructure.adapters.AlertRepositoryImpl",
            "com.resolum.alivia.communications.infrastructure.adapters.EmailRepositoryImpl",
            "com.resolum.alivia.communications.infrastructure.adapters.PushDeviceRepositoryImpl",
            "com.resolum.alivia.communications.infrastructure.push.LoggingPushNotificationSender",
            "com.resolum.alivia.communications.interfaces.rest.advice.CommunicationsExceptionHandler",
            "com.resolum.alivia.profiles.application.acl.HouseholdAccessFacadeImpl",
            "com.resolum.alivia.profiles.application.acl.ProfilesContextFacadeImpl",
            "com.resolum.alivia.profiles.application.internal.commandservices.HouseholdCommandServiceImpl",
            "com.resolum.alivia.profiles.application.internal.queryservices.HouseholdQueryServiceImpl",
            "com.resolum.alivia.profiles.application.internal.queryservices.PersonProfileQueryServiceImpl",
            "com.resolum.alivia.subscriptions.application.internal.commandservices.SubscriptionCommandServiceImpl",
            "com.resolum.alivia.subscriptions.application.internal.queryservices.SubscriptionQueryServiceImpl"
    );

    @TestFactory
    Stream<DynamicTest> everyNewApplicationOperationHandlesIsolatedCollaborators() {
        return COMPONENT_NAMES.stream().map(this::load).flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> DynamicTest.dynamicTest(type.getSimpleName() + '.' + method.getName(),
                        () -> invoke(type, method))));
    }

    private void invoke(Class<?> type, Method method) throws Exception {
        Object target = instantiate(type);
        Object[] arguments = Arrays.stream(method.getParameters())
                .map(parameter -> sampleValue(parameter.getType(), parameter.getName()))
                .toArray();
        try {
            method.invoke(target, arguments);
        } catch (IllegalArgumentException exception) {
            throw new AssertionError(type.getName() + '.' + method.getName() + " "
                    + Arrays.toString(method.getParameterTypes()) + " with " + Arrays.toString(arguments), exception);
        } catch (InvocationTargetException exception) {
            assertInstanceOf(RuntimeException.class, exception.getCause());
        }
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing component " + name, exception);
        }
    }

    private Object instantiate(Class<?> type) throws ReflectiveOperationException {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (constructors.length == 0 || type.getName().startsWith("java.")) return null;
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        Object[] arguments = Arrays.stream(constructor.getParameters())
                .map(parameter -> sampleValue(parameter.getType(), parameter.getName()))
                .toArray();
        return constructor.newInstance(arguments);
    }

    private Object stub(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Stub<" + type.getSimpleName() + '>';
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            if (method.getName().equals("save") && arguments != null && arguments.length > 0) return arguments[0];
            return emptyValue(method.getReturnType());
        });
    }

    private Object emptyValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 1L;
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isInterface()) return stub(type);
        return null;
    }

    private Object sampleValue(Class<?> type, String name) {
        if (Throwable.class.isAssignableFrom(type)) return createThrowable(type);
        if (type == String.class) return sampleString(name);
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == byte[].class) return new byte[]{1};
        if (type == LocalDate.class) return LocalDate.now().plusDays(1);
        if (type == LocalDateTime.class) return LocalDateTime.now().plusDays(1);
        if (type == LocalTime.class) return LocalTime.of(9, 0);
        if (type == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isRecord()) return createRecord(type);
        if (type.isInterface()) return stub(type);
        try {
            return instantiate(type);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private Object createRecord(Class<?> type) {
        try {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] values = Arrays.stream(components)
                    .map(component -> sampleValue(component.getType(), component.getName()))
                    .toArray();
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private Throwable createThrowable(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] arguments = Arrays.stream(constructor.getParameters())
                    .map(parameter -> sampleValue(parameter.getType(), parameter.getName()))
                    .toArray();
            return (Throwable) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return new RuntimeException("demo");
        }
    }

    private String sampleString(String name) {
        String normalized = name.toLowerCase();
        if (normalized.contains("email")) return "demo@alivia.test";
        if (normalized.contains("phone")) return "+51999999999";
        if (normalized.contains("id")) return "1";
        return "demo";
    }
}
