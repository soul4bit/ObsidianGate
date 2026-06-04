package ru.mcrpg.forgeauth.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AchievementTitleCatalog {

    private static final List<AchievementTitle> TITLES;
    private static final Map<String, AchievementTitle> BY_ID;

    static {
        ArrayList<AchievementTitle> titles = new ArrayList<AchievementTitle>();

        add(titles, "stone_dust", "Каменная пыль", "добыть 10 руд", AchievementTitle.Metric.ORES, 10, "\u00A77");
        add(titles, "prospector", "Старатель", "добыть 50 руд", AchievementTitle.Metric.ORES, 50, "\u00A7e");
        add(titles, "ore_digger", "Рудокоп", "добыть 100 руд", AchievementTitle.Metric.ORES, 100, "\u00A76");
        add(titles, "miner", "Шахтер", "добыть 500 руд", AchievementTitle.Metric.ORES, 500, "\u00A7a");
        add(titles, "deep_miner", "Глубинный шахтер", "добыть 1500 руд", AchievementTitle.Metric.ORES, 1500, "\u00A73");
        add(titles, "heart_of_mountain", "Сердце горы", "добыть 5000 руд", AchievementTitle.Metric.ORES, 5000, "\u00A75");

        add(titles, "diamond_spark", "Алмазная искра", "добыть 5 алмазных руд", AchievementTitle.Metric.DIAMONDS, 5, "\u00A7b");
        add(titles, "diamond_scent", "Алмазный нюх", "добыть 25 алмазных руд", AchievementTitle.Metric.DIAMONDS, 25, "\u00A7b");
        add(titles, "diamond_vein", "Жила удачи", "добыть 75 алмазных руд", AchievementTitle.Metric.DIAMONDS, 75, "\u00A7d");
        add(titles, "crystal_lord", "Кристальный лорд", "добыть 200 алмазных руд", AchievementTitle.Metric.DIAMONDS, 200, "\u00A75");

        add(titles, "tracker", "Следопыт", "убить 25 мобов", AchievementTitle.Metric.MOB_KILLS, 25, "\u00A72");
        add(titles, "hunter", "Охотник", "убить 100 мобов", AchievementTitle.Metric.MOB_KILLS, 100, "\u00A7a");
        add(titles, "slayer", "Истребитель", "убить 500 мобов", AchievementTitle.Metric.MOB_KILLS, 500, "\u00A7c");
        add(titles, "night_blade", "Ночной клинок", "убить 1500 мобов", AchievementTitle.Metric.MOB_KILLS, 1500, "\u00A74");
        add(titles, "server_wrath", "Гнев сервера", "убить 5000 мобов", AchievementTitle.Metric.MOB_KILLS, 5000, "\u00A75");

        add(titles, "monster_bane", "Гроза тварей", "убить 100 враждебных мобов", AchievementTitle.Metric.HOSTILE_KILLS, 100, "\u00A7c");
        add(titles, "blood_moon", "Кровавая луна", "убить 500 враждебных мобов", AchievementTitle.Metric.HOSTILE_KILLS, 500, "\u00A74");
        add(titles, "last_stand", "Последний рубеж", "убить 1500 враждебных мобов", AchievementTitle.Metric.HOSTILE_KILLS, 1500, "\u00A76");
        add(titles, "void_reaper", "Жнец Бездны", "убить 4000 враждебных мобов", AchievementTitle.Metric.HOSTILE_KILLS, 4000, "\u00A75");

        TITLES = Collections.unmodifiableList(titles);
        LinkedHashMap<String, AchievementTitle> byId = new LinkedHashMap<String, AchievementTitle>();
        for (AchievementTitle title : titles) {
            byId.put(title.id(), title);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private AchievementTitleCatalog() {
    }

    static List<AchievementTitle> all() {
        return TITLES;
    }

    static AchievementTitle find(String id) {
        return id == null ? null : BY_ID.get(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static void add(
        List<AchievementTitle> titles,
        String id,
        String label,
        String description,
        AchievementTitle.Metric metric,
        long threshold,
        String color
    ) {
        titles.add(new AchievementTitle(id, label, description, metric, threshold, color));
    }
}
