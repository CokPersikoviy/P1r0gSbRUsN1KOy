package ru.wilyfox.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BossLevel {
    private static final Map<String, Integer> LEVELS_BY_NAME;
    private static final Map<Integer, String> NAMES_BY_LEVEL;

    static {
        Map<String, Integer> levels = new LinkedHashMap<>();
        register(levels, 15, "Кригер");
        register(levels, 20, "Слизень");
        register(levels, 25, "Крысиный Король");
        register(levels, 30, "Кошмар");
        register(levels, 35, "Вендиго");
        register(levels, 40, "Ульдрик");
        register(levels, 45, "Паучиха");
        register(levels, 50, "Мерлок");
        register(levels, 55, "Элементалист");
        register(levels, 60, "Жнец");
        register(levels, 65, "Наездник");
        register(levels, 70, "Разбойник");
        register(levels, 75, "Шаман");
        register(levels, 80, "Варден");
        register(levels, 90, "Королевская Жаба");
        register(levels, 100, "Гигант");
        register(levels, 105, "Бессмертный Легион");
        register(levels, 110, "Безумный Алхимик");
        register(levels, 115, "Некромант");
        register(levels, 120, "Пожиратель Тьмы");
        register(levels, 125, "Чудовище");
        register(levels, 130, "Октопус");
        register(levels, 140, "Кузнец");
        register(levels, 150, "Повелитель Грома");
        register(levels, 160, "Гаргулья");
        register(levels, 170, "Всадник");
        register(levels, 180, "Кобольд");
        register(levels, 190, "Самурай");
        register(levels, 200, "Повелитель Мёртвых");
        register(levels, 210, "Рыцарь Света");
        register(levels, 220, "Гигантская черепаха");
        register(levels, 230, "Змеиная Жрица");
        register(levels, 240, "Могущественный Шалкер");
        register(levels, 250, "Снежный Монстр");
        register(levels, 260, "Дух Леса");
        register(levels, 270, "Спектральный Куб");
        register(levels, 280, "Циклоп");
        register(levels, 300, "Гидра");
        register(levels, 320, "Магнус");
        register(levels, 330, "Вестница Ада");
        register(levels, 340, "Цербер");
        register(levels, 345, "Король Ифритов");
        register(levels, 350, "Бафомет");
        register(levels, 360, "Лавовый Монстр");
        register(levels, 370, "Королева Пиглинов");
        register(levels, 380, "Дракайна");
        register(levels, 390, "Верховный Бес");
        register(levels, 400, "Брутальный Пиглин");
        register(levels, 410, "Адский Слизень");
        register(levels, 420, "Зоглин");
        register(levels, 430, "Демонический Рыцарь");
        register(levels, 440, "Синтия");
        register(levels, 450, "Рыцарь Энда");
        register(levels, 460, "Маг Пространства");
        register(levels, 470, "Шалкеровый Страж");
        register(levels, 480, "Эндер Голем");
        register(levels, 490, "Королева Теней");
        register(levels, 500, "Хранитель");
        register(levels, 510, "Воид");
        register(levels, 520, "Странник Измерений");
        LEVELS_BY_NAME = Collections.unmodifiableMap(levels);

        Map<Integer, String> names = new LinkedHashMap<>();
        levels.forEach((name, level) -> names.put(level, name));
        NAMES_BY_LEVEL = Collections.unmodifiableMap(names);
    }

    private BossLevel() {
    }

    public static Integer getBossLevel(String text) {
        return text == null ? null : LEVELS_BY_NAME.get(text);
    }

    public static String getBossNameByLevel(int level) {
        return NAMES_BY_LEVEL.get(level);
    }

    public static Map<Integer, String> getKnownBosses() {
        return NAMES_BY_LEVEL;
    }

    private static void register(Map<String, Integer> levels, int level, String name) {
        levels.put(name, level);
    }
}
