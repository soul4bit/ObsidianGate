package ru.mcrpg.forgeauth.server;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RegionSelectionMessageNoopHandler implements IMessageHandler<RegionSelectionMessage, IMessage> {

    @Override
    public IMessage onMessage(RegionSelectionMessage message, MessageContext context) {
        return null;
    }
}
