package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

final class MinecraftPlayerBridge {

    interface TextComponentFactory {
        Object create(String message);
    }

    private final TextComponentFactory textComponentFactory;

    MinecraftPlayerBridge() {
        this(new ReflectiveTextComponentFactory());
    }

    MinecraftPlayerBridge(TextComponentFactory textComponentFactory) {
        this.textComponentFactory = textComponentFactory;
    }

    Object extractPlayerFromEvent(Object event) {
        return readField(event, "player");
    }

    Object extractPlayerFromServerHandler(Object serverHandler) {
        return readField(serverHandler, "player", "field_147369_b");
    }

    Object extractPlayerFromMessageContext(Object messageContext) {
        Object serverHandler = readField(messageContext, "netHandler");
        return extractPlayerFromServerHandler(serverHandler);
    }

    String extractUsername(Object player) {
        return extractUsernameStatic(player);
    }

    String extractUuid(Object player) {
        Object value = invokeZeroArg(player, "getUniqueID", "func_110124_au");
        if (value instanceof UUID) {
            return value.toString();
        }
        return value == null ? "" : value.toString().trim();
    }

    static String extractUsernameStatic(Object player) {
        Object value = invokeZeroArg(player, "func_70005_c_", "getName");
        return value == null ? "" : value.toString().trim();
    }

    void disconnectPlayer(Object player, String reason) {
        Object connection = readField(player, "connection", "field_71135_a");
        Object textComponent = textComponentFactory.create(reason);
        invokeSingleArg(connection, textComponent, "func_194028_b", "func_147360_c", "disconnect");
    }

    private static Object readField(Object target, String... candidates) {
        if (target == null) {
            throw new IllegalArgumentException("Целевой объект обязателен.");
        }

        for (String candidate : candidates) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    type = type.getSuperclass();
                }
            }
        }

        throw new IllegalStateException("Не удалось прочитать ожидаемое поле из " + target.getClass().getName());
    }

    private static Object invokeZeroArg(Object target, String... candidates) {
        if (target == null) {
            throw new IllegalArgumentException("Целевой объект обязателен.");
        }

        for (String candidate : candidates) {
            try {
                Method method = target.getClass().getMethod(candidate);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }

            try {
                Method method = target.getClass().getDeclaredMethod(candidate);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        throw new IllegalStateException("Не удалось найти accessor игрока в " + target.getClass().getName());
    }

    private static void invokeSingleArg(Object target, Object argument, String... candidates) {
        if (target == null) {
            throw new IllegalArgumentException("Целевой объект обязателен.");
        }

        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (matches(method, argument, candidates)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, argument);
                        return;
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать disconnect у " + target.getClass().getName(), exception);
                    }
                }
            }
            type = type.getSuperclass();
        }

        throw new IllegalStateException("Не удалось найти метод disconnect у " + target.getClass().getName());
    }

    private static boolean matches(Method method, Object argument, String... candidates) {
        if (method.getParameterCount() != 1) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equals(method.getName())) {
                return method.getParameterTypes()[0].isAssignableFrom(argument.getClass());
            }
        }
        return false;
    }

    private static final class ReflectiveTextComponentFactory implements TextComponentFactory {
        @Override
        public Object create(String message) {
            try {
                Class<?> componentClass = Class.forName("net.minecraft.util.text.TextComponentString");
                Constructor<?> constructor = componentClass.getConstructor(String.class);
                return constructor.newInstance(message);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Не удалось создать TextComponentString.", exception);
            }
        }
    }
}
