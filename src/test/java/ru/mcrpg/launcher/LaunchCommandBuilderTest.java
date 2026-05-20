package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchCommandBuilderTest {

    private final LaunchCommandBuilder builder = new LaunchCommandBuilder();

    @Test
    void buildKeepsPlaceholderValueWithSpacesAsSingleArgument() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setJavaCommand("java");
        config.setGameDirectory("C:/Games/MC RPG");
        config.setWorkingDirectory("C:/Games/MC RPG");
        config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} -jar forge.jar --gameDir {gameDir} --username {username}");

        List<String> command = builder.build(config, identity("Player One"));

        assertEquals(
            Arrays.asList(
                "java",
                "-Xms1024M",
                "-Xmx4096M",
                "-jar",
                "forge.jar",
                "--gameDir",
                "C:/Games/MC RPG",
                "--username",
                "Player One"
            ),
            command
        );
    }

    @Test
    void buildSupportsQuotedSegmentsInTemplate() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setJavaCommand("java");
        config.setGameDirectory("/srv/mc-rpg");
        config.setWorkingDirectory("/srv/mc-rpg");
        config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} --label \"Player {username}\" --server {serverHost}");

        List<String> command = builder.build(config, identity("RPG"));

        assertEquals(
            Arrays.asList(
                "java",
                "-Xms1024M",
                "-Xmx4096M",
                "--label",
                "Player RPG",
                "--server",
                LauncherConfig.DEFAULT_SERVER_HOST
            ),
            command
        );
    }

    @Test
    void buildRejectsUnknownPlaceholders() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory("/srv/mc-rpg");
        config.setWorkingDirectory("/srv/mc-rpg");
        config.setLaunchTemplate("{java} --server {server}");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> builder.build(config, identity("RPG"))
        );

        assertEquals(
            "Неизвестный плейсхолдер {server}. Доступны: [java, username, gameDir, workingDir, serverHost, serverPort, uuid, accessToken, userType, gameSessionFile, memoryMin, memoryMax, memoryMinMb, memoryMaxMb]",
            exception.getMessage()
        );
    }

    @Test
    void buildAllowsBlankOptionalFieldsWhenTemplateDoesNotUseThem() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("");
        config.setJavaCommand("");
        config.setGameDirectory("");
        config.setWorkingDirectory("");
        config.setServerHost("");
        config.setServerPort(25565);
        config.setLaunchTemplate("start-client.bat");

        List<String> command = builder.build(config, identity("RPG"));

        assertEquals(Arrays.asList("start-client.bat"), command);
    }

    @Test
    void buildSupportsAuthenticatedPlaceholders() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setLaunchTemplate("{java} --uuid {uuid} --token {accessToken} --userType {userType}");

        List<String> command = builder.build(config, identity("Soul4"));

        assertEquals(
            Arrays.asList(
                "java",
                "-Xms1024M",
                "-Xmx4096M",
                "--uuid",
                "uuid-Soul4",
                "--token",
                "token-Soul4",
                "--userType",
                "legacy"
            ),
            command
        );
    }

    @Test
    void buildInjectsSessionFileJvmArgWhenAuthenticated() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setJavaCommand("java");
        config.setLaunchTemplate("{java} -jar forge.jar --username {username}");

        List<String> command = builder.build(
            config,
            LaunchIdentity.authenticated("Knight", "uuid-1", "token-1", Path.of("C:/Games/.obsidiangate/session.json"))
        );

        assertEquals(
            Arrays.asList(
                "java",
                "-Dobsidiangate.sessionFile=" + Path.of("C:/Games/.obsidiangate/session.json"),
                "-Xms1024M",
                "-Xmx4096M",
                "-jar",
                "forge.jar",
                "--username",
                "Knight"
            ),
            command
        );
    }

    @Test
    void buildSupportsMemoryPlaceholders() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setMemoryMinMb(2048);
        config.setMemoryMaxMb(6144);
        config.setLaunchTemplate("{java} -Xms{memoryMin} -Xmx{memoryMax} --min {memoryMinMb} --max {memoryMaxMb}");

        List<String> command = builder.build(config, identity("Soul4"));

        assertEquals(
            Arrays.asList("java", "-Xms2048M", "-Xmx6144M", "--min", "2048", "--max", "6144"),
            command
        );
    }

    @Test
    void buildDoesNotInjectMemoryWhenTemplateAlreadyConfiguresIt() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setLaunchTemplate("{java} -Xmx2G -jar forge.jar --username {username}");

        List<String> command = builder.build(config, identity("Soul4"));

        assertEquals(Arrays.asList("java", "-Xmx2G", "-jar", "forge.jar", "--username", "Soul4"), command);
    }

    private static LaunchIdentity identity(String username) {
        return LaunchIdentity.authenticated(username, "uuid-" + username, "token-" + username, null);
    }
}
