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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class JudgementNightService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-judgement-night.properties");
    private static final int TICKS_PER_SECOND = 20;
    private static final String HOSTILE_MARKER_CLASS = "net.minecraft.entity.monster.IMob";
    private static final String DIVINE_HOSTILE_BASE_CLASS = "divinerpg.objects.entities.entity.EntityDivineMob";
    private static final List<String> DEFAULT_MOB_CLASS_NAMES = Collections.unmodifiableList(Arrays.asList(
        "net.minecraft.entity.monster.EntityZombie",
        "net.minecraft.entity.monster.EntitySkeleton",
        "net.minecraft.entity.monster.EntitySpider",
        "net.minecraft.entity.monster.EntityCreeper",
        "net.minecraft.entity.monster.EntityWitch",
        "net.minecraft.entity.monster.EntityEnderman",
        "divinerpg.objects.entities.entity.vanilla.EntityCaveCrawler",
        "divinerpg.objects.entities.entity.vanilla.EntityCaveclops",
        "divinerpg.objects.entities.entity.vanilla.EntityCyclops",
        "divinerpg.objects.entities.entity.vanilla.EntityEnthralledDramcryx",
        "divinerpg.objects.entities.entity.vanilla.EntityFrost",
        "divinerpg.objects.entities.entity.vanilla.EntityGlacon",
        "divinerpg.objects.entities.entity.vanilla.EntityJungleDramcryx",
        "divinerpg.objects.entities.entity.vanilla.EntityJungleSpider",
        "divinerpg.objects.entities.entity.vanilla.EntityKobblin",
        "divinerpg.objects.entities.entity.vanilla.EntityMiner",
        "divinerpg.objects.entities.entity.vanilla.EntityRotatick",
        "divinerpg.objects.entities.entity.vanilla.EntitySaguaroWorm",
        "divinerpg.objects.entities.entity.vanilla.EntityTheEye",
        "divinerpg.objects.entities.entity.vanilla.EntityTheGrue"
    ));

    private final Logger logger;
    private final Path configPath;
    private final Supplier<Object> serverSupplier;
    private final Random random;
    private volatile Config config;
    private int ticksUntilSecond;
    private int secondsUntilWave;
    private long announcedDay = -1L;

    JudgementNightService(Logger logger) {
        this(logger, CONFIG_PATH);
    }

    JudgementNightService(Logger logger, Path configPath) {
        this(logger, configPath, JudgementNightService::minecraftServer, new Random());
    }

    JudgementNightService(Logger logger, Path configPath, Supplier<Object> serverSupplier, Random random) {
        this.logger = logger;
        this.configPath = configPath;
        this.serverSupplier = serverSupplier;
        this.random = random;
        this.config = Config.defaults();
        this.ticksUntilSecond = TICKS_PER_SECOND;
        this.secondsUntilWave = this.config.waveIntervalSeconds;
    }

    synchronized void load() {
        config = loadConfig();
        ticksUntilSecond = TICKS_PER_SECOND;
        secondsUntilWave = config.waveIntervalSeconds;
        announcedDay = -1L;
        logger.info(String.format(
            "Judgement night loaded. enabled=%s periodDays=%d waveIntervalSeconds=%d mobsPerPlayer=%d maxHostilesNearPlayer=%d radius=%d-%d dimension=%d mobClasses=%d",
            config.enabled,
            config.periodDays,
            config.waveIntervalSeconds,
            config.mobsPerPlayer,
            config.maxHostilesNearPlayer,
            config.minSpawnRadius,
            config.maxSpawnRadius,
            config.dimension,
            config.mobClassNames.size()
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

        Object server = serverSupplier.get();
        Object world = world(server, snapshot.dimension);
        if (!isJudgementNight(world, snapshot)) {
            secondsUntilWave = snapshot.waveIntervalSeconds;
            return;
        }

        long day = dayNumber(world);
        if (announcedDay != day) {
            announcedDay = day;
            broadcast(server, "Судная ночь", "наступила. Держитесь вместе: волны мобов будут чаще обычного.");
            logger.info("Judgement night started for day " + day + ".");
        }

        secondsUntilWave--;
        if (secondsUntilWave > 0) {
            return;
        }
        secondsUntilWave = snapshot.waveIntervalSeconds;

        int spawned = spawnWaves(server, snapshot);
        if (spawned > 0) {
            logger.info("Judgement night spawned hostile mobs: " + spawned);
        }
    }

    Config config() {
        return config;
    }

    private int spawnWaves(Object server, Config snapshot) {
        int spawned = 0;
        for (Object player : playersInDimension(server, snapshot.dimension)) {
            Object world = readFieldIfPresent(player, "world", "field_70170_p", "l");
            if (countHostilesNear(world, player, snapshot.densityCheckRadius) >= snapshot.maxHostilesNearPlayer) {
                continue;
            }
            for (int index = 0; index < snapshot.mobsPerPlayer; index++) {
                if (spawnMobNear(player, snapshot)) {
                    spawned++;
                }
            }
        }
        return spawned;
    }

    private boolean spawnMobNear(Object player, Config snapshot) {
        Object world = readFieldIfPresent(player, "world", "field_70170_p", "l");
        if (world == null) {
            return false;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = snapshot.minSpawnRadius + random.nextInt(snapshot.maxSpawnRadius - snapshot.minSpawnRadius + 1);
            int x = (int) Math.floor(TeleportSupport.playerX(player) + Math.cos(angle) * distance);
            int z = (int) Math.floor(TeleportSupport.playerZ(player) + Math.sin(angle) * distance);
            Object top = topPosition(world, x, z);
            if (top == null) {
                continue;
            }
            int y = (int) Math.floor(blockY(top));
            if (!isSafeSpawn(world, x, y, z)) {
                continue;
            }
            Object mob = createRandomMob(world, snapshot.mobClassNames);
            if (mob == null) {
                continue;
            }
            invokeIfPresent(mob, new Object[] {
                Double.valueOf(x + 0.5D),
                Double.valueOf(y),
                Double.valueOf(z + 0.5D),
                Float.valueOf(random.nextFloat() * 360.0F),
                Float.valueOf(0.0F)
            }, "setLocationAndAngles", "func_70012_b");
            if (Boolean.TRUE.equals(invokeIfPresent(world, new Object[] { mob }, "spawnEntity", "func_72838_d"))) {
                return true;
            }
        }
        return false;
    }

    private Config loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not read judgement night config. Using defaults.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clamp(readInt(properties, "periodDays", 7), 2, 100),
            clamp(readInt(properties, "waveIntervalSeconds", 20), 10, 3600),
            clamp(readInt(properties, "mobsPerPlayer", 10), 1, 40),
            clamp(readInt(properties, "maxHostilesNearPlayer", 96), 1, 200),
            clamp(readInt(properties, "densityCheckRadius", 72), 16, 256),
            clamp(readInt(properties, "minSpawnRadius", 18), 8, 256),
            clamp(readInt(properties, "maxSpawnRadius", 64), 8, 512),
            readInt(properties, "dimension", 0),
            clamp(readInt(properties, "nightStartTick", 12000), 0, 23999),
            clamp(readInt(properties, "nightEndTick", 23999), 0, 23999),
            readMobClassNames(properties)
        ).normalized();

        if (!Files.exists(configPath)) {
            save(loaded);
        }
        return loaded;
    }

    private void save(Config value) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value.enabled));
        properties.setProperty("periodDays", Integer.toString(value.periodDays));
        properties.setProperty("waveIntervalSeconds", Integer.toString(value.waveIntervalSeconds));
        properties.setProperty("mobsPerPlayer", Integer.toString(value.mobsPerPlayer));
        properties.setProperty("maxHostilesNearPlayer", Integer.toString(value.maxHostilesNearPlayer));
        properties.setProperty("densityCheckRadius", Integer.toString(value.densityCheckRadius));
        properties.setProperty("minSpawnRadius", Integer.toString(value.minSpawnRadius));
        properties.setProperty("maxSpawnRadius", Integer.toString(value.maxSpawnRadius));
        properties.setProperty("dimension", Integer.toString(value.dimension));
        properties.setProperty("nightStartTick", Integer.toString(value.nightStartTick));
        properties.setProperty("nightEndTick", Integer.toString(value.nightEndTick));
        properties.setProperty("mobClassNames", join(value.mobClassNames));

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "ObsidianGate judgement night.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not write judgement night config.", exception);
        }
    }

    static boolean isJudgementNight(Object world, Config config) {
        if (world == null) {
            return false;
        }
        long day = dayNumber(world);
        long tick = worldTime(world) % 24000L;
        return day > 0L && day % config.periodDays == 0L && tick >= config.nightStartTick && tick <= config.nightEndTick;
    }

    static long dayNumber(Object world) {
        return worldTime(world) / 24000L;
    }

    private static long worldTime(Object world) {
        Object value = invokeIfPresent(world, new Object[0], "getWorldTime", "func_72820_D");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int countHostilesNear(Object world, Object player, int radius) {
        Object entities = readFieldIfPresent(world, "loadedEntityList", "field_72996_f");
        if (!(entities instanceof List<?>)) {
            return 0;
        }
        double px = TeleportSupport.playerX(player);
        double py = TeleportSupport.playerY(player);
        double pz = TeleportSupport.playerZ(player);
        double limit = (double) radius * (double) radius;
        int count = 0;
        for (Object entity : (List<?>) entities) {
            if (isHostile(entity) && distanceSquared(entity, px, py, pz) <= limit) {
                count++;
            }
        }
        return count;
    }

    private static double distanceSquared(Object entity, double x, double y, double z) {
        double dx = readDoubleField(entity, "posX", "field_70165_t") - x;
        double dy = readDoubleField(entity, "posY", "field_70163_u") - y;
        double dz = readDoubleField(entity, "posZ", "field_70161_v") - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isHostile(Object entity) {
        Class<?> type = entity == null ? null : entity.getClass();
        while (type != null) {
            if (DIVINE_HOSTILE_BASE_CLASS.equals(type.getName())) {
                return true;
            }
            for (Class<?> marker : type.getInterfaces()) {
                if (HOSTILE_MARKER_CLASS.equals(marker.getName())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean isSafeSpawn(Object world, int x, int y, int z) {
        if (y < 2 || y > 253) {
            return false;
        }
        Object feet = blockPos(x, y, z);
        Object head = blockPos(x, y + 1, z);
        Object below = blockPos(x, y - 1, z);
        return isAir(world, feet) && isAir(world, head) && !isAir(world, below) && !isLiquid(world, below);
    }

    private static boolean isAir(Object world, Object position) {
        Object value = invokeIfPresent(world, new Object[] { position }, "isAirBlock", "func_175623_d");
        return Boolean.TRUE.equals(value);
    }

    private static boolean isLiquid(Object world, Object position) {
        Object state = invokeIfPresent(world, new Object[] { position }, "getBlockState", "func_180495_p");
        Object material = invokeIfPresent(state, new Object[0], "getMaterial", "func_185904_a");
        Object liquid = invokeIfPresent(material, new Object[0], "isLiquid", "func_76224_d");
        return Boolean.TRUE.equals(liquid);
    }

    private Object createRandomMob(Object world, List<String> mobClassNames) {
        if (mobClassNames == null || mobClassNames.isEmpty()) {
            return null;
        }
        int start = random.nextInt(mobClassNames.size());
        for (int offset = 0; offset < mobClassNames.size(); offset++) {
            Object mob = createMob(world, mobClassNames.get((start + offset) % mobClassNames.size()));
            if (mob != null) {
                return mob;
            }
        }
        return null;
    }

    private static Object createMob(Object world, String className) {
        try {
            Class<?> worldType = Class.forName("net.minecraft.world.World");
            Class<?> mobType = Class.forName(className);
            Constructor<?> constructor = mobType.getConstructor(worldType);
            return constructor.newInstance(world);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object minecraftServer() {
        try {
            Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
            Object handler = invokeIfPresent(handlerType, new Object[0], "instance");
            return invokeIfPresent(handler, new Object[0], "getMinecraftServerInstance");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Object world(Object server, int dimension) {
        return invokeIfPresent(server, new Object[] { Integer.valueOf(dimension) }, "getWorld", "func_71218_a");
    }

    private static List<Object> playersInDimension(Object server, int dimension) {
        List<Object> result = new ArrayList<>();
        Object playerList = invokeIfPresent(server, new Object[0], "getPlayerList", "func_184103_al");
        Object players = invokeIfPresent(playerList, new Object[0], "getPlayers", "func_181057_v");
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

    private static void broadcast(Object server, String subject, String detail) {
        Object playerList = invokeIfPresent(server, new Object[0], "getPlayerList", "func_184103_al");
        Object players = invokeIfPresent(playerList, new Object[0], "getPlayers", "func_181057_v");
        if (!(players instanceof Iterable<?>)) {
            return;
        }
        for (Object player : (Iterable<?>) players) {
            ServerChat.status(player, ServerChat.Tone.WARNING, subject, detail);
        }
    }

    private static Object topPosition(Object world, int x, int z) {
        return invokeIfPresent(world, new Object[] { blockPos(x, 0, z) }, "getTopSolidOrLiquidBlock", "func_175672_r");
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
        Object value = invokeIfPresent(position, new Object[0], "getY", "func_177956_o");
        return value instanceof Number ? ((Number) value).doubleValue() : 64.0D;
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

    private static List<String> readMobClassNames(Properties properties) {
        String rawValue = properties.getProperty("mobClassNames");
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return DEFAULT_MOB_CLASS_NAMES;
        }

        List<String> result = new ArrayList<String>();
        String[] parts = rawValue.split(",");
        for (String part : parts) {
            String className = part == null ? "" : part.trim();
            if (!className.isEmpty()) {
                result.add(className);
            }
        }
        if (result.isEmpty()) {
            return DEFAULT_MOB_CLASS_NAMES;
        }
        return Collections.unmodifiableList(result);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
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
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
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

    private static double readDoubleField(Object target, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    static final class Config {
        final boolean enabled;
        final int periodDays;
        final int waveIntervalSeconds;
        final int mobsPerPlayer;
        final int maxHostilesNearPlayer;
        final int densityCheckRadius;
        final int minSpawnRadius;
        final int maxSpawnRadius;
        final int dimension;
        final int nightStartTick;
        final int nightEndTick;
        final List<String> mobClassNames;

        Config(
            boolean enabled,
            int periodDays,
            int waveIntervalSeconds,
            int mobsPerPlayer,
            int maxHostilesNearPlayer,
            int densityCheckRadius,
            int minSpawnRadius,
            int maxSpawnRadius,
            int dimension,
            int nightStartTick,
            int nightEndTick,
            List<String> mobClassNames
        ) {
            this.enabled = enabled;
            this.periodDays = periodDays;
            this.waveIntervalSeconds = waveIntervalSeconds;
            this.mobsPerPlayer = mobsPerPlayer;
            this.maxHostilesNearPlayer = maxHostilesNearPlayer;
            this.densityCheckRadius = densityCheckRadius;
            this.minSpawnRadius = minSpawnRadius;
            this.maxSpawnRadius = maxSpawnRadius;
            this.dimension = dimension;
            this.nightStartTick = nightStartTick;
            this.nightEndTick = nightEndTick;
            this.mobClassNames = mobClassNames == null || mobClassNames.isEmpty()
                ? DEFAULT_MOB_CLASS_NAMES
                : Collections.unmodifiableList(new ArrayList<String>(mobClassNames));
        }

        Config normalized() {
            if (minSpawnRadius <= maxSpawnRadius) {
                return this;
            }
            return new Config(
                enabled,
                periodDays,
                waveIntervalSeconds,
                mobsPerPlayer,
                maxHostilesNearPlayer,
                densityCheckRadius,
                maxSpawnRadius,
                minSpawnRadius,
                dimension,
                nightStartTick,
                nightEndTick,
                mobClassNames
            );
        }

        static Config defaults() {
            return new Config(true, 7, 20, 10, 96, 72, 18, 64, 0, 12000, 23999, DEFAULT_MOB_CLASS_NAMES);
        }
    }
}
