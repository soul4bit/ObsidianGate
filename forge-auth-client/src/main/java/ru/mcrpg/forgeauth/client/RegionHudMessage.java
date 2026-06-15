package ru.mcrpg.forgeauth.client;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class RegionHudMessage implements IMessage {

    String regionName = "";

    public RegionHudMessage() {
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
        buf.writeShort(value.length);
        buf.writeBytes(value);
    }
}
