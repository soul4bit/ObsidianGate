package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class SpawnRoadBuilderService {

    private static final int SPAWN_RADIUS = 48;
    private static final int CLEAR_ABOVE_HEIGHT = 20;
    private static final int FOUNDATION_DEPTH = 6;
    private static final int FOUNDATION_MIN_Y = 1;
    private static final int ROAD_HALF_WIDTH = 3;
    private static final int FENCE_OFFSET = ROAD_HALF_WIDTH + 1;
    private static final int FENCE_OPENING_RADIUS = 3;
    private static final int WATER_LANDFILL_HALF_WIDTH = ROAD_HALF_WIDTH + 1;
    private static final int WATER_LANDFILL_SCAN_DEPTH = 48;
    private static final int CLEAR_HALF_WIDTH = 8;
    private static final int REBUILD_CLEAR_HALF_WIDTH = 18;
    private static final int REBUILD_CLEAR_BELOW = 10;
    private static final int REBUILD_CLEAR_ABOVE = 34;
    private static final int TERRAIN_SAMPLE_OFFSET = 10;
    private static final int TERRAIN_LOOKAHEAD_DISTANCE = 16;
    private static final int TERRAIN_LOOKAHEAD_RISE_MARGIN = 6;
    private static final int TERRAIN_STEP_DEADBAND = 2;
    private static final int MAX_TERRAIN_DELTA = 96;
    private static final int LIGHT_EVERY = 7;
    private static final int LAMP_EVERY = 12;
    private static final int OUTPOST_INTERVAL = 250;
    private static final int OUTPOST_COUNT = 4;
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
        return start(world, sender, centerX, roadY, centerZ, length, blocksPerTick, false);
    }

    synchronized boolean rebuild(Object world, Object sender, int centerX, int roadY, int centerZ, int length, int blocksPerTick) {
        return start(world, sender, centerX, roadY, centerZ, length, blocksPerTick, true);
    }

    private boolean start(Object world, Object sender, int centerX, int roadY, int centerZ, int length, int blocksPerTick, boolean clearFirst) {
        if (activeTask != null && !activeTask.done) {
            return false;
        }
        int safeBlocksPerTick = blocksPerTick <= 0 ? DEFAULT_BLOCKS_PER_TICK : blocksPerTick;
        blocks.resetChunkCache();
        activeTask = RoadTask.create(world, sender, centerX, roadY, centerZ, length, safeBlocksPerTick, clearFirst, blocks);
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
            boolean clearFirst,
            BlockAccess blocks
        ) {
            ArrayList<Stage> stages = new ArrayList<Stage>();
            int startDistance = SPAWN_RADIUS + 1;
            int endDistance = SPAWN_RADIUS + length;

            if (clearFirst) {
                addManagedRoadCleanup(stages, "north", centerX, roadY, centerZ, true, 1, startDistance, endDistance, blocks);
                addManagedRoadCleanup(stages, "south", centerX, roadY, centerZ, true, -1, startDistance, endDistance, blocks);
                addManagedRoadCleanup(stages, "east", centerX, roadY, centerZ, false, 1, startDistance, endDistance, blocks);
                addManagedRoadCleanup(stages, "west", centerX, roadY, centerZ, false, -1, startDistance, endDistance, blocks);
            }

            addAdaptiveRoad(stages, RoadPath.create("north", "Северный путь", centerX, roadY, centerZ, true, 1, startDistance, endDistance, 14, world, blocks), blocks);
            addAdaptiveRoad(stages, RoadPath.create("south", "Южный путь", centerX, roadY, centerZ, true, -1, startDistance, endDistance, 10, world, blocks), blocks);
            addAdaptiveRoad(stages, RoadPath.create("east", "Восточный путь", centerX, roadY, centerZ, false, 1, startDistance, endDistance, 3, world, blocks), blocks);
            addAdaptiveRoad(stages, RoadPath.create("west", "Западный путь", centerX, roadY, centerZ, false, -1, startDistance, endDistance, 5, world, blocks), blocks);
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
            if (done) {
                return "done.";
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

    private static void addAdaptiveRoad(List<Stage> stages, RoadPath path, BlockAccess blocks) {
        stages.add(new RoadPathStage("adaptive-road-" + path.id, path, blocks));
        stages.add(new RoadFenceStage("adaptive-fence-" + path.id, path, blocks));
        stages.add(new RoadLampStage("adaptive-lamps-" + path.id, path, blocks));
        stages.add(new OutpostStage("outposts-" + path.id, path, blocks));
    }

    private static void addManagedRoadCleanup(
        List<Stage> stages,
        String id,
        int centerX,
        int roadY,
        int centerZ,
        boolean zAxis,
        int direction,
        int startDistance,
        int endDistance,
        BlockAccess blocks
    ) {
        stages.add(new ManagedRoadCleanupStage(
            "cleanup-" + id,
            centerX,
            roadY,
            centerZ,
            zAxis,
            direction,
            startDistance,
            endDistance,
            blocks
        ));
    }

    private static final class RoadPath {
        final String id;
        final String label;
        final int centerX;
        final int roadY;
        final int centerZ;
        final boolean zAxis;
        final int direction;
        final int colorMeta;
        final List<RoadPoint> points;
        private final Map<Integer, RoadPoint> byDistance;

        private RoadPath(
            String id,
            String label,
            int centerX,
            int roadY,
            int centerZ,
            boolean zAxis,
            int direction,
            int colorMeta,
            List<RoadPoint> points
        ) {
            this.id = id;
            this.label = label;
            this.centerX = centerX;
            this.roadY = roadY;
            this.centerZ = centerZ;
            this.zAxis = zAxis;
            this.direction = direction;
            this.colorMeta = colorMeta;
            this.points = points;
            this.byDistance = new HashMap<Integer, RoadPoint>();
            for (RoadPoint point : points) {
                byDistance.put(Integer.valueOf(point.distance), point);
            }
        }

        static RoadPath create(
            String id,
            String label,
            int centerX,
            int roadY,
            int centerZ,
            boolean zAxis,
            int direction,
            int startDistance,
            int endDistance,
            int colorMeta,
            Object world,
            BlockAccess blocks
        ) {
            ArrayList<RoadPoint> points = new ArrayList<RoadPoint>();
            for (int distance = startDistance; distance <= endDistance; distance++) {
                int x = zAxis ? centerX : centerX + direction * distance;
                int z = zAxis ? centerZ + direction * distance : centerZ;
                points.add(new RoadPoint(distance, x, z, roadY));
            }
            return new RoadPath(id, label, centerX, roadY, centerZ, zAxis, direction, colorMeta, points);
        }

        RoadPoint pointAtDistance(int distance) {
            return byDistance.get(Integer.valueOf(distance));
        }

        RoadPoint nearestPointAtDistance(int distance) {
            RoadPoint exact = pointAtDistance(distance);
            if (exact != null) {
                return exact;
            }
            RoadPoint nearest = null;
            int best = Integer.MAX_VALUE;
            for (RoadPoint point : points) {
                int delta = Math.abs(point.distance - distance);
                if (delta < best) {
                    nearest = point;
                    best = delta;
                }
            }
            return nearest;
        }

        int xWithOffset(RoadPoint point, int offset) {
            return zAxis ? point.x + offset : point.x;
        }

        int zWithOffset(RoadPoint point, int offset) {
            return zAxis ? point.z : point.z + offset;
        }

        int uphillMeta(int uphillDirection) {
            if (zAxis) {
                return uphillDirection > 0 ? 2 : 3;
            }
            return uphillDirection > 0 ? 0 : 1;
        }

        int sideOffset(int milestone) {
            return ((milestone / OUTPOST_INTERVAL) % 2 == 0 ? -1 : 1) * 12;
        }
    }

    private static int sampleTerrainY(
        Object world,
        BlockAccess blocks,
        int centerX,
        int centerZ,
        int fallbackY,
        boolean zAxis,
        int direction,
        int distance
    ) {
        int sampled = sampleTerrainEdgeY(world, blocks, centerX, centerZ, fallbackY, zAxis, direction, distance);
        int ahead = sampleTerrainEdgeY(
            world,
            blocks,
            centerX,
            centerZ,
            fallbackY,
            zAxis,
            direction,
            distance + TERRAIN_LOOKAHEAD_DISTANCE
        );
        if (ahead > sampled) {
            sampled = Math.max(sampled, ahead - TERRAIN_LOOKAHEAD_RISE_MARGIN);
        }
        int min = Math.max(FOUNDATION_MIN_Y + FOUNDATION_DEPTH, fallbackY - MAX_TERRAIN_DELTA);
        int max = Math.min(240, fallbackY + MAX_TERRAIN_DELTA);
        return Math.max(min, Math.min(max, sampled));
    }

    private static int sampleTerrainEdgeY(
        Object world,
        BlockAccess blocks,
        int centerX,
        int centerZ,
        int fallbackY,
        boolean zAxis,
        int direction,
        int distance
    ) {
        int x = zAxis ? centerX : centerX + direction * distance;
        int z = zAxis ? centerZ + direction * distance : centerZ;
        int left = zAxis
            ? blocks.terrainHeight(world, x - TERRAIN_SAMPLE_OFFSET, z, fallbackY)
            : blocks.terrainHeight(world, x, z - TERRAIN_SAMPLE_OFFSET, fallbackY);
        int right = zAxis
            ? blocks.terrainHeight(world, x + TERRAIN_SAMPLE_OFFSET, z, fallbackY)
            : blocks.terrainHeight(world, x, z + TERRAIN_SAMPLE_OFFSET, fallbackY);
        return Math.max(left, right) + 1;
    }

    private static int smooth(int previousY, int targetY) {
        int delta = targetY - previousY;
        if (Math.abs(delta) <= TERRAIN_STEP_DEADBAND) {
            return previousY;
        }
        if (delta > 0) {
            return previousY + 1;
        }
        return previousY - 1;
    }

    private static final class RoadPoint {
        final int distance;
        final int x;
        final int z;
        int y;
        String biomeName;
        boolean sampled;

        private RoadPoint(int distance, int x, int z, int y) {
            this.distance = distance;
            this.x = x;
            this.z = z;
            this.y = y;
            this.biomeName = "";
        }
    }

    private static final class ManagedRoadCleanupStage implements Stage {
        private final String name;
        private final int centerX;
        private final int roadY;
        private final int centerZ;
        private final boolean zAxis;
        private final int direction;
        private final int startDistance;
        private final int endDistance;
        private final BlockAccess blocks;
        private final Object air;
        private int distance;
        private int offset;
        private int yCursor;
        private int minY;
        private int maxY;
        private int previousPathY;
        private boolean phaseStarted;
        private boolean hasPreviousPathY;
        private boolean done;

        ManagedRoadCleanupStage(
            String name,
            int centerX,
            int roadY,
            int centerZ,
            boolean zAxis,
            int direction,
            int startDistance,
            int endDistance,
            BlockAccess blocks
        ) {
            this.name = name;
            this.centerX = centerX;
            this.roadY = roadY;
            this.centerZ = centerZ;
            this.zAxis = zAxis;
            this.direction = direction;
            this.startDistance = startDistance;
            this.endDistance = endDistance;
            this.blocks = blocks;
            this.air = blocks.air();
            this.distance = startDistance;
            this.offset = -REBUILD_CLEAR_HALF_WIDTH;
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                if (!phaseStarted) {
                    int pathY = cleanupPathY(task.world, distance);
                    minY = Math.max(FOUNDATION_MIN_Y, Math.min(roadY, pathY) - REBUILD_CLEAR_BELOW);
                    maxY = Math.min(255, Math.max(roadY, pathY) + REBUILD_CLEAR_ABOVE);
                    yCursor = minY;
                    offset = -REBUILD_CLEAR_HALF_WIDTH;
                    phaseStarted = true;
                }

                int x = zAxis ? centerX + offset : centerX + direction * distance;
                int z = zAxis ? centerZ + direction * distance : centerZ + offset;
                if (blocks.isManagedRoadBlock(task.world, x, yCursor, z)) {
                    blocks.set(task.world, x, yCursor, z, air, false);
                }
                used++;
                advance();
            }
            return used;
        }

        private int cleanupPathY(Object world, int currentDistance) {
            int previousY = hasPreviousPathY ? previousPathY : roadY;
            int sampledY = sampleTerrainY(world, blocks, centerX, centerZ, roadY, zAxis, direction, currentDistance);
            previousPathY = smooth(previousY, sampledY);
            hasPreviousPathY = true;
            return previousPathY;
        }

        private void advance() {
            yCursor++;
            if (yCursor <= maxY) {
                return;
            }
            yCursor = minY;
            offset++;
            if (offset <= REBUILD_CLEAR_HALF_WIDTH) {
                return;
            }
            distance++;
            phaseStarted = false;
            if (distance > endDistance) {
                done = true;
            }
        }

        public boolean done() {
            return done;
        }

        public long total() {
            return (long) (endDistance - startDistance + 1)
                * (long) (REBUILD_CLEAR_HALF_WIDTH * 2 + 1)
                * (long) (REBUILD_CLEAR_BELOW + REBUILD_CLEAR_ABOVE + MAX_TERRAIN_DELTA + 1);
        }

        public String name() {
            return name;
        }
    }

    private static final class RoadPathStage implements Stage {
        private final String name;
        private final RoadPath path;
        private final BlockAccess blocks;
        private int pointIndex;
        private int phase;
        private int offset = -CLEAR_HALF_WIDTH;
        private int yCursor;
        private boolean phaseStarted;
        private boolean done;

        RoadPathStage(String name, RoadPath path, BlockAccess blocks) {
            this.name = name;
            this.path = path;
            this.blocks = blocks;
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                RoadPoint point = sampledPoint(task.world, pointIndex);
                Palette palette = blocks.palette(point.biomeName, path.colorMeta);
                if (phase == 0) {
                    used += runWaterLandfillPhase(task.world, point, palette, budget - used);
                } else if (phase == 1) {
                    used += runClearPhase(task.world, point, budget - used);
                } else if (phase == 2) {
                    used += runFoundationPhase(task.world, point, palette, budget - used);
                } else {
                    used += runSurfacePhase(task.world, point, palette, budget - used);
                }
                if (used >= budget) {
                    break;
                }
            }
            return used;
        }

        private RoadPoint sampledPoint(Object world, int index) {
            RoadPoint point = path.points.get(index);
            if (point.sampled) {
                return point;
            }
            int previousY = index == 0 ? path.roadY : sampledPoint(world, index - 1).y;
            int sampledY = sampleTerrainY(world, blocks, path.centerX, path.centerZ, path.roadY, path.zAxis, path.direction, point.distance);
            point.y = smooth(previousY, sampledY);
            point.biomeName = blocks.biomeName(world, point.x, point.z);
            point.sampled = true;
            return point;
        }

        private int runWaterLandfillPhase(Object world, RoadPoint point, Palette palette, int budget) {
            int used = 0;
            int maxY = point.y - 1;
            if (maxY < FOUNDATION_MIN_Y) {
                nextPhase();
                return 0;
            }
            if (!phaseStarted) {
                offset = -WATER_LANDFILL_HALF_WIDTH;
                prepareWaterLandfillColumn(world, point);
                phaseStarted = true;
            }
            while (used < budget && offset <= WATER_LANDFILL_HALF_WIDTH) {
                if (yCursor > maxY) {
                    offset++;
                    prepareWaterLandfillColumn(world, point);
                    continue;
                }
                int x = path.xWithOffset(point, offset);
                int z = path.zWithOffset(point, offset);
                blocks.set(world, x, yCursor, z, palette.foundation, false);
                used++;
                yCursor++;
            }
            if (offset > WATER_LANDFILL_HALF_WIDTH) {
                nextPhase();
            }
            return used;
        }

        private void prepareWaterLandfillColumn(Object world, RoadPoint point) {
            if (offset > WATER_LANDFILL_HALF_WIDTH) {
                return;
            }
            int x = path.xWithOffset(point, offset);
            int z = path.zWithOffset(point, offset);
            int maxY = point.y - 1;
            int minY = Math.max(FOUNDATION_MIN_Y, point.y - WATER_LANDFILL_SCAN_DEPTH);
            yCursor = blocks.waterLandfillBottom(world, x, z, minY, maxY);
        }

        private int runClearPhase(Object world, RoadPoint point, int budget) {
            int used = 0;
            int minY = point.y + 1;
            int maxY = Math.max(point.y + CLEAR_ABOVE_HEIGHT, path.roadY + CLEAR_ABOVE_HEIGHT);
            if (!phaseStarted) {
                yCursor = Math.max(1, minY);
                phaseStarted = true;
            }
            while (used < budget && offset <= CLEAR_HALF_WIDTH) {
                int x = path.xWithOffset(point, offset);
                int z = path.zWithOffset(point, offset);
                blocks.set(world, x, yCursor, z, blocks.air(), true);
                used++;
                yCursor++;
                if (yCursor > Math.min(255, maxY)) {
                    yCursor = Math.max(1, minY);
                    offset++;
                }
            }
            if (offset > CLEAR_HALF_WIDTH) {
                nextPhase();
            }
            return used;
        }

        private int runFoundationPhase(Object world, RoadPoint point, Palette palette, int budget) {
            int used = 0;
            int minY = Math.max(FOUNDATION_MIN_Y, point.y - FOUNDATION_DEPTH);
            int maxY = point.y - 1;
            if (!phaseStarted) {
                offset = -ROAD_HALF_WIDTH - 1;
                yCursor = minY;
                phaseStarted = true;
            }
            while (used < budget && offset <= ROAD_HALF_WIDTH + 1) {
                int x = path.xWithOffset(point, offset);
                int z = path.zWithOffset(point, offset);
                blocks.set(world, x, yCursor, z, palette.foundation, false);
                used++;
                yCursor++;
                if (yCursor > maxY) {
                    yCursor = minY;
                    offset++;
                }
            }
            if (offset > ROAD_HALF_WIDTH + 1) {
                nextPhase();
            }
            return used;
        }

        private int runSurfacePhase(Object world, RoadPoint point, Palette palette, int budget) {
            int used = 0;
            if (!phaseStarted) {
                if (pointIndex + 1 < path.points.size()) {
                    sampledPoint(world, pointIndex + 1);
                }
                offset = -ROAD_HALF_WIDTH;
                phaseStarted = true;
            }
            while (used < budget && offset <= ROAD_HALF_WIDTH) {
                int x = path.xWithOffset(point, offset);
                int z = path.zWithOffset(point, offset);
                blocks.set(world, x, point.y, z, surfaceState(point, offset, palette), false);
                used++;
                offset++;
            }
            if (offset > ROAD_HALF_WIDTH) {
                nextPoint();
            }
            return used;
        }

        private Object surfaceState(RoadPoint point, int offset, Palette palette) {
            int index = point.distance - path.points.get(0).distance;
            int previousY = index > 0 ? path.points.get(index - 1).y : point.y;
            int nextY = index + 1 < path.points.size() ? path.points.get(index + 1).y : point.y;
            int uphillDirection = 0;
            if (nextY > point.y) {
                uphillDirection = path.direction;
            } else if (previousY > point.y) {
                uphillDirection = -path.direction;
            }
            if (uphillDirection != 0) {
                int meta = path.uphillMeta(uphillDirection);
                return Math.abs(offset) == ROAD_HALF_WIDTH ? palette.edgeStair(meta) : palette.surfaceStair(meta);
            }
            if (point.distance % LIGHT_EVERY == 0 && Math.abs(offset) == 1) {
                return palette.light;
            }
            if (Math.abs(offset) == ROAD_HALF_WIDTH) {
                return palette.edge;
            }
            if (Math.abs(offset) == ROAD_HALF_WIDTH - 1) {
                return palette.marker;
            }
            return palette.surface;
        }

        private void nextPhase() {
            phase++;
            offset = -CLEAR_HALF_WIDTH;
            yCursor = 0;
            phaseStarted = false;
        }

        private void nextPoint() {
            pointIndex++;
            phase = 0;
            offset = -CLEAR_HALF_WIDTH;
            yCursor = 0;
            phaseStarted = false;
            if (pointIndex >= path.points.size()) {
                done = true;
            }
        }

        public boolean done() {
            return done;
        }

        public long total() {
            return path.points.size() * 520L;
        }

        public String name() {
            return name;
        }
    }

    private static final class RoadFenceStage implements Stage {
        private final String name;
        private final RoadPath path;
        private final BlockAccess blocks;
        private final Object fence;
        private int pointIndex;
        private int side;
        private int part;
        private boolean done;

        RoadFenceStage(String name, RoadPath path, BlockAccess blocks) {
            this.name = name;
            this.path = path;
            this.blocks = blocks;
            this.fence = blocks.state("minecraft:fence", 0);
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                RoadPoint point = path.points.get(pointIndex);
                int sideOffset = side == 0 ? -FENCE_OFFSET : FENCE_OFFSET;
                int x = path.xWithOffset(point, sideOffset);
                int z = path.zWithOffset(point, sideOffset);
                if (part == 0) {
                    Palette palette = blocks.palette(point.biomeName, path.colorMeta);
                    blocks.set(task.world, x, point.y, z, palette.edge, false);
                } else if (!openingAt(point.distance, sideOffset)) {
                    blocks.set(task.world, x, point.y + 1, z, fence, false);
                }
                used++;
                advance();
            }
            return used;
        }

        private boolean openingAt(int distance, int sideOffset) {
            int sideSign = Integer.signum(sideOffset);
            for (int index = 1; index <= OUTPOST_COUNT; index++) {
                int milestone = OUTPOST_INTERVAL * index;
                if (Math.abs(distance - milestone) <= FENCE_OPENING_RADIUS
                    && Integer.signum(path.sideOffset(milestone)) == sideSign) {
                    return true;
                }
            }
            return false;
        }

        private void advance() {
            part++;
            if (part < 2) {
                return;
            }
            part = 0;
            side++;
            if (side < 2) {
                return;
            }
            side = 0;
            pointIndex++;
            if (pointIndex >= path.points.size()) {
                done = true;
            }
        }

        public boolean done() {
            return done;
        }

        public long total() {
            return path.points.size() * 4L;
        }

        public String name() {
            return name;
        }
    }

    private static final class RoadLampStage implements Stage {
        private final String name;
        private final RoadPath path;
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

        RoadLampStage(String name, RoadPath path, BlockAccess blocks) {
            this.name = name;
            this.path = path;
            this.blocks = blocks;
            this.stone = blocks.state("minecraft:stonebrick", 0);
            this.fence = blocks.state("minecraft:fence", 0);
            this.seaLantern = blocks.state("minecraft:sea_lantern", 0);
            this.torchEast = blocks.state("minecraft:torch", 1);
            this.torchWest = blocks.state("minecraft:torch", 2);
            this.torchSouth = blocks.state("minecraft:torch", 3);
            this.torchNorth = blocks.state("minecraft:torch", 4);
            this.distance = SPAWN_RADIUS + 6;
        }

        public int run(RoadTask task, int budget) {
            int used = 0;
            while (used < budget && !done) {
                RoadPoint point = path.nearestPointAtDistance(distance);
                if (point == null) {
                    done = true;
                    break;
                }
                int sideOffset = lampSide == 0 ? -6 : 6;
                placePart(task.world, path.xWithOffset(point, sideOffset), point.y, path.zWithOffset(point, sideOffset), lampPart);
                lampPart++;
                if (lampPart >= 10) {
                    lampPart = 0;
                    lampSide++;
                    if (lampSide >= 2) {
                        lampSide = 0;
                        distance += LAMP_EVERY;
                        if (distance > path.points.get(path.points.size() - 1).distance) {
                            done = true;
                        }
                    }
                }
                used++;
            }
            return used;
        }

        private void placePart(Object world, int x, int y, int z, int part) {
            if (part == 0) {
                supportToGround(world, x, y - 1, z, stone);
                blocks.set(world, x, y, z, stone, false);
            } else if (part >= 1 && part <= 4) {
                blocks.set(world, x, y + part, z, fence, false);
            } else if (part == 5) {
                blocks.set(world, x, y + 5, z, seaLantern, false);
            } else if (part == 6) {
                blocks.set(world, x + 1, y + 4, z, torchEast, false);
            } else if (part == 7) {
                blocks.set(world, x - 1, y + 4, z, torchWest, false);
            } else if (part == 8) {
                blocks.set(world, x, y + 4, z + 1, torchSouth, false);
            } else {
                blocks.set(world, x, y + 4, z - 1, torchNorth, false);
            }
        }

        private void supportToGround(Object world, int x, int topY, int z, Object state) {
            if (topY < FOUNDATION_MIN_Y) {
                return;
            }
            int terrainY = blocks.terrainHeight(world, x, z, topY + 1);
            int minY = Math.max(FOUNDATION_MIN_Y, terrainY + 1);
            for (int y = minY; y <= topY; y++) {
                blocks.set(world, x, y, z, state, false);
            }
        }

        public boolean done() {
            return done;
        }

        public long total() {
            return Math.max(0L, (((path.points.get(path.points.size() - 1).distance - distance) / LAMP_EVERY) + 1L) * 20L);
        }

        public String name() {
            return name;
        }
    }

    private static final class OutpostStage implements Stage {
        private final String name;
        private final RoadPath path;
        private final BlockAccess blocks;
        private List<BlockOp> operations;
        private int index;

        OutpostStage(String name, RoadPath path, BlockAccess blocks) {
            this.name = name;
            this.path = path;
            this.blocks = blocks;
        }

        public int run(RoadTask task, int budget) {
            if (operations == null) {
                operations = createOperations(path, blocks);
            }
            int used = 0;
            while (used < budget && index < operations.size()) {
                operations.get(index).apply(task.world);
                index++;
                used++;
            }
            return used;
        }

        public boolean done() {
            return operations != null && index >= operations.size();
        }

        public long total() {
            return OUTPOST_COUNT * 900L;
        }

        public String name() {
            return name;
        }

        private static List<BlockOp> createOperations(RoadPath path, BlockAccess blocks) {
            ArrayList<BlockOp> ops = new ArrayList<BlockOp>();
            Object air = blocks.air();
            Object stone = blocks.state("minecraft:stonebrick", 0);
            Object mossy = blocks.state("minecraft:stonebrick", 1);
            Object fence = blocks.state("minecraft:fence", 0);
            Object seaLantern = blocks.state("minecraft:sea_lantern", 0);
            Object glowstone = blocks.state("minecraft:glowstone", 0);
            Object accent = blocks.state("minecraft:wool", path.colorMeta);
            Object slab = blocks.state("minecraft:stone_slab", 5);

            for (int index = 1; index <= OUTPOST_COUNT; index++) {
                int milestone = OUTPOST_INTERVAL * index;
                RoadPoint point = path.pointAtDistance(milestone);
                if (point == null) {
                    continue;
                }
                int sideOffset = path.sideOffset(milestone);
                int cx = path.xWithOffset(point, sideOffset);
                int cz = path.zWithOffset(point, sideOffset);
                int y = point.y;
                addCuboid(ops, blocks, cx - 4, y + 1, cz - 4, cx + 4, y + 10, cz + 4, air, true);
                addSupportedPad(ops, blocks, cx - 3, cz - 3, cx + 3, cz + 3, y - 1, stone);
                addCuboid(ops, blocks, cx - 3, y, cz - 3, cx + 3, y, cz + 3, stone, false);
                addCuboid(ops, blocks, cx - 2, y, cz - 2, cx + 2, y, cz + 2, mossy, false);
                addConnector(ops, path, blocks, point, sideOffset, stone, accent);
                addWalls(ops, blocks, path, sideOffset, cx, y, cz, stone, mossy, glowstone);
                addCuboid(ops, blocks, cx - 2, y + 5, cz - 2, cx + 2, y + 5, cz + 2, slab, false);
                addCrenels(ops, blocks, cx, y + 6, cz, stone);
                addCuboid(ops, blocks, cx, y + 6, cz, cx, y + 9, cz, fence, false);
                addCuboid(ops, blocks, cx + (path.zAxis ? Integer.signum(sideOffset) : 0), y + 8, cz + (path.zAxis ? 0 : Integer.signum(sideOffset)), cx + (path.zAxis ? Integer.signum(sideOffset) * 2 : 0), y + 9, cz + (path.zAxis ? 0 : Integer.signum(sideOffset) * 2), accent, false);
                addCuboid(ops, blocks, cx, y + 4, cz, cx, y + 4, cz, seaLantern, false);
            }
            return ops;
        }

        private static void addConnector(
            List<BlockOp> ops,
            RoadPath path,
            BlockAccess blocks,
            RoadPoint point,
            int sideOffset,
            Object stone,
            Object accent
        ) {
            int step = sideOffset > 0 ? 1 : -1;
            for (int offset = step * (ROAD_HALF_WIDTH + 1); Math.abs(offset) < Math.abs(sideOffset) - 3; offset += step) {
                int x = path.xWithOffset(point, offset);
                int z = path.zWithOffset(point, offset);
                ops.add(BlockOp.support(blocks, x, point.y, z, Math.abs(offset) % 3 == 0 ? accent : stone));
            }
        }

        private static void addWalls(
            List<BlockOp> ops,
            BlockAccess blocks,
            RoadPath path,
            int sideOffset,
            int cx,
            int y,
            int cz,
            Object stone,
            Object mossy,
            Object glowstone
        ) {
            for (int yy = y + 1; yy <= y + 4; yy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        boolean border = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                        if (!border) {
                            continue;
                        }
                        if (isDoor(path, sideOffset, dx, dz) && yy <= y + 2) {
                            continue;
                        }
                        Object state = (yy == y + 3 && (Math.abs(dx) + Math.abs(dz)) % 4 == 0) ? glowstone : ((dx + dz + yy) % 5 == 0 ? mossy : stone);
                        ops.add(new BlockOp(blocks, cx + dx, yy, cz + dz, state, false));
                    }
                }
            }
        }

        private static boolean isDoor(RoadPath path, int sideOffset, int dx, int dz) {
            if (path.zAxis) {
                return dz == 0 && dx == (sideOffset > 0 ? -3 : 3);
            }
            return dx == 0 && dz == (sideOffset > 0 ? -3 : 3);
        }

        private static void addCrenels(List<BlockOp> ops, BlockAccess blocks, int cx, int y, int cz, Object stone) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                    boolean edge = (Math.abs(dx) == 3 || Math.abs(dz) == 3) && ((dx + dz) & 1) == 0;
                    if (corner || edge) {
                        ops.add(new BlockOp(blocks, cx + dx, y, cz + dz, stone, false));
                    }
                }
            }
        }

        private static void addCuboid(
            List<BlockOp> ops,
            BlockAccess blocks,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            Object state,
            boolean skipIfAir
        ) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.max(1, Math.min(y1, y2));
            int maxY = Math.min(255, Math.max(y1, y2));
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        ops.add(new BlockOp(blocks, x, y, z, state, skipIfAir));
                    }
                }
            }
        }

        private static void addSupportedPad(
            List<BlockOp> ops,
            BlockAccess blocks,
            int x1,
            int z1,
            int x2,
            int z2,
            int topY,
            Object state
        ) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    ops.add(BlockOp.support(blocks, x, topY, z, state));
                }
            }
        }
    }

    private static final class BlockOp {
        private final BlockAccess blocks;
        private final int x;
        private final int y;
        private final int z;
        private final Object state;
        private final boolean skipIfAir;
        private final boolean supportToGround;

        private BlockOp(BlockAccess blocks, int x, int y, int z, Object state, boolean skipIfAir) {
            this(blocks, x, y, z, state, skipIfAir, false);
        }

        private BlockOp(BlockAccess blocks, int x, int y, int z, Object state, boolean skipIfAir, boolean supportToGround) {
            this.blocks = blocks;
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.skipIfAir = skipIfAir;
            this.supportToGround = supportToGround;
        }

        static BlockOp support(BlockAccess blocks, int x, int y, int z, Object state) {
            return new BlockOp(blocks, x, y, z, state, false, true);
        }

        private void apply(Object world) {
            if (supportToGround) {
                int terrainY = blocks.terrainHeight(world, x, z, y + 1);
                int minY = Math.max(FOUNDATION_MIN_Y, terrainY + 1);
                for (int supportY = minY; supportY <= y; supportY++) {
                    blocks.set(world, x, supportY, z, state, false);
                }
                return;
            }
            blocks.set(world, x, y, z, state, skipIfAir);
        }
    }

    private static final class Palette {
        final Object foundation;
        final Object edge;
        final Object surface;
        final Object marker;
        final Object light;
        private final Object[] edgeStairs;
        private final Object[] surfaceStairs;

        private Palette(
            Object foundation,
            Object edge,
            Object surface,
            Object marker,
            Object light,
            Object[] edgeStairs,
            Object[] surfaceStairs
        ) {
            this.foundation = foundation;
            this.edge = edge;
            this.surface = surface;
            this.marker = marker;
            this.light = light;
            this.edgeStairs = edgeStairs;
            this.surfaceStairs = surfaceStairs;
        }

        Object edgeStair(int meta) {
            return edgeStairs[Math.max(0, Math.min(edgeStairs.length - 1, meta))];
        }

        Object surfaceStair(int meta) {
            return surfaceStairs[Math.max(0, Math.min(surfaceStairs.length - 1, meta))];
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
        private final Method getBlockState;
        private final Method stateGetBlock;
        private final Method blockGetRegistryName;
        private final Method worldGetBiome;
        private final Method biomeGetRegistryName;
        private final List<BlockState> states = new ArrayList<BlockState>();
        private final Map<String, Palette> palettes = new HashMap<String, Palette>();
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
                getBlockState = findAnyMethod(worldType, new String[] { "getBlockState", "func_180495_p" }, blockPosType);
                Class<?> blockStateType = Class.forName("net.minecraft.block.state.IBlockState");
                stateGetBlock = findAnyMethod(blockStateType, new String[] { "getBlock", "func_177230_c" });
                blockGetRegistryName = findAnyMethod(blockType, new String[] { "getRegistryName" });
                worldGetBiome = findAnyMethodOrNull(worldType, new String[] { "getBiome", "func_180494_b" }, blockPosType);
                Class<?> biomeType = Class.forName("net.minecraft.world.biome.Biome");
                biomeGetRegistryName = findAnyMethodOrNull(biomeType, new String[] { "getRegistryName" });
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot initialize block reflection.", exception);
            }
        }

        Object air() {
            return state("minecraft:air", 0);
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

        Palette palette(String biomeName, int colorMeta) {
            String normalized = biomeName == null ? "" : biomeName.toLowerCase(Locale.ROOT);
            String type = "default";
            if (normalized.contains("desert") || normalized.contains("savanna") || normalized.contains("mesa")) {
                type = "sand";
            } else if (normalized.contains("ice") || normalized.contains("snow") || normalized.contains("frozen") || normalized.contains("cold")) {
                type = "snow";
            } else if (normalized.contains("forest") || normalized.contains("jungle") || normalized.contains("swamp")) {
                type = "green";
            }
            String key = type + ":" + colorMeta;
            Palette cached = palettes.get(key);
            if (cached != null) {
                return cached;
            }

            Palette created;
            if ("sand".equals(type)) {
                created = new Palette(
                    state("minecraft:sandstone", 0),
                    state("minecraft:sandstone", 0),
                    state("minecraft:sandstone", 2),
                    state("minecraft:stained_hardened_clay", colorMeta),
                    state("minecraft:sea_lantern", 0),
                    stairStates("minecraft:sandstone_stairs"),
                    stairStates("minecraft:sandstone_stairs")
                );
            } else if ("snow".equals(type)) {
                created = new Palette(
                    state("minecraft:dirt", 0),
                    state("minecraft:stonebrick", 0),
                    state("minecraft:quartz_block", 0),
                    state("minecraft:packed_ice", 0),
                    state("minecraft:sea_lantern", 0),
                    stairStates("minecraft:stone_brick_stairs"),
                    stairStates("minecraft:quartz_stairs")
                );
            } else if ("green".equals(type)) {
                created = new Palette(
                    state("minecraft:dirt", 0),
                    state("minecraft:stonebrick", 1),
                    state("minecraft:quartz_block", 0),
                    state("minecraft:stained_hardened_clay", Math.max(0, Math.min(15, colorMeta))),
                    state("minecraft:sea_lantern", 0),
                    stairStates("minecraft:stone_brick_stairs"),
                    stairStates("minecraft:quartz_stairs")
                );
            } else {
                created = new Palette(
                    state("minecraft:dirt", 0),
                    state("minecraft:stonebrick", 0),
                    state("minecraft:quartz_block", 0),
                    state("minecraft:stained_hardened_clay", colorMeta),
                    state("minecraft:sea_lantern", 0),
                    stairStates("minecraft:stone_brick_stairs"),
                    stairStates("minecraft:quartz_stairs")
                );
            }
            palettes.put(key, created);
            return created;
        }

        private Object[] stairStates(String blockName) {
            return new Object[] {
                state(blockName, 0),
                state(blockName, 1),
                state(blockName, 2),
                state(blockName, 3)
            };
        }

        int terrainHeight(Object world, int x, int z, int fallbackY) {
            if (world == null) {
                return fallbackY - 1;
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                if (!TeleportSupport.prepareDestinationChunk(world, x + 0.5D, z + 0.5D)) {
                    return fallbackY - 1;
                }
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }
            int startY = Math.min(255, fallbackY + MAX_TERRAIN_DELTA + CLEAR_ABOVE_HEIGHT);
            int minY = Math.max(FOUNDATION_MIN_Y, fallbackY - MAX_TERRAIN_DELTA - FOUNDATION_DEPTH);
            for (int y = startY; y >= minY; y--) {
                Object position = blockPos(x, y, z);
                if (Boolean.TRUE.equals(invoke(isAirBlock, world, position))) {
                    continue;
                }
                String blockName = blockName(world, position);
                if (!isIgnoredTerrainBlock(blockName)) {
                    return y;
                }
            }
            return fallbackY - 1;
        }

        int waterLandfillBottom(Object world, int x, int z, int minY, int maxY) {
            if (world == null || maxY < minY) {
                return maxY + 1;
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                if (!TeleportSupport.prepareDestinationChunk(world, x + 0.5D, z + 0.5D)) {
                    return maxY + 1;
                }
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }

            boolean sawLiquid = false;
            for (int y = maxY; y >= minY; y--) {
                Object position = blockPos(x, y, z);
                if (Boolean.TRUE.equals(invoke(isAirBlock, world, position))) {
                    continue;
                }
                String blockName = blockName(world, position);
                if (isLiquidTerrainBlock(blockName)) {
                    sawLiquid = true;
                    continue;
                }
                if (sawLiquid && !isIgnoredTerrainBlock(blockName)) {
                    return y + 1;
                }
            }
            return sawLiquid ? minY : maxY + 1;
        }

        String biomeName(Object world, int x, int z) {
            if (world == null || worldGetBiome == null) {
                return "";
            }
            try {
                if (!TeleportSupport.prepareDestinationChunk(world, x + 0.5D, z + 0.5D)) {
                    return "";
                }
                Object biome = worldGetBiome.invoke(world, blockPos(x, 64, z));
                if (biome == null) {
                    return "";
                }
                if (biomeGetRegistryName != null) {
                    Object name = biomeGetRegistryName.invoke(biome);
                    if (name != null) {
                        return name.toString();
                    }
                }
                return biome.toString();
            } catch (ReflectiveOperationException exception) {
                return "";
            }
        }

        private String blockName(Object world, Object position) {
            Object state = invoke(getBlockState, world, position);
            Object block = invoke(stateGetBlock, state);
            Object name = invoke(blockGetRegistryName, block);
            return name == null ? String.valueOf(block).toLowerCase(Locale.ROOT) : name.toString().toLowerCase(Locale.ROOT);
        }

        private static boolean isIgnoredTerrainBlock(String blockName) {
            return blockName.contains("air")
                || isManagedRoadBlockName(blockName)
                || blockName.contains("leaves")
                || blockName.contains("log")
                || blockName.contains("sapling")
                || blockName.contains("vine")
                || blockName.contains("tallgrass")
                || blockName.contains("double_plant")
                || blockName.contains("yellow_flower")
                || blockName.contains("red_flower")
                || blockName.contains("snow_layer")
                || blockName.contains("deadbush")
                || blockName.contains("reeds")
                || blockName.contains("cactus");
        }

        private static boolean isLiquidTerrainBlock(String blockName) {
            return SpawnRoadBuilderService.isRoadLiquidBlockName(blockName);
        }

        boolean isManagedRoadBlock(Object world, int x, int y, int z) {
            if (world == null || y < 0 || y > 255) {
                return false;
            }
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                if (!TeleportSupport.prepareDestinationChunk(world, x + 0.5D, z + 0.5D)) {
                    return false;
                }
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }
            Object position = blockPos(x, y, z);
            if (Boolean.TRUE.equals(invoke(isAirBlock, world, position))) {
                return false;
            }
            return isManagedRoadBlockName(blockName(world, position));
        }

        private static boolean isManagedRoadBlockName(String blockName) {
            return blockName.contains("wool")
                || blockName.contains("glowstone")
                || blockName.contains("sea_lantern")
                || blockName.contains("stonebrick")
                || blockName.contains("stone_brick_stairs")
                || blockName.contains("sandstone")
                || blockName.contains("quartz_block")
                || blockName.contains("quartz_stairs")
                || blockName.contains("carpet")
                || blockName.contains("fence")
                || blockName.contains("torch")
                || blockName.contains("end_rod")
                || blockName.contains("stone_slab");
        }

        void set(Object world, int x, int y, int z, Object state, boolean skipIfAir) {
            if (world == null) {
                throw new IllegalStateException("World is not loaded.");
            }
            if (y < 0 || y > 255) {
                return;
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

    private static Method findAnyMethodOrNull(Class<?> type, String[] names, Class<?>... parameters) {
        try {
            return findAnyMethod(type, names, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
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
        Collections.addAll(options, "build", "rebuild", "status", "cancel");
        return options;
    }

    static boolean isRoadLiquidBlockName(String blockName) {
        String normalized = blockName == null ? "" : blockName.toLowerCase(Locale.ROOT);
        return normalized.contains("water")
            || normalized.contains("lava")
            || normalized.contains("fluid")
            || normalized.contains("liquid");
    }
}
