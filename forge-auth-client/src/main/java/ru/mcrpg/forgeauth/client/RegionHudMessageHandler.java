package ru.mcrpg.forgeauth.client;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RegionHudMessageHandler implements IMessageHandler<RegionHudMessage, IMessage> {

    @Override
    public IMessage onMessage(RegionHudMessage message, MessageContext context) {
        RegionHudRenderer.update(message.regionName);
        return null;
    }
}
