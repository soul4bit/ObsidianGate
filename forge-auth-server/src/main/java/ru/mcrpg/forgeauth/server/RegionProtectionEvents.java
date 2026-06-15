package ru.mcrpg.forgeauth.server;

import java.util.Iterator;
import java.util.List;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class RegionProtectionEvents {

    private static final String SUBJECT = "Приват";
    private final RegionProtectionService regions;

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
        if (!canBuild(player, ServerReflection.invoke(event, new String[] { "getPos" }))) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            event.setCanceled(true);
            deny(player);
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
