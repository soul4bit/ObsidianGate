package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ServerReflection {

    private ServerReflection() {
    }

    static Object invoke(Object target, String[] names, Object... args) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ignored) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (matches(method, names, safeArgs)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    static Object field(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    static boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static boolean matches(Method method, String[] names, Object[] args) {
        boolean named = false;
        for (String name : names) {
            if (name.equals(method.getName())) {
                named = true;
                break;
            }
        }
        if (!named || method.getParameterTypes().length != args.length) {
            return false;
        }
        Class<?>[] types = method.getParameterTypes();
        for (int index = 0; index < types.length; index++) {
            if (!assignable(types[index], args[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean assignable(Class<?> type, Object value) {
        if (value == null) {
            return !type.isPrimitive();
        }
        if (!type.isPrimitive()) {
            return type.isAssignableFrom(value.getClass());
        }
        return (type == Boolean.TYPE && value instanceof Boolean)
            || (type == Integer.TYPE && value instanceof Integer)
            || (type == Long.TYPE && value instanceof Long)
            || (type == Float.TYPE && value instanceof Float)
            || (type == Double.TYPE && value instanceof Double);
    }
}
