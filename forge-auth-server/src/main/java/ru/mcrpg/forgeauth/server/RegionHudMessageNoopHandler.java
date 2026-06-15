package ru.mcrpg.forgeauth.server;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RegionHudMessageNoopHandler implements IMessageHandler<RegionHudMessage, IMessage> {

    @Override
    public IMessage onMessage(RegionHudMessage message, MessageContext context) {
        return null;
    }
}
