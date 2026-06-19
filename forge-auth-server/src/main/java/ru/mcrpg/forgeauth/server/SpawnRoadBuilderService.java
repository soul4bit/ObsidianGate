package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class SpawnRoadBuilderService {

    private static final int SPAWN_RADIUS = 48;
    private static final int CLEAR_ABOVE_HEIGHT = 20;
    private static final int FOUNDATION_DEPTH = 6;
    private static final int FOUNDATION_MIN_Y = 1;
    private static final int LIGHT_EVERY = 7;
    private static final int LAMP_EVERY = 12;
    private static final int DEFAULT_BLOCKS_PER_TICK = 2500;
    private static final long PROGRESS_LOG_INTERVAL_MILLIS = 5000L;

    private final Logger logger;
    private final BlockAccess blocks;
    private volatile RoadTask activeTask;

    SpawnRoadBuilderService(Logger logger) {
        this.logger = logger;
        this.blocks = new BlockAccess();
    }

    synchronized boolean start(Object world, Object sender, int centerX, int roadY, int centerZ, int length, int blocksPerTick) {
        if (activeTask != null && !activeTask.done) {
            return false;
        }
        int safeBlocksPerTick = blocksPerTick <= 0 ? DEFAULT_BLOCKS_PER_TICK : blocksPerTick;
        blocks.resetChunkCache();
        activeTask = RoadTask.create(world, sender, centerX, roadY, centerZ, length, safeBlocksPerTick, blocks);
        return true;
    }

    synchronized boolean cancel(Object sender) {
        RoadTask task = activeTask;
        if (task == null || task.done) {
            return false;
        }
        task.cancelled = true;
        ServerChat.status(sender, ServerChat.Tone.WARNING, "Spawn Roads", "build cancelled.");
        logger.info("Spawn road build cancelled.");
        return true;
    }

    String statusText() {
        RoadTask task = activeTask;
        if (task == null) {
            return "idle.";
        }
        return task.statusText();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RoadTask task = activeTask;
        if (task == null || task.done) {
            return;
        }

        try {
            task.tick(logger);
            if (task.done) {
                activeTask = task;
            }
        } catch (RuntimeException exception) {
            task.done = true;
            activeTask = task;
            logger.log(Level.SEVERE, "Spawn road build failed.", exception);
            ServerChat.status(task.sender, ServerChat.Tone.ERROR, "Spawn Roads", "build failed: " + detail(exception));
        }
    }

    private static String detail(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private interface Stage {
        int run(RoadTask task, int budget);

        boolean done();

        long total();

        String name();
    }

    private static final class RoadTask {
        final Object world;
        final Object sender;
        final int centerX;
        final int roadY;
        final int centerZ;
        final int length;
        final int blocksPerTick;
        final BlockAccess blocks;
        final List<Stage> stages;
        final long totalBlocks;
        int stageIndex;
        long placedBlocks;
        long lastProgressLog;
        volatile boolean cancelled;
        volatile boolean done;

        private RoadTask(
            Object world,
            Object sender,
            int centerX,
            int roadY,
            int centerZ,
            int length,
            int blocksPerTick,
            BlockAccess blocks,
            List<Stage> stages
        ) {
            this.world = world;
            this.sender = sender;
            this.centerX = centerX;
            this.roadY = roadY;
            this.centerZ = centerZ;
            this.length = length;
            this.blocksPerTick = blocksPerTick;
            this.blocks = blocks;
            this.stages = stages;
            long total = 0L;
            for (Stage stage : stages) {
                total += stage.total();
            }
            this.totalBlocks = total;
        }

        static RoadTask create(
            Object world,
            Object sender,
            int centerX,
            int roadY,
            int centerZ,
            int length,
            int blocksPerTick,
            BlockAccess blocks
        ) {
            ArrayList<Stage> stages = new ArrayList<Stage>();
            int startDistance = SPAWN_RADIUS + 1;
            int endDistance = SPAWN_RADIUS + length;
            int foundationY1 = Math.max(FOUNDATION_MIN_Y, roadY - FOUNDATION_DEPTH);
            int supportY = roadY - 1;
            int headY1 = roadY + 1;
            int headY2 = roadY + CLEAR_ABOVE_HEIGHT;

            addZRoad(stages, centerX, roadY, centerZ, centerZ + startDistance, centerZ + endDistance, 14, 1, foundationY1, supportY, headY1, headY2, blocks);
            addZRoad(stages, centerX, roadY, centerZ, centerZ - startDistance, centerZ - endDistance, 10, -1, foundationY1, supportY, headY1, headY2, blocks);
            addXRoad(stages, centerX, roadY, centerZ, centerX + startDistance, centerX + endDistance, 3, 1, foundationY1, supportY, headY1, headY2, blocks);
            addXRoad(stages, centerX, roadY, centerZ, centerX - startDistance, centerX - endDistance, 5, -1, foundationY1, supportY, headY1, headY2, blocks);
            return new RoadTask(world, sender, centerX, roadY, centerZ, length, blocksPerTick, blocks, stages);
        }

        void tick(Logger logger) {
            if (cancelled) {
                done = true;
                return;
            }

            int budget = blocksPerTick;
            while (budget > 0 && stageIndex < stages.size()) {
                Stage stage = stages.get(stageIndex);
                int used = stage.run(this, budget);
                placedBlocks += used;
                budget -= used;
                if (stage.done()) {
                    stageIndex++;
                }
                if (used == 0 && !stage.done()) {
                    break;
                }
            }

            long now = System.currentTimeMillis();
            if (now - lastProgressLog >= PROGRESS_LOG_INTERVAL_MILLIS) {
                lastProgressLog = now;
                logger.info("Spawn road build " + statusText());
            }

            if (stageIndex >= stages.size()) {
                done = true;
                logger.info("Spawn road build completed: " + statusText());
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, "Spawn Roads", "build completed. Run /save-all to flush it to disk.");
            }
        }

        String statusText() {
            if (cancelled) {
                return "cancelled.";
            }
            if (stages.isEmpty()) {
                return done ? "done." : "starting.";
            }
            int percent = totalBlocks <= 0L ? 100 : (int) Math.min(100L, (placedBlocks * 100L) / totalBlocks);
            String stageName = stageIndex >= stages.size() ? "done" : stages.get(stageIndex).name();
            return percent + "%, stage " + (Math.min(stageIndex + 1, stages.size())) + "/" + stages.size() + " " + stageName +
                ", blocks " + placedBlocks + "/" + totalBlocks + ".";
        }
    }

    private static void addZRoad(
        List<Stage> stages,
        int centerX,
        int roadY,
        int centerZ,
        int startZ,
        int endZ,
        int carpetMeta,
        int direction,
        int foundationY1,
        int supportY,
        int headY1,
        int headY2,
        BlockAccess blocks
    ) {
        stages.add(new FillStage("foundation-z", centerX - 8, foundationY1, startZ, centerX + 8, supportY, endZ, blocks.state("minecraft:dirt", 0), false));
        stages.add(new FillStage("clear-z", centerX - 8, headY1, startZ, centerX + 8, headY2, endZ, blocks.state("minecraft:air", 0), true));
        stages.add(new FillStage("stone-edge-z", centerX - 3, roadY, startZ, centerX - 3, roadY, endZ, blocks.state("minecraft:stonebrick", 0), false));
        stages.add(new FillStage("stone-edge-z", centerX + 3, roadY, startZ, centerX + 3, roadY, endZ, blocks.state("minecraft:stonebrick", 0), false));
        stages.add(new FillStage("quartz-z", centerX - 2, roadY, startZ, centerX + 2, roadY, endZ, blocks.state("minecraft:quartz_block", 0), false));
        stages.add(new EmbeddedLightsStage("lights-z", centerX, roadY, centerZ, startZ, endZ, true, blocks));
        stages.add(new FillStage("carpet-z", centerX - 2, roadY + 1, startZ, centerX - 2, roadY + 1, endZ, blocks.state("minecraft:carpet", carpetMeta), false));
        stages.add(new FillStage("carpet-z", centerX + 2, roadY + 1, startZ, centerX + 2, roadY + 1, endZ, blocks.state("minecraft:carpet", carpetMeta), false));
        stages.add(new LampStage("lamps-z", centerX, roadY, centerZ, true, direction, SPAWN_RADIUS + 6, SPAWN_RADIUS + Math.abs(endZ - centerZ), blocks));
    }

    private static void addXRoad(
        List<Stage> stages,
        int centerX,
        int roadY,
        int centerZ,
        int startX,
        int endX,
        int carpetMeta,
        int direction,
        int foundationY1,
        int supportY,
        int headY1,
        int headY2,
        BlockAccess blocks
    ) {
        stages.add(new FillStage("foundation-x", startX, foundationY1, centerZ - 8, endX, supportY, centerZ + 8, blocks.state("minecraft:dirt", 0), false));
        stages.add(new FillStage("clear-x", startX, headY1, centerZ - 8, endX, headY2, centerZ + 8, blocks.state("minecraft:air", 0), true));
        stages.add(new FillStage("stone-edge-x", startX, roadY, centerZ - 3, endX, roadY, centerZ - 3, blocks.state("minecraft:stonebrick", 0), false));
        stages.add(new FillStage("stone-edge-x", startX, roadY, centerZ + 3, endX, roadY, centerZ + 3, blocks.state("minecraft:stonebrick", 0), false));
        stages.add(new FillStage("quartz-x", startX, roadY, centerZ - 2, endX, roadY, centerZ + 2, blocks.state("minecraft:quartz_block", 0), false));
        stages.add(new EmbeddedLightsStage("lights-x", centerX, roadY, centerZ, startX, endX, false, blocks));
        stages.add(new FillStage("carpet-x", startX, roadY + 1, centerZ - 2, endX, roadY + 1, centerZ - 2, blocks.state("minecraft:carpet", carpetMeta), false));
        stages.add(new FillStage("carpet-x", startX, roadY + 1, centerZ + 2, endX, roadY + 1, centerZ + 2, blocks.state("minecraft:carpet", carpetMeta), false));
        stages.add(new LampStage("lamps-x", centerX, roadY, centerZ, false, direction, SPAWN_RADIUS + 6, SPAWN_RADIUS + Math.abs(endX - centerX), blocks));
    }

    private static final class FillStage implements Stage {
        private final String name;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final Object state;
        private final boolean skipAir;
        private int x;
        private int y;
        private int z;
        private boolean done;

        FillStage(String name, int x1, int y1, int z1, int x2, int y2, int z2, Object state, boolean skipAir) {
            this.name = name;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
            this.state = state;
            this.skipAir = skipAir;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                task.blocks.set(task.world, x, y, z, state, skipAir);
                used++;
                advance();
            }
            return used;
        }

        private void advance() {
            if (x < maxX) {
                x++;
                return;
            }
            x = minX;
            if (z < maxZ) {
                z++;
                return;
            }
            z = minZ;
            if (y < maxY) {
                y++;
                return;
            }
            done = true;
        }

        public boolean done() {
            return done;
        }

        public long total() {
            return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        }

        public String name() {
            return name;
        }
    }

    private static final class EmbeddedLightsStage implements Stage {
        private final String name;
        private final int centerX;
        private final int roadY;
        private final int centerZ;
        private final int start;
        private final int end;
        private final int step;
        private final boolean zAxis;
        private final BlockAccess blocks;
        private final Object seaLantern;
        private int current;
        private boolean firstLightQueued;
        private boolean secondLightQueued;
        private boolean done;

        EmbeddedLightsStage(String name, int centerX, int roadY, int centerZ, int start, int end, boolean zAxis, BlockAccess blocks) {
            this.name = name;
            this.centerX = centerX;
            this.roadY = roadY;
            this.centerZ = centerZ;
            this.start = start;
            this.end = end;
            this.step = start <= end ? 1 : -1;
            this.zAxis = zAxis;
            this.blocks = blocks;
            this.seaLantern = blocks.state("minecraft:sea_lantern", 0);
            this.current = start;
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                int local = zAxis ? current - centerZ + SPAWN_RADIUS : current - centerX + SPAWN_RADIUS;
                if (local % LIGHT_EVERY == 0) {
                    if (!firstLightQueued) {
                        if (zAxis) {
                            blocks.set(task.world, centerX - 1, roadY, current, seaLantern, false);
                        } else {
                            blocks.set(task.world, current, roadY, centerZ - 1, seaLantern, false);
                        }
                        firstLightQueued = true;
                        used++;
                        continue;
                    }
                    if (!secondLightQueued) {
                        if (zAxis) {
                            blocks.set(task.world, centerX + 1, roadY, current, seaLantern, false);
                        } else {
                            blocks.set(task.world, current, roadY, centerZ + 1, seaLantern, false);
                        }
                        secondLightQueued = true;
                        used++;
                        continue;
                    }
                }
                firstLightQueued = false;
                secondLightQueued = false;
                if (current == end) {
                    done = true;
                } else {
                    current += step;
                }
            }
            return used;
        }

        public boolean done() {
            return done;
        }

        public long total() {
            int count = 0;
            int value = start;
            while (true) {
                int local = zAxis ? value - centerZ + SPAWN_RADIUS : value - centerX + SPAWN_RADIUS;
                if (local % LIGHT_EVERY == 0) {
                    count += 2;
                }
                if (value == end) {
                    break;
                }
                value += step;
            }
            return count;
        }

        public String name() {
            return name;
        }
    }

    private static final class LampStage implements Stage {
        private final String name;
        private final int centerX;
        private final int roadY;
        private final int centerZ;
        private final boolean zAxis;
        private final int direction;
        private final int endDistance;
        private final BlockAccess blocks;
        private final Object stone;
        private final Object fence;
        private final Object seaLantern;
        private final Object torchEast;
        private final Object torchWest;
        private final Object torchSouth;
        private final Object torchNorth;
        private int distance;
        private int lampSide;
        private int lampPart;
        private boolean done;

        LampStage(String name, int centerX, int roadY, int centerZ, boolean zAxis, int direction, int startDistance, int endDistance, BlockAccess blocks) {
            this.name = name;
            this.centerX = centerX;
            this.roadY = roadY;
            this.centerZ = centerZ;
            this.zAxis = zAxis;
            this.direction = direction;
            this.distance = startDistance;
            this.endDistance = endDistance;
            this.blocks = blocks;
            this.stone = blocks.state("minecraft:stonebrick", 0);
            this.fence = blocks.state("minecraft:fence", 0);
            this.seaLantern = blocks.state("minecraft:sea_lantern", 0);
            this.torchEast = blocks.state("minecraft:torch", 1);
            this.torchWest = blocks.state("minecraft:torch", 2);
            this.torchSouth = blocks.state("minecraft:torch", 3);
            this.torchNorth = blocks.state("minecraft:torch", 4);
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                int x;
                int z;
                if (zAxis) {
                    x = lampSide == 0 ? centerX - 6 : centerX + 6;
                    z = centerZ + direction * distance;
                } else {
                    x = centerX + direction * distance;
                    z = lampSide == 0 ? centerZ - 6 : centerZ + 6;
                }
                placePart(task.world, x, z, lampPart);
                lampPart++;
                if (lampPart >= 10) {
                    lampPart = 0;
                    lampSide++;
                    if (lampSide >= 2) {
                        lampSide = 0;
                        distance += LAMP_EVERY;
                        if (distance > endDistance) {
                            done = true;
                        }
                    }
                }
                used++;
            }
            return used;
        }

        private void placePart(Object world, int x, int z, int part) {
            if (part == 0) {
                blocks.set(world, x, roadY, z, stone, false);
            } else if (part >= 1 && part <= 4) {
                blocks.set(world, x, roadY + part, z, fence, false);
            } else if (part == 5) {
                blocks.set(world, x, roadY + 5, z, seaLantern, false);
            } else if (part == 6) {
                blocks.set(world, x + 1, roadY + 4, z, torchEast, false);
            } else if (part == 7) {
                blocks.set(world, x - 1, roadY + 4, z, torchWest, false);
            } else if (part == 8) {
                blocks.set(world, x, roadY + 4, z + 1, torchSouth, false);
            } else {
                blocks.set(world, x, roadY + 4, z - 1, torchNorth, false);
            }
        }

        public boolean done() {
            return done;
        }

        public long total() {
            if (distance > endDistance) {
                return 0L;
            }
            return (((endDistance - distance) / LAMP_EVERY) + 1L) * 2L * 10L;
        }

        public String name() {
            return name;
        }
    }

    private static final class BlockAccess {
        private final Constructor<?> blockPosConstructor;
        private final Method getBlockFromName;
        private final Method getStateFromMeta;
        private final Method isAirBlock;
        private final List<BlockState> states = new ArrayList<BlockState>();
        private Method setBlockState;
        private int lastChunkX = Integer.MIN_VALUE;
        private int lastChunkZ = Integer.MIN_VALUE;

        BlockAccess() {
            try {
                Class<?> blockPosType = Class.forName("net.minecraft.util.math.BlockPos");
                blockPosConstructor = blockPosType.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
                Class<?> blockType = Class.forName("net.minecraft.block.Block");
                getBlockFromName = findStaticMethod(blockType, new String[] { "getBlockFromName", "func_149684_b" }, String.class);
                getStateFromMeta = findAnyMethod(blockType, new String[] { "getStateFromMeta", "func_176203_a" }, Integer.TYPE);
                Class<?> worldType = Class.forName("net.minecraft.world.World");
                isAirBlock = findAnyMethod(worldType, new String[] { "isAirBlock", "func_175623_d" }, blockPosType);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot initialize block reflection.", exception);
            }
        }

        Object state(String name, int meta) {
            for (BlockState cached : states) {
                if (cached.name.equals(name) && cached.meta == meta) {
                    return cached.state;
                }
            }
            try {
                Object block = getBlockFromName.invoke(null, name);
                if (block == null) {
                    throw new IllegalStateException("Unknown block " + name + ".");
                }
                Object state = getStateFromMeta.invoke(block, Integer.valueOf(meta));
                states.add(new BlockState(name, meta, state));
                return state;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create block state " + name + ":" + meta + ".", exception);
            }
        }

        void set(Object world, int x, int y, int z, Object state, boolean skipIfAir) {
            if (world == null) {
                throw new IllegalStateException("World is not loaded.");
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                if (!TeleportSupport.prepareDestinationChunk(world, x + 0.5D, z + 0.5D)) {
                    throw new IllegalStateException("Cannot load chunk " + chunkX + "," + chunkZ + ".");
                }
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }

            Object position = blockPos(x, y, z);
            if (skipIfAir && Boolean.TRUE.equals(invoke(isAirBlock, world, position))) {
                return;
            }
            Method method = setBlockState(world.getClass(), position, state);
            invoke(method, world, position, state, Integer.valueOf(2));
        }

        void resetChunkCache() {
            lastChunkX = Integer.MIN_VALUE;
            lastChunkZ = Integer.MIN_VALUE;
        }

        private Object blockPos(int x, int y, int z) {
            try {
                return blockPosConstructor.newInstance(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create BlockPos.", exception);
            }
        }

        private Method setBlockState(Class<?> worldClass, Object position, Object state) {
            Method cached = setBlockState;
            if (cached != null) {
                return cached;
            }
            Class<?> type = worldClass;
            while (type != null) {
                Method[] methods;
                try {
                    methods = type.getDeclaredMethods();
                } catch (LinkageError ignored) {
                    type = type.getSuperclass();
                    continue;
                }
                for (Method method : methods) {
                    if (!("setBlockState".equals(method.getName()) || "func_180501_a".equals(method.getName()))) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (
                        params.length == 3
                            && params[0].isAssignableFrom(position.getClass())
                            && params[1].isAssignableFrom(state.getClass())
                            && params[2] == Integer.TYPE
                    ) {
                        method.setAccessible(true);
                        setBlockState = method;
                        return method;
                    }
                }
                type = type.getSuperclass();
            }
            throw new IllegalStateException("Cannot find World#setBlockState.");
        }
    }

    private static final class BlockState {
        final String name;
        final int meta;
        final Object state;

        BlockState(String name, int meta, Object state) {
            this.name = name;
            this.meta = meta;
            this.state = state;
        }
    }

    private static Method findStaticMethod(Class<?> type, String[] names, Class<?>... parameters) throws NoSuchMethodException {
        Method method = findAnyMethod(type, names, parameters);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(method.getName() + " is not static.");
        }
        return method;
    }

    private static Method findAnyMethod(Class<?> type, String[] names, Class<?>... parameters) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (matches(method, names, parameters)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }

    private static boolean matches(Method method, String[] names, Class<?>... parameters) {
        boolean nameMatches = false;
        for (String name : names) {
            if (name.equals(method.getName())) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches) {
            return false;
        }
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != parameters.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].isAssignableFrom(parameters[i]) && !parameters[i].isAssignableFrom(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot call " + method.getName() + ".", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getTargetException() == null ? exception : exception.getTargetException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Cannot call " + method.getName() + ": " + detail(cause) + ".", cause);
        }
    }

    static List<String> commandOptions() {
        ArrayList<String> options = new ArrayList<String>();
        Collections.addAll(options, "build", "status", "cancel");
        return options;
    }
}
