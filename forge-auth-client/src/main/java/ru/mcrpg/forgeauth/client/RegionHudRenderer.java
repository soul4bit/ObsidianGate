package ru.mcrpg.forgeauth.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class RegionHudRenderer {

    private static volatile String regionName = "";

    static void update(String value) {
        regionName = value == null ? "" : value.trim();
    }

    static void clear() {
        regionName = "";
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || regionName.isEmpty()) {
            return;
        }

        Object minecraft = invokeStatic("net.minecraft.client.Minecraft", "getMinecraft", "func_71410_x");
        Object font = field(minecraft, "fontRenderer", "field_71466_p");
        if (font == null) {
            return;
        }

        String text = "Регион: " + regionName;
        int width = integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, text));
        drawRect(5, 5, width + 15, 20, 0x99000000);
        invoke(
            font,
            new String[] { "drawStringWithShadow", "func_175063_a" },
            text,
            Float.valueOf(10.0F),
            Float.valueOf(10.0F),
            Integer.valueOf(0xFFF1C75B)
        );
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Class<?> gui = Class.forName("net.minecraft.client.gui.Gui");
            for (Method method : gui.getMethods()) {
                if (("drawRect".equals(method.getName()) || "func_73734_a".equals(method.getName()))
                    && method.getParameterTypes().length == 5) {
                    method.invoke(null, left, top, right, bottom, color);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // The label still remains readable through the font shadow.
        }
    }

    private static Object invokeStatic(String className, String... names) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (matches(method.getName(), names) && method.getParameterTypes().length == 0) {
                    return method.invoke(null);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static Object invoke(Object target, String[] names, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (matches(method.getName(), names) && method.getParameterTypes().length == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Try another mapped name or overload.
                }
            }
        }
        return null;
    }

    private static Object field(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    // Try another mapped name or superclass.
                }
            }
        }
        return null;
    }

    private static boolean matches(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
