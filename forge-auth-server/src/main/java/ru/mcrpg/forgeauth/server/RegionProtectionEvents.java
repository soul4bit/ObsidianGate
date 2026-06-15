package ru.mcrpg.forgeauth.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class RegionProtectionEvents {

    private static final String SUBJECT = "Приват";
    private final RegionProtectionService regions;
    private final Map<String, String> playerRegions = new ConcurrentHashMap<String, String>();
    private final Map<String, Long> lastRegionChecks = new ConcurrentHashMap<String, Long>();

    RegionProtectionEvents(RegionProtectionService regions) {
        this.regions = regions;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWandLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Object player = ServerReflection.invoke(event, new String[] { "getEntityPlayer", "getEntity" });
        if (!isWand(player, ServerReflection.invoke(event, new String[] { "getHand" }))) {
            return;
        }
        setSelection(player, 1, ServerReflection.invoke(event, new String[] { "getPos" }));
        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWandRightClick(PlayerInteractEvent.RightClickBlock event) {
        Object player = ServerReflection.invoke(event, new String[] { "getEntityPlayer", "getEntity" });
        if (!isWand(player, ServerReflection.invoke(event, new String[] { "getHand" }))) {
            return;
        }
        setSelection(player, 2, ServerReflection.invoke(event, new String[] { "getPos" }));
        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Object player = ServerReflection.invoke(event, new String[] { "getPlayer" });
        if (!canBuild(player, ServerReflection.invoke(event, new String[] { "getPos" }))) {
            event.setCanceled(true);
            event.setExpToDrop(0);
            deny(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Object player = ServerReflection.invoke(event, new String[] { "getPlayer" });
        if (!canBuild(player, ServerReflection.invoke(event, new String[] { "getPos" }))) {
            event.setCanceled(true);
            deny(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Object player = ServerReflection.invoke(event, new String[] { "getEntityPlayer", "getEntity" });
        Object hand = ServerReflection.invoke(event, new String[] { "getHand" });
        if (event.isCanceled() || isWand(player, hand)) {
            return;
        }
        Object pos = ServerReflection.invoke(event, new String[] { "getPos" });
        RegionProtectionService.RegionFlag flag = interactionFlag(event, pos);
        boolean allowed = flag == null ? canBuild(player, pos) : allows(player, pos, flag);
        if (!allowed) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            event.setCanceled(true);
            deny(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerHurt(LivingHurtEvent event) {
        Object victim = ServerReflection.invoke(event, new String[] { "getEntityLiving", "getEntity" });
        Object source = ServerReflection.invoke(event, new String[] { "getSource" });
        Object attacker = ServerReflection.invoke(source, new String[] { "getTrueSource", "func_76346_g" });
        if (victim == null || attacker == null || !isPlayer(victim) || !isPlayer(attacker)) {
            return;
        }
        int dimension = TeleportSupport.playerDimension(victim);
        int x = (int) Math.floor(TeleportSupport.playerX(victim));
        int y = (int) Math.floor(TeleportSupport.playerY(victim));
        int z = (int) Math.floor(TeleportSupport.playerZ(victim));
        RegionProtectionService.Region region = regions.regionAt(dimension, x, y, z);
        if (region != null && !region.flag(RegionProtectionService.RegionFlag.PVP)) {
            event.setCanceled(true);
            ServerChat.status(attacker, ServerChat.Tone.WARNING, SUBJECT, "PvP в регионе " + ServerChat.value(region.name) + " запрещено.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        Object target = ServerReflection.invoke(event, new String[] { "getPos" });
        Object source = ServerReflection.invoke(event, new String[] { "getLiquidPos" });
        Object world = ServerReflection.invoke(event, new String[] { "getWorld" });
        int dimension = dimension(world);
        if (regions.crossesProtectedBoundary(dimension, x(source), y(source), z(source), x(target), y(target), z(target))) {
            RegionProtectionService.Region targetRegion = regions.regionAt(dimension, x(target), y(target), z(target));
            if (targetRegion != null && !targetRegion.flag(RegionProtectionService.RegionFlag.LIQUIDS)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        Object world = ServerReflection.invoke(event, new String[] { "getWorld" });
        Object source = ServerReflection.invoke(event, new String[] { "getPos" });
        String block = blockName(world, source);
        if (!isBoundaryMachine(block)) {
            return;
        }
        int dimension = dimension(world);
        int[][] offsets = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] offset : offsets) {
            int targetX = x(source) + offset[0];
            int targetY = y(source) + offset[1];
            int targetZ = z(source) + offset[2];
            if (regions.crossesProtectedBoundary(dimension, x(source), y(source), z(source), targetX, targetY, targetZ)) {
                RegionProtectionService.Region target = regions.regionAt(dimension, targetX, targetY, targetZ);
                RegionProtectionService.RegionFlag flag = isLiquidOrFire(block)
                    ? RegionProtectionService.RegionFlag.LIQUIDS
                    : RegionProtectionService.RegionFlag.MECHANISMS;
                if (target != null && !target.flag(flag)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Object player = ServerReflection.field(event, "player");
        if (player == null) {
            return;
        }
        String playerId = PlayerIdentity.id(player);
        long now = System.currentTimeMillis();
        Long previousCheck = lastRegionChecks.put(playerId, Long.valueOf(now));
        if (previousCheck != null && now - previousCheck.longValue() < 1000L) {
            return;
        }
        RegionProtectionService.Region current = regions.regionAt(
            TeleportSupport.playerDimension(player),
            (int) Math.floor(TeleportSupport.playerX(player)),
            (int) Math.floor(TeleportSupport.playerY(player)),
            (int) Math.floor(TeleportSupport.playerZ(player))
        );
        String previousName = playerRegions.get(playerId);
        String currentName = current == null ? null : current.name;
        if (equals(previousName, currentName)) {
            return;
        }
        if (previousName != null) {
            ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "вы покинули регион " + ServerChat.value(previousName) + ".");
        }
        if (currentName != null) {
            ServerChat.status(player, ServerChat.Tone.INFO, SUBJECT, "вы вошли в регион " + ServerChat.value(currentName) + ", владелец " + ServerChat.value(current.ownerName) + ".");
            playerRegions.put(playerId, currentName);
        } else {
            playerRegions.remove(playerId);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        Object world = ServerReflection.invoke(event, new String[] { "getWorld" });
        Object provider = ServerReflection.field(world, "provider", "field_73011_w", "s");
        int dimension = ServerReflection.integer(ServerReflection.invoke(provider, new String[] { "getDimension", "func_186058_p", "i" }));
        Object rawAffected = ServerReflection.invoke(event, new String[] { "getAffectedBlocks" });
        if (!(rawAffected instanceof List<?>)) {
            return;
        }
        Iterator<?> iterator = ((List<?>) rawAffected).iterator();
        while (iterator.hasNext()) {
            Object pos = iterator.next();
            if (regions.regionAt(dimension, x(pos), y(pos), z(pos)) != null) {
                iterator.remove();
            }
        }
    }

    private void setSelection(Object player, int point, Object pos) {
        regions.setSelectionPoint(
            PlayerIdentity.id(player),
            point,
            new RegionProtectionService.Position(
                TeleportSupport.playerDimension(player),
                x(pos),
                y(pos),
                z(pos)
            )
        );
        ServerChat.status(
            player,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            "точка " + point + ": " + x(pos) + ", " + y(pos) + ", " + z(pos) + "."
        );
    }

    private boolean canBuild(Object player, Object pos) {
        return regions.canBuild(
            PlayerIdentity.id(player),
            PlayerIdentity.name(player),
            ServerReflection.bool(ServerReflection.invoke(player, new String[] { "canUseCommand", "func_70003_b" }, Integer.valueOf(2), "rg")),
            TeleportSupport.playerDimension(player),
            x(pos),
            y(pos),
            z(pos)
        );
    }

    private boolean allows(Object player, Object pos, RegionProtectionService.RegionFlag flag) {
        return regions.allows(
            flag,
            PlayerIdentity.id(player),
            PlayerIdentity.name(player),
            isOperator(player),
            TeleportSupport.playerDimension(player),
            x(pos),
            y(pos),
            z(pos)
        );
    }

    private RegionProtectionService.RegionFlag interactionFlag(Object event, Object pos) {
        Object world = ServerReflection.invoke(event, new String[] { "getWorld" });
        String name = blockName(world, pos);
        if (containsAny(name, "door", "trapdoor", "fence_gate")) {
            return RegionProtectionService.RegionFlag.DOORS;
        }
        if (containsAny(name, "chest", "barrel", "shulker", "furnace", "dispenser", "dropper")) {
            return RegionProtectionService.RegionFlag.CHESTS;
        }
        if (containsAny(name, "lever", "button", "hopper", "piston", "repeater", "comparator", "redstone", "machine")) {
            return RegionProtectionService.RegionFlag.MECHANISMS;
        }
        return null;
    }

    private static boolean isWand(Object player, Object hand) {
        if (player == null || (hand != null && !"MAIN_HAND".equals(String.valueOf(hand)))) {
            return false;
        }
        Object stack = ServerReflection.invoke(player, new String[] { "getHeldItemMainhand", "func_184614_ca" });
        if (stack == null || ServerReflection.bool(ServerReflection.invoke(stack, new String[] { "isEmpty", "func_190926_b" }))) {
            return false;
        }
        Object item = ServerReflection.invoke(stack, new String[] { "getItem", "func_77973_b" });
        Object registryName = ServerReflection.invoke(item, new String[] { "getRegistryName" });
        return "minecraft:wooden_axe".equals(String.valueOf(registryName));
    }

    private static void deny(Object player) {
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "это чужая территория.");
    }

    private static boolean isOperator(Object player) {
        return ServerReflection.bool(ServerReflection.invoke(
            player,
            new String[] { "canUseCommand", "func_70003_b" },
            Integer.valueOf(2),
            "rg"
        ));
    }

    private static int dimension(Object world) {
        Object provider = ServerReflection.field(world, "provider", "field_73011_w", "s");
        return ServerReflection.integer(ServerReflection.invoke(provider, new String[] { "getDimension", "func_186058_p", "i" }));
    }

    private static String blockName(Object world, Object pos) {
        Object state = ServerReflection.invoke(world, new String[] { "getBlockState", "func_180495_p" }, pos);
        Object block = ServerReflection.invoke(state, new String[] { "getBlock", "func_177230_c" });
        Object registryName = ServerReflection.invoke(block, new String[] { "getRegistryName" });
        return String.valueOf(registryName).toLowerCase();
    }

    private static boolean isBoundaryMachine(String name) {
        return containsAny(name, "piston", "hopper", "fire", "water", "lava", "fluid");
    }

    private static boolean isLiquidOrFire(String name) {
        return containsAny(name, "fire", "water", "lava", "fluid");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlayer(Object entity) {
        return entity != null && entity.getClass().getName().toLowerCase().contains("player");
    }

    private static boolean equals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int x(Object pos) {
        return ServerReflection.integer(ServerReflection.invoke(pos, new String[] { "getX", "func_177958_n", "p" }));
    }

    private static int y(Object pos) {
        return ServerReflection.integer(ServerReflection.invoke(pos, new String[] { "getY", "func_177956_o", "q" }));
    }

    private static int z(Object pos) {
        return ServerReflection.integer(ServerReflection.invoke(pos, new String[] { "getZ", "func_177952_p", "r" }));
    }
}
