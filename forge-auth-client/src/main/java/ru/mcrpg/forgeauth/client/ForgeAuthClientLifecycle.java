package ru.mcrpg.forgeauth.client;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import ru.mcrpg.gameauth.GameTicketProof;
import ru.mcrpg.gameauth.LauncherSession;
import ru.mcrpg.gameauth.LauncherSessionFiles;

final class ForgeAuthClientLifecycle {

    private static final int INITIAL_SEND_DELAY_TICKS = 0;
    private static final int SEND_RETRY_INTERVAL_TICKS = 20;
    private static final int MAX_SEND_ATTEMPTS = 5;

    private final TicketSender ticketSender;
    private final Logger logger;
    private final LauncherSessionFiles sessionFiles;

    private LauncherSession pendingSession;
    private Path pendingSessionPath;
    private GameTicketProof pendingProof;
    private boolean pendingSend;
    private boolean currentTicketConsumed;
    private int sendAttempts;
    private int ticksUntilSend;

    ForgeAuthClientLifecycle(SimpleNetworkWrapper channel, Logger logger) {
        this(new NetworkTicketSender(channel), logger, new LauncherSessionFiles());
    }

    ForgeAuthClientLifecycle(TicketSender ticketSender, Logger logger, LauncherSessionFiles sessionFiles) {
        this.ticketSender = ticketSender;
        this.logger = logger;
        this.sessionFiles = sessionFiles;
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        RegionHudRenderer.clear();
        RegionSelectionRenderer.clear();
        pendingSession = null;
        pendingSessionPath = null;
        pendingProof = null;
        pendingSend = false;
        currentTicketConsumed = false;
        sendAttempts = 0;
        ticksUntilSend = 0;

        try {
            pendingSessionPath = sessionFiles.resolveFromSystemProperty();
            LauncherSession session = sessionFiles.read(pendingSessionPath);
            if (session.isExpired(Instant.now())) {
                logger.warning("Ticket сессии лаунчера уже истек. Отправка отменена.");
                return;
            }
            if (!session.hasTickets()) {
                logger.warning("В сессии лаунчера не осталось ticket для переподключения. Отправка отменена.");
                return;
            }

            GameTicketProof proof = session.toTicketProof();
            if (!proof.isComplete()) {
                logger.warning("В сессии лаунчера не хватает данных ticket proof. Отправка отменена.");
                return;
            }

            pendingSession = session;
            pendingProof = proof;
            pendingSend = true;
            ticksUntilSend = INITIAL_SEND_DELAY_TICKS;
            logger.info(String.format(
                "Сессия лаунчера загружена для %s. Отправляем ticket proof с повторными попытками.",
                session.getUsername()
            ));
        } catch (IOException exception) {
            logger.warning("Файл сессии лаунчера недоступен: " + exception.getMessage());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Не удалось подготовить proof сессии лаунчера.", exception);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        runClientEndTick();
    }

    void runClientEndTick() {
        if (!pendingSend) {
            return;
        }

        if (pendingSession == null || pendingProof == null) {
            pendingSend = false;
            ticksUntilSend = 0;
            return;
        }

        if (ticksUntilSend > 0) {
            ticksUntilSend--;
            return;
        }

        if (sendAttempts >= MAX_SEND_ATTEMPTS) {
            pendingSend = false;
            return;
        }

        sendAttempts++;
        try {
            ticketSender.send(new AuthTicketMessage(pendingProof));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Не удалось отправить launcher ticket proof. Попытка " + sendAttempts + ".", exception);
            scheduleRetryOrStop();
            return;
        }

        if (!currentTicketConsumed) {
            consumeCurrentTicket();
            currentTicketConsumed = true;
        }

        logger.info(String.format(
            "Ticket proof лаунчера для %s отправлен в серверный канал. Попытка %d/%d.",
            pendingSession.getUsername(),
            sendAttempts,
            MAX_SEND_ATTEMPTS
        ));
        scheduleRetryOrStop();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        RegionHudRenderer.clear();
        RegionSelectionRenderer.clear();
        pendingSession = null;
        pendingSessionPath = null;
        pendingProof = null;
        pendingSend = false;
        currentTicketConsumed = false;
        sendAttempts = 0;
        ticksUntilSend = 0;
    }

    private void scheduleRetryOrStop() {
        if (sendAttempts >= MAX_SEND_ATTEMPTS) {
            pendingSend = false;
            ticksUntilSend = 0;
            return;
        }

        ticksUntilSend = SEND_RETRY_INTERVAL_TICKS;
    }

    private void consumeCurrentTicket() {
        if (pendingSession == null || pendingSessionPath == null) {
            return;
        }

        try {
            sessionFiles.write(pendingSessionPath, pendingSession.consumeTicket());
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось сохранить оставшиеся ticket переподключения.", exception);
        }
    }

    interface TicketSender {
        void send(AuthTicketMessage message);
    }

    private static final class NetworkTicketSender implements TicketSender {
        private final SimpleNetworkWrapper channel;

        private NetworkTicketSender(SimpleNetworkWrapper channel) {
            this.channel = channel;
        }

        @Override
        public void send(AuthTicketMessage message) {
            channel.sendToServer(message);
        }
    }
}
