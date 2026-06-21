package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnRoadBuilderServiceTest {

    @Test
    void commandOptionsIncludeRebuild() {
        assertTrue(SpawnRoadBuilderService.commandOptions().contains("rebuild"));
    }
}
