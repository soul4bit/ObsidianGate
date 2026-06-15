package ru.mcrpg.forgeauth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class RegionHudMessageTest {

    @Test
    void roundTripKeepsUtf8RegionName() {
        RegionHudMessage original = new RegionHudMessage();
        original.regionName = "home_player";
        original.ownerName = "soul4bit";
        original.relation = RegionHudMessage.RELATION_OWNER;
        ByteBuf buffer = Unpooled.buffer();

        original.toBytes(buffer);

        RegionHudMessage restored = new RegionHudMessage();
        restored.fromBytes(buffer);
        assertEquals("home_player", restored.regionName);
        assertEquals("soul4bit", restored.ownerName);
        assertEquals(RegionHudMessage.RELATION_OWNER, restored.relation);
    }

    @Test
    void roundTripKeepsLegacyHiddenFormat() {
        RegionHudMessage original = new RegionHudMessage();
        original.regionName = " none ";
        original.ownerName = "ignored";
        original.relation = RegionHudMessage.RELATION_OWNER;
        ByteBuf buffer = Unpooled.buffer();

        original.toBytes(buffer);

        assertEquals(0, buffer.readUnsignedShort());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rendererTreatsNoneAsHiddenRegion() throws Exception {
        RegionHudRenderer.update(" none ", "owner", RegionHudMessage.RELATION_OWNER);

        assertEquals("", currentRegionName());
        assertEquals("", currentOwnerName());
        assertEquals(RegionHudMessage.RELATION_VISITOR, currentRelation());
    }

    @Test
    void rendererKeepsTrimmedRegionName() throws Exception {
        RegionHudRenderer.update("  spawn  ", " soul4bit ", RegionHudMessage.RELATION_OWNER);

        assertEquals("spawn", currentRegionName());
        assertEquals("soul4bit", currentOwnerName());
        assertEquals(RegionHudMessage.RELATION_OWNER, currentRelation());
    }

    private static String currentRegionName() throws Exception {
        Field field = RegionHudRenderer.class.getDeclaredField("regionName");
        field.setAccessible(true);
        return String.valueOf(field.get(null));
    }

    private static String currentOwnerName() throws Exception {
        Field field = RegionHudRenderer.class.getDeclaredField("ownerName");
        field.setAccessible(true);
        return String.valueOf(field.get(null));
    }

    private static int currentRelation() throws Exception {
        Field field = RegionHudRenderer.class.getDeclaredField("relation");
        field.setAccessible(true);
        return ((Number) field.get(null)).intValue();
    }
}
