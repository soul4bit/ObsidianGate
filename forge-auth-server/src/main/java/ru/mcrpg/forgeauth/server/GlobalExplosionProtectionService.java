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
import net.minecraftforge.event.world.ExplosionEvent;
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
            "Global explosion protection loaded. enabled=%s preventBlockDamage=%s",
            config.enabled,
            config.preventBlockDamage
        ));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        int removed = clearAffectedBlocks(event);
        if (removed > 0 && config().logBlockedExplosions) {
            logger.info("Global explosion protection removed " + removed + " block(s) from an explosion.");
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
            readBoolean(properties, "logBlockedExplosions", false)
        );
        save(loaded);
        return loaded;
    }

    private void save(Config snapshot) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(snapshot.enabled));
        properties.setProperty("preventBlockDamage", Boolean.toString(snapshot.preventBlockDamage));
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

    static final class Config {
        final boolean enabled;
        final boolean preventBlockDamage;
        final boolean logBlockedExplosions;

        Config(boolean enabled, boolean preventBlockDamage, boolean logBlockedExplosions) {
            this.enabled = enabled;
            this.preventBlockDamage = preventBlockDamage;
            this.logBlockedExplosions = logBlockedExplosions;
        }

        static Config defaults() {
            return new Config(true, true, false);
        }
    }
}
