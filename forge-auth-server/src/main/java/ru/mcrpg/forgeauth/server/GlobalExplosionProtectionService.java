package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class GlobalExplosionProtectionService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-explosion-protection.properties");

    private final Logger logger;
    private final Path configPath;
    private volatile Config config;

    GlobalExplosionProtectionService(Logger logger) {
        this(logger, CONFIG_PATH);
    }

    GlobalExplosionProtectionService(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.config = Config.defaults();
    }

    synchronized void load() {
        config = loadConfig();
        logger.info(String.format(
            "Global explosion protection loaded. enabled=%s preventBlockDamage=%s preventFireSpread=%s preventFireTick=%s",
            config.enabled,
            config.preventBlockDamage,
            config.preventFireSpread,
            config.preventFireTick
        ));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        int removed = clearAffectedBlocks(event);
        if (removed > 0 && config().logBlockedExplosions) {
            logger.info("Global explosion protection removed " + removed + " block(s) from an explosion.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (shouldCancelFireSpread(event)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        if (disableFireTick(ServerReflection.invoke(event, new String[] { "getWorld" }))) {
            logger.info("Global explosion protection set doFireTick=false for a loaded world.");
        }
    }

    int clearAffectedBlocks(Object event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.preventBlockDamage) {
            return 0;
        }
        Object rawAffected = ServerReflection.invoke(event, new String[] { "getAffectedBlocks" });
        if (!(rawAffected instanceof List<?>)) {
            return 0;
        }
        List<?> affectedBlocks = (List<?>) rawAffected;
        int removed = affectedBlocks.size();
        affectedBlocks.clear();
        return removed;
    }

    boolean shouldCancelFireSpread(Object event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.preventFireSpread) {
            return false;
        }
        Object world = ServerReflection.invoke(event, new String[] { "getWorld" });
        Object pos = ServerReflection.invoke(event, new String[] { "getPos" });
        String blockName = blockName(world, pos);
        return blockName.contains("fire");
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
                logger.log(Level.WARNING, "Could not read " + configPath + ". Using defaults.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            readBoolean(properties, "preventBlockDamage", true),
            readBoolean(properties, "preventFireSpread", true),
            readBoolean(properties, "preventFireTick", true),
            readBoolean(properties, "logBlockedExplosions", false)
        );
        save(loaded);
        return loaded;
    }

    private void save(Config snapshot) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(snapshot.enabled));
        properties.setProperty("preventBlockDamage", Boolean.toString(snapshot.preventBlockDamage));
        properties.setProperty("preventFireSpread", Boolean.toString(snapshot.preventFireSpread));
        properties.setProperty("preventFireTick", Boolean.toString(snapshot.preventFireTick));
        properties.setProperty("logBlockedExplosions", Boolean.toString(snapshot.logBlockedExplosions));
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "ObsidianGate global explosion protection.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not save " + configPath + ".", exception);
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String blockName(Object world, Object pos) {
        Object state = ServerReflection.invoke(world, new String[] { "getBlockState", "func_180495_p" }, pos);
        Object block = ServerReflection.invoke(state, new String[] { "getBlock", "func_177230_c" });
        Object registryName = ServerReflection.invoke(block, new String[] { "getRegistryName" });
        return String.valueOf(registryName).toLowerCase();
    }

    boolean disableFireTick(Object world) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.preventFireTick || world == null) {
            return false;
        }
        Object gameRules = ServerReflection.invoke(world, new String[] { "getGameRules", "func_82736_K" });
        if (gameRules == null) {
            return false;
        }
        ServerReflection.invoke(gameRules, new String[] { "setOrCreateGameRule", "func_82764_b" }, "doFireTick", "false");
        return true;
    }

    static final class Config {
        final boolean enabled;
        final boolean preventBlockDamage;
        final boolean preventFireSpread;
        final boolean preventFireTick;
        final boolean logBlockedExplosions;

        Config(
            boolean enabled,
            boolean preventBlockDamage,
            boolean preventFireSpread,
            boolean preventFireTick,
            boolean logBlockedExplosions
        ) {
            this.enabled = enabled;
            this.preventBlockDamage = preventBlockDamage;
            this.preventFireSpread = preventFireSpread;
            this.preventFireTick = preventFireTick;
            this.logBlockedExplosions = logBlockedExplosions;
        }

        static Config defaults() {
            return new Config(true, true, true, true, false);
        }
    }
}
