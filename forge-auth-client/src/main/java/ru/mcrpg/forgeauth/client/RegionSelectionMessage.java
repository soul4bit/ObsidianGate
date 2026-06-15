package ru.mcrpg.forgeauth.client;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class RegionSelectionMessage implements IMessage {

    boolean visible;
    boolean hasSecond;
    int dimension;
    int firstX;
    int firstY;
    int firstZ;
    int secondX;
    int secondY;
    int secondZ;

    public RegionSelectionMessage() {
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
