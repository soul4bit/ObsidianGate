package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class BackLocationService {

    private static final Path DEFAULT_LOCATIONS_PATH = Paths.get("obsidiangate", "back-locations.properties");
    private static final String LOCATION_PREFIX = "back.";

    private final Logger logger;
    private final Path locationsPath;
    private final Properties locations = new Properties();
    private boolean loaded;

    BackLocationService(Logger logger) {
        this(logger, DEFAULT_LOCATIONS_PATH);
    }

    BackLocationService(Logger logger, Path locationsPath) {
        this.logger = logger;
        this.locationsPath = locationsPath;
    }

    synchronized void load() {
        locations.clear();
        if (Files.exists(locationsPath)) {
            try (InputStream input = Files.newInputStream(locationsPath)) {
                locations.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать точки /back. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Точки /back загружены из %s. Записей=%d", locationsPath, locations.size()));
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Object entity = invokeZeroArgIfPresent(event, "getEntityLiving", "getEntity");
        if (!TeleportSupport.isPlayer(entity)) {
            return;
        }
        try {
            recordDeath(entity);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Не удалось сохранить точку смерти для /back.", exception);
        }
    }

    synchronized void recordDeath(Object player) {
        ensureLoaded();
        String playerId = PlayerIdentity.id(player);
        DeathLocation location = DeathLocation.fromPlayer(player);
        locations.setProperty(locationKey(playerId), location.serialize());
        save();
        logger.info(String.format(
            "Точка /back сохранена для %s: dim=%d x=%.1f y=%.1f z=%.1f",
            PlayerIdentity.name(player),
            location.dimension,
            location.x,
            location.y,
            location.z
        ));
    }

    synchronized DeathLocation lastDeath(String playerId) {
        ensureLoaded();
        String value = locations.getProperty(locationKey(playerId));
        return value == null ? null : DeathLocation.parse(value);
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = locationsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(locationsPath)) {
                locations.store(output, "ObsidianGate /back death locations.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить точки /back.", exception);
        }
    }

    private static String locationKey(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return LOCATION_PREFIX + playerId.trim().toLowerCase(Locale.ROOT);
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                for (String methodName : methodNames) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(target);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    static final class DeathLocation {
        final int dimension;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        DeathLocation(int dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = Math.max(1.0D, y);
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private static DeathLocation fromPlayer(Object player) {
            return new DeathLocation(
                TeleportSupport.playerDimension(player),
                TeleportSupport.playerX(player),
                TeleportSupport.playerY(player),
                TeleportSupport.playerZ(player),
                TeleportSupport.playerYaw(player),
                TeleportSupport.playerPitch(player)
            );
        }

        private String serialize() {
            return dimension + "," + x + "," + y + "," + z + "," + yaw + "," + pitch;
        }

        private static DeathLocation parse(String value) {
            String[] parts = value == null ? new String[0] : value.split(",");
            if (parts.length != 6) {
                throw new IllegalArgumentException("Некорректная запись /back.");
            }
            return new DeathLocation(
                Integer.parseInt(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
            );
        }
    }
}
