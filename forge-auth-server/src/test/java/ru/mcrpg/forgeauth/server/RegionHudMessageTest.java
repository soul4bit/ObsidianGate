package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RegionHudMessageTest {

    @Test
    void noneRegionSerializesAsHiddenHud() {
        ByteBuf buffer = Unpooled.buffer();

        RegionHudMessage.show(" none ").toBytes(buffer);

        assertEquals(0, buffer.readUnsignedShort());
        assertFalse(buffer.isReadable());
    }

    @Test
    void visibleRegionNameIsTrimmedBeforeSerialization() {
        ByteBuf buffer = Unpooled.buffer();

        RegionHudMessage.show("  spawn  ").toBytes(buffer);

        int length = buffer.readUnsignedShort();
        byte[] value = new byte[length];
        buffer.readBytes(value);
        assertEquals("spawn", new String(value, StandardCharsets.UTF_8));
    }

    @Test
    void visibleRegionIncludesOwnerAndRelationAfterLegacyName() {
        ByteBuf buffer = Unpooled.buffer();

        RegionHudMessage.show("  spawn  ", " soul4bit ", RegionHudMessage.RELATION_OWNER).toBytes(buffer);

        assertEquals("spawn", readString(buffer));
        assertEquals("soul4bit", readString(buffer));
        assertEquals(RegionHudMessage.RELATION_OWNER, buffer.readUnsignedByte());
        assertFalse(buffer.isReadable());
    }

    private static String readString(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        byte[] value = new byte[length];
        buffer.readBytes(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
