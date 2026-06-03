package ru.mcrpg.authapi.service;

import java.time.Instant;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.domain.repository.GameTicketRepository;
import ru.mcrpg.authapi.domain.repository.LauncherSessionRepository;

@Service
public class AuthDataCleanupService {

    private static final Logger LOGGER = Logger.getLogger(AuthDataCleanupService.class.getName());

    private final AuthApiProperties properties;
    private final GameTicketRepository gameTicketRepository;
    private final LauncherSessionRepository launcherSessionRepository;

    public AuthDataCleanupService(
        AuthApiProperties properties,
        GameTicketRepository gameTicketRepository,
        LauncherSessionRepository launcherSessionRepository
    ) {
        this.properties = properties;
        this.gameTicketRepository = gameTicketRepository;
        this.launcherSessionRepository = launcherSessionRepository;
    }

    @Scheduled(fixedDelayString = "${auth.cleanup-interval-millis:3600000}")
    @Transactional
    public void cleanupStaleAuthData() {
        if (!properties.isCleanupEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant usedTicketCutoff = now.minusSeconds(Math.max(0L, properties.getUsedGameTicketRetentionSeconds()));
        Instant revokedSessionCutoff = now.minusSeconds(Math.max(0L, properties.getRevokedSessionRetentionDays()) * 24L * 60L * 60L);

        int deletedTickets = gameTicketRepository.deleteStaleTickets(now, usedTicketCutoff);
        int deletedSessions = launcherSessionRepository.deleteStaleSessions(now, revokedSessionCutoff);

        if (deletedTickets > 0 || deletedSessions > 0) {
            LOGGER.info("Auth cleanup removed stale records. gameTickets=" + deletedTickets + " launcherSessions=" + deletedSessions);
        }
    }
}
