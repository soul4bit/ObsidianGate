package ru.mcrpg.forgeauth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class RegionHudMessageTest {

    @Test
    void roundTripKeepsUtf8RegionName() {
        RegionHudMessage original = new RegionHudMessage();
        original.regionName = "дом_игрока";
        ByteBuf buffer = Unpooled.buffer();

        original.toBytes(buffer);

        RegionHudMessage restored = new RegionHudMessage();
        restored.fromBytes(buffer);
        assertEquals("дом_игрока", restored.regionName);
    }
}
