package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class TreeFellingService {

    private static final int MAX_BLOCKS_PER_SWING = 24;
    private static final int MAX_SEARCH_NODES = 96;

    private final Logger logger;
    private boolean felling;

    TreeFellingService(Logger logger) {
        this.logger = logger;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (felling || event.isCanceled()) {
            return;
        }

        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        Object origin = invokeZeroArgIfPresent(event, "getPos");
        if (player == null || world == null || origin == null || isRemote(world)) {
            return;
        }
        if (!Boolean.TRUE.equals(invokeZeroArgIfPresent(player, "isSneaking", "func_70093_af")) || isCreative(player)) {
            return;
        }
        Object tool = invokeZeroArgIfPresent(player, "getHeldItemMainhand", "func_184614_ca");
        if (!isUsableAxe(tool) || !isLog(world, origin)) {
            return;
        }

        int availableDurability = Math.max(0, maxDamage(tool) - itemDamage(tool));
        int maxExtraBlocks = Math.min(MAX_BLOCKS_PER_SWING - 1, Math.max(0, availableDurability - 1));
        if (maxExtraBlocks <= 0) {
            return;
        }

        BlockPosition originPosition = blockPosition(origin);
        Set<BlockPosition> logs = collectConnectedLogs(world, originPosition, maxExtraBlocks);
        if (logs.isEmpty()) {
            return;
        }

        int broken = breakExtraLogs(world, player, tool, logs);
        if (broken > 0) {
            logger.fine("Tree QoL broke " + broken + " extra logs for " + TeleportSupport.playerName(player) + ".");
        }
    }

    private int breakExtraLogs(Object world, Object player, Object tool, Set<BlockPosition> logs) {
        int broken = 0;
        felling = true;
        try {
            for (BlockPosition position : logs) {
                if (isEmptyStack(tool) || itemDamage(tool) >= maxDamage(tool)) {
                    break;
                }
                Object pos = position.pos;
                if (pos == null) {
                    continue;
                }
                if (!isLog(world, pos)) {
                    continue;
                }
                if (Boolean.TRUE.equals(invokeIfPresent(world, new Object[] { pos, Boolean.TRUE }, "destroyBlock", "func_175655_b"))) {
                    broken++;
                    invokeIfPresent(tool, new Object[] { Integer.valueOf(1), player }, "damageItem", "func_77972_a");
                }
            }
        } finally {
            felling = false;
        }
        return broken;
    }

    private static Set<BlockPosition> collectConnectedLogs(Object world, BlockPosition origin, int limit) {
        Set<BlockPosition> result = new HashSet<BlockPosition>();
        Set<BlockPosition> visited = new HashSet<BlockPosition>();
        ArrayDeque<BlockPosition> queue = new ArrayDeque<BlockPosition>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && visited.size() <= MAX_SEARCH_NODES && result.size() < limit) {
            BlockPosition current = queue.removeFirst();
            for (BlockPosition next : neighbors(current)) {
                if (!visited.add(next)) {
                    continue;
                }
                Object pos = next.pos;
                if (pos == null || !isLog(world, pos)) {
                    continue;
                }
                if (!next.equals(origin)) {
                    result.add(next);
                    if (result.size() >= limit) {
                        break;
                    }
                }
                queue.addLast(next);
            }
        }
        return result;
    }

    private static BlockPosition[] neighbors(BlockPosition pos) {
        return new BlockPosition[] {
            pos.offset(0, 1, 0),
            pos.offset(1, 0, 0),
            pos.offset(-1, 0, 0),
            pos.offset(0, 0, 1),
            pos.offset(0, 0, -1),
            pos.offset(1, 1, 0),
            pos.offset(-1, 1, 0),
            pos.offset(0, 1, 1),
            pos.offset(0, 1, -1),
            pos.offset(0, -1, 0)
        };
    }

    private static boolean isUsableAxe(Object stack) {
        if (isEmptyStack(stack) || !Boolean.TRUE.equals(invokeZeroArgIfPresent(stack, "isItemStackDamageable", "func_77984_f"))) {
            return false;
        }
        String className = "";
        Object item = invokeZeroArgIfPresent(stack, "getItem", "func_77973_b");
        Object toolClasses = invokeIfPresent(item, new Object[] { stack }, "getToolClasses");
        if (toolClasses instanceof Set<?> && ((Set<?>) toolClasses).contains("axe")) {
            return itemDamage(stack) < maxDamage(stack);
        }
        if (item != null) {
            className = item.getClass().getName().toLowerCase();
        }
        return className.contains("axe") && itemDamage(stack) < maxDamage(stack);
    }

    private static boolean isLog(Object world, Object pos) {
        Object state = invokeIfPresent(world, new Object[] { pos }, "getBlockState", "func_180495_p");
        Object block = invokeZeroArgIfPresent(state, "getBlock", "func_177230_c");
        return Boolean.TRUE.equals(invokeIfPresent(block, new Object[] { world, pos }, "isWood"));
    }

    private static int maxDamage(Object stack) {
        return intValue(invokeZeroArgIfPresent(stack, "getMaxDamage", "func_77958_k"));
    }

    private static int itemDamage(Object stack) {
        return intValue(invokeZeroArgIfPresent(stack, "getItemDamage", "func_77952_i"));
    }

    private static boolean isEmptyStack(Object stack) {
        return stack == null || Boolean.TRUE.equals(invokeZeroArgIfPresent(stack, "isEmpty", "func_190926_b"));
    }

    private static boolean isRemote(Object world) {
        return Boolean.TRUE.equals(readFieldIfPresent(world, "isRemote", "field_72995_K"));
    }

    private static boolean isCreative(Object player) {
        Object capabilities = readFieldIfPresent(player, "capabilities", "field_71075_bZ");
        return Boolean.TRUE.equals(readFieldIfPresent(capabilities, "isCreativeMode", "field_75098_d"));
    }

    private static BlockPosition blockPosition(Object blockPos) {
        return new BlockPosition(
            intValue(invokeZeroArgIfPresent(blockPos, "getX", "func_177958_n", "p")),
            intValue(invokeZeroArgIfPresent(blockPos, "getY", "func_177956_o", "q")),
            intValue(invokeZeroArgIfPresent(blockPos, "getZ", "func_177952_p", "r")),
            blockPos
        );
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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
                    } catch (ReflectiveOperationException ignored) {
                        return null;
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

    private static final class BlockPosition {
        final int x;
        final int y;
        final int z;
        final Object pos;

        private BlockPosition(int x, int y, int z, Object pos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.pos = pos;
        }

        private BlockPosition offset(int dx, int dy, int dz) {
            Object next = invokeIfPresent(pos, new Object[] {
                Integer.valueOf(dx),
                Integer.valueOf(dy),
                Integer.valueOf(dz)
            }, "add", "func_177982_a");
            return new BlockPosition(x + dx, y + dy, z + dz, next);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BlockPosition)) {
                return false;
            }
            BlockPosition that = (BlockPosition) other;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
