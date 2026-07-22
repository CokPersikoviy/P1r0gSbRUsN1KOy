package ru.wilyfox.client.alchemy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoBrewingCostOverlay {
    public static final String AUTO_BREWING_TITLE = "\uB221";

    private static final Pattern PRICE_PATTERN = Pattern.compile("\u0437\u0430\\s+(\\d+)");

    private AutoBrewingCostOverlay() {
    }

    public static OverlayData collect(String screenTitle, AbstractContainerMenu menu, Player player) {
        if (!ConfigManager.get().alchemy.autoBrewingCost
                || screenTitle == null
                || !screenTitle.contains(AUTO_BREWING_TITLE)
                || menu == null
                || player == null) {
            return null;
        }

        Integer currentPrice = findCurrentPrice(menu, player);
        if (currentPrice == null) {
            return null;
        }

        List<String> lines = new ArrayList<>(4);
        for (int count = 3; count <= 6; count++) {
            lines.add("x" + count + ": " + autoBrewCost(currentPrice, count) + " \uE365");
        }
        return new OverlayData("\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c \u0430\u0432\u0442\u043e\u0432\u0430\u0440\u043a\u0438", lines);
    }

    public static void render(GuiGraphics context, int x, int y, OverlayData data) {
        Minecraft minecraft = Minecraft.getInstance();
        int lineHeight = minecraft.font.lineHeight + 1;
        int width = minecraft.font.width(data.title());
        for (String line : data.lines()) {
            width = Math.max(width, minecraft.font.width(line));
        }
        width += 12;
        int height = 12 + lineHeight + data.lines().size() * lineHeight;

        HudSurface.drawPanel(context, x, y, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer());
        int textY = y + 5;
        context.drawString(minecraft.font, data.title(), x + 6, textY, WidgetTheme.TITLE);
        textY += lineHeight + 2;
        for (String line : data.lines()) {
            context.drawString(minecraft.font, line, x + 6, textY, WidgetTheme.TEXT_SOFT);
            textY += lineHeight;
        }
    }

    static int getPotionsBrewed(int currentPrice) {
        if (currentPrice <= 0) {
            return 0;
        }
        return (int) Math.round(Math.log(currentPrice / 40.0) / Math.log(1.25));
    }

    static int getPrice(int stage) {
        return (int) Math.ceil(40.0 * Math.pow(1.25, stage));
    }

    static int autoBrewCost(int currentPrice, int count) {
        int stage = getPotionsBrewed(currentPrice);
        int total = 0;
        for (int i = 1; i <= count; i++) {
            total += getPrice(stage + i);
        }
        return total;
    }

    static Integer parsePrice(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = PRICE_PATTERN.matcher(line.replace('\u00A0', ' '));
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer findCurrentPrice(AbstractContainerMenu menu, Player player) {
        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            List<Component> tooltip = stack.getTooltipLines(
                    Item.TooltipContext.of(player.level()),
                    player,
                    TooltipFlag.NORMAL
            );
            if (tooltip.isEmpty()) {
                continue;
            }

            Integer price = parsePrice(tooltip.getLast().getString());
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    public record OverlayData(String title, List<String> lines) {
    }
}
