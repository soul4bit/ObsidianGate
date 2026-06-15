package ru.mcrpg.forgeauth.server;

import java.util.logging.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(
    modid = ForgeAuthServerMod.MOD_ID,
    name = ForgeAuthServerMod.MOD_NAME,
    version = ForgeAuthServerMod.VERSION,
    serverSideOnly = true,
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class ForgeAuthServerMod {

    public static final String MOD_ID = "obsidiangateauthserver";
    public static final String MOD_NAME = "ObsidianGate Auth Server";
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final String NETWORK_CHANNEL = "ogauth";

    static {
        BiblioCraftWarningFilter.install();
    }

    private static final Logger LOGGER = Logger.getLogger(MOD_NAME);
    private static SimpleNetworkWrapper networkChannel;
    private static final PlayerAchievementService ACHIEVEMENTS = new PlayerAchievementService(LOGGER);
    private static final ForgeAuthServerLifecycle LIFECYCLE = new ForgeAuthServerLifecycle(LOGGER, ACHIEVEMENTS);
    private static final SpawnProtectionService SPAWN_PROTECTION = new SpawnProtectionService(LOGGER);
    private static final ItemCleanupService ITEM_CLEANUP = new ItemCleanupService(LOGGER);
    private static final RandomLightningService RANDOM_LIGHTNING = new RandomLightningService(LOGGER);
    private static final JudgementNightService JUDGEMENT_NIGHT = new JudgementNightService(LOGGER);
    private static final KitService KIT_SERVICE = new KitService(LOGGER);
    private static final FirstJoinWelcomeService FIRST_JOIN_WELCOME = new FirstJoinWelcomeService(LOGGER);
    private static final HomeService HOME_SERVICE = new HomeService(LOGGER);
    private static final HomeRespawnService HOME_RESPAWN = new HomeRespawnService(LOGGER, HOME_SERVICE);
    private static final TeleportGuardService TELEPORT_GUARD = new TeleportGuardService();
    private static final BackLocationService BACK_LOCATIONS = new BackLocationService(LOGGER);
    private static final PlayerDataGuardService PLAYERDATA_GUARD = new PlayerDataGuardService(LOGGER);
    private static final RegionProtectionService REGIONS = new RegionProtectionService(LOGGER);
    private static final RegionAuditService REGION_AUDIT = new RegionAuditService(LOGGER);
    private static final TreeFellingService TREE_FELLING = new TreeFellingService(LOGGER, REGIONS, REGION_AUDIT);
    private static final RegionProtectionEvents REGION_EVENTS = new RegionProtectionEvents(REGIONS, REGION_AUDIT);

    static ForgeAuthServerLifecycle getLifecycle() {
        return LIFECYCLE;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BiblioCraftWarningFilter.install();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        networkChannel = NetworkRegistry.INSTANCE.newSimpleChannel(NETWORK_CHANNEL);
        networkChannel.registerMessage(AuthTicketMessageHandler.class, AuthTicketMessage.class, 0, net.minecraftforge.fml.relauncher.Side.SERVER);
        networkChannel.registerMessage(
            RegionSelectionMessageNoopHandler.class,
            RegionSelectionMessage.class,
            1,
            net.minecraftforge.fml.relauncher.Side.CLIENT
        );
        networkChannel.registerMessage(
            RegionHudMessageNoopHandler.class,
            RegionHudMessage.class,
            2,
            net.minecraftforge.fml.relauncher.Side.CLIENT
        );
        SPAWN_PROTECTION.load();
        ITEM_CLEANUP.load();
        RANDOM_LIGHTNING.load();
        JUDGEMENT_NIGHT.load();
        KIT_SERVICE.load();
        FIRST_JOIN_WELCOME.load();
        HOME_SERVICE.load();
        BACK_LOCATIONS.load();
        REGIONS.load();
        ACHIEVEMENTS.load();
        MinecraftForge.EVENT_BUS.register(PLAYERDATA_GUARD);
        MinecraftForge.EVENT_BUS.register(LIFECYCLE);
        MinecraftForge.EVENT_BUS.register(FIRST_JOIN_WELCOME);
        MinecraftForge.EVENT_BUS.register(ACHIEVEMENTS);
        MinecraftForge.EVENT_BUS.register(SPAWN_PROTECTION);
        MinecraftForge.EVENT_BUS.register(TREE_FELLING);
        MinecraftForge.EVENT_BUS.register(ITEM_CLEANUP);
        MinecraftForge.EVENT_BUS.register(RANDOM_LIGHTNING);
        MinecraftForge.EVENT_BUS.register(JUDGEMENT_NIGHT);
        MinecraftForge.EVENT_BUS.register(TELEPORT_GUARD);
        MinecraftForge.EVENT_BUS.register(BACK_LOCATIONS);
        MinecraftForge.EVENT_BUS.register(HOME_RESPAWN);
        MinecraftForge.EVENT_BUS.register(REGION_EVENTS);

        AuthServerConfig config = AuthServerConfig.fromSystem();
        if (config.isReady()) {
            LOGGER.info(String.format(
                "Forge auth server инициализирован. Auth base URL=%s serverId=%s grace=%ss",
                config.getAuthBaseUrl(),
                config.getServerId(),
                config.getGraceSeconds()
            ));
        } else {
            LOGGER.warning(
                "Forge auth server инициализирован без настройки авторизации. " +
                "Укажи -D" + AuthServerConfig.AUTH_BASE_URL_PROPERTY + " и -D" + AuthServerConfig.SERVER_ID_PROPERTY + "."
            );
        }
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        SpawnCommand.register(event, TELEPORT_GUARD, LIFECYCLE);
        WaypointTeleportCommand.register(event);
        CallCommand.register(event);
        KitCommand.register(event, KIT_SERVICE, LIFECYCLE);
        HomeCommand.register(event, HOME_SERVICE, TELEPORT_GUARD, LIFECYCLE);
        BackCommand.register(event, BACK_LOCATIONS, TELEPORT_GUARD, LIFECYCLE);
        RandomTeleportCommand.register(event, TELEPORT_GUARD, LIFECYCLE);
        SpawnProtectionCommand.register(event, SPAWN_PROTECTION);
        AchievementCommand.register(event, ACHIEVEMENTS);
        RegionCommand.register(event, REGIONS, REGION_AUDIT, LIFECYCLE);
        HelpCommand.register(event);
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        ACHIEVEMENTS.shutdown();
        LIFECYCLE.shutdown();
    }

    static SimpleNetworkWrapper networkChannel() {
        return networkChannel;
    }
}
