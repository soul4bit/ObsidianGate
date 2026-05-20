package ru.mcrpg.launcher;

public final class LauncherConfig {

    public static final String DEFAULT_SERVER_HOST = "obsidiangates.duckdns.org";
    public static final int DEFAULT_SERVER_PORT = 25565;
    public static final int DEFAULT_MEMORY_MIN_MB = 1024;
    public static final int DEFAULT_MEMORY_MAX_MB = 4096;
    public static final String DEFAULT_LAUNCH_TEMPLATE =
        "{java} -jar forge-1.12.2-14.23.5.2847.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}";

    private String username;
    private String javaCommand;
    private String gameDirectory;
    private String workingDirectory;
    private String serverHost;
    private int serverPort;
    private String launchTemplate;
    private String manifestUrl;
    private String authBaseUrl;
    private String serverId;
    private int memoryMinMb;
    private int memoryMaxMb;
    private boolean updateFilesBeforeLaunch;
    private boolean launcherUpdatesEnabled;

    public static LauncherConfig defaults() {
        LauncherConfig config = new LauncherConfig();
        config.setUsername(LauncherDefaults.defaultUsername());
        config.setJavaCommand("java");
        config.setGameDirectory(LauncherDefaults.defaultGameDirectory());
        config.setWorkingDirectory("");
        config.setServerHost(DEFAULT_SERVER_HOST);
        config.setServerPort(DEFAULT_SERVER_PORT);
        config.setLaunchTemplate(DEFAULT_LAUNCH_TEMPLATE);
        config.setManifestUrl(LauncherDefaults.defaultManifestUrl(DEFAULT_SERVER_HOST));
        config.setAuthBaseUrl(LauncherDefaults.defaultAuthBaseUrl(DEFAULT_SERVER_HOST));
        config.setServerId(LauncherDefaults.defaultServerId());
        config.setMemoryMinMb(DEFAULT_MEMORY_MIN_MB);
        config.setMemoryMaxMb(DEFAULT_MEMORY_MAX_MB);
        config.setUpdateFilesBeforeLaunch(true);
        config.setLauncherUpdatesEnabled(true);
        return config;
    }

    public LauncherConfig copy() {
        LauncherConfig copy = new LauncherConfig();
        copy.setUsername(username);
        copy.setJavaCommand(javaCommand);
        copy.setGameDirectory(gameDirectory);
        copy.setWorkingDirectory(workingDirectory);
        copy.setServerHost(serverHost);
        copy.setServerPort(serverPort);
        copy.setLaunchTemplate(launchTemplate);
        copy.setManifestUrl(manifestUrl);
        copy.setAuthBaseUrl(authBaseUrl);
        copy.setServerId(serverId);
        copy.setMemoryMinMb(memoryMinMb);
        copy.setMemoryMaxMb(memoryMaxMb);
        copy.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunch);
        copy.setLauncherUpdatesEnabled(launcherUpdatesEnabled);
        return copy;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = sanitize(username);
    }

    public String getJavaCommand() {
        return javaCommand;
    }

    public void setJavaCommand(String javaCommand) {
        this.javaCommand = sanitize(javaCommand);
    }

    public String getGameDirectory() {
        return gameDirectory;
    }

    public void setGameDirectory(String gameDirectory) {
        this.gameDirectory = sanitize(gameDirectory);
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = sanitize(workingDirectory);
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = sanitize(serverHost);
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getLaunchTemplate() {
        return launchTemplate;
    }

    public void setLaunchTemplate(String launchTemplate) {
        this.launchTemplate = sanitize(launchTemplate);
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public void setManifestUrl(String manifestUrl) {
        this.manifestUrl = sanitize(manifestUrl);
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = sanitize(authBaseUrl);
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = sanitize(serverId);
    }

    public int getMemoryMinMb() {
        return memoryMinMb;
    }

    public void setMemoryMinMb(int memoryMinMb) {
        this.memoryMinMb = memoryMinMb;
    }

    public int getMemoryMaxMb() {
        return memoryMaxMb;
    }

    public void setMemoryMaxMb(int memoryMaxMb) {
        this.memoryMaxMb = memoryMaxMb;
    }

    public boolean isUpdateFilesBeforeLaunch() {
        return updateFilesBeforeLaunch;
    }

    public void setUpdateFilesBeforeLaunch(boolean updateFilesBeforeLaunch) {
        this.updateFilesBeforeLaunch = updateFilesBeforeLaunch;
    }

    public boolean isLauncherUpdatesEnabled() {
        return launcherUpdatesEnabled;
    }

    public void setLauncherUpdatesEnabled(boolean launcherUpdatesEnabled) {
        this.launcherUpdatesEnabled = launcherUpdatesEnabled;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value;
    }
}
