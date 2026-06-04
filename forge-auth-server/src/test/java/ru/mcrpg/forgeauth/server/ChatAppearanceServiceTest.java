package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ChatAppearanceServiceTest {

    @Test
    void formatsChatLineWithRolePrefixAndSanitizedMessage() {
        String line = ChatAppearanceService.formatChatLine("admin", "soul4bit", "\u00A7ksecret привет");

        assertEquals(
            "\u00A78[\u00A74Админ\u00A78] \u00A7csoul4bit \u00A78\u00BB \u00A7fsecret привет",
            line
        );
    }

    @Test
    void formatsChatLineWithActiveAchievementTitle() {
        String line = ChatAppearanceService.formatChatLine("player", "\u00A76Рудокоп\u00A7r", "soul4bit", "нашел жилу");

        assertEquals(
            "\u00A78[\u00A77Игрок\u00A78]\u00A78[\u00A76Рудокоп\u00A7r\u00A78] \u00A77soul4bit \u00A78\u00BB \u00A7fнашел жилу",
            line
        );
    }

    @Test
    void appliesTabNameAndScoreboardTeam() {
        FakePlayer player = new FakePlayer("romik71ya");
        ChatAppearanceService service = new ChatAppearanceService(
            Logger.getLogger("test"),
            Collections.emptyMap(),
            message -> message
        );

        service.applyPlayerAppearance(player, "vip");

        assertEquals("\u00A78[\u00A76VIP\u00A78] \u00A7eromik71ya", player.tabListDisplayName);
        assertTrue(player.world.scoreboard.teams.containsKey("og20vip"));
        FakeTeam team = player.world.scoreboard.teams.get("og20vip");
        assertEquals("\u00A78[\u00A76VIP\u00A78] \u00A7e", team.prefix);
        assertEquals("\u00A7r", team.suffix);
        assertEquals("og20vip", player.world.scoreboard.playerTeams.get("romik71ya"));
    }

    static final class FakePlayer {
        private final String username;
        private final FakeWorld world = new FakeWorld();
        private Object tabListDisplayName;

        FakePlayer(String username) {
            this.username = username;
        }

        public String getName() {
            return username;
        }

        public void setTabListDisplayName(Object tabListDisplayName) {
            this.tabListDisplayName = tabListDisplayName;
        }
    }

    static final class FakeWorld {
        private final FakeScoreboard scoreboard = new FakeScoreboard();

        public FakeScoreboard getScoreboard() {
            return scoreboard;
        }
    }

    static final class FakeScoreboard {
        private final Map<String, FakeTeam> teams = new HashMap<String, FakeTeam>();
        private final Map<String, String> playerTeams = new HashMap<String, String>();

        public FakeTeam getTeam(String name) {
            return teams.get(name);
        }

        public FakeTeam createTeam(String name) {
            FakeTeam team = new FakeTeam();
            teams.put(name, team);
            return team;
        }

        public boolean addPlayerToTeam(String username, String teamName) {
            playerTeams.put(username, teamName);
            return true;
        }
    }

    static final class FakeTeam {
        private String prefix;
        private String suffix;

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }
    }
}
