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
    private static final int TEXT_OWNER = 0xFFFFD86B;
    private static final int TEXT_MEMBER = 0xFF7EE8FF;
    private static final int TEXT_VISITOR = 0xFFFFA0A0;
    private static volatile Method drawRectMethod;
    private static volatile String regionName = "";
    private static volatile String ownerName = "";
    private static volatile int relation = RegionHudMessage.RELATION_VISITOR;

    static void update(String value) {
        update(value, "", RegionHudMessage.RELATION_VISITOR);
    }

    static void update(String regionValue, String ownerValue, int relationValue) {
        String normalizedRegion = normalize(regionValue);
        if (normalizedRegion.isEmpty()) {
            clear();
            return;
        }
        regionName = normalizedRegion;
        ownerName = normalizeOwner(ownerValue);
        relation = normalizeRelation(relationValue);
    }

    static void clear() {
        regionName = "";
        ownerName = "";
        relation = RegionHudMessage.RELATION_VISITOR;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        String displayName = displayText(regionName, ownerName);
        int displayRelation = relation;
        if (regionName.isEmpty()) {
            return;
        }

        Object minecraft = invokeStatic("net.minecraft.client.Minecraft", "getMinecraft", "func_71410_x");
        Object font = field(minecraft, "fontRenderer", "field_71466_p");
        if (font == null) {
            return;
        }

        int screenWidth = scaledWidth(event, minecraft);
        String text = displayName;
        int availableTextWidth = screenWidth <= 0
            ? 160
            : Math.max(20, screenWidth - MARGIN * 2 - ICON_WIDTH - ICON_GAP);
        text = fitText(font, text, availableTextWidth);
        int width = stringWidth(font, text);
        int totalWidth = ICON_WIDTH + ICON_GAP + width;
        int left = screenWidth <= 0 ? MARGIN : Math.max(MARGIN, (screenWidth - totalWidth) / 2);
        int top = MARGIN;
        drawShield(left, top + 1, displayRelation);
        invoke(
            font,
            new String[] { "drawStringWithShadow", "func_175063_a" },
            text,
            Float.valueOf(left + ICON_WIDTH + ICON_GAP),
            Float.valueOf(top + 2.0F),
            Integer.valueOf(textColor(displayRelation))
        );
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || "none".equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String normalizeOwner(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeRelation(int value) {
        if (value == RegionHudMessage.RELATION_OWNER || value == RegionHudMessage.RELATION_MEMBER) {
            return value;
        }
        return RegionHudMessage.RELATION_VISITOR;
    }

    private static String displayText(String region, String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            return region;
        }
        return region + " \u00b7 owner " + owner.trim();
    }

    private static String fitText(Object font, String text, int maxWidth) {
        if (stringWidth(font, text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = stringWidth(font, suffix);
        int bodyWidth = Math.max(0, maxWidth - suffixWidth);
        String result = text;
        while (!result.isEmpty() && stringWidth(font, result) > bodyWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }

    private static int textColor(int relation) {
        if (relation == RegionHudMessage.RELATION_OWNER) {
            return TEXT_OWNER;
        }
        if (relation == RegionHudMessage.RELATION_MEMBER) {
            return TEXT_MEMBER;
        }
        return TEXT_VISITOR;
    }

    private static void drawShield(int x, int y, int relation) {
        int highlight;
        int upper;
        int middle;
        int lower;
        int dark;
        if (relation == RegionHudMessage.RELATION_OWNER) {
            highlight = 0xFFFFF4B8;
            upper = 0xFFFFC247;
            middle = 0xFFD68A16;
            lower = 0xFF9D5D0B;
            dark = 0xFF6F3B06;
        } else if (relation == RegionHudMessage.RELATION_MEMBER) {
            highlight = 0xFFB6ECFF;
            upper = 0xFF37B8FF;
            middle = 0xFF166CEB;
            lower = 0xFF124BB7;
            dark = 0xFF0D2E82;
        } else {
            highlight = 0xFFFFD1D1;
            upper = 0xFFFF6B6B;
            middle = 0xFFD43D3D;
            lower = 0xFF962828;
            dark = 0xFF641818;
        }
        drawRect(x + 2, y, x + 8, y + 1, highlight);
        drawRect(x + 1, y + 1, x + 9, y + 3, upper);
        drawRect(x, y + 3, x + 10, y + 6, middle);
        drawRect(x + 1, y + 6, x + 9, y + 8, lower);
        drawRect(x + 3, y + 8, x + 7, y + 10, dark);
        drawRect(x + 4, y + 10, x + 6, y + 11, dark);
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

    private static int scaledWidth(Object event, Object minecraft) {
        int eventWidth = scaledWidthFrom(invoke(event, new String[] { "getResolution" }));
        if (eventWidth > 0) {
            return eventWidth;
        }
        int fieldWidth = scaledWidthFrom(field(event, "resolution"));
        if (fieldWidth > 0) {
            return fieldWidth;
        }
        int displayWidth = integer(field(minecraft, "displayWidth", "field_71443_c"));
        int guiScale = integer(field(field(minecraft, "gameSettings", "field_71474_y"), "guiScale", "field_74335_Z"));
        if (displayWidth <= 0) {
            return 0;
        }
        int scaleFactor = 1;
        int targetScale = guiScale == 0 ? 1000 : guiScale;
        while (scaleFactor < targetScale && displayWidth / (scaleFactor + 1) >= 320) {
            scaleFactor++;
        }
        return displayWidth / scaleFactor;
    }

    private static int scaledWidthFrom(Object resolution) {
        return integer(invoke(resolution, new String[] { "getScaledWidth", "func_78326_a" }));
    }

    private static int stringWidth(Object font, String text) {
        return integer(invoke(font, new String[] { "getStringWidth", "func_78256_a" }, text));
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
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!matches(method.getName(), names) || method.getParameterTypes().length != args.length) {
                    continue;
                }
                try {
                    method.setAccessible(true);
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
