package ru.mcrpg.forgeauth.server;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class RegionHudMessage implements IMessage {

    private String regionName = "";

    public RegionHudMessage() {
    }

    static RegionHudMessage show(String regionName) {
        RegionHudMessage message = new RegionHudMessage();
        message.regionName = regionName == null ? "" : regionName;
        return message;
    }

    static RegionHudMessage hidden() {
        return new RegionHudMessage();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        byte[] value = new byte[length];
        buf.readBytes(value);
        regionName = new String(value, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] value = regionName.getBytes(StandardCharsets.UTF_8);
        if (value.length > 512) {
            throw new IllegalArgumentException("Region name is too long.");
        }
        buf.writeShort(value.length);
        buf.writeBytes(value);
    }
}
