package ru.mcrpg.forgeauth.client;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RegionSelectionMessageHandler implements IMessageHandler<RegionSelectionMessage, IMessage> {

    @Override
    public IMessage onMessage(final RegionSelectionMessage message, MessageContext context) {
        RegionSelectionRenderer.update(message);
        return null;
    }
}
