package ru.mcrpg.forgeauth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class RegionSelectionMessageTest {

    @Test
    void roundTripKeepsThreeDimensionalSelection() {
        RegionSelectionMessage original = new RegionSelectionMessage();
        original.visible = true;
        original.hasSecond = true;
        original.dimension = 7;
        original.firstX = -12;
        original.firstY = 64;
        original.firstZ = 30;
        original.secondX = 18;
        original.secondY = 91;
        original.secondZ = -4;

        RegionSelectionMessage restored = roundTrip(original);

        assertTrue(restored.visible);
        assertTrue(restored.hasSecond);
        assertEquals(7, restored.dimension);
        assertEquals(-12, restored.firstX);
        assertEquals(64, restored.firstY);
        assertEquals(30, restored.firstZ);
        assertEquals(18, restored.secondX);
        assertEquals(91, restored.secondY);
        assertEquals(-4, restored.secondZ);
    }

    @Test
    void hiddenMessageContainsNoSelection() {
        RegionSelectionMessage restored = roundTrip(new RegionSelectionMessage());

        assertFalse(restored.visible);
        assertFalse(restored.hasSecond);
    }

    private static RegionSelectionMessage roundTrip(RegionSelectionMessage original) {
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        RegionSelectionMessage restored = new RegionSelectionMessage();
        restored.fromBytes(buffer);
        return restored;
    }
}
