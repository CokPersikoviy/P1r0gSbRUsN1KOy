package ru.wilyfox.client.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.chat.LowHpMessageFormatter;
import ru.wilyfox.client.hud.config.BossTimerElement;
import ru.wilyfox.client.hud.config.BossWidgetConfig;

/** Shared text composition for live rows and the menu preview. */
public final class BossTimerRowFormatter {
    private BossTimerRowFormatter() {}
    public static int color(BossWidgetConfig config, BossTimerElement part, int theme) {
        String code = config.colorCode(part);
        return LowHpMessageFormatter.isValidColor(code) ? 0xFF000000 | net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.getByCode(Character.toLowerCase(code.charAt(1)))).getValue() : theme;
    }
    public record Appearance(int name, int capture, int mythical, int collectibles) {}
    public static Appearance appearance(BossWidgetConfig config, int theme) {
        return new Appearance(color(config, BossTimerElement.NAME, theme), color(config, BossTimerElement.CAPTURE, theme),
                color(config, BossTimerElement.MYTHICAL, theme), color(config, BossTimerElement.COLLECTIBLES, theme));
    }
    public static Component label(BossWidgetConfig config, String name, boolean captured, boolean mythical,
                                  Boolean collected, boolean includeName, Appearance colors) {
        MutableComponent result = Component.empty();
        if (captured && config.showCapture) add(result, "✗", colors.capture());
        if (mythical && config.showMythical) add(result, "✦", colors.mythical());
        if (includeName) add(result, name, colors.name());
        if (collected != null && config.showCollectibles) add(result, collected ? "✔" : "◇", colors.collectibles());
        return result;
    }
    private static void add(MutableComponent result, String text, int color) {
        if (text == null || text.isEmpty()) return;
        if (!result.getSiblings().isEmpty()) result.append(Component.literal(" "));
        result.append(Component.literal(text).withColor(color & 0xFFFFFF));
    }
}
