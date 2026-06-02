package ru.mcrpg.authapi.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ru.mcrpg.authapi.domain.entity.AccountEntity;

final class MinecraftPlayerUuid {

    private MinecraftPlayerUuid() {
    }

    static String offlineUuidForUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String stableUuidFor(AccountEntity account) {
        String stored = account.getMinecraftUuid();
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }

        String generated = offlineUuidForUsername(account.getUsername());
        account.setMinecraftUuid(generated);
        return generated;
    }
}
