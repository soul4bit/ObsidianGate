package ru.mcrpg.authapi.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.service.GameTicketService;
import ru.mcrpg.authapi.service.RequestAuthService;

@RestController
@RequestMapping("/game/tickets")
public class GameTicketController {

    private final RequestAuthService requestAuthService;
    private final GameTicketService gameTicketService;

    public GameTicketController(RequestAuthService requestAuthService, GameTicketService gameTicketService) {
        this.requestAuthService = requestAuthService;
        this.gameTicketService = gameTicketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameTicketResponse create(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody(required = false) CreateGameTicketRequest request
    ) {
        AccountEntity account = requestAuthService.authenticate(authorizationHeader);
        GameTicketService.CreatedGameTicket ticket = gameTicketService.create(
            account,
            request == null ? null : request.serverId()
        );
        return new GameTicketResponse(
            ticket.ticket(),
            ticket.username(),
            ticket.playerUuid(),
            ticket.serverId(),
            ticket.expiresAt()
        );
    }

    @PostMapping("/verify")
    public VerifyGameTicketResponse verify(@Valid @RequestBody VerifyGameTicketRequest request) {
        GameTicketService.VerifiedGameTicket ticket = gameTicketService.verify(request.ticket(), request.serverId());
        return new VerifyGameTicketResponse(
            ticket.valid(),
            ticket.accountId(),
            ticket.username(),
            ticket.playerUuid(),
            ticket.role(),
            ticket.reason()
        );
    }
}
