package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HelpCommandTest {

    @Test
    void playerHelpShowsPlayerCommandsOnly() {
        List<HelpCommand.HelpSection> sections = HelpCommand.visibleSections(false);

        assertTrue(containsCommand(sections, "/spawn"));
        assertTrue(containsCommand(sections, "/rtp"));
        assertTrue(containsCommand(sections, "/claim create <название> <радиус>"));
        assertFalse(containsCommand(sections, "/spawnprotect info"));
    }

    @Test
    void adminHelpIncludesSpawnProtectionCommands() {
        List<HelpCommand.HelpSection> sections = HelpCommand.visibleSections(true);

        assertTrue(containsCommand(sections, "/spawnprotect info"));
        assertTrue(containsCommand(sections, "/spawnprotect reload"));
    }

    private static boolean containsCommand(List<HelpCommand.HelpSection> sections, String command) {
        for (HelpCommand.HelpSection section : sections) {
            for (HelpCommand.HelpEntry entry : section.entries) {
                if (command.equals(entry.command)) {
                    return true;
                }
            }
        }
        return false;
    }
}
