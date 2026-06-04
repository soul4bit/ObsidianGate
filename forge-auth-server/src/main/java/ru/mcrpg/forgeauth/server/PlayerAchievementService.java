package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class PlayerAchievementService {

    private static final Path STATE_PATH = Paths.get("obsidiangate", "player-achievements.properties");
    private static final String SUBJECT = "Коллекция";
    private static final String HOSTILE_MARKER_CLASS = "net.minecraft.entity.monster.IMob";
    private static final String DIVINE_HOSTILE_BASE_CLASS = "divinerpg.objects.entities.entity.EntityDivineMob";

    private final Logger logger;
    private final Path statePath;
    private final Map<String, PlayerProgress> players = new LinkedHashMap<String, PlayerProgress>();

    PlayerAchievementService(Logger logger) {
        this(logger, STATE_PATH);
    }

    PlayerAchievementService(Logger logger, Path statePath) {
        this.logger = logger;
        this.statePath = statePath;
    }

    synchronized void load() {
        players.clear();
        Properties properties = new Properties();
        if (Files.exists(statePath)) {
            try (InputStream input = Files.newInputStream(statePath)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not read player achievements. Starting with empty state.", exception);
            }
        }

        Set<String> ids = new HashSet<String>();
        for (String key : properties.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot > 0) {
                ids.add(key.substring(0, dot));
            }
        }
        for (String id : ids) {
            PlayerProgress progress = new PlayerProgress(id);
            progress.name = properties.getProperty(id + ".name", "player");
            progress.ores = readLong(properties, id + ".ores", 0L);
            progress.diamonds = readLong(properties, id + ".diamonds", 0L);
            progress.mobKills = readLong(properties, id + ".mobKills", 0L);
            progress.hostileKills = readLong(properties, id + ".hostileKills", 0L);
            progress.activeTitle = properties.getProperty(id + ".activeTitle", "");
            progress.unlocked.addAll(split(properties.getProperty(id + ".unlocked", "")));
            unlockEarnedTitles(progress, null, false);
            players.put(id, progress);
        }
        logger.info("Player achievements loaded: " + players.size() + " players.");
    }

    synchronized void shutdown() {
        save();
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Object player = invokeIfPresent(event, new Object[0], "getPlayer");
        String blockId = blockId(event);
        if (player != null && isOreBlock(blockId)) {
            recordOreBreakForPlayer(player, blockId);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Object victim = invokeIfPresent(event, new Object[0], "getEntityLiving", "getEntity");
        Object killer = killerFromDeathEvent(event);
        if (TeleportSupport.isPlayer(killer) && victim != null && !TeleportSupport.isPlayer(victim)) {
            recordMobKillForPlayer(killer, entityId(victim), isHostile(victim));
        }
    }

    synchronized void recordOreBreakForPlayer(Object player, String blockId) {
        PlayerProgress progress = progressFor(player);
        progress.ores++;
        if (isDiamondOre(blockId)) {
            progress.diamonds++;
        }
        unlockEarnedTitles(progress, player, true);
        save();
    }

    synchronized void recordMobKillForPlayer(Object player, String entityId, boolean hostile) {
        PlayerProgress progress = progressFor(player);
        progress.mobKills++;
        if (hostile) {
            progress.hostileKills++;
        }
        unlockEarnedTitles(progress, player, true);
        save();
    }

    synchronized PlayerProgress progressFor(Object player) {
        String id = PlayerIdentity.id(player);
        PlayerProgress progress = players.get(id);
        if (progress == null) {
            progress = new PlayerProgress(id);
            players.put(id, progress);
        }
        String name = PlayerIdentity.name(player);
        if (name != null && !name.trim().isEmpty()) {
            progress.name = name.trim();
        }
        return progress;
    }

    synchronized PlayerProgress snapshotFor(Object player) {
        return progressFor(player).copy();
    }

    synchronized boolean setActiveTitle(Object player, String titleId) {
        PlayerProgress progress = progressFor(player);
        AchievementTitle title = AchievementTitleCatalog.find(titleId);
        if (title == null || !progress.unlocked.contains(title.id())) {
            return false;
        }
        progress.activeTitle = title.id();
        save();
        return true;
    }

    synchronized void clearActiveTitle(Object player) {
        PlayerProgress progress = progressFor(player);
        progress.activeTitle = "";
        save();
    }

    synchronized String activeTitleLabelFor(Object player) {
        if (player == null) {
            return "";
        }
        PlayerProgress progress = progressFor(player);
        AchievementTitle title = AchievementTitleCatalog.find(progress.activeTitle);
        return title == null ? "" : title.coloredLabel();
    }

    synchronized List<PlayerProgress> top(AchievementTitle.Metric metric, int limit) {
        ArrayList<PlayerProgress> result = new ArrayList<PlayerProgress>();
        for (PlayerProgress progress : players.values()) {
            result.add(progress.copy());
        }
        Collections.sort(result, new Comparator<PlayerProgress>() {
            @Override
            public int compare(PlayerProgress left, PlayerProgress right) {
                int value = Long.compare(right.value(metric), left.value(metric));
                if (value != 0) {
                    return value;
                }
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        if (result.size() <= limit) {
            return result;
        }
        return new ArrayList<PlayerProgress>(result.subList(0, limit));
    }

    private void unlockEarnedTitles(PlayerProgress progress, Object player, boolean announce) {
        for (AchievementTitle title : AchievementTitleCatalog.all()) {
            if (!progress.unlocked.contains(title.id()) && title.isUnlocked(progress)) {
                progress.unlocked.add(title.id());
                if (announce && player != null) {
                    ServerChat.status(
                        player,
                        ServerChat.Tone.SUCCESS,
                        SUBJECT,
                        "открыт титул " + title.coloredLabel() + "\u00A7a. Включить: " + ServerChat.command("/ach set " + title.id())
                    );
                }
            }
        }
    }

    private void save() {
        Properties properties = new Properties();
        for (PlayerProgress progress : players.values()) {
            String id = progress.id;
            properties.setProperty(id + ".name", safe(progress.name));
            properties.setProperty(id + ".ores", Long.toString(progress.ores));
            properties.setProperty(id + ".diamonds", Long.toString(progress.diamonds));
            properties.setProperty(id + ".mobKills", Long.toString(progress.mobKills));
            properties.setProperty(id + ".hostileKills", Long.toString(progress.hostileKills));
            properties.setProperty(id + ".activeTitle", safe(progress.activeTitle));
            properties.setProperty(id + ".unlocked", join(progress.unlocked));
        }

        try {
            Path parent = statePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(statePath)) {
                properties.store(output, "ObsidianGate player achievement titles.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not write player achievements.", exception);
        }
    }

    private static String blockId(Object event) {
        Object state = invokeIfPresent(event, new Object[0], "getState");
        Object block = invokeIfPresent(state, new Object[0], "getBlock", "func_177230_c");
        Object registryName = invokeIfPresent(block, new Object[0], "getRegistryName");
        return registryName == null ? "" : registryName.toString().toLowerCase(Locale.ROOT);
    }

    static boolean isOreBlock(String blockId) {
        String normalized = blockId == null ? "" : blockId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.contains("ore")
            || normalized.endsWith(":coal_ore")
            || normalized.endsWith(":iron_ore")
            || normalized.endsWith(":gold_ore")
            || normalized.endsWith(":lapis_ore")
            || normalized.endsWith(":redstone_ore")
            || normalized.endsWith(":lit_redstone_ore")
            || normalized.endsWith(":diamond_ore")
            || normalized.endsWith(":emerald_ore")
            || normalized.contains("cinnabar")
            || normalized.contains("amber")
            || normalized.contains("quartz");
    }

    private static boolean isDiamondOre(String blockId) {
        String normalized = blockId == null ? "" : blockId.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("diamond") && normalized.contains("ore");
    }

    private static Object killerFromDeathEvent(Object event) {
        Object source = invokeIfPresent(event, new Object[0], "getSource");
        Object killer = invokeIfPresent(source, new Object[0], "getTrueSource", "func_76346_g");
        if (TeleportSupport.isPlayer(killer)) {
            return killer;
        }
        return invokeIfPresent(source, new Object[0], "getImmediateSource", "func_76364_f");
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

    private static String entityId(Object entity) {
        Object registryName = invokeIfPresent(entity, new Object[0], "getName");
        return registryName == null ? entity.getClass().getName() : registryName.toString();
    }

    private static long readLong(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Set<String> split(String raw) {
        HashSet<String> result = new HashSet<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String value = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String join(Set<String> values) {
        ArrayList<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (String value : sorted) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        Object instance = target instanceof Class<?> ? null : target;
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
        return false;
    }

    static final class PlayerProgress {
        private final String id;
        private String name = "player";
        private long ores;
        private long diamonds;
        private long mobKills;
        private long hostileKills;
        private String activeTitle = "";
        private final Set<String> unlocked = new HashSet<String>();

        private PlayerProgress(String id) {
            this.id = id;
        }

        long value(AchievementTitle.Metric metric) {
            if (metric == AchievementTitle.Metric.ORES) {
                return ores;
            }
            if (metric == AchievementTitle.Metric.DIAMONDS) {
                return diamonds;
            }
            if (metric == AchievementTitle.Metric.MOB_KILLS) {
                return mobKills;
            }
            if (metric == AchievementTitle.Metric.HOSTILE_KILLS) {
                return hostileKills;
            }
            return 0L;
        }

        String name() {
            return name;
        }

        long ores() {
            return ores;
        }

        long diamonds() {
            return diamonds;
        }

        long mobKills() {
            return mobKills;
        }

        long hostileKills() {
            return hostileKills;
        }

        String activeTitle() {
            return activeTitle;
        }

        Set<String> unlocked() {
            return Collections.unmodifiableSet(unlocked);
        }

        private PlayerProgress copy() {
            PlayerProgress copy = new PlayerProgress(id);
            copy.name = name;
            copy.ores = ores;
            copy.diamonds = diamonds;
            copy.mobKills = mobKills;
            copy.hostileKills = hostileKills;
            copy.activeTitle = activeTitle;
            copy.unlocked.addAll(unlocked);
            return copy;
        }
    }
}
