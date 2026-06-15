package ru.mcrpg.forgeauth.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class RegionHudRenderer {

    private static final int MARGIN = 8;
    private static final int ICON_WIDTH = 10;
    private static final int ICON_GAP = 5;
    private static final int TEXT_COLOR = 0xFFFFD86B;
    private static volatile Method drawRectMethod;
    private static volatile String regionName = "";

    static void update(String value) {
        regionName = normalize(value);
    }

    static void clear() {
        regionName = "";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        String displayName = regionName;
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || displayName.isEmpty()) {
            return;
        }

        Object minecraft = invokeStatic("net.minecraft.client.Minecraft", "getMinecraft", "func_71410_x");
        Object font = field(minecraft, "fontRenderer", "field_71466_p");
        if (font == null) {
            return;
        }

        Object resolution = invoke(event, new String[] { "getResolution" });
        int screenWidth = integer(invoke(resolution, new String[] { "getScaledWidth", "func_78326_a" }));
        if (screenWidth <= 0) {
            return;
        }

        String text = displayName;
        int availableTextWidth = Math.max(20, screenWidth - MARGIN * 2 - ICON_WIDTH - ICON_GAP);
        text = fitText(font, text, availableTextWidth);
        int width = integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, text));
        int left = Math.max(MARGIN, screenWidth - width - ICON_WIDTH - ICON_GAP - MARGIN);
        int top = MARGIN;
        drawShield(left, top + 1);
        invoke(
            font,
            new String[] { "drawStringWithShadow", "func_175063_a" },
            text,
            Float.valueOf(left + ICON_WIDTH + ICON_GAP),
            Float.valueOf(top + 2.0F),
            Integer.valueOf(TEXT_COLOR)
        );
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || "none".equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String fitText(Object font, String text, int maxWidth) {
        if (integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, text)) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, suffix));
        int bodyWidth = Math.max(0, maxWidth - suffixWidth);
        String result = text;
        while (!result.isEmpty()
            && integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, result)) > bodyWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }

    private static void drawShield(int x, int y) {
        drawRect(x + 2, y, x + 8, y + 1, 0xFFB6ECFF);
        drawRect(x + 1, y + 1, x + 9, y + 3, 0xFF37B8FF);
        drawRect(x, y + 3, x + 10, y + 6, 0xFF166CEB);
        drawRect(x + 1, y + 6, x + 9, y + 8, 0xFF124BB7);
        drawRect(x + 3, y + 8, x + 7, y + 10, 0xFF0D2E82);
        drawRect(x + 4, y + 10, x + 6, y + 11, 0xFF0D2E82);
        drawRect(x + 4, y + 2, x + 6, y + 7, 0x66FFFFFF);
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Method method = drawRectMethod;
            if (method == null) {
                method = findDrawRectMethod();
                drawRectMethod = method;
            }
            if (method != null) {
                method.invoke(null, left, top, right, bottom, color);
            }
        } catch (ReflectiveOperationException ignored) {
            // The label still remains readable through the font shadow if the icon cannot be drawn.
        }
    }

    private static Method findDrawRectMethod() throws ClassNotFoundException {
        Class<?> gui = Class.forName("net.minecraft.client.gui.Gui");
        for (Method method : gui.getMethods()) {
            if (("drawRect".equals(method.getName()) || "func_73734_a".equals(method.getName()))
                && method.getParameterTypes().length == 5) {
                return method;
            }
        }
        return null;
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
