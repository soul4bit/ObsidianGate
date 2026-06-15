package ru.mcrpg.forgeauth.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class RegionSelectionRenderer {

    private static final long MESSAGE_TIMEOUT_MS = 2600L;
    private static final int GL_LINES = 1;
    private static final int GL_BLEND = 3042;
    private static final int GL_CULL_FACE = 2884;
    private static final int GL_DEPTH_TEST = 2929;
    private static final int GL_LIGHTING = 2896;
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_SRC_ALPHA = 770;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 771;

    private static volatile Selection selection;

    static void update(RegionSelectionMessage message) {
        selection = message.visible ? new Selection(message) : null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Selection current = selection;
        if (current == null) {
            return;
        }
        if (System.currentTimeMillis() - current.updatedAt > MESSAGE_TIMEOUT_MS) {
            selection = null;
            return;
        }

        Object minecraft = invokeStatic("net.minecraft.client.Minecraft", new String[] { "getMinecraft", "func_71410_x" });
        Object player = field(minecraft, "player", "field_71439_g");
        Object world = field(minecraft, "world", "field_71441_e");
        if (player == null || world == null || integer(field(player, "dimension", "field_71093_bK")) != current.dimension) {
            selection = null;
            return;
        }
        Object camera = invoke(minecraft, new String[] { "getRenderViewEntity", "func_175606_aa" });
        if (camera == null) {
            return;
        }

        double partialTicks = event.getPartialTicks();
        double cameraX = interpolate(camera, partialTicks, "lastTickPosX", "field_70142_S", "posX", "field_70165_t");
        double cameraY = interpolate(camera, partialTicks, "lastTickPosY", "field_70137_T", "posY", "field_70163_u");
        double cameraZ = interpolate(camera, partialTicks, "lastTickPosZ", "field_70136_U", "posZ", "field_70161_v");

        gl("glPushMatrix");
        gl("glTranslated", Double.valueOf(-cameraX), Double.valueOf(-cameraY), Double.valueOf(-cameraZ));
        gl("glDisable", Integer.valueOf(GL_TEXTURE_2D));
        gl("glDisable", Integer.valueOf(GL_LIGHTING));
        gl("glDisable", Integer.valueOf(GL_CULL_FACE));
        gl("glDisable", Integer.valueOf(GL_DEPTH_TEST));
        gl("glEnable", Integer.valueOf(GL_BLEND));
        gl("glBlendFunc", Integer.valueOf(GL_SRC_ALPHA), Integer.valueOf(GL_ONE_MINUS_SRC_ALPHA));
        gl("glDepthMask", Boolean.FALSE);
        gl("glLineWidth", Float.valueOf(2.0F));

        if (current.hasSecond) {
            drawGrid(current);
            drawMarker(current.firstX, current.firstY, current.firstZ, 0.2F, 0.45F, 1.0F);
            drawMarker(current.secondX, current.secondY, current.secondZ, 0.15F, 1.0F, 0.35F);
        } else {
            drawMarker(current.firstX, current.firstY, current.firstZ, 0.2F, 0.45F, 1.0F);
        }

        gl("glDepthMask", Boolean.TRUE);
        gl("glEnable", Integer.valueOf(GL_DEPTH_TEST));
        gl("glDisable", Integer.valueOf(GL_BLEND));
        gl("glEnable", Integer.valueOf(GL_CULL_FACE));
        gl("glEnable", Integer.valueOf(GL_LIGHTING));
        gl("glEnable", Integer.valueOf(GL_TEXTURE_2D));
        gl("glPopMatrix");
    }

    private static void drawGrid(Selection selection) {
        Object buffer = beginLines();
        for (double x : coordinates(selection.minX, selection.maxX)) {
            line(buffer, x, selection.minY, selection.minZ, x, selection.minY, selection.maxZ);
            line(buffer, x, selection.maxY, selection.minZ, x, selection.maxY, selection.maxZ);
            line(buffer, x, selection.minY, selection.minZ, x, selection.maxY, selection.minZ);
            line(buffer, x, selection.minY, selection.maxZ, x, selection.maxY, selection.maxZ);
        }
        for (double y : coordinates(selection.minY, selection.maxY)) {
            line(buffer, selection.minX, y, selection.minZ, selection.maxX, y, selection.minZ);
            line(buffer, selection.minX, y, selection.maxZ, selection.maxX, y, selection.maxZ);
            line(buffer, selection.minX, y, selection.minZ, selection.minX, y, selection.maxZ);
            line(buffer, selection.maxX, y, selection.minZ, selection.maxX, y, selection.maxZ);
        }
        for (double z : coordinates(selection.minZ, selection.maxZ)) {
            line(buffer, selection.minX, selection.minY, z, selection.maxX, selection.minY, z);
            line(buffer, selection.minX, selection.maxY, z, selection.maxX, selection.maxY, z);
            line(buffer, selection.minX, selection.minY, z, selection.minX, selection.maxY, z);
            line(buffer, selection.maxX, selection.minY, z, selection.maxX, selection.maxY, z);
        }
        finishLines();
    }

    private static void drawMarker(double x, double y, double z, float red, float green, float blue) {
        Object buffer = beginLines();
        double maxX = x + 1.0D;
        double maxY = y + 1.0D;
        double maxZ = z + 1.0D;
        coloredLine(buffer, x, y, z, maxX, y, z, red, green, blue);
        coloredLine(buffer, x, y, z, x, maxY, z, red, green, blue);
        coloredLine(buffer, x, y, z, x, y, maxZ, red, green, blue);
        coloredLine(buffer, maxX, maxY, maxZ, x, maxY, maxZ, red, green, blue);
        coloredLine(buffer, maxX, maxY, maxZ, maxX, y, maxZ, red, green, blue);
        coloredLine(buffer, maxX, maxY, maxZ, maxX, maxY, z, red, green, blue);
        coloredLine(buffer, maxX, y, z, maxX, maxY, z, red, green, blue);
        coloredLine(buffer, maxX, y, z, maxX, y, maxZ, red, green, blue);
        coloredLine(buffer, x, maxY, z, maxX, maxY, z, red, green, blue);
        coloredLine(buffer, x, maxY, z, x, maxY, maxZ, red, green, blue);
        coloredLine(buffer, x, y, maxZ, maxX, y, maxZ, red, green, blue);
        coloredLine(buffer, x, y, maxZ, x, maxY, maxZ, red, green, blue);
        finishLines();
    }

    private static Object beginLines() {
        Object tessellator = invokeStatic(
            "net.minecraft.client.renderer.Tessellator",
            new String[] { "getInstance", "func_178181_a" }
        );
        Object buffer = invoke(tessellator, new String[] { "getBuffer", "func_178180_c" });
        Object positionColor = staticField(
            "net.minecraft.client.renderer.vertex.DefaultVertexFormats",
            "POSITION_COLOR",
            "field_181706_f"
        );
        invoke(buffer, new String[] { "begin", "func_181668_a" }, Integer.valueOf(GL_LINES), positionColor);
        return buffer;
    }

    private static void finishLines() {
        Object tessellator = invokeStatic(
            "net.minecraft.client.renderer.Tessellator",
            new String[] { "getInstance", "func_178181_a" }
        );
        invoke(tessellator, new String[] { "draw", "func_78381_a" });
    }

    private static List<Double> coordinates(double min, double max) {
        double step = Math.max(1.0D, Math.ceil((max - min) / 12.0D));
        List<Double> values = new ArrayList<Double>();
        values.add(Double.valueOf(min));
        for (double value = min + step; value < max; value += step) {
            values.add(Double.valueOf(value));
        }
        values.add(Double.valueOf(max));
        return values;
    }

    private static void line(Object buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        coloredLine(buffer, x1, y1, z1, x2, y2, z2, 1.0F, 0.12F, 0.22F);
    }

    private static void coloredLine(
        Object buffer,
        double x1,
        double y1,
        double z1,
        double x2,
        double y2,
        double z2,
        float red,
        float green,
        float blue
    ) {
        vertex(buffer, x1, y1, z1, red, green, blue);
        vertex(buffer, x2, y2, z2, red, green, blue);
    }

    private static void vertex(Object buffer, double x, double y, double z, float red, float green, float blue) {
        Object positioned = invoke(
            buffer,
            new String[] { "pos", "func_181662_b" },
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z)
        );
        Object colored = invoke(
            positioned,
            new String[] { "color", "func_181666_a" },
            Float.valueOf(red),
            Float.valueOf(green),
            Float.valueOf(blue),
            Float.valueOf(0.85F)
        );
        invoke(colored, new String[] { "endVertex", "func_181675_d" });
    }

    private static double interpolate(
        Object entity,
        double partialTicks,
        String lastName,
        String lastSrg,
        String currentName,
        String currentSrg
    ) {
        double last = number(field(entity, lastName, lastSrg));
        double current = number(field(entity, currentName, currentSrg));
        return last + (current - last) * partialTicks;
    }

    private static void gl(String methodName, Object... args) {
        invokeStatic("org.lwjgl.opengl.GL11", new String[] { methodName }, args);
    }

    private static Object invokeStatic(String className, String[] names, Object... args) {
        try {
            return invoke(Class.forName(className), null, names, args);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing client class " + className, exception);
        }
    }

    private static Object invoke(Object target, String[] names, Object... args) {
        return invoke(target.getClass(), target, names, args);
    }

    private static Object invoke(Class<?> type, Object target, String[] names, Object... args) {
        for (Method method : type.getMethods()) {
            if (matches(method.getName(), names) && method.getParameterTypes().length == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Try another overload or mapped method name.
                }
            }
        }
        throw new IllegalStateException("Missing method " + names[0] + " on " + type.getName());
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
                    // Try the mapped name or superclass.
                }
            }
        }
        return null;
    }

    private static Object staticField(String className, String... names) {
        try {
            Class<?> type = Class.forName(className);
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(null);
                } catch (ReflectiveOperationException ignored) {
                    // Try the mapped name.
                }
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing client class " + className, exception);
        }
        throw new IllegalStateException("Missing field " + names[0] + " on " + className);
    }

    private static boolean matches(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static final class Selection {
        private final long updatedAt = System.currentTimeMillis();
        private final int dimension;
        private final boolean hasSecond;
        private final int firstX;
        private final int firstY;
        private final int firstZ;
        private final int secondX;
        private final int secondY;
        private final int secondZ;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private Selection(RegionSelectionMessage message) {
            dimension = message.dimension;
            hasSecond = message.hasSecond;
            firstX = message.firstX;
            firstY = message.firstY;
            firstZ = message.firstZ;
            secondX = message.secondX;
            secondY = message.secondY;
            secondZ = message.secondZ;
            minX = Math.min(firstX, secondX);
            minY = Math.min(firstY, secondY);
            minZ = Math.min(firstZ, secondZ);
            maxX = Math.max(firstX, secondX) + 1.0D;
            maxY = Math.max(firstY, secondY) + 1.0D;
            maxZ = Math.max(firstZ, secondZ) + 1.0D;
        }
    }
}
