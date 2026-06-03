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
        assertFalse(containsCommandPrefix(sections, "/claim"));
        assertFalse(containsCommand(sections, "/spawnprotect info"));
    }

    @Test
    void adminHelpIncludesSpawnProtectionCommands() {
        List<HelpCommand.HelpSection> sections = HelpCommand.visibleSections(true);

        assertFalse(containsCommandPrefix(sections, "/claim"));
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

    private static boolean containsCommandPrefix(List<HelpCommand.HelpSection> sections, String prefix) {
        for (HelpCommand.HelpSection section : sections) {
            for (HelpCommand.HelpEntry entry : section.entries) {
                if (entry.command.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
