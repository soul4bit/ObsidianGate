package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class SpawnRoadCommand {

    private static final String COMMAND_NAME = "spawnroads";
    private static final String SUBJECT = "Spawn Roads";
    private static final int DEFAULT_LENGTH = 1000;
    private static final int DEFAULT_BLOCKS_PER_TICK = 2500;

    private SpawnRoadCommand() {
    }

    static void register(FMLServerStartingEvent event, SpawnRoadBuilderService service) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                SpawnRoadCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(service)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot register /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final SpawnRoadBuilderService service;

        private Handler(SpawnRoadBuilderService service) {
            this.service = service;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return usage();
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[0], args[1], args[2]);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.valueOf(canUse(args[1]));
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return SpawnRoadBuilderService.commandOptions();
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(name)) {
                return Integer.valueOf(compareTo(args == null ? null : args[0]));
            }
            if ("toString".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(COMMAND_NAME.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }

        private void execute(Object server, Object sender, Object arguments) {
            if (!canUse(sender)) {
                ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "not enough permissions.");
                return;
            }

            String[] raw = arguments instanceof String[] ? (String[]) arguments : new String[0];
            if (raw.length == 0 || "status".equalsIgnoreCase(raw[0])) {
                ServerChat.status(sender, SUBJECT, service.statusText());
                return;
            }
            if ("cancel".equalsIgnoreCase(raw[0])) {
                if (!service.cancel(sender)) {
                    ServerChat.status(sender, ServerChat.Tone.WARNING, SUBJECT, "no active build.");
                }
                return;
            }
            if (!"build".equalsIgnoreCase(raw[0])) {
                ServerChat.usage(sender, usage());
                return;
            }
            if (raw.length < 4 || raw.length > 6) {
                ServerChat.usage(sender, usage());
                return;
            }

            try {
                int centerX = Integer.parseInt(raw[1]);
                int roadY = Integer.parseInt(raw[2]);
                int centerZ = Integer.parseInt(raw[3]);
                int length = raw.length >= 5 ? Integer.parseInt(raw[4]) : DEFAULT_LENGTH;
                int blocksPerTick = raw.length >= 6 ? Integer.parseInt(raw[5]) : DEFAULT_BLOCKS_PER_TICK;
                if (roadY < 1 || roadY > 240) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "roadY must be between 1 and 240.");
                    return;
                }
                if (length < 1 || length > 20000) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "length must be between 1 and 20000.");
                    return;
                }
                if (blocksPerTick < 100 || blocksPerTick > 20000) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "blocksPerTick must be between 100 and 20000.");
                    return;
                }
                Object world = invokeIfPresent(server, new Object[] { Integer.valueOf(0) }, "getWorld", "func_71218_a");
                if (world == null) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "overworld is not loaded.");
                    return;
                }
                if (!service.start(world, sender, centerX, roadY, centerZ, length, blocksPerTick)) {
                    ServerChat.status(sender, ServerChat.Tone.WARNING, SUBJECT, "build is already running: " + service.statusText());
                    return;
                }
                ServerChat.status(
                    sender,
                    ServerChat.Tone.SUCCESS,
                    SUBJECT,
                    "started from " + centerX + " " + roadY + " " + centerZ + ", length " + length + ", budget " + blocksPerTick + "/tick."
                );
            } catch (NumberFormatException ignored) {
                ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "coordinates and length must be numbers.");
            }
        }
    }

    private static boolean canUse(Object sender) {
        if (sender == null) {
            return false;
        }
        Object result = invokeIfPresent(sender, new Object[] { Integer.valueOf(2), COMMAND_NAME }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
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
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Cannot call " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean methodMatches(Method method, Object[] args, String... methodNames) {
        if (method.getParameterTypes().length != args.length) {
            return false;
        }
        boolean nameMatches = false;
        for (String methodName : methodNames) {
            if (methodName.equals(method.getName())) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        if (parameterType == Integer.TYPE) {
            return value instanceof Integer;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        return false;
    }

    private static String usage() {
        return "/" + COMMAND_NAME + " <status|cancel|build <centerX> <roadY> <centerZ> [length] [blocksPerTick]>";
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeIfPresent(other, new Object[0], "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
