package ru.wilyfox.client.rune;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetExperienceOverlay {
    private static final Pattern PET_EXP_PATTERN = Pattern.compile("Опыт питомца:\\s*([\\d\\s\\u00A0.,'_]+)");

    private PetExperienceOverlay() {
    }

    public static OverlayData collect(AbstractContainerMenu menu) {
        long totalExp = 0L;

        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) {
                continue;
            }

            totalExp += extractPetExperience(slot.getItem());
        }

        if (totalExp <= 0L) {
            return null;
        }

        return new OverlayData("Опыт питомца", List.of("Всего: " + totalExp));
    }

    public static void render(GuiGraphics context, int x, int y, OverlayData data) {
        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight + 1;

        int maxWidth = mc.font.width(data.title());
        for (String line : data.lines()) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }

        int width = maxWidth + 12;
        int height = 12 + lineHeight + Math.max(0, data.lines().size()) * lineHeight;

        HudSurface.drawPanel(context, x, y, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer());

        int textY = y + 5;
        context.drawString(mc.font, data.title(), x + 6, textY, WidgetTheme.TITLE);
        textY += lineHeight + 2;

        for (String line : data.lines()) {
            context.drawString(mc.font, line, x + 6, textY, WidgetTheme.TEXT_SOFT);
            textY += lineHeight;
        }
    }

    private static long extractPetExperience(ItemStack stack) {
        for (String line : getLoreLines(stack)) {
            Matcher matcher = PET_EXP_PATTERN.matcher(line);
            if (matcher.find()) {
                long experiencePerItem = parseFlexibleLong(matcher.group(1));
                if (experiencePerItem > 0L) {
                    return experiencePerItem * Math.max(1, stack.getCount());
                }
            }
        }

        return 0L;
    }

    private static long parseFlexibleLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        String digitsOnly = value
                .replace('\u00A0', ' ')
                .replaceAll("[^\\d]", "");
        if (digitsOnly.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(digitsOnly);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static List<String> getLoreLines(ItemStack stack) {
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.of(Minecraft.getInstance().player.level()),
                Minecraft.getInstance().player,
                Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        );
        List<String> lines = new ArrayList<>();

        for (int i = 1; i < tooltip.size(); i++) {
            String text = Formatting.stripMinecraftFormatting(tooltip.get(i).getString()).trim();
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }

        return lines;
    }

    public record OverlayData(String title, List<String> lines) {
    }
}
