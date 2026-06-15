package ru.mcrpg.forgeauth.client;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class RegionHudMessage implements IMessage {

    static final int RELATION_OWNER = 0;
    static final int RELATION_MEMBER = 1;
    static final int RELATION_VISITOR = 2;

    String regionName = "";
    String ownerName = "";
    int relation = RELATION_VISITOR;

    public RegionHudMessage() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        regionName = readString(buf);
        ownerName = buf.isReadable() ? readString(buf) : "";
        relation = buf.isReadable() ? normalizeRelation(buf.readUnsignedByte()) : RELATION_VISITOR;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        String normalizedRegion = normalize(regionName);
        writeString(buf, normalizedRegion);
        if (normalizedRegion.isEmpty()) {
            return;
        }
        writeString(buf, ownerName);
        buf.writeByte(normalizeRelation(relation));
    }

    private static String readString(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        byte[] value = new byte[length];
        buf.readBytes(value);
        return new String(value, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String text) {
        byte[] value = normalizeText(text).getBytes(StandardCharsets.UTF_8);
        buf.writeShort(value.length);
        buf.writeBytes(value);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty() || "none".equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private static int normalizeRelation(int value) {
        if (value == RELATION_OWNER || value == RELATION_MEMBER) {
            return value;
        }
        return RELATION_VISITOR;
    }
}
