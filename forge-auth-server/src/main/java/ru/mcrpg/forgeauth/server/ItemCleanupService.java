package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ItemCleanupService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-item-cleanup.properties");
    private static final int TICKS_PER_SECOND = 20;
    private static final int DEFAULT_INTERVAL_SECONDS = 3600;
    private static final int DEFAULT_MIN_AGE_SECONDS = 1800;
    private static final int MIN_INTERVAL_SECONDS = 60;
    private static final int MAX_INTERVAL_SECONDS = 86400;
    private static final String ENTITY_ITEM_CLASS_NAME = "net.minecraft.entity.item.EntityItem";

    private final Logger logger;
    private final Path configPath;
    private final Supplier<Object> serverSupplier;
    private final Set<Integer> warnedSeconds = new HashSet<>();
    private volatile Config config;
    private int ticksUntilSecond;
    private int secondsUntilCleanup;

    ItemCleanupService(Logger logger) {
        this(logger, CONFIG_PATH);
    }

    ItemCleanupService(Logger logger, Path configPath) {
        this(logger, configPath, ItemCleanupService::minecraftServer);
    }

    ItemCleanupService(Logger logger, Path configPath, Supplier<Object> serverSupplier) {
        this.logger = logger;
        this.configPath = configPath;
        this.serverSupplier = serverSupplier;
        this.config = Config.defaults();
        this.ticksUntilSecond = TICKS_PER_SECOND;
        this.secondsUntilCleanup = this.config.intervalSeconds;
    }

    synchronized void load() {
        config = loadConfig();
        secondsUntilCleanup = config.intervalSeconds;
        ticksUntilSecond = TICKS_PER_SECOND;
        warnedSeconds.clear();
        logger.info(String.format(
            "Очистка предметов загружена. enabled=%s intervalSeconds=%d minAgeSeconds=%d warnings=%s",
            config.enabled,
            config.intervalSeconds,
            config.minAgeSeconds,
            config.warningSeconds
        ));
    }

    @SubscribeEvent
    public synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (!isEndPhase(event)) {
            return;
        }

        runServerEndTick();
    }

    synchronized void runServerEndTick() {
        Config snapshot = config;
        if (!snapshot.enabled) {
            return;
        }

        long watchdogStart = MainThreadWatchdog.start();
        CleanupResult result = null;
        try {
        ticksUntilSecond--;
        if (ticksUntilSecond > 0) {
            return;
        }

        ticksUntilSecond = TICKS_PER_SECOND;
        secondsUntilCleanup--;

        if (secondsUntilCleanup > 0 && snapshot.warningSeconds.contains(Integer.valueOf(secondsUntilCleanup))) {
            if (warnedSeconds.add(Integer.valueOf(secondsUntilCleanup))) {
                broadcastCountdown("Очистка предметов", secondsUntilCleanup);
            }
            return;
        }

        if (secondsUntilCleanup <= 0) {
            result = cleanupDroppedItems(snapshot.minAgeSeconds);
            broadcastStatus("Очистка предметов", cleanupSummary(result));
            logger.info(
                "Dropped item cleanup finished. worlds=" + result.worlds
                    + " scanned=" + result.scanned
                    + " removed=" + result.removed
                    + " skippedFresh=" + result.skippedFresh
                    + " skippedDead=" + result.skippedDead
                    + " minAgeSeconds=" + snapshot.minAgeSeconds
            );
            secondsUntilCleanup = snapshot.intervalSeconds;
            warnedSeconds.clear();
        }
        } finally {
            MainThreadWatchdog.warnIfSlow(
                logger,
                "item-cleanup tick",
                watchdogStart,
                result == null
                    ? "cleanup=false secondsUntilCleanup=" + secondsUntilCleanup
                    : "cleanup=true worlds=" + result.worlds + " scanned=" + result.scanned + " removed=" + result.removed
            );
        }
    }

    Config config() {
        return config;
    }

    private Config loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать конфиг очистки предметов. Используем значения по умолчанию.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clampInterval(readInt(properties, "intervalSeconds", DEFAULT_INTERVAL_SECONDS)),
            clampInterval(readInt(properties, "minAgeSeconds", DEFAULT_MIN_AGE_SECONDS)),
            readWarningSeconds(properties)
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
        properties.setProperty("minAgeSeconds", Integer.toString(value.minAgeSeconds));
        properties.setProperty("warningSeconds", joinIntegers(value.warningSeconds));

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "ObsidianGate dropped item cleanup.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось записать конфиг очистки предметов.", exception);
        }
    }

    private CleanupResult cleanupDroppedItems(int minAgeSeconds) {
        Object server = serverSupplier.get();
        CleanupResult result = new CleanupResult();
        for (Object world : loadedWorlds(server)) {
            result.worlds++;
            Object entities = readFieldIfPresent(world, "loadedEntityList", "field_72996_f");
            if (!(entities instanceof List<?>)) {
                continue;
            }
            for (Object entity : new ArrayList<Object>((List<?>) entities)) {
                if (!isDroppedItem(entity)) {
                    continue;
                }
                result.scanned++;
                if (isDead(entity)) {
                    result.skippedDead++;
                    continue;
                }
                if (isOldEnoughToClean(entity, minAgeSeconds)) {
                    invokeZeroArgIfPresent(entity, "setDead", "func_70106_y");
                    result.removed++;
                } else {
                    result.skippedFresh++;
                }
            }
        }
        return result;
    }

    private static String cleanupSummary(CleanupResult result) {
        String summary = "завершена. Удалено: " + result.removed + ".";
        if (result.skippedFresh > 0) {
            summary += " Свежих предметов пропущено: " + result.skippedFresh + ".";
        }
        return summary;
    }

    private void broadcastStatus(String subject, String detail) {
        String message = ServerChat.statusText(subject, detail);
        logger.info(message);
        Object server = serverSupplier.get();
        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        Object players = invokeZeroArgIfPresent(playerList, "getPlayers", "func_181057_v");
        if (!(players instanceof Iterable<?>)) {
            return;
        }
        for (Object player : (Iterable<?>) players) {
            ServerChat.status(player, subject, detail);
        }
    }

    private void broadcastCountdown(String subject, int seconds) {
        String message = ServerChat.countdownText(subject, seconds);
        logger.info(message);
        Object server = serverSupplier.get();
        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        Object players = invokeZeroArgIfPresent(playerList, "getPlayers", "func_181057_v");
        if (!(players instanceof Iterable<?>)) {
            return;
        }
        for (Object player : (Iterable<?>) players) {
            ServerChat.countdown(player, subject, seconds);
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

    private static List<Object> loadedWorlds(Object server) {
        List<Object> worlds = new ArrayList<>();
        Object value = readFieldIfPresent(server, "worlds", "field_71305_c");
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                Object world = Array.get(value, index);
                if (world != null) {
                    worlds.add(world);
                }
            }
            return worlds;
        }
        if (value instanceof Iterable<?>) {
            for (Object world : (Iterable<?>) value) {
                if (world != null) {
                    worlds.add(world);
                }
            }
            return worlds;
        }

        Object overworld = invokeIfPresent(server, new Object[] { Integer.valueOf(0) }, "getWorld", "func_71218_a");
        if (overworld != null) {
            worlds.add(overworld);
        }
        return worlds;
    }

    private static boolean isDroppedItem(Object entity) {
        Class<?> type = entity == null ? null : entity.getClass();
        while (type != null) {
            if (ENTITY_ITEM_CLASS_NAME.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean isDead(Object entity) {
        Object value = readFieldIfPresent(entity, "isDead", "field_70128_L");
        return Boolean.TRUE.equals(value);
    }

    private static boolean isOldEnoughToClean(Object entity, int minAgeSeconds) {
        if (minAgeSeconds <= 0) {
            return true;
        }
        Object ageValue = readFieldIfPresent(entity, "age", "field_70292_b");
        Object ticksExistedValue = readFieldIfPresent(entity, "ticksExisted", "field_70173_aa");
        int ageTicks = 0;
        if (ageValue instanceof Number) {
            ageTicks = Math.max(ageTicks, ((Number) ageValue).intValue());
        }
        if (ticksExistedValue instanceof Number) {
            ageTicks = Math.max(ageTicks, ((Number) ticksExistedValue).intValue());
        }
        if (ageTicks <= 0) {
            return false;
        }
        return ageTicks >= minAgeSeconds * TICKS_PER_SECOND;
    }

    private static boolean isEndPhase(Object event) {
        Object phase = readFieldIfPresent(event, "phase");
        return phase instanceof Enum<?> && "END".equals(((Enum<?>) phase).name());
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target instanceof Class<?> ? null : target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
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
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
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

    private static Set<Integer> readWarningSeconds(Properties properties) {
        String raw = properties.getProperty("warningSeconds", "60,5");
        Set<Integer> values = new HashSet<>();
        for (String part : raw.split(",")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value > 0) {
                    values.add(Integer.valueOf(value));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (values.isEmpty()) {
            values.add(Integer.valueOf(60));
            values.add(Integer.valueOf(5));
        }
        return values;
    }

    private static String joinIntegers(Set<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort((left, right) -> Integer.compare(right.intValue(), left.intValue()));
        StringBuilder builder = new StringBuilder();
        for (Integer value : sorted) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.intValue());
        }
        return builder.toString();
    }

    private static int clampInterval(int seconds) {
        return Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
    }

    static final class Config {
        final boolean enabled;
        final int intervalSeconds;
        final int minAgeSeconds;
        final Set<Integer> warningSeconds;

        private Config(boolean enabled, int intervalSeconds, int minAgeSeconds, Set<Integer> warningSeconds) {
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
            this.minAgeSeconds = minAgeSeconds;
            this.warningSeconds = new HashSet<>(warningSeconds);
        }

        private static Config defaults() {
            Set<Integer> warnings = new HashSet<>();
            warnings.add(Integer.valueOf(60));
            warnings.add(Integer.valueOf(5));
            return new Config(true, DEFAULT_INTERVAL_SECONDS, DEFAULT_MIN_AGE_SECONDS, warnings);
        }
    }

    private static final class CleanupResult {
        private int worlds;
        private int scanned;
        private int removed;
        private int skippedFresh;
        private int skippedDead;
    }
}
