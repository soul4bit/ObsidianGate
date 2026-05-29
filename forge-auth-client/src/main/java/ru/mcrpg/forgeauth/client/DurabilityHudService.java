package ru.mcrpg.forgeauth.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class DurabilityHudService {

    private static final String PREFIX = "\u041f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c: ";
    private static final int BOTTOM_OFFSET = 62;
    private static final int PANEL_HEIGHT = 16;
    private static final int PANEL_PADDING_X = 7;
    private static final int PANEL_MIN_WIDTH = 92;

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }

        Object minecraft = minecraft();
        Object player = readFieldIfPresent(minecraft, "player", "thePlayer", "field_71439_g");
        Object gameSettings = readFieldIfPresent(minecraft, "gameSettings", "field_71474_y");
        if (player == null || Boolean.TRUE.equals(readFieldIfPresent(gameSettings, "showDebugInfo", "field_74330_P"))) {
            return;
        }

        Object stack = invokeZeroArgIfPresent(player, "getHeldItemMainhand", "func_184614_ca");
        if (!isDamageableStack(stack)) {
            return;
        }

        int maxDamage = intValue(invokeZeroArgIfPresent(stack, "getMaxDamage", "func_77958_k"));
        if (maxDamage <= 0) {
            return;
        }

        int itemDamage = intValue(invokeZeroArgIfPresent(stack, "getItemDamage", "func_77952_i"));
        int remaining = Math.max(0, maxDamage - itemDamage);
        double ratio = Math.max(0.0D, Math.min(1.0D, (double) remaining / (double) maxDamage));
        drawCenteredPanel(
            minecraft,
            invokeZeroArgIfPresent(event, "getResolution"),
            PREFIX + remaining + "/" + maxDamage,
            ratio,
            colorFor(ratio)
        );
    }

    private static boolean isDamageableStack(Object stack) {
        if (stack == null || Boolean.TRUE.equals(invokeZeroArgIfPresent(stack, "isEmpty", "func_190926_b"))) {
            return false;
        }
        return Boolean.TRUE.equals(invokeZeroArgIfPresent(stack, "isItemStackDamageable", "func_77984_f"));
    }

    private static void drawCenteredPanel(Object minecraft, Object resolution, String text, double ratio, int accentColor) {
        Object font = readFieldIfPresent(minecraft, "fontRenderer", "fontRendererObj", "field_71466_p");
        if (font == null || resolution == null) {
            return;
        }

        int width = intValue(invokeZeroArgIfPresent(resolution, "getScaledWidth", "func_78326_a"));
        int height = intValue(invokeZeroArgIfPresent(resolution, "getScaledHeight", "func_78328_b"));
        int textWidth = intValue(invokeIfPresent(font, new Object[] { text }, "getStringWidth", "func_78256_a"));
        int panelWidth = Math.max(PANEL_MIN_WIDTH, textWidth + PANEL_PADDING_X * 2);
        int left = (width - panelWidth) / 2;
        int top = height - BOTTOM_OFFSET;
        int right = left + panelWidth;
        int bottom = top + PANEL_HEIGHT;
        int barLeft = left + 4;
        int barTop = bottom - 4;
        int barRight = right - 4;
        int barFillRight = barLeft + (int) Math.round((barRight - barLeft) * ratio);

        drawRect(left, top, right, bottom, 0x99081018);
        drawRect(left, top, right, top + 1, 0x55FFFFFF);
        drawRect(left, bottom - 1, right, bottom, 0x66000000);
        drawRect(barLeft, barTop, barRight, barTop + 2, 0x66323A46);
        drawRect(barLeft, barTop, Math.max(barLeft, barFillRight), barTop + 2, accentColor);

        int textX = left + (panelWidth - textWidth) / 2;
        int textY = top + 3;
        invokeIfPresent(font, new Object[] {
            text,
            Float.valueOf(textX),
            Float.valueOf(textY),
            Integer.valueOf(0xF2F5F8)
        }, "drawStringWithShadow", "func_175063_a");
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Class<?> type = Class.forName("net.minecraft.client.gui.Gui");
            invokeIfPresent(null, type, new Object[] {
                Integer.valueOf(left),
                Integer.valueOf(top),
                Integer.valueOf(right),
                Integer.valueOf(bottom),
                Integer.valueOf(color)
            }, "drawRect", "func_73734_a");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static Object minecraft() {
        try {
            Class<?> type = Class.forName("net.minecraft.client.Minecraft");
            return invokeIfPresent(null, type, new Object[0], "getMinecraft", "func_71410_x");
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private static int colorFor(double ratio) {
        if (ratio <= 0.15D) {
            return 0xFFFF5C5C;
        }
        if (ratio <= 0.35D) {
            return 0xFFFFC857;
        }
        return 0xFF57D68D;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, null, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        return invokeIfPresent(target, null, args, methodNames);
    }

    private static Object invokeIfPresent(Object target, Class<?> staticType, Object[] args, String... methodNames) {
        if (target == null && staticType == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = staticType == null ? target.getClass() : staticType;
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
                        return method.invoke(staticType == null ? target : null, safeArgs);
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
}
