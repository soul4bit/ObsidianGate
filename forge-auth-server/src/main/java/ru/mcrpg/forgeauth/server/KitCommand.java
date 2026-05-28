package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class KitCommand {

    private static final String COMMAND_NAME = "kit";
    private static final String SUBJECT = "Стартовый набор";
    private static final String PLAYER_CLASS_NAME = "net.minecraft.entity.player.EntityPlayerMP";

    private KitCommand() {
    }

    static void register(FMLServerStartingEvent event, KitService service, PlayerRoleLookup auth) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                KitCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(service, auth)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /kit.", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final KitService service;
        private final PlayerRoleLookup auth;

        private Handler(KitService service, PlayerRoleLookup auth) {
            this.service = service;
            this.auth = auth;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return "/" + COMMAND_NAME + " start";
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[1], args[2], service, auth);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(args == null || args.length < 3 ? null : args[2]);
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(name)) {
                return Integer.valueOf(compareTo(args == null ? null : args[0]));
            }
            if ("toString".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(COMMAND_NAME.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }

        private static List<String> tabCompletions(Object arguments) {
            String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
            if (args.length <= 1 && (args.length == 0 || "start".startsWith(args[0].toLowerCase(Locale.ROOT)))) {
                return Collections.singletonList("start");
            }
            return Collections.emptyList();
        }
    }

    private static void execute(Object sender, Object arguments, KitService service, PlayerRoleLookup auth) {
        Object player = resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/kit") + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length != 1 || !"start".equalsIgnoreCase(args[0])) {
            ServerChat.usage(sender, "/kit start");
            return;
        }

        String playerName = playerName(player);
        String accountId = auth == null ? "" : auth.accountIdFor(player);
        if (accountId == null || accountId.trim().isEmpty()) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "доступен после подтверждения авторизации лаунчера.");
            return;
        }

        String claimId = accountClaimId(accountId);
        if (service.hasClaimedStart(claimId)) {
            ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "уже был получен на этом аккаунте.");
            return;
        }

        try {
            List<Object> kitItems = createStartKit();
            int dropped = giveItems(player, kitItems);
            service.recordStartClaim(claimId, playerName, accountId, playerUuid(player));
            if (dropped > 0) {
                ServerChat.status(player, ServerChat.Tone.WARNING, SUBJECT, "выдан, но часть предметов выпала рядом: инвентарь заполнен.");
            } else {
                ServerChat.status(player, ServerChat.Tone.SUCCESS, SUBJECT, "выдан.");
            }
        } catch (RuntimeException exception) {
            ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "не удалось выдать: " + exception.getMessage());
        }
    }

    private static List<Object> createStartKit() {
        List<Object> items = new ArrayList<Object>();
        items.add(namedStack("minecraft:leather_helmet", 1, "\u00A76Кит Старт: кожаный шлем"));
        items.add(namedStack("minecraft:leather_chestplate", 1, "\u00A76Кит Старт: кожаная куртка"));
        items.add(namedStack("minecraft:leather_leggings", 1, "\u00A76Кит Старт: кожаные штаны"));
        items.add(namedStack("minecraft:leather_boots", 1, "\u00A76Кит Старт: кожаные ботинки"));
        items.add(itemStack("minecraft:stone_sword", 1));
        items.add(itemStack("minecraft:stone_axe", 1));
        items.add(itemStack("minecraft:stone_pickaxe", 1));
        items.add(itemStack("minecraft:stone_shovel", 1));
        items.add(itemStack("minecraft:cooked_beef", 32));
        return items;
    }

    private static Object namedStack(String itemId, int count, String displayName) {
        Object stack = itemStack(itemId, count);
        applyDisplay(stack, displayName, "\u00A77ObsidianGate | одноразовый стартовый набор");
        return stack;
    }

    private static Object itemStack(String itemId, int count) {
        try {
            Class<?> itemType = Class.forName("net.minecraft.item.Item");
            Class<?> stackType = Class.forName("net.minecraft.item.ItemStack");
            Object item = invokeStatic(itemType, new Object[] { itemId }, "getByNameOrId", "func_111206_d");
            if (item == null) {
                throw new IllegalStateException("Неизвестный предмет: " + itemId);
            }
            Constructor<?> constructor = stackType.getConstructor(itemType, Integer.TYPE);
            return constructor.newInstance(item, Integer.valueOf(count));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось создать stack предмета " + itemId + ".", exception);
        }
    }

    private static void applyDisplay(Object stack, String displayName, String loreLine) {
        try {
            Class<?> nbtBaseType = Class.forName("net.minecraft.nbt.NBTBase");
            Class<?> compoundType = Class.forName("net.minecraft.nbt.NBTTagCompound");
            Class<?> listType = Class.forName("net.minecraft.nbt.NBTTagList");
            Class<?> stringType = Class.forName("net.minecraft.nbt.NBTTagString");

            Object display = compoundType.getConstructor().newInstance();
            invokeRequiredMethod(display, new Object[] { "Name", displayName }, "setString", "func_74778_a");

            Object lore = listType.getConstructor().newInstance();
            Object loreValue = stringType.getConstructor(String.class).newInstance(loreLine);
            invokeRequiredMethod(lore, new Object[] { loreValue }, "appendTag", "func_74742_a");
            invokeRequiredMethod(display, new Object[] { "Lore", lore }, "setTag", "func_74782_a");

            invokeRequiredMethod(stack, new Object[] { "display", display }, "setTagInfo", "func_77983_a");
            if (!nbtBaseType.isInstance(display)) {
                throw new IllegalStateException("Display tag не является NBT tag.");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось добавить подпись предмета из кита.", exception);
        }
    }

    private static int giveItems(Object player, List<Object> items) {
        Object inventory = readFieldIfPresent(player, "inventory", "field_71071_by");
        if (inventory == null) {
            throw new IllegalStateException("Инвентарь игрока недоступен.");
        }

        int dropped = 0;
        for (Object stack : items) {
            Object added = invokeIfPresent(inventory, new Object[] { stack }, "addItemStackToInventory", "func_70441_a");
            if (Boolean.TRUE.equals(added)) {
                continue;
            }
            if (!dropItem(player, stack)) {
                throw new IllegalStateException("Инвентарь игрока заполнен, а выбросить предмет рядом не удалось.");
            }
            dropped++;
        }
        return dropped;
    }

    private static boolean dropItem(Object player, Object stack) {
        if (invokeMethodIfPresent(player, new Object[] { stack, Boolean.FALSE }, "dropItem", "func_71019_a")) {
            return true;
        }
        return invokeMethodIfPresent(player, new Object[] { stack, Float.valueOf(0.0F) }, "entityDropItem", "func_70099_a");
    }

    private static Object resolvePlayer(Object sender) {
        if (isPlayer(sender)) {
            return sender;
        }
        Object entity = invokeZeroArgIfPresent(sender, "getCommandSenderEntity", "func_174793_f");
        return isPlayer(entity) ? entity : null;
    }

    private static boolean isPlayer(Object value) {
        if (value == null) {
            return false;
        }

        Class<?> type = value.getClass();
        while (type != null) {
            if (PLAYER_CLASS_NAME.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String accountClaimId(String accountId) {
        return "account:" + accountId.trim();
    }

    private static String playerName(Object player) {
        Object name = invokeZeroArgIfPresent(player, "getName", "func_70005_c_");
        return name == null ? "unknown" : name.toString();
    }

    private static String playerUuid(Object player) {
        Object uniqueId = invokeZeroArgIfPresent(player, "getUniqueID", "func_110124_au");
        return uniqueId instanceof UUID ? uniqueId.toString() : "";
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeStatic(Class<?> type, Object[] args, String... methodNames) {
        Object result = invokeIfPresent(type, null, args, methodNames);
        if (result == null) {
            throw new IllegalStateException("Не найден метод " + String.join("/", methodNames) + ".");
        }
        return result;
    }

    private static void invokeRequiredMethod(Object target, Object[] args, String... methodNames) {
        if (!invokeMethodIfPresent(target, args, methodNames)) {
            throw new IllegalStateException("Не найден метод " + String.join("/", methodNames) + ".");
        }
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        return invokeIfPresent(target.getClass(), target, args, methodNames);
    }

    private static Object invokeIfPresent(Class<?> type, Object target, Object[] args, String... methodNames) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean invokeMethodIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return false;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, safeArgs);
                        return true;
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return false;
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
        if (parameterType == Long.TYPE) {
            return value instanceof Long;
        }
        if (parameterType == Short.TYPE) {
            return value instanceof Short;
        }
        if (parameterType == Byte.TYPE) {
            return value instanceof Byte;
        }
        if (parameterType == Character.TYPE) {
            return value instanceof Character;
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

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0.0F);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0.0D);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
