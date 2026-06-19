package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SpawnRoadMilestoneServiceTest {

    @Test
    void detectsMilestonesOnEachRoad() {
        assertEquals(
            "Северный путь",
            SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X, SpawnRoadMilestoneService.CENTER_Z + 250).routeName
        );
        assertEquals(
            "Южный путь",
            SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X, SpawnRoadMilestoneService.CENTER_Z - 500).routeName
        );
        assertEquals(
            "Восточный путь",
            SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X + 750, SpawnRoadMilestoneService.CENTER_Z).routeName
        );
        assertEquals(
            "Западный путь",
            SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X - 1000, SpawnRoadMilestoneService.CENTER_Z).routeName
        );
    }

    @Test
    void ignoresPositionsAwayFromRoadOrMilestones() {
        assertNull(SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X + 25, SpawnRoadMilestoneService.CENTER_Z + 250));
        assertNull(SpawnRoadMilestoneService.noticeAt(SpawnRoadMilestoneService.CENTER_X, SpawnRoadMilestoneService.CENTER_Z + 300));
    }
}
