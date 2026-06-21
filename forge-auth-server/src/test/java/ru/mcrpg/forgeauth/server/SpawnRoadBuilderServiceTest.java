package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SpawnRoadBuilderServiceTest {

    @Test
    void commandOptionsIncludeRebuild() {
        assertTrue(SpawnRoadBuilderService.commandOptions().contains("rebuild"));
    }

    @Test
    void roadWaterLandfillRecognizesVanillaAndModdedLiquids() {
        assertTrue(SpawnRoadBuilderService.isRoadLiquidBlockName("minecraft:water"));
        assertTrue(SpawnRoadBuilderService.isRoadLiquidBlockName("minecraft:flowing_lava"));
        assertTrue(SpawnRoadBuilderService.isRoadLiquidBlockName("modid:fluid_oil"));
        assertFalse(SpawnRoadBuilderService.isRoadLiquidBlockName("minecraft:grass"));
    }
}
