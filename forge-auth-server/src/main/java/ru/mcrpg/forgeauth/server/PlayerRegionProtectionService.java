package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class PlayerRegionProtectionService {

    private static final String SUBJECT = "Приват";

    private final Logger logger;
    private final PlayerRegionService regions;

    PlayerRegionProtectionService(Logger logger, PlayerRegionService regions) {
        this.logger = logger;
        this.regions = regions;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        BlockPosition position = blockPosition(invokeZeroArgIfPresent(event, "getPos"));
        ProtectionCheck check = check(invokeZeroArgIfPresent(event, "getWorld"), position, player);
        if (check.allowed) {
            return;
        }
        cancel(event);
        event.setExpToDrop(0);
        logger.info("Приват заблокировал ломание блока: " + describe(position) + " player=" + TeleportSupport.playerName(player) + " region=" + check.region.name);
        deny(player, "ломать блоки в чужом регионе нельзя. Регион: " + ServerChat.value(check.region.name) + ".");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        ProtectionCheck check = check(invokeZeroArgIfPresent(event, "getWorld"), blockPosition(invokeZeroArgIfPresent(event, "getPos")), player);
        if (check.allowed) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        deny(player, "ломать блоки в чужом регионе нельзя. Регион: " + ServerChat.value(check.region.name) + ".");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerBreakSpeed(PlayerEvent.BreakSpeed event) {
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        Object world = readFieldIfPresent(player, "world", "field_70170_p", "l");
        ProtectionCheck check = check(world, blockPosition(invokeZeroArgIfPresent(event, "getPos")), player);
        if (check.allowed) {
            return;
        }
        invokeIfPresent(event, new Object[] { Float.valueOf(0.0F) }, "setNewSpeed");
        cancel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        BlockPosition position = blockPosition(invokeZeroArgIfPresent(event, "getPos"));
        ProtectionCheck check = check(invokeZeroArgIfPresent(event, "getWorld"), position, player);
        if (check.allowed) {
            return;
        }
        cancel(event);
        logger.info("Приват заблокировал установку блока: " + describe(position) + " player=" + TeleportSupport.playerName(player) + " region=" + check.region.name);
        deny(player, "ставить блоки в чужом регионе нельзя. Регион: " + ServerChat.value(check.region.name) + ".");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Object entity = invokeZeroArgIfPresent(event, "getEntity");
        ProtectionCheck check = check(invokeZeroArgIfPresent(event, "getWorld"), blockPosition(invokeZeroArgIfPresent(event, "getPos")), entity);
        if (check.allowed) {
            return;
        }
        cancel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        ProtectionCheck check = check(invokeZeroArgIfPresent(event, "getWorld"), blockPosition(invokeZeroArgIfPresent(event, "getPos")), player);
        if (check.allowed) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        deny(player, "взаимодействие с блоками в чужом регионе закрыто. Регион: " + ServerChat.value(check.region.name) + ".");
    }

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        Object explosion = invokeZeroArgIfPresent(event, "getExplosion");
        Object world = eventWorld(event, explosion);
        if (explosionProtected(world, explosion)) {
            cancel(event);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Object explosion = invokeZeroArgIfPresent(event, "getExplosion");
        Object world = eventWorld(event, explosion);
        Object affectedBlocks = invokeZeroArgIfPresent(event, "getAffectedBlocks");
        if (!(affectedBlocks instanceof List<?>)) {
            return;
        }

        if (explosionProtected(world, explosion)) {
            ((List<?>) affectedBlocks).clear();
            return;
        }

        Iterator<?> iterator = ((List<?>) affectedBlocks).iterator();
        while (iterator.hasNext()) {
            Object pos = iterator.next();
            PlayerRegionService.Region region = regionAt(world, blockPosition(pos));
            if (region != null) {
                iterator.remove();
            }
        }
    }

    boolean isProtectedFrom(Object world, Object blockPos, Object actor) {
        return !check(world, blockPosition(blockPos), actor).allowed;
    }

    private ProtectionCheck check(Object world, BlockPosition position, Object actor) {
        PlayerRegionService.Region region = regionAt(world, position);
        if (region == null) {
            return ProtectionCheck.allowed();
        }
        if (canBypass(actor)) {
            return ProtectionCheck.allowed();
        }
        if (actor != null && TeleportSupport.isPlayer(actor) && region.allows(PlayerIdentity.id(actor), PlayerIdentity.name(actor))) {
            return ProtectionCheck.allowed();
        }
        return ProtectionCheck.denied(region);
    }

    private PlayerRegionService.Region regionAt(Object world, BlockPosition position) {
        if (world == null || position == null) {
            return null;
        }
        return regions.findAt(dimension(world), position.x, position.y, position.z);
    }

    private boolean explosionProtected(Object world, Object explosion) {
        double x = readExplosionCoordinate(explosion, 0, "explosionX", "field_77284_b");
        double y = readExplosionCoordinate(explosion, 1, "explosionY", "field_77286_c");
        double z = readExplosionCoordinate(explosion, 2, "explosionZ", "field_77285_d");
        return world != null && regions.findAt(dimension(world), x, y, z) != null;
    }

    private static Object eventWorld(Object event, Object explosion) {
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        if (world != null) {
            return world;
        }
        return readFieldIfPresent(explosion, "world", "worldObj", "field_77287_j");
    }

    private boolean canBypass(Object actor) {
        if (actor == null || !TeleportSupport.isPlayer(actor)) {
            return false;
        }
        Object result = invokeIfPresent(actor, new Object[] { Integer.valueOf(2), "claim" }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private static void deny(Object player, String message) {
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, message);
    }

    private static int dimension(Object world) {
        Object provider = readFieldIfPresent(world, "provider", "field_73011_w", "s");
        Object value = invokeZeroArgIfPresent(provider, "getDimension", "func_186058_p", "i");
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static void cancel(Object event) {
        invokeIfPresent(event, new Object[] { Boolean.TRUE }, "setCanceled");
    }

    private static void denyInteraction(Object event) {
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseBlock");
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseItem");
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

    private static BlockPosition blockPosition(Object blockPos) {
        if (blockPos == null) {
            return null;
        }
        return new BlockPosition(
            readBlockCoordinate(blockPos, "getX", "func_177958_n", "p"),
            readBlockCoordinate(blockPos, "getY", "func_177956_o", "q"),
            readBlockCoordinate(blockPos, "getZ", "func_177952_p", "r")
        );
    }

    private static double readBlockCoordinate(Object blockPos, String deobfuscatedName, String srgName, String obfuscatedName) {
        Object value = invokeZeroArgIfPresent(blockPos, deobfuscatedName, srgName, obfuscatedName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static String describe(BlockPosition position) {
        if (position == null) {
            return "unknown";
        }
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
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (LinkageError ignored) {
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
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

    private static final class ProtectionCheck {
        final boolean allowed;
        final PlayerRegionService.Region region;

        private ProtectionCheck(boolean allowed, PlayerRegionService.Region region) {
            this.allowed = allowed;
            this.region = region;
        }

        private static ProtectionCheck allowed() {
            return new ProtectionCheck(true, null);
        }

        private static ProtectionCheck denied(PlayerRegionService.Region region) {
            return new ProtectionCheck(false, region);
        }
    }

    private static final class BlockPosition {
        final double x;
        final double y;
        final double z;

        private BlockPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
