package ru.mcrpg.forgeauth.server;

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

    static RegionHudMessage show(String regionName) {
        return show(regionName, "", RELATION_VISITOR);
    }

    static RegionHudMessage show(String regionName, String ownerName, int relation) {
        RegionHudMessage message = new RegionHudMessage();
        message.regionName = normalize(regionName);
        message.ownerName = normalizeOwner(ownerName);
        message.relation = normalizeRelation(relation);
        return message;
    }

    static RegionHudMessage hidden() {
        return new RegionHudMessage();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        regionName = normalize(readString(buf));
        if (buf.isReadable()) {
            ownerName = normalizeOwner(readString(buf));
        }
        if (buf.isReadable()) {
            relation = normalizeRelation(buf.readUnsignedByte());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, regionName, "Region name");
        if (regionName.isEmpty()) {
            return;
        }
        writeString(buf, ownerName, "Region owner");
        buf.writeByte(normalizeRelation(relation));
    }

    private static String readString(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        byte[] value = new byte[length];
        buf.readBytes(value);
        return new String(value, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String text, String label) {
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        if (value.length > 512) {
            throw new IllegalArgumentException(label + " is too long.");
        }
        buf.writeShort(value.length);
        buf.writeBytes(value);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || "none".equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String normalizeOwner(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeRelation(int value) {
        if (value == RELATION_OWNER || value == RELATION_MEMBER) {
            return value;
        }
        return RELATION_VISITOR;
    }
}
