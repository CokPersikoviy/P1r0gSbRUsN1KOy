package ru.wilyfox.utils;

import ru.wilyfox.client.protocol.DwBossType;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BossName {
    private static final Pattern MULTIPLICITY = Pattern.compile("[xXхХ×]\\d+");

    private BossName() {}

    public static String getBossName(String text) {
        if (text == null) return null;
        text = Normalizer.normalize(Formatting.sanitize(withoutMultiplicity(text)), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        switch (text) {
            // Overworld
            case "КРИГЕР" -> {
                return  "Кригер";
            }
            case "СЛИЗЕНЬ" -> {
                return  "Слизень";
            }
            case "КРЫСИНЫИ КОРОЛЬ" -> {
                return  "Крысиный Король";
            }
            case "КОШМАР" -> {
                return  "Кошмар";
            }
            case "ВЕНДИГО" -> {
                return  "Вендиго";
            }
            case "УЛЬДРИК" -> {
                return  "Ульдрик";
            }
            case "ПАУЧИХА" -> {
                return  "Паучиха";
            }
            case "МЕРЛОК" -> {
                return  "Мерлок";
            }
            case "ЭЛЕМЕНТАЛИСТ" -> {
                return  "Элементалист";
            }
            case "ЖНЕЦ" -> {
                return  "Жнец";
            }
            case "НАЕЗДНИК" -> {
                return  "Наездник";
            }
            case "РАЗБОИНИК" -> {
                return  "Разбойник";
            }
            case "ШАМАН" -> {
                return  "Шаман";
            }
            case "ВАРДЕН" -> {
                return  "Варден";
            }
            case "КОРОЛЕВСКАЯ ЖАБА" -> {
                return  "Королевская Жаба";
            }
            case "ГИГАНТ" -> {
                return  "Гигант";
            }
            case "БЕССМЕРТНЫИ ЛЕГИОН", "КОМАНДИР ЛЕГИОНА" -> {
                return  "Бессмертный Легион";
            }
            case "БЕЗУМНЫИ АЛХИМИК" -> {
                return  "Безумный Алхимик";
            }
            case "НЕКРОМАНТ" -> {
                return  "Некромант";
            }
            case "ПОЖИРАТЕЛЬ ТЬМЫ" -> {
                return  "Пожиратель Тьмы";
            }
            case "ЧУДОВИЩЕ" -> {
                return  "Чудовище";
            }
            case "ОКТОПУС" -> {
                return  "Октопус";
            }
            case "КУЗНЕЦ" -> {
                return  "Кузнец";
            }
            case "ПОВЕЛИТЕЛЬ ГРОМА" -> {
                return  "Повелитель Грома";
            }
            case "ГАРГУЛЬЯ" -> {
                return  "Гаргулья";
            }
            case "ВСАДНИК" -> {
                return  "Всадник";
            }
            case "КОБОЛЬД" -> {
                return  "Кобольд";
            }
            case "САМУРАИ" -> {
                return  "Самурай";
            }
            case "ПОВЕЛИТЕЛЬ МЕРТВЫХ" -> {
                return  "Повелитель Мёртвых";
            }
            case "РЫЦАРЬ СВЕТА" -> {
                return  "Рыцарь Света";
            }
            case "ГИГАНТСКАЯ ЧЕРЕПАХА" -> {
                return  "Гигантская черепаха";
            }
            case "ЗМЕИНАЯ ЖРИЦА" -> {
                return  "Змеиная Жрица";
            }
            case "МОГУЩЕСТВЕННЫИ ШАЛКЕР" -> {
                return  "Могущественный Шалкер";
            }
            case "СНЕЖНЫИ МОНСТР" -> {
                return  "Снежный Монстр";
            }
            case "ДУХ ЛЕСА" -> {
                return  "Дух Леса";
            }
            case "СПЕКТРАЛЬНЫИ КУБ" -> {
                return  "Спектральный Куб";
            }
            case "ЦИКЛОП" -> {
                return  "Циклоп";
            }
            case "ГИДРА" -> {
                return  "Гидра";
            }
            case "МАГНУС" -> {
                return  "Магнус";
            }
            case "ВЕСТНИЦА АДА" -> {
                return  "Вестница Ада";
            }

            // Nether
            case "ЦЕРБЕР" -> {
                return  "Цербер";
            }
            case "КОРОЛЬ ИФРИТОВ" -> {
                return  "Король Ифритов";
            }
            case "БАФОМЕТ" -> {
                return  "Бафомет";
            }
            case "ЛАВОВЫИ МОНСТР" -> {
                return  "Лавовый Монстр";
            }
            case "КОРОЛЕВА ПИГЛИНОВ" -> {
                return  "Королева Пиглинов";
            }
            case "ДРАКАИНА" -> {
                return  "Дракайна";
            }
            case "ВЕРХОВНЫИ БЕС" -> {
                return  "Верховный Бес";
            }
            case "БРУТАЛЬНЫИ ПИГЛИН" -> {
                return  "Брутальный Пиглин";
            }
            case "АДСКИИ СЛИЗЕНЬ" -> {
                return  "Адский Слизень";
            }
            case "ЗОГЛИН" -> {
                return  "Зоглин";
            }
            case "ДЕМОНИЧЕСКИИ РЫЦАРЬ" -> {
                return  "Демонический Рыцарь";
            }

            // END
            case "СИНТИЯ" -> {
                return  "Синтия";
            }
            case "РЫЦАРЬ ЭНДА" -> {
                return  "Рыцарь Энда";
            }
            case "МАГ ПРОСТРАНСТВА" -> {
                return  "Маг Пространства";
            }
            case "ШАЛКЕРОВЫИ СТРАЖ" -> {
                return  "Шалкеровый Страж";
            }
            case "ЭНДЕР ГОЛЕМ" -> {
                return  "Эндер Голем";
            }
            case "КОРОЛЕВА ТЕНЕИ" -> {
                return  "Королева Теней";
            }
            case "ХРАНИТЕЛЬ" -> {
                return  "Хранитель";
            }
            case "ВОИД" -> {
                return  "Воид";
            }
            case "СТРАННИК ИЗМЕРЕНИИ" -> {
                return  "Странник Измерений";
            }
            default -> {
                return null;
            }
        }
    }

    /** RU/EN holograms resolve against server IDs and names rather than translations guessed locally. */
    public static String resolveRegistryName(String text, Collection<DwBossType> types) {
        DwBossType type = resolveRegistryType(text, types);
        return type == null ? null : type.name() == null || type.name().isBlank() ? type.id() : type.name();
    }

    public static DwBossType resolveRegistryType(String text, Collection<DwBossType> types) {
        String candidate = lookupKey(text);
        if (candidate.isEmpty() || types == null) return null;
        if (candidate.equals("LEGIONCOMMANDER")) candidate = "IMMORTALLEGION";
        if (candidate.equals("КОМАНДИРЛЕГИОНА")) candidate = "БЕССМЕРТНЫИЛЕГИОН";
        for (DwBossType type : types) {
            if (type == null) continue;
            if (candidate.equals(lookupKey(type.id())) || candidate.equals(lookupKey(type.name()))) {
                return type;
            }
        }
        return null;
    }

    private static String lookupKey(String value) {
        if (value == null) return "";
        return Normalizer.normalize(Formatting.stripMinecraftFormatting(withoutMultiplicity(value)), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-zA-Zа-яА-Я]+", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String withoutMultiplicity(String text) {
        return MULTIPLICITY.matcher(text).replaceAll("");
    }
}
