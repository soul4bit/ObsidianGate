package ru.mcrpg.forgeauth.server;

interface PlayerRoleLookup {
    String roleFor(Object player);

    default String accountIdFor(Object player) {
        return "";
    }
}
