package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.List;

public final class ModpackManifest {

    private int schemaVersion = 1;
    private String id;
    private String version;
    private String baseUrl;
    private ModpackNews news = new ModpackNews();
    private ModpackNews changelog = new ModpackNews();
    private List<ModpackNews> history = new ArrayList<ModpackNews>();
    private LauncherManifestSettings launcher = new LauncherManifestSettings();
    private LauncherUpdateSettings launcherUpdate = new LauncherUpdateSettings();
    private ModpackRuntime runtime = new ModpackRuntime();
    private MinecraftBootstrapSettings minecraft = new MinecraftBootstrapSettings();
    private List<ModpackFile> files = new ArrayList<ModpackFile>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ModpackNews getNews() {
        return news;
    }

    public void setNews(ModpackNews news) {
        this.news = news == null ? new ModpackNews() : news;
    }

    public ModpackNews getChangelog() {
        return changelog;
    }

    public void setChangelog(ModpackNews changelog) {
        this.changelog = changelog == null ? new ModpackNews() : changelog;
    }

    public List<ModpackNews> getHistory() {
        return history;
    }

    public void setHistory(List<ModpackNews> history) {
        this.history = sanitizeHistory(history);
    }

    public LauncherManifestSettings getLauncher() {
        return launcher;
    }

    public void setLauncher(LauncherManifestSettings launcher) {
        this.launcher = launcher == null ? new LauncherManifestSettings() : launcher;
    }

    public LauncherUpdateSettings getLauncherUpdate() {
        return launcherUpdate;
    }

    public void setLauncherUpdate(LauncherUpdateSettings launcherUpdate) {
        this.launcherUpdate = launcherUpdate == null ? new LauncherUpdateSettings() : launcherUpdate;
    }

    public ModpackRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(ModpackRuntime runtime) {
        this.runtime = runtime == null ? new ModpackRuntime() : runtime;
    }

    public MinecraftBootstrapSettings getMinecraft() {
        return minecraft;
    }

    public void setMinecraft(MinecraftBootstrapSettings minecraft) {
        this.minecraft = minecraft == null ? new MinecraftBootstrapSettings() : minecraft;
    }

    public List<ModpackFile> getFiles() {
        return files;
    }

    public void setFiles(List<ModpackFile> files) {
        this.files = files == null ? new ArrayList<ModpackFile>() : files;
    }

    private static List<ModpackNews> sanitizeHistory(List<ModpackNews> values) {
        List<ModpackNews> sanitized = new ArrayList<ModpackNews>();
        if (values == null) {
            return sanitized;
        }
        for (ModpackNews value : values) {
            if (value != null && value.hasContent()) {
                sanitized.add(value);
            }
        }
        return sanitized;
    }
}
