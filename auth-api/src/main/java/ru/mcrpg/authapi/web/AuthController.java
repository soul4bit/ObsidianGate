package ru.mcrpg.authapi.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.mcrpg.authapi.domain.entity.AccountEntity;
import ru.mcrpg.authapi.service.AvatarCatalog;
import ru.mcrpg.authapi.service.AuthService;
import ru.mcrpg.authapi.service.RequestAuthService;

@RestController
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final RequestAuthService requestAuthService;
    private final AvatarCatalog avatarCatalog;

    public AuthController(AuthService authService, RequestAuthService requestAuthService, AvatarCatalog avatarCatalog) {
        this.authService = authService;
        this.requestAuthService = requestAuthService;
        this.avatarCatalog = avatarCatalog;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthSessionResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return toSessionResponse(authService.register(
            request.username(),
            request.email(),
            request.password(),
            request.deviceName(),
            servletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/auth/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return toSessionResponse(authService.login(
            request.login(),
            request.password(),
            request.deviceName(),
            servletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/auth/refresh")
    public AuthSessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toSessionResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public AccountResponse me(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return toAccountResponse(requestAuthService.authenticate(authorizationHeader));
    }

    @PatchMapping("/me")
    public AccountResponse updateMe(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody UpdateMeRequest request
    ) {
        AccountEntity account = requestAuthService.authenticate(authorizationHeader);
        return toAccountResponse(authService.updateEmail(account, request.email()));
    }

    private AuthSessionResponse toSessionResponse(AuthService.AuthSessionResult result) {
        return new AuthSessionResponse(
            toAccountResponse(result.account()),
            result.accessToken(),
            result.refreshToken(),
            java.time.Duration.between(java.time.Instant.now(), result.accessTokenExpiresAt()).getSeconds(),
            result.accessTokenExpiresAt()
        );
    }

    private AccountResponse toAccountResponse(AccountEntity account) {
        String avatar = avatarCatalog.avatarFor(account);
        return new AccountResponse(
            account.getId().toString(),
            account.getUsername(),
            account.getEmail(),
            account.getRole(),
            account.getStatus(),
            avatar,
            avatarUrl(avatar)
        );
    }

    private static String avatarUrl(String avatar) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .pathSegment("avatars", avatar)
            .toUriString();
    }
}
