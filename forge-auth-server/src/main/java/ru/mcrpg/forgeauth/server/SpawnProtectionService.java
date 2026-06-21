package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class SpawnProtectionService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-spawn-protection.properties");
    private static final int DEFAULT_RADIUS = 96;
    private static final int MAX_RADIUS = 2048;
    private static final double MIN_REGION_Y = 0.0D;
    private static final double MAX_REGION_Y = 255.0D;
    private static final String ADMIN_PLAYER_NAME = "admin";
    private static final String HOSTILE_MARKER_CLASS = "net.minecraft.entity.monster.IMob";
    private static final String SUBJECT = "Защита спавна";
    static final String CENTER_MODE_WORLDSPAWN = "worldspawn";
    static final String CENTER_MODE_FIXED = "fixed";
    static final String REGION_MODE_RADIUS = "radius";
    static final String REGION_MODE_BOX = "box";

    private final Logger logger;
    private final Path configPath;
    private volatile Config config;

    SpawnProtectionService(Logger logger) {
        this(logger, CONFIG_PATH);
    }

    SpawnProtectionService(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.config = Config.defaults();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Config snapshot = config();
        BlockPosition position = blockPosition(invokeZeroArgIfPresent(event, "getPos"));
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtectedEvent(event, position, player, snapshot)) {
            return;
        }
        if (canBypass(player, snapshot)) {
            return;
        }
        cancel(event);
        event.setExpToDrop(0);
        logger.info("Защита спавна заблокировала ломание блока: " + describe(position) + " player=" + TeleportSupport.playerName(player));
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ломать блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Config snapshot = config();
        BlockPosition position = blockPosition(invokeZeroArgIfPresent(event, "getPos"));
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtectedEvent(event, position, player, snapshot)) {
            return;
        }
        if (canBypass(player, snapshot)) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ломать блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerBreakSpeed(PlayerEvent.BreakSpeed event) {
        // BreakSpeed is a very hot mining path. Spawn protection is still
        // enforced by left-click, break, place, explosion, and spawn events.
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Config snapshot = config();
        BlockPosition position = blockPosition(invokeZeroArgIfPresent(event, "getPos"));
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtectedEvent(event, position, player, snapshot)) {
            return;
        }
        if (canBypass(player, snapshot)) {
            return;
        }
        cancel(event);
        logger.info("Защита спавна заблокировала установку блока: " + describe(position) + " player=" + TeleportSupport.playerName(player));
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ставить блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Config snapshot = config();
        Object entity = invokeZeroArgIfPresent(event, "getEntity");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtectedEvent(event, blockPosition(invokeZeroArgIfPresent(event, "getPos")), entity, snapshot)) {
            return;
        }
        if (canBypass(entity, snapshot)) {
            return;
        }
        cancel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Config snapshot = config();
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtectedEvent(event, blockPosition(invokeZeroArgIfPresent(event, "getPos")), player, snapshot)) {
            return;
        }
        if (canBypass(player, snapshot)) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "взаимодействие с этим блоком закрыто.");
    }

    @SubscribeEvent
    public void onMobSpawnCheck(LivingSpawnEvent.CheckSpawn event) {
        Object entity = invokeZeroArgIfPresent(event, "getEntityLiving", "getEntity");
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        double x = readDouble(event, "getX");
        double y = readDouble(event, "getY");
        double z = readDouble(event, "getZ");
        if (shouldDenyHostileSpawn(world, entity, x, y, z)) {
            event.setResult(Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Object entity = invokeZeroArgIfPresent(event, "getEntity");
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        if (shouldDenyHostileSpawn(
            world,
            entity,
            TeleportSupport.playerX(entity),
            TeleportSupport.playerY(entity),
            TeleportSupport.playerZ(entity)
        )) {
            cancel(event);
            killEntity(entity);
        }
    }

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyExplosions) {
            return;
        }
        Object explosion = invokeZeroArgIfPresent(event, "getExplosion");
        Object world = explosionWorld(event, explosion);
        double x = readExplosionCoordinate(explosion, 0, "explosionX", "field_77284_b");
        double y = readExplosionCoordinate(explosion, 1, "explosionY", "field_77286_c");
        double z = readExplosionCoordinate(explosion, 2, "explosionZ", "field_77285_d");
        if (isProtected(world, x, y, z, snapshot)) {
            cancel(event);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyExplosions) {
            return;
        }
        Object explosion = invokeZeroArgIfPresent(event, "getExplosion");
        Object world = explosionWorld(event, explosion);
        Object affectedBlocks = invokeZeroArgIfPresent(event, "getAffectedBlocks");
        if (!(affectedBlocks instanceof List<?>)) {
            return;
        }
        double x = readExplosionCoordinate(explosion, 0, "explosionX", "field_77284_b");
        double y = readExplosionCoordinate(explosion, 1, "explosionY", "field_77286_c");
        double z = readExplosionCoordinate(explosion, 2, "explosionZ", "field_77285_d");
        if (isProtected(world, x, y, z, snapshot)) {
            ((List<?>) affectedBlocks).clear();
            return;
        }
        Iterator<?> iterator = ((List<?>) affectedBlocks).iterator();
        while (iterator.hasNext()) {
            Object pos = iterator.next();
            if (isProtected(world, blockPosition(pos), snapshot)) {
                iterator.remove();
            }
        }
    }

    synchronized void load() {
        config = loadConfig();
        logger.info(String.format(
            "Защита спавна загружена. enabled=%s mode=%s radius=%d protectBlocks=%s denyHostileSpawns=%s denyExplosions=%s allowOperatorBypass=%s",
            config.enabled,
            config.regionMode,
            config.radius,
            config.protectBlocks,
            config.denyHostileSpawns,
            config.denyExplosions,
            config.allowOperatorBypass
        ));
    }

    synchronized void setEnabled(boolean enabled) {
        Config current = config();
        config = new Config(
            enabled,
            current.radius,
            current.centerMode,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass,
            current.regionMode,
            current.minX,
            current.minY,
            current.minZ,
            current.maxX,
            current.maxY,
            current.maxZ
        );
        save(config);
    }

    synchronized void setRadius(int radius) {
        Config current = config();
        config = new Config(
            current.enabled,
            clampRadius(radius),
            current.centerMode,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass,
            REGION_MODE_RADIUS,
            current.minX,
            current.minY,
            current.minZ,
            current.maxX,
            current.maxY,
            current.maxZ
        );
        save(config);
    }

    synchronized void setFixedCenter(int dimension, double centerX, double centerZ) {
        Config current = config();
        config = new Config(
            current.enabled,
            current.radius,
            CENTER_MODE_FIXED,
            dimension,
            centerX,
            centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass,
            REGION_MODE_RADIUS,
            current.minX,
            current.minY,
            current.minZ,
            current.maxX,
            current.maxY,
            current.maxZ
        );
        save(config);
    }

    synchronized void useWorldSpawnCenter() {
        Config current = config();
        config = new Config(
            current.enabled,
            current.radius,
            CENTER_MODE_WORLDSPAWN,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass,
            REGION_MODE_RADIUS,
            current.minX,
            current.minY,
            current.minZ,
            current.maxX,
            current.maxY,
            current.maxZ
        );
        save(config);
    }

    synchronized void setBoxRegion(int dimension, double x1, double y1, double z1, double x2, double y2, double z2) {
        Config current = config();
        config = new Config(
            current.enabled,
            current.radius,
            current.centerMode,
            dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass,
            REGION_MODE_BOX,
            x1,
            y1,
            z1,
            x2,
            y2,
            z2
        );
        save(config);
    }

    Config config() {
        Config snapshot = config;
        if (snapshot == null) {
            load();
            snapshot = config;
        }
        return snapshot;
    }

    boolean isProtectedPlayerPosition(Object player) {
        Config snapshot = config();
        Object world = actorWorld(player);
        if (isProtected(world, TeleportSupport.playerX(player), TeleportSupport.playerY(player), TeleportSupport.playerZ(player), snapshot)) {
            return true;
        }
        return isProtected(
            TeleportSupport.playerDimension(player),
            TeleportSupport.playerX(player),
            TeleportSupport.playerY(player),
            TeleportSupport.playerZ(player),
            snapshot
        );
    }

    boolean isProtectedBlockPosition(Object world, Object blockPos) {
        return isProtected(world, blockPosition(blockPos), config());
    }

    boolean shouldDenyHostileSpawn(Object world, Object entity, double x, double y, double z) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyHostileSpawns || !isHostile(entity)) {
            return false;
        }
        Object checkedWorld = world == null ? actorWorld(entity) : world;
        if (isProtected(checkedWorld, x, y, z, snapshot)) {
            return true;
        }
        return entity != null && isProtected(TeleportSupport.playerDimension(entity), x, y, z, snapshot);
    }

    String describePlayerPosition(Object player) {
        return String.format(
            "dim=%d x=%d y=%d z=%d protected=%s",
            Integer.valueOf(TeleportSupport.playerDimension(player)),
            Integer.valueOf((int) Math.floor(TeleportSupport.playerX(player))),
            Integer.valueOf((int) Math.floor(TeleportSupport.playerY(player))),
            Integer.valueOf((int) Math.floor(TeleportSupport.playerZ(player))),
            Boolean.valueOf(isProtectedPlayerPosition(player))
        );
    }

    private Config loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать конфиг защиты спавна. Используем значения по умолчанию.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clampRadius(readInt(properties, "radius", DEFAULT_RADIUS)),
            readCenterMode(properties),
            readInt(properties, "dimension", 0),
            readDouble(properties, "centerX", 0.0D),
            readDouble(properties, "centerZ", 0.0D),
            readBoolean(properties, "protectBlocks", true),
            readBoolean(properties, "denyHostileSpawns", true),
            readBoolean(properties, "denyExplosions", true),
            readBoolean(properties, "allowOperatorBypass", false),
            readRegionMode(properties),
            readDouble(properties, "minX", -DEFAULT_RADIUS),
            readDouble(properties, "minY", MIN_REGION_Y),
            readDouble(properties, "minZ", -DEFAULT_RADIUS),
            readDouble(properties, "maxX", DEFAULT_RADIUS),
            readDouble(properties, "maxY", MAX_REGION_Y),
            readDouble(properties, "maxZ", DEFAULT_RADIUS)
        );

        if (
            !Files.exists(configPath)
                || !properties.containsKey("allowOperatorBypass")
                || !properties.containsKey("centerMode")
                || !properties.containsKey("regionMode")
        ) {
            save(loaded);
        }
        return loaded;
    }

    private void save(Config value) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value.enabled));
        properties.setProperty("radius", Integer.toString(value.radius));
        properties.setProperty("centerMode", value.centerMode);
        properties.setProperty("dimension", Integer.toString(value.dimension));
        properties.setProperty("centerX", Double.toString(value.centerX));
        properties.setProperty("centerZ", Double.toString(value.centerZ));
        properties.setProperty("protectBlocks", Boolean.toString(value.protectBlocks));
        properties.setProperty("denyHostileSpawns", Boolean.toString(value.denyHostileSpawns));
        properties.setProperty("denyExplosions", Boolean.toString(value.denyExplosions));
        properties.setProperty("allowOperatorBypass", Boolean.toString(value.allowOperatorBypass));
        properties.setProperty("regionMode", value.regionMode);
        properties.setProperty("minX", Double.toString(value.minX));
        properties.setProperty("minY", Double.toString(value.minY));
        properties.setProperty("minZ", Double.toString(value.minZ));
        properties.setProperty("maxX", Double.toString(value.maxX));
        properties.setProperty("maxY", Double.toString(value.maxY));
        properties.setProperty("maxZ", Double.toString(value.maxZ));

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "Защита спавна ObsidianGate. Регион центрируется по точке /setworldspawn в overworld.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось записать конфиг защиты спавна.", exception);
        }
    }

    private boolean isProtectedEvent(Object event, BlockPosition position, Object actor, Config snapshot) {
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        return isProtectedPosition(world, position, actor, snapshot);
    }

    private boolean isProtectedPosition(Object world, BlockPosition position, Object actor, Config snapshot) {
        if (isProtected(world, position, snapshot)) {
            return true;
        }
        Object actorWorld = actorWorld(actor);
        if (isProtected(actorWorld, position, snapshot)) {
            return true;
        }
        return actor != null && position != null && isProtected(
            TeleportSupport.playerDimension(actor),
            position.x,
            position.y,
            position.z,
            snapshot
        );
    }

    private boolean isProtected(Object world, BlockPosition position, Config snapshot) {
        if (world == null || position == null || dimension(world) != snapshot.dimension) {
            return false;
        }
        if (REGION_MODE_BOX.equals(snapshot.regionMode)) {
            return isInsideBox(position.x, position.y, position.z, snapshot);
        }

        Center center = protectedCenter(world, snapshot);
        return center != null && Math.abs(position.x - center.x) <= snapshot.radius && Math.abs(position.z - center.z) <= snapshot.radius;
    }

    private boolean isProtected(Object world, double x, double z, Config snapshot) {
        if (world == null || dimension(world) != snapshot.dimension) {
            return false;
        }
        if (REGION_MODE_BOX.equals(snapshot.regionMode)) {
            return isInsideBox(x, Double.NaN, z, snapshot);
        }
        Center center = protectedCenter(world, snapshot);
        return center != null && Math.abs(x - center.x) <= snapshot.radius && Math.abs(z - center.z) <= snapshot.radius;
    }

    private boolean isProtected(Object world, double x, double y, double z, Config snapshot) {
        if (world == null || dimension(world) != snapshot.dimension) {
            return false;
        }
        if (REGION_MODE_BOX.equals(snapshot.regionMode)) {
            return isInsideBox(x, y, z, snapshot);
        }
        Center center = protectedCenter(world, snapshot);
        return center != null && Math.abs(x - center.x) <= snapshot.radius && Math.abs(z - center.z) <= snapshot.radius;
    }

    private static boolean isProtected(int dimension, double x, double y, double z, Config snapshot) {
        if (dimension != snapshot.dimension) {
            return false;
        }
        if (REGION_MODE_BOX.equals(snapshot.regionMode)) {
            return isInsideBox(x, y, z, snapshot);
        }
        return Math.abs(x - snapshot.centerX) <= snapshot.radius && Math.abs(z - snapshot.centerZ) <= snapshot.radius;
    }

    private static boolean isInsideBox(double x, double y, double z, Config snapshot) {
        if (x < snapshot.minX || x > snapshot.maxX || z < snapshot.minZ || z > snapshot.maxZ) {
            return false;
        }
        return Double.isNaN(y) || (y >= snapshot.minY && y <= snapshot.maxY);
    }

    private Center protectedCenter(Object world, Config snapshot) {
        if (CENTER_MODE_FIXED.equals(snapshot.centerMode)) {
            return new Center(snapshot.centerX, snapshot.centerZ);
        }

        Object spawn = invokeZeroArgIfPresent(world, "getSpawnPoint", "func_175694_M", "T");
        if (spawn == null) {
            return null;
        }
        BlockPosition position = blockPosition(spawn);
        return new Center(
            position.x,
            position.z
        );
    }

    private static Object actorWorld(Object actor) {
        return readFieldIfPresent(actor, "world", "field_70170_p", "l");
    }

    boolean canBypass(Object entity, Config snapshot) {
        if (entity == null) {
            return false;
        }
        if (ADMIN_PLAYER_NAME.equalsIgnoreCase(TeleportSupport.playerName(entity))) {
            return true;
        }
        if (!snapshot.allowOperatorBypass) {
            return false;
        }
        Object result = invokeIfPresent(entity, new Object[] { Integer.valueOf(2), "spawnprotect" }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private boolean isHostile(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            return Class.forName(HOSTILE_MARKER_CLASS).isInstance(entity);
        } catch (ClassNotFoundException ignored) {
            return entity.getClass().getName().contains(".monster.");
        }
    }

    private static Object explosionWorld(Object event, Object explosion) {
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        if (world != null) {
            return world;
        }
        return readFieldIfPresent(explosion, "world", "worldObj", "field_77287_j");
    }

    private int dimension(Object world) {
        Object provider = readFieldIfPresent(world, "provider", "field_73011_w", "s");
        Object value = invokeZeroArgIfPresent(provider, "getDimension", "func_186058_p", "i");
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static void cancel(Object event) {
        invokeIfPresent(event, new Object[] { Boolean.TRUE }, "setCanceled");
    }

    private static void killEntity(Object entity) {
        invokeIfPresent(entity, new Object[0], "setDead", "func_70106_y");
    }

    private static void denyInteraction(Object event) {
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseBlock");
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseItem");
    }

    private static double readDouble(Object target, String methodName) {
        Object value = invokeZeroArgIfPresent(target, methodName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static double readExplosionCoordinate(Object explosion, int vectorIndex, String... fieldNames) {
        Object value = readFieldIfPresent(explosion, fieldNames);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        Object vector = invokeZeroArgIfPresent(explosion, "getPosition");
        if (vector == null) {
            return 0.0D;
        }
        Object[] fields = {
            readFieldIfPresent(vector, "x", "field_72450_a"),
            readFieldIfPresent(vector, "y", "field_72448_b"),
            readFieldIfPresent(vector, "z", "field_72449_c")
        };
        Object vectorValue = fields[vectorIndex];
        return vectorValue instanceof Number ? ((Number) vectorValue).doubleValue() : 0.0D;
    }

    private static double readBlockCoordinate(Object blockPos, String deobfuscatedName, String srgName, String obfuscatedName) {
        Object value = invokeZeroArgIfPresent(blockPos, deobfuscatedName, srgName, obfuscatedName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static BlockPosition blockPosition(Object blockPos) {
        return new BlockPosition(
            readBlockCoordinate(blockPos, "getX", "func_177958_n", "p"),
            readBlockCoordinate(blockPos, "getY", "func_177956_o", "q"),
            readBlockCoordinate(blockPos, "getZ", "func_177952_p", "r")
        );
    }

    private static String describe(BlockPosition position) {
        return "x=" + (int) Math.floor(position.x) + " y=" + (int) Math.floor(position.y) + " z=" + (int) Math.floor(position.z);
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            Method exactMethod = findDeclaredMethod(type, safeArgs, methodNames);
            if (exactMethod != null) {
                return invokeMethod(target, exactMethod, safeArgs);
            }

            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ignored) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    return invokeMethod(target, method, safeArgs);
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Method findDeclaredMethod(Class<?> type, Object[] args, String... methodNames) {
        Class<?>[][] parameterTypes = parameterTypeCandidates(args);
        for (String methodName : methodNames) {
            for (Class<?>[] parameters : parameterTypes) {
                try {
                    Method method = type.getDeclaredMethod(methodName, parameters);
                    if (methodMatches(method, args, methodNames)) {
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (LinkageError ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object invokeMethod(Object target, Method method, Object[] args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke " + method.getName() + ".", exception);
        }
    }

    private static Class<?>[][] parameterTypeCandidates(Object[] args) {
        if (args.length == 0) {
            return new Class<?>[][] { new Class<?>[0] };
        }

        int total = 1;
        Class<?>[][] candidatesByArgument = new Class<?>[args.length][];
        for (int i = 0; i < args.length; i++) {
            candidatesByArgument[i] = parameterTypeCandidates(args[i]);
            total *= candidatesByArgument[i].length;
        }

        Class<?>[][] combinations = new Class<?>[total][args.length];
        for (int combination = 0; combination < total; combination++) {
            int divisor = 1;
            for (int argument = 0; argument < args.length; argument++) {
                Class<?>[] candidates = candidatesByArgument[argument];
                combinations[combination][argument] = candidates[(combination / divisor) % candidates.length];
                divisor *= candidates.length;
            }
        }
        return combinations;
    }

    private static Class<?>[] parameterTypeCandidates(Object value) {
        if (value == null) {
            return new Class<?>[] { Object.class };
        }
        if (value instanceof Integer) {
            return new Class<?>[] { Integer.TYPE, Integer.class, Number.class, Object.class };
        }
        if (value instanceof Double) {
            return new Class<?>[] { Double.TYPE, Double.class, Number.class, Object.class };
        }
        if (value instanceof Float) {
            return new Class<?>[] { Float.TYPE, Float.class, Number.class, Object.class };
        }
        if (value instanceof Boolean) {
            return new Class<?>[] { Boolean.TYPE, Boolean.class, Object.class };
        }
        if (value instanceof Long) {
            return new Class<?>[] { Long.TYPE, Long.class, Number.class, Object.class };
        }
        if (value instanceof Short) {
            return new Class<?>[] { Short.TYPE, Short.class, Number.class, Object.class };
        }
        if (value instanceof Byte) {
            return new Class<?>[] { Byte.TYPE, Byte.class, Number.class, Object.class };
        }
        if (value instanceof Character) {
            return new Class<?>[] { Character.TYPE, Character.class, Object.class };
        }
        return new Class<?>[] { value.getClass(), Object.class };
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
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
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

    private static double readDouble(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String readCenterMode(Properties properties) {
        String value = properties.getProperty("centerMode", CENTER_MODE_WORLDSPAWN).trim().toLowerCase();
        return CENTER_MODE_FIXED.equals(value) ? CENTER_MODE_FIXED : CENTER_MODE_WORLDSPAWN;
    }

    private static String readRegionMode(Properties properties) {
        String value = properties.getProperty("regionMode", REGION_MODE_RADIUS).trim().toLowerCase();
        return REGION_MODE_BOX.equals(value) ? REGION_MODE_BOX : REGION_MODE_RADIUS;
    }

    private static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_RADIUS, radius));
    }

    private static double clampRegionY(double y) {
        return Math.max(MIN_REGION_Y, Math.min(MAX_REGION_Y, y));
    }

    static final class Config {
        final boolean enabled;
        final int radius;
        final String centerMode;
        final int dimension;
        final double centerX;
        final double centerZ;
        final boolean protectBlocks;
        final boolean denyHostileSpawns;
        final boolean denyExplosions;
        final boolean allowOperatorBypass;
        final String regionMode;
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        private Config(
            boolean enabled,
            int radius,
            String centerMode,
            int dimension,
            double centerX,
            double centerZ,
            boolean protectBlocks,
            boolean denyHostileSpawns,
            boolean denyExplosions,
            boolean allowOperatorBypass
        ) {
            this(
                enabled,
                radius,
                centerMode,
                dimension,
                centerX,
                centerZ,
                protectBlocks,
                denyHostileSpawns,
                denyExplosions,
                allowOperatorBypass,
                REGION_MODE_RADIUS,
                -radius,
                MIN_REGION_Y,
                -radius,
                radius,
                MAX_REGION_Y,
                radius
            );
        }

        private Config(
            boolean enabled,
            int radius,
            String centerMode,
            int dimension,
            double centerX,
            double centerZ,
            boolean protectBlocks,
            boolean denyHostileSpawns,
            boolean denyExplosions,
            boolean allowOperatorBypass,
            String regionMode,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
        ) {
            this.enabled = enabled;
            this.radius = radius;
            this.centerMode = centerMode;
            this.dimension = dimension;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.protectBlocks = protectBlocks;
            this.denyHostileSpawns = denyHostileSpawns;
            this.denyExplosions = denyExplosions;
            this.allowOperatorBypass = allowOperatorBypass;
            this.regionMode = REGION_MODE_BOX.equals(regionMode) ? REGION_MODE_BOX : REGION_MODE_RADIUS;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(clampRegionY(minY), clampRegionY(maxY));
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(clampRegionY(minY), clampRegionY(maxY));
            this.maxZ = Math.max(minZ, maxZ);
        }

        private static Config defaults() {
            return new Config(true, DEFAULT_RADIUS, CENTER_MODE_WORLDSPAWN, 0, 0.0D, 0.0D, true, true, true, false);
        }
    }

    private static final class Center {
        private final double x;
        private final double z;

        private Center(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class BlockPosition {
        private final double x;
        private final double y;
        private final double z;

        private BlockPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
