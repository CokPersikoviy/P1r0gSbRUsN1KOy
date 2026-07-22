package ru.wilyfox.client.rune;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetExperienceOverlay {
    private static final Pattern LOOSE_EXP = Pattern.compile("^Опыт питомца: (\\d+)$");
    private static final Pattern BUCKET_EXP = Pattern.compile("^Опыта питомца в ведре: (\\d+)/.*$");
    private static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";
    private static final String DIMENSION_KEY = "prisonevo:dimension";

    private PetExperienceOverlay() {
    }

    public static OverlayData collect(AbstractContainerMenu menu) {
        ExperienceByDimension values = new ExperienceByDimension();
        for (Slot slot : menu.slots) {
            if (slot.hasItem()) {
                collectStack(slot.getItem(), values);
            }
        }
        if (values.total() <= 0L) {
            return null;
        }
        return new OverlayData("Опыт рыбы", List.of(
                "Всего: " + values.total(),
                "Обычного: " + values.overworld,
                "Адского: " + values.nether,
                "Эндер: " + values.end
        ));
    }

    public static void render(GuiGraphics context, int x, int y, OverlayData data) {
        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight + 1;
        int maxWidth = mc.font.width(data.title());
        for (String line : data.lines()) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }
        int width = maxWidth + 12;
        int height = 12 + lineHeight + data.lines().size() * lineHeight;
        HudSurface.drawPanel(context, x, y, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer());

        int textY = y + 5;
        context.drawString(mc.font, data.title(), x + 6, textY, WidgetTheme.TITLE);
        textY += lineHeight + 2;
        for (int i = 0; i < data.lines().size(); i++) {
            int color = switch (i) {
                case 1 -> WidgetTheme.TEXT_PRIMARY;
                case 2 -> WidgetTheme.TEXT_SECONDARY;
                case 3 -> WidgetTheme.TEXT_SOFT;
                default -> WidgetTheme.TITLE;
            };
            context.drawString(mc.font, data.lines().get(i), x + 6, textY, color);
            textY += lineHeight;
        }
    }

    private static void collectStack(ItemStack stack, ExperienceByDimension values) {
        long experience = extractExperience(stack);
        if (experience <= 0L) {
            return;
        }
        long stackExperience = experience * Math.max(1, stack.getCount());
        switch (readDimension(stack)) {
            case "nether" -> values.nether += stackExperience;
            case "end" -> values.end += stackExperience;
            default -> values.overworld += stackExperience;
        }
    }

    private static long extractExperience(ItemStack stack) {
        for (String line : getLoreLines(stack)) {
            Long value = matchExperience(LOOSE_EXP, line);
            if (value == null) {
                value = matchExperience(BUCKET_EXP, line);
            }
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }

    private static Long matchExperience(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String readDimension(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return "overworld";
        }
        CompoundTag root = customData.getUnsafe();
        CompoundTag values = root.contains(PUBLIC_BUKKIT_VALUES) ? root.getCompound(PUBLIC_BUKKIT_VALUES) : root;
        if (!values.contains(DIMENSION_KEY)) {
            return "overworld";
        }
        String dimension = values.getString(DIMENSION_KEY).trim().toLowerCase(Locale.ROOT);
        return dimension.isBlank() ? "overworld" : dimension;
    }

    private static List<String> getLoreLines(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return List.of();
        }
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
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

    private static final class ExperienceByDimension {
        private long overworld;
        private long nether;
        private long end;

        private long total() {
            return overworld + nether + end;
        }
    }

    public record OverlayData(String title, List<String> lines) {
    }
}
