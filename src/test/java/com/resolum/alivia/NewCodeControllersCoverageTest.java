package com.resolum.alivia;

import com.resolum.alivia.communications.interfaces.rest.controllers.AlertsController;
import com.resolum.alivia.communications.interfaces.rest.controllers.EmailsController;
import com.resolum.alivia.communications.interfaces.rest.controllers.NotificationsController;
import com.resolum.alivia.communications.interfaces.rest.controllers.PushDevicesController;
import com.resolum.alivia.profiles.interfaces.rest.controllers.HouseholdsController;
import com.resolum.alivia.profiles.interfaces.rest.controllers.PersonProfilesController;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.SubscriptionsController;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.security.Principal;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for new endpoints outside Assistance that are part of this pull request. */
class NewCodeControllersCoverageTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AlertsController.class,
            EmailsController.class,
            NotificationsController.class,
            PushDevicesController.class,
            HouseholdsController.class,
            PersonProfilesController.class,
            SubscriptionsController.class
    );

    @TestFactory
    Stream<DynamicTest> everyEndpointHandlesCompleteAndOptionalInputs() {
        return CONTROLLERS.stream().flatMap(controllerType -> Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .flatMap(method -> Stream.of(false, true).map(fallback -> DynamicTest.dynamicTest(
                        controllerType.getSimpleName() + '.' + method.getName()
                                + (fallback ? "[fallback]" : "[complete]"),
                        () -> invoke(controllerType, method, fallback)))));
    }

    private void invoke(Class<?> controllerType, Method method, boolean fallback) throws Exception {
        Object controller = instantiate(controllerType);
        Object[] arguments = Arrays.stream(method.getParameters()).map(parameter -> {
            if (fallback && parameter.getType() == Principal.class) return null;
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (fallback && requestParam != null && !requestParam.required()) return null;
            return sampleValue(parameter.getType(), parameter.getName());
        }).toArray();

        try {
            Object result = method.invoke(controller, arguments);
            assertNotNull(result);
            if (ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
                assertTrue(result instanceof ResponseEntity<?>);
            }
        } catch (InvocationTargetException exception) {
            assertTrue(exception.getCause() instanceof RuntimeException,
                    "Only validation/domain failures are accepted from isolated endpoints");
        }
    }

    private Object instantiate(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(this::stub).toArray();
        return constructor.newInstance(arguments);
    }

    private Object stub(Class<?> type) {
        if (!type.isInterface()) {
            try {
                return instantiate(type);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot instantiate dependency " + type.getName(), exception);
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
        if (type == String.class) return sampleString(name);
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == byte[].class) return new byte[]{1, 2, 3};
        if (type == LocalDate.class) return LocalDate.now().plusDays(1);
        if (type == LocalDateTime.class) return LocalDateTime.now().plusDays(1);
        if (type == LocalTime.class) return LocalTime.of(9, 0);
        if (type == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (type == Principal.class) return (Principal) () -> "1";
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isRecord()) return createRecord(type);
        return type.isInterface() ? stub(type) : emptyValue(type);
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
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create request " + type.getName(), exception);
        }
    }

    private String sampleString(String name) {
        String normalized = name.toLowerCase();
        if (normalized.contains("email")) return "demo@alivia.test";
        if (normalized.contains("phone")) return "+51999999999";
        if (normalized.contains("id")) return "1";
        if (normalized.contains("token")) return "demo-token";
        return "demo";
    }
}
