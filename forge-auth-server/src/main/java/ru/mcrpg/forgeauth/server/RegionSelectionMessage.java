package ru.mcrpg.forgeauth.server;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class RegionSelectionMessage implements IMessage {

    private boolean visible;
    private boolean hasSecond;
    private int dimension;
    private int firstX;
    private int firstY;
    private int firstZ;
    private int secondX;
    private int secondY;
    private int secondZ;

    public RegionSelectionMessage() {
    }

    static RegionSelectionMessage hidden() {
        return new RegionSelectionMessage();
    }

    static RegionSelectionMessage visible(
        RegionProtectionService.Position first,
        RegionProtectionService.Position second
    ) {
        RegionSelectionMessage message = new RegionSelectionMessage();
        message.visible = true;
        message.hasSecond = second != null && first.dimension == second.dimension;
        message.dimension = first.dimension;
        message.firstX = first.x;
        message.firstY = first.y;
        message.firstZ = first.z;
        if (message.hasSecond) {
            message.secondX = second.x;
            message.secondY = second.y;
            message.secondZ = second.z;
        }
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        visible = buf.readBoolean();
        if (!visible) {
            return;
        }
        hasSecond = buf.readBoolean();
        dimension = buf.readInt();
        firstX = buf.readInt();
        firstY = buf.readInt();
        firstZ = buf.readInt();
        if (hasSecond) {
            secondX = buf.readInt();
            secondY = buf.readInt();
            secondZ = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(visible);
        if (!visible) {
            return;
        }
        buf.writeBoolean(hasSecond);
        buf.writeInt(dimension);
        buf.writeInt(firstX);
        buf.writeInt(firstY);
        buf.writeInt(firstZ);
        if (hasSecond) {
            buf.writeInt(secondX);
            buf.writeInt(secondY);
            buf.writeInt(secondZ);
        }
    }
}
