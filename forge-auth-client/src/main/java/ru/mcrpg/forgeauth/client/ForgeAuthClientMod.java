package ru.mcrpg.forgeauth.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.util.logging.Logger;

@Mod(
    modid = ForgeAuthClientMod.MOD_ID,
    name = ForgeAuthClientMod.MOD_NAME,
    version = ForgeAuthClientMod.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class ForgeAuthClientMod {

    public static final String MOD_ID = "obsidiangateauthclient";
    public static final String MOD_NAME = "ObsidianGate Auth Client";
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final String NETWORK_CHANNEL = "ogauth";

    private static final Logger LOGGER = Logger.getLogger(MOD_NAME);

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ClientModHotfixes.apply(LOGGER);
        SimpleNetworkWrapper channel = NetworkRegistry.INSTANCE.newSimpleChannel(NETWORK_CHANNEL);
        channel.registerMessage(AuthTicketMessageNoopHandler.class, AuthTicketMessage.class, 0, net.minecraftforge.fml.relauncher.Side.SERVER);
        MinecraftForge.EVENT_BUS.register(new ForgeAuthClientLifecycle(channel, LOGGER));
        LOGGER.info("Forge auth client инициализирован.");
    }
}
