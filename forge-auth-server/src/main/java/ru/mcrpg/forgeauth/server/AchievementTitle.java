package ru.mcrpg.forgeauth.server;

final class AchievementTitle {

    enum Metric {
        ORES,
        DIAMONDS,
        MOB_KILLS,
        HOSTILE_KILLS
    }

    private final String id;
    private final String label;
    private final String description;
    private final Metric metric;
    private final long threshold;
    private final String color;

    AchievementTitle(String id, String label, String description, Metric metric, long threshold, String color) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.metric = metric;
        this.threshold = threshold;
        this.color = color;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    String coloredLabel() {
        return color + label + "\u00A7r";
    }

    String description() {
        return description;
    }

    Metric metric() {
        return metric;
    }

    long threshold() {
        return threshold;
    }

    boolean isUnlocked(PlayerAchievementService.PlayerProgress progress) {
        return progress.value(metric) >= threshold;
    }
}
