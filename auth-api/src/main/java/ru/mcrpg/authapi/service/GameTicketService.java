package ru.mcrpg.authapi.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcrpg.authapi.config.AuthApiProperties;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.domain.entity.GameTicketEntity;
import ru.mcrpg.authapi.domain.repository.GameTicketRepository;

@Service
public class GameTicketService {

    private final AuthApiProperties properties;
    private final GameTicketRepository gameTicketRepository;
    private final RandomTokenService randomTokenService;
    private final HashingService hashingService;

    public GameTicketService(
        AuthApiProperties properties,
        GameTicketRepository gameTicketRepository,
        RandomTokenService randomTokenService,
        HashingService hashingService
    ) {
        this.properties = properties;
        this.gameTicketRepository = gameTicketRepository;
        this.randomTokenService = randomTokenService;
        this.hashingService = hashingService;
    }

    @Transactional
    public CreatedGameTicket create(AccountEntity account, String requestedServerId) {
        String resolvedServerId = normalizeServerId(requestedServerId);
        String rawTicket = randomTokenService.nextToken(32);
        Instant expiresAt = Instant.now().plusSeconds(properties.getGameTicketTtlSeconds());

        GameTicketEntity ticket = new GameTicketEntity();
        ticket.setAccount(account);
        ticket.setUsername(account.getUsername());
        ticket.setPlayerUuid(MinecraftPlayerUuid.stableUuidFor(account));
        ticket.setTicketHash(hashingService.sha256(rawTicket));
        ticket.setServerId(resolvedServerId);
        ticket.setExpiresAt(expiresAt);
        gameTicketRepository.save(ticket);

        return new CreatedGameTicket(rawTicket, account.getUsername(), MinecraftPlayerUuid.stableUuidFor(account), resolvedServerId, expiresAt);
    }

    @Transactional
    public VerifiedGameTicket verify(String rawTicket, String serverId) {
        String normalizedServerId = normalizeServerId(serverId);
        String hashed = hashingService.sha256(requireText(rawTicket));
        GameTicketEntity ticket = gameTicketRepository.findForUpdateByTicketHash(hashed)
            .orElse(null);

        if (ticket == null) {
            return VerifiedGameTicket.invalid("invalid");
        }
        if (ticket.getUsedAt() != null) {
            return VerifiedGameTicket.invalid("used");
        }
        if (ticket.getExpiresAt().isBefore(Instant.now())) {
            return VerifiedGameTicket.invalid("expired");
        }
        if (!normalizedServerId.equals(ticket.getServerId())) {
            return VerifiedGameTicket.invalid("server_mismatch");
        }

        ticket.setUsedAt(Instant.now());
        gameTicketRepository.save(ticket);

        AccountEntity account = ticket.getAccount();
        if (!"active".equalsIgnoreCase(account.getStatus())) {
            return VerifiedGameTicket.invalid("account_inactive");
        }

        return VerifiedGameTicket.valid(
            account.getId().toString(),
            account.getUsername(),
            MinecraftPlayerUuid.stableUuidFor(account),
            account.getRole()
        );
    }

    private String normalizeServerId(String requestedServerId) {
        if (requestedServerId == null || requestedServerId.trim().isEmpty()) {
            return properties.getServerId();
        }
        return requestedServerId.trim();
    }

    private static String requireText(String value) {
        return value == null ? "" : value.trim();
    }

    public record CreatedGameTicket(
        String ticket,
        String username,
        String playerUuid,
        String serverId,
        Instant expiresAt
    ) {
    }

    public record VerifiedGameTicket(
        boolean valid,
        String accountId,
        String username,
        String playerUuid,
        String role,
        String reason
    ) {
        static VerifiedGameTicket valid(String accountId, String username, String playerUuid, String role) {
            return new VerifiedGameTicket(true, accountId, username, playerUuid, role, null);
        }

        static VerifiedGameTicket invalid(String reason) {
            return new VerifiedGameTicket(false, null, null, null, null, reason);
        }
    }
}
