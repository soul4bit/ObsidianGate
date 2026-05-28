package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class RandomLightningService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-random-lightning.properties");
    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_INTERVAL_SECONDS = 900;
    private static final int DEFAULT_CHANCE_PERCENT = 35;
    private static final int DEFAULT_RADIUS = 384;

    private final Logger logger;
    private final Path configPath;
    private final Supplier<Object> serverSupplier;
    private final Random random;
    private volatile Config config;
    private int ticksUntilSecond;
    private int secondsUntilRoll;

    RandomLightningService(Logger logger) {
        this(logger, CONFIG_PATH);
    }

    RandomLightningService(Logger logger, Path configPath) {
        this(logger, configPath, RandomLightningService::minecraftServer, new Random());
    }

    RandomLightningService(Logger logger, Path configPath, Supplier<Object> serverSupplier, Random random) {
        this.logger = logger;
        this.configPath = configPath;
        this.serverSupplier = serverSupplier;
        this.random = random;
        this.config = Config.defaults();
        this.ticksUntilSecond = TICKS_PER_SECOND;
        this.secondsUntilRoll = this.config.intervalSeconds;
    }

    synchronized void load() {
        config = loadConfig();
        ticksUntilSecond = TICKS_PER_SECOND;
        secondsUntilRoll = config.intervalSeconds;
        logger.info(String.format(
            "Random lightning loaded. enabled=%s intervalSeconds=%d chancePercent=%d radius=%d dimension=%d minPlayers=%d requireRaining=%s effectOnly=%s",
            config.enabled,
            config.intervalSeconds,
            config.chancePercent,
            config.radius,
            config.dimension,
            config.minPlayers,
            config.requireRaining,
            config.effectOnly
        ));
    }

    @SubscribeEvent
    public synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        runServerEndTick();
    }

    synchronized void runServerEndTick() {
        Config snapshot = config;
        if (!snapshot.enabled) {
            return;
        }

        ticksUntilSecond--;
        if (ticksUntilSecond > 0) {
            return;
        }
        ticksUntilSecond = TICKS_PER_SECOND;
        secondsUntilRoll--;

        if (secondsUntilRoll > 0) {
            return;
        }
        secondsUntilRoll = snapshot.intervalSeconds;

        Object server = serverSupplier.get();
        List<Object> candidates = playersInDimension(server, snapshot.dimension);
        if (candidates.size() < snapshot.minPlayers) {
            return;
        }
        if (random.nextInt(100) >= snapshot.chancePercent) {
            return;
        }

        Object anchor = candidates.get(random.nextInt(candidates.size()));
        Object world = readFieldIfPresent(anchor, "world", "field_70170_p", "l");
        if (snapshot.requireRaining && !isRaining(world)) {
            return;
        }

        int x = (int) Math.floor(TeleportSupport.playerX(anchor)) + randomOffset(snapshot.radius);
        int z = (int) Math.floor(TeleportSupport.playerZ(anchor)) + randomOffset(snapshot.radius);
        Object top = topPosition(world, x, z);
        double strikeX = x + 0.5D;
        double strikeY = top == null ? Math.max(64.0D, TeleportSupport.playerY(anchor)) : blockY(top);
        double strikeZ = z + 0.5D;
        if (spawnLightning(world, strikeX, strikeY, strikeZ, snapshot.effectOnly)) {
            logger.info(String.format("Random lightning struck dim=%d x=%.1f y=%.1f z=%.1f", snapshot.dimension, strikeX, strikeY, strikeZ));
        }
    }

    Config config() {
        return config;
    }

    private int randomOffset(int radius) {
        return random.nextInt(radius * 2 + 1) - radius;
    }

    private Config loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not read random lightning config. Using defaults.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clamp(readInt(properties, "intervalSeconds", DEFAULT_INTERVAL_SECONDS), 30, 86400),
            clamp(readInt(properties, "chancePercent", DEFAULT_CHANCE_PERCENT), 0, 100),
            clamp(readInt(properties, "radius", DEFAULT_RADIUS), 16, 4096),
            readInt(properties, "dimension", 0),
            clamp(readInt(properties, "minPlayers", 1), 0, 200),
            readBoolean(properties, "requireRaining", false),
            readBoolean(properties, "effectOnly", false)
        );

        if (!Files.exists(configPath)) {
            save(loaded);
        }
        return loaded;
    }

    private void save(Config value) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value.enabled));
        properties.setProperty("intervalSeconds", Integer.toString(value.intervalSeconds));
        properties.setProperty("chancePercent", Integer.toString(value.chancePercent));
        properties.setProperty("radius", Integer.toString(value.radius));
        properties.setProperty("dimension", Integer.toString(value.dimension));
        properties.setProperty("minPlayers", Integer.toString(value.minPlayers));
        properties.setProperty("requireRaining", Boolean.toString(value.requireRaining));
        properties.setProperty("effectOnly", Boolean.toString(value.effectOnly));

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "ObsidianGate random lightning.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not write random lightning config.", exception);
        }
    }

    private static Object minecraftServer() {
        try {
            Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
            Object handler = invokeZeroArgIfPresent(handlerType, "instance");
            return invokeZeroArgIfPresent(handler, "getMinecraftServerInstance");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static List<Object> playersInDimension(Object server, int dimension) {
        List<Object> result = new ArrayList<>();
        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        Object players = invokeZeroArgIfPresent(playerList, "getPlayers", "func_181057_v");
        if (!(players instanceof Iterable<?>)) {
            return result;
        }
        for (Object player : (Iterable<?>) players) {
            if (TeleportSupport.playerDimension(player) == dimension) {
                result.add(player);
            }
        }
        return result;
    }

    private static Object topPosition(Object world, int x, int z) {
        Object zero = blockPos(x, 0, z);
        return invokeIfPresent(world, new Object[] { zero }, "getTopSolidOrLiquidBlock", "func_175672_r");
    }

    private static Object blockPos(int x, int y, int z) {
        try {
            Class<?> type = Class.forName("net.minecraft.util.math.BlockPos");
            Constructor<?> constructor = type.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
            return constructor.newInstance(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static double blockY(Object position) {
        Object value = invokeZeroArgIfPresent(position, "getY", "func_177956_o");
        return value instanceof Number ? ((Number) value).doubleValue() : 64.0D;
    }

    private static boolean spawnLightning(Object world, double x, double y, double z, boolean effectOnly) {
        if (world == null) {
            return false;
        }
        try {
            Class<?> worldType = Class.forName("net.minecraft.world.World");
            Class<?> lightningType = Class.forName("net.minecraft.entity.effect.EntityLightningBolt");
            Constructor<?> constructor = lightningType.getConstructor(worldType, Double.TYPE, Double.TYPE, Double.TYPE, Boolean.TYPE);
            Object bolt = constructor.newInstance(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z), Boolean.valueOf(effectOnly));
            return Boolean.TRUE.equals(invokeIfPresent(world, new Object[] { bolt }, "addWeatherEffect", "func_72942_c"));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static boolean isRaining(Object world) {
        Object value = invokeZeroArgIfPresent(world, "isRaining", "func_72896_J");
        return Boolean.TRUE.equals(value);
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int readInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        Object instance = target instanceof Class<?> ? null : target;
        Object[] safeArgs = args == null ? new Object[0] : args;
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (matches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(instance, safeArgs);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean matches(Method method, Object[] args, String... methodNames) {
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
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!isAssignable(parameterTypes[index], args[index])) {
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
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        return false;
    }

    private static Object readFieldIfPresent(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    static final class Config {
        final boolean enabled;
        final int intervalSeconds;
        final int chancePercent;
        final int radius;
        final int dimension;
        final int minPlayers;
        final boolean requireRaining;
        final boolean effectOnly;

        Config(
            boolean enabled,
            int intervalSeconds,
            int chancePercent,
            int radius,
            int dimension,
            int minPlayers,
            boolean requireRaining,
            boolean effectOnly
        ) {
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
            this.chancePercent = chancePercent;
            this.radius = radius;
            this.dimension = dimension;
            this.minPlayers = minPlayers;
            this.requireRaining = requireRaining;
            this.effectOnly = effectOnly;
        }

        static Config defaults() {
            return new Config(true, DEFAULT_INTERVAL_SECONDS, DEFAULT_CHANCE_PERCENT, DEFAULT_RADIUS, 0, 1, false, false);
        }
    }
}
