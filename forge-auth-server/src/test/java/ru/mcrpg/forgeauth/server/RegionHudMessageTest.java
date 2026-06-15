package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
