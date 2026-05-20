package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class HomeRespawnService {

    private static final int RESPAWN_DELAY_TICKS = 2;
    private static final int MAX_ATTEMPTS = 5;
    private static final String SUBJECT = "Возрождение";

    private final Logger logger;
    private final HomeService homes;
    private final ConcurrentMap<String, PendingRespawn> pendingRespawns =
        new ConcurrentHashMap<String, PendingRespawn>();

    HomeRespawnService(Logger logger, HomeService homes) {
        this.logger = logger;
        this.homes = homes;
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (isEndConquered(event)) {
            return;
        }
        queueRespawn(readFieldIfPresent(event, "player"));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        runServerEndTick();
    }

    void queueRespawn(Object player) {
        if (player == null) {
            return;
        }

        String playerId = PlayerIdentity.id(player);
        if (homes.primaryHomeName(playerId) == null) {
            return;
        }
        pendingRespawns.put(playerId, new PendingRespawn(player));
    }

    void runServerEndTick() {
        Iterator<Map.Entry<String, PendingRespawn>> iterator = pendingRespawns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingRespawn> entry = iterator.next();
            PendingRespawn pending = entry.getValue();
            if (pending.delayTicks > 0) {
                pending.delayTicks--;
                continue;
            }

            if (teleportToPrimaryHome(pending.player, serverFromPlayer(pending.player))) {
                pendingRespawns.remove(entry.getKey(), pending);
                continue;
            }

            pending.attemptsLeft--;
            if (pending.attemptsLeft <= 0) {
                pendingRespawns.remove(entry.getKey(), pending);
            }
        }
    }

    boolean teleportToPrimaryHome(Object player, Object server) {
        if (player == null) {
            return false;
        }

        String playerId = PlayerIdentity.id(player);
        String homeName = homes.primaryHomeName(playerId);
        if (homeName == null) {
            return false;
        }

        HomeService.HomeLocation location = homes.getHome(playerId, homeName);
        if (location == null) {
            return false;
        }

        try {
            Object moved = TeleportSupport.teleportToDimension(
                server,
                player,
                location.dimension,
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch
            );
            ServerChat.status(moved, ServerChat.Tone.SUCCESS, SUBJECT, "вы появились дома " + ServerChat.value(homeName) + ".");
            logger.info(String.format(
                "Игрок %s после смерти перенесен в home '%s'.",
                TeleportSupport.playerName(moved),
                homeName
            ));
            return true;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Не удалось перенести игрока после смерти в home " + homeName + ".", exception);
            return false;
        }
    }

    private static Object serverFromPlayer(Object player) {
        Object server = invokeZeroArgIfPresent(player, "getServer", "func_184102_h");
        if (server != null) {
            return server;
        }
        return readFieldIfPresent(player, "mcServer", "server", "field_71133_b");
    }

    private static boolean isEndConquered(Object event) {
        Object value = invokeZeroArgIfPresent(event, "isEndConquered", "getEndConquered");
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        value = readFieldIfPresent(event, "endConquered");
        return value instanceof Boolean && ((Boolean) value).booleanValue();
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

    private static final class PendingRespawn {
        private final Object player;
        private int delayTicks = RESPAWN_DELAY_TICKS;
        private int attemptsLeft = MAX_ATTEMPTS;

        private PendingRespawn(Object player) {
            this.player = player;
        }
    }
}
