package ru.mcrpg.forgeauth.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class SpawnRoadMilestoneService {

    static final int CENTER_X = 356;
    static final int CENTER_Z = 2823;
    static final int ROAD_START_DISTANCE = 49;
    static final int ROAD_END_DISTANCE = 1048;
    private static final int ROAD_HALF_WIDTH = 18;
    private static final int MILESTONE_TOLERANCE = 4;
    private static final int NOTICE_INTERVAL_TICKS = 20;
    private static final int[] MILESTONES = { 250, 500, 750, 1000 };

    private final Map<String, String> lastMilestones = new ConcurrentHashMap<String, String>();
    private final Map<String, Integer> lastNoticeTicks = new ConcurrentHashMap<String, Integer>();
    private int tickCounter;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Object player = ServerReflection.field(event, "player");
        if (player == null || TeleportSupport.playerDimension(player) != 0) {
            return;
        }

        tickCounter++;
        RoadNotice notice = noticeAt(
            (int) Math.floor(TeleportSupport.playerX(player)),
            (int) Math.floor(TeleportSupport.playerZ(player))
        );
        String playerId = PlayerIdentity.id(player);
        if (notice == null) {
            lastMilestones.remove(playerId);
            return;
        }

        String key = notice.routeId + ":" + notice.milestone;
        String previous = lastMilestones.get(playerId);
        Integer previousTick = lastNoticeTicks.get(playerId);
        if (key.equals(previous) && previousTick != null && tickCounter - previousTick.intValue() < NOTICE_INTERVAL_TICKS) {
            return;
        }

        lastMilestones.put(playerId, key);
        lastNoticeTicks.put(playerId, Integer.valueOf(tickCounter));
        ServerChat.actionBar(player, notice.message());
    }

    static RoadNotice noticeAt(int x, int z) {
        RoadNotice zNotice = noticeOnAxis("north", "Северный путь", z - CENTER_Z, Math.abs(x - CENTER_X));
        if (zNotice != null) {
            return zNotice;
        }
        zNotice = noticeOnAxis("south", "Южный путь", CENTER_Z - z, Math.abs(x - CENTER_X));
        if (zNotice != null) {
            return zNotice;
        }
        RoadNotice xNotice = noticeOnAxis("east", "Восточный путь", x - CENTER_X, Math.abs(z - CENTER_Z));
        if (xNotice != null) {
            return xNotice;
        }
        return noticeOnAxis("west", "Западный путь", CENTER_X - x, Math.abs(z - CENTER_Z));
    }

    private static RoadNotice noticeOnAxis(String routeId, String routeName, int distance, int sideOffset) {
        if (sideOffset > ROAD_HALF_WIDTH || distance < ROAD_START_DISTANCE || distance > ROAD_END_DISTANCE) {
            return null;
        }
        for (int milestone : MILESTONES) {
            if (Math.abs(distance - milestone) <= MILESTONE_TOLERANCE) {
                return new RoadNotice(routeId, routeName, milestone);
            }
        }
        return null;
    }

    static final class RoadNotice {
        final String routeId;
        final String routeName;
        final int milestone;

        private RoadNotice(String routeId, String routeName, int milestone) {
            this.routeId = routeId;
            this.routeName = routeName;
            this.milestone = milestone;
        }

        String message() {
            return "\u00A76" + routeName + " \u00A78• \u00A7f" + milestone + " \u00A77блоков от спавна";
        }
    }
}
