package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class TeleportGuardService {

    static final String CHANNEL_HOME = "home";
    static final String CHANNEL_RTP = "rtp";
    static final String CHANNEL_SPAWN = "spawn";
    static final long DEFAULT_COMBAT_MILLIS = 15000L;

    private final ConcurrentMap<String, Long> combatUntil = new ConcurrentHashMap<String, Long>();
    private final ConcurrentMap<String, Long> cooldownUntil = new ConcurrentHashMap<String, Long>();
    private final long combatMillis;

    TeleportGuardService() {
        this(DEFAULT_COMBAT_MILLIS);
    }

    TeleportGuardService(long combatMillis) {
        this.combatMillis = combatMillis;
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Object victim = invokeZeroArgIfPresent(event, "getEntityLiving", "getEntity");
        Object source = invokeZeroArgIfPresent(event, "getSource");
        Object attacker = invokeZeroArgIfPresent(source, "getTrueSource", "getImmediateSource", "getEntity");
        long now = System.currentTimeMillis();

        if (TeleportSupport.isPlayer(victim) && attacker != null && attacker != victim) {
            markCombat(victim, now);
        }
        if (TeleportSupport.isPlayer(attacker) && victim != null && attacker != victim) {
            markCombat(attacker, now);
        }
    }

    int combatRemainingSeconds(Object player) {
        return remainingSeconds(combatUntil, PlayerIdentity.id(player), System.currentTimeMillis());
    }

    boolean isInCombat(Object player) {
        return combatRemainingSeconds(player) > 0;
    }

    int cooldownRemainingSeconds(String playerId, String channel) {
        return remainingSeconds(cooldownUntil, cooldownKey(playerId, channel), System.currentTimeMillis());
    }

    void startCooldown(String playerId, String channel, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return;
        }
        cooldownUntil.put(cooldownKey(playerId, channel), Long.valueOf(System.currentTimeMillis() + cooldownMillis));
    }

    void markCombat(Object player, long nowMillis) {
        if (player == null) {
            return;
        }
        combatUntil.put(PlayerIdentity.id(player), Long.valueOf(nowMillis + combatMillis));
    }

    private static int remainingSeconds(ConcurrentMap<String, Long> values, String key, long nowMillis) {
        Long until = values.get(key);
        if (until == null) {
            return 0;
        }
        long remaining = until.longValue() - nowMillis;
        if (remaining <= 0L) {
            values.remove(key, until);
            return 0;
        }
        return (int) ((remaining + 999L) / 1000L);
    }

    private static String cooldownKey(String playerId, String channel) {
        return playerId.toLowerCase(Locale.ROOT) + "\n" + channel;
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
}
