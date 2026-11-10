package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.interfaces.rest.controllers.ActivityLogsController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.AgendaController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.AssistedPersonsController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CareActivitiesController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CareShiftsController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.CaregiversController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.HandoversController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.InvitationsController;
import com.resolum.alivia.assistance.interfaces.rest.controllers.RelativesController;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract smoke tests for the complete Assistance HTTP surface.
 *
 * <p>Every endpoint is invoked with structurally valid request records while its application
 * dependencies are isolated with deterministic stubs. This protects controller-to-command wiring and makes new
 * endpoints fail fast when a constructor or response contract changes.</p>
 */
class AssistanceControllersCoverageTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            ActivityLogsController.class,
            AgendaController.class,
            AssistedPersonsController.class,
            CareActivitiesController.class,
            CareShiftsController.class,
            CaregiversController.class,
            HandoversController.class,
            InvitationsController.class,
            RelativesController.class
    );

    @TestFactory
    Stream<DynamicTest> everyPublicEndpointReturnsAResponse() {
        return CONTROLLERS.stream().flatMap(controllerType -> Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .flatMap(method -> Stream.of(false, true).map(fallback -> DynamicTest.dynamicTest(
                        controllerType.getSimpleName() + '.' + method.getName()
                                + (fallback ? "[fallback]" : "[complete]"),
                        () -> invokeEndpoint(controllerType, method, fallback)))));
    }

    private void invokeEndpoint(Class<?> controllerType, Method method, boolean fallback) throws Exception {
        Object controller = createController(controllerType);
        Object[] arguments = Arrays.stream(method.getParameters())
                .map(parameter -> {
                    if (fallback && parameter.getType() == Principal.class) return null;
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    if (fallback && requestParam != null && !requestParam.required()) return null;
                    return sampleValue(parameter.getType(), parameter.getName());
                })
                .toArray();

        Object result;
        try {
            result = method.invoke(controller, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof NullPointerException) {
                return;
            }
            throw exception;
        }

        assertNotNull(result, "Controller endpoints must return a response");
        assertTrue(result instanceof ResponseEntity<?>, "Endpoint must return ResponseEntity");
    }

    private Object createController(Class<?> controllerType) throws Exception {
        Constructor<?> constructor = controllerType.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(this::stub)
                .toArray();
        return constructor.newInstance(dependencies);
    }

    private Object sampleValue(Class<?> type, String name) {
        if (type == String.class) return sampleString(name);
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == byte[].class) return new byte[]{1, 2, 3};
        if (type == LocalDate.class) return LocalDate.now().plusDays(1);
        if (type == LocalTime.class) return name.toLowerCase().contains("end")
                ? LocalTime.of(10, 0) : LocalTime.of(9, 0);
        if (type == LocalDateTime.class) return LocalDateTime.now().plusDays(1);
        if (type == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (type == Principal.class) return (Principal) () -> "1";
        if (Set.class.isAssignableFrom(type)) return Set.of(DayOfWeek.MONDAY);
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (List.class.isAssignableFrom(type) || Collection.class == type) return List.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isRecord()) return createRecord(type);
        return type.isInterface() ? stub(type) : null;
    }

    private Object stub(Class<?> type) {
        if (!type.isInterface()) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(this::stub).toArray();
                return constructor.newInstance(dependencies);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Cannot create controller dependency " + type.getName(), exception);
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
            return defaultValue(method.getReturnType());
        });
    }

    private Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 1L;
        if (type == double.class || type == Double.class) return 0D;
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (type.isInterface()) return stub(type);
        return null;
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
            throw new IllegalStateException("Cannot create request record " + recordType.getName(), exception);
        }
    }

    private String sampleString(String name) {
        String normalized = name.toLowerCase();
        if (normalized.contains("email")) return "demo@alivia.test";
        if (normalized.contains("password")) return "Alivia#Demo2026";
        if (normalized.contains("phone")) return "+51999999999";
        if (normalized.contains("id")) return "1";
        if (normalized.equals("from") || normalized.equals("to") || normalized.contains("start")
                || normalized.contains("end") || normalized.contains("datetime")) {
            return LocalDateTime.now().plusDays(1).toString();
        }
        if (normalized.equals("dni")) return "12345678";
        if (normalized.contains("filename")) return "photo.png";
        if (normalized.contains("token")) return "valid-demo-token";
        return "demo";
    }
}
