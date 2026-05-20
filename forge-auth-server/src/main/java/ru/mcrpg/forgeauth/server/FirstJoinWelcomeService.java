package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

final class FirstJoinWelcomeService {

    private static final Path DEFAULT_SEEN_PATH = Paths.get("obsidiangate", "welcome-seen.properties");
    private static final String SEEN_PREFIX = "seen.";
    private static final String SUBJECT = "Добро пожаловать";

    private final Logger logger;
    private final Path seenPath;
    private final Properties seen = new Properties();
    private boolean loaded;

    FirstJoinWelcomeService(Logger logger) {
        this(logger, DEFAULT_SEEN_PATH);
    }

    FirstJoinWelcomeService(Logger logger, Path seenPath) {
        this.logger = logger;
        this.seenPath = seenPath;
    }

    synchronized void load() {
        seen.clear();
        if (Files.exists(seenPath)) {
            try (InputStream input = Files.newInputStream(seenPath)) {
                seen.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать отметки первого входа. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Отметки первого входа загружены из %s. Записей=%d", seenPath, seen.size()));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            greetIfFirstJoin(readPlayer(event));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Не удалось отправить приветствие первого входа.", exception);
        }
    }

    boolean greetIfFirstJoin(Object player) {
        if (player == null) {
            return false;
        }

        String playerId = PlayerIdentity.id(player);
        String playerName = PlayerIdentity.name(player);
        if (!markSeen(playerId, playerName)) {
            return false;
        }

        ServerChat.status(
            player,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            playerName + ", заберите стартовый набор: " + ServerChat.command("/kit start") + "."
        );
        ServerChat.helpHint(player, "Основные команды: " + ServerChat.command("/help") + ".");
        logger.info("Первое приветствие отправлено игроку " + playerName + ".");
        return true;
    }

    synchronized boolean hasSeen(String playerId) {
        ensureLoaded();
        return seen.containsKey(seenKey(playerId));
    }

    private synchronized boolean markSeen(String playerId, String playerName) {
        ensureLoaded();
        String key = seenKey(playerId);
        if (seen.containsKey(key)) {
            return false;
        }
        seen.setProperty(key, Instant.now().toString() + "|" + (playerName == null ? "" : playerName));
        save();
        return true;
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = seenPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(seenPath)) {
                seen.store(output, "ObsidianGate first join welcome markers.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить отметки первого входа.", exception);
        }
    }

    private static String seenKey(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return SEEN_PREFIX + playerId.trim().toLowerCase(Locale.ROOT);
    }

    private static Object readPlayer(Object event) {
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        return player == null ? readFieldIfPresent(event, "player") : player;
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
}
