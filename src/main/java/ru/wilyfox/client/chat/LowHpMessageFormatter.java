package ru.wilyfox.client.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.hud.config.LowHpMessageElement;
import ru.wilyfox.client.hud.config.LowHpMessageFormatConfig;
import ru.wilyfox.utils.Formatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One message model shared by preview, local chat and the server's &-formatted clan chat. */
public final class LowHpMessageFormatter {
    private LowHpMessageFormatter() {}

    public static void warmUp() {
        var sample = format(new LowHpMessageFormatConfig(), "PE", "Boss", 1, 1, 1, true, "Stage 1");
        sample.component();
        sample.clanText();
    }

    public static Message format(LowHpMessageFormatConfig config, String server, String name, int level,
                                 double health, double percent, boolean cursed, String label) {
        if (config == null) config = new LowHpMessageFormatConfig();
        var header = new ArrayList<Fragment>();
        add(header, config, LowHpMessageElement.SERVER, bracket(server));
        add(header, config, LowHpMessageElement.NAME, clean(name));
        add(header, config, LowHpMessageElement.LEVEL, level > 0 ? "[" + level + "]" : "");
        add(header, config, LowHpMessageElement.CURSE, cursed ? "[Прок]" : "");

        var body = new ArrayList<Fragment>();
        if (Double.isFinite(health) && health >= 0) {
            var number = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
            add(body, config, LowHpMessageElement.HEALTH, number.format(health) + "❤");
        }
        if (Double.isFinite(percent)) {
            String text = Math.round(Math.max(0, Math.min(100, percent))) + "%";
            add(body, config, LowHpMessageElement.PERCENT, body.isEmpty() ? text : "(" + text + ")");
        }

        var result = new ArrayList<>(header);
        if (!body.isEmpty()) {
            if (!result.isEmpty()) result.add(new Fragment(" — ", '8'));
            result.addAll(body);
        }
        add(result, config, LowHpMessageElement.STAGE, bracket(label));
        return new Message(result);
    }

    public static Message preview(LowHpMessageFormatConfig config) {
        return format(config, "PE1.2", "Бессмертный Легион", 130, 125, 20, true, "Стадия 2/4");
    }

    public static boolean isValidColor(String code) {
        if (code == null || code.length() != 2 || code.charAt(0) != '&' && code.charAt(0) != '§') return false;
        char value = Character.toLowerCase(code.charAt(1));
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f';
    }

    private static void add(List<Fragment> result, LowHpMessageFormatConfig config, LowHpMessageElement element, String text) {
        var style = config.element(element);
        if (!style.visible || text.isBlank()) return;
        if (!result.isEmpty()) result.add(new Fragment(" ", 'f'));
        String color = isValidColor(style.colorCode) ? style.colorCode : element.defaultColor();
        result.add(new Fragment(text, Character.toLowerCase(color.charAt(1))));
    }

    private static String clean(String text) {
        return Formatting.stripMinecraftFormatting(text).replace('\u00a0', ' ').trim();
    }

    private static String bracket(String text) {
        String plain = clean(text);
        return plain.isBlank() ? "" : "[" + plain + "]";
    }

    public record Fragment(String text, char color) {}

    public record Message(List<Fragment> fragments) {
        public Message { fragments = List.copyOf(fragments); }

        public boolean isEmpty() { return fragments.isEmpty(); }

        public String plainText() {
            var result = new StringBuilder();
            fragments.forEach(fragment -> result.append(fragment.text()));
            return result.toString();
        }

        public String clanText() {
            var result = new StringBuilder();
            for (Fragment fragment : fragments) {
                result.append('&').append(fragment.color()).append(fragment.text());
            }
            if (!isEmpty()) result.append("&r");
            return result.toString();
        }

        public Component component() {
            MutableComponent result = Component.empty();
            for (Fragment fragment : fragments) {
                result.append(Component.literal(fragment.text()).withStyle(ChatFormatting.getByCode(fragment.color())));
            }
            return result;
        }
    }
}
