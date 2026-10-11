package ru.wilyfox.client.rune;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.InventoryOverlayPanel;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetExperienceOverlay {
    private static final Pattern LOOSE_EXP = Pattern.compile("^(?:Опыт питомца|Pet experience): (\\d+)$");
    private static final Pattern BUCKET_EXP = Pattern.compile("^(?:Опыта питомца в ведре|Bucket Pet experience): (\\d+)/.*$");
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

    public static void render(GuiGraphicsExtractor context, int x, int y, OverlayData data) {
        InventoryOverlayPanel.render(context, x, y, data.title(), data.lines(),
                ConfigManager.get().fishing.petExperienceOverlayScalePercent, false, index -> switch (index) {
                    case 1 -> WidgetTheme.TEXT_PRIMARY;
                    case 2 -> WidgetTheme.TEXT_SECONDARY;
                    case 3 -> WidgetTheme.TEXT_SOFT;
                    default -> WidgetTheme.TITLE;
                });
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

    static long extractExperience(ItemStack stack) {
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
        CompoundTag root = customData.copyTag();
        CompoundTag values = root.contains(PUBLIC_BUKKIT_VALUES) ? root.getCompoundOrEmpty(PUBLIC_BUKKIT_VALUES) : root;
        if (!values.contains(DIMENSION_KEY)) {
            return "overworld";
        }
        String dimension = values.getStringOr(DIMENSION_KEY, "").trim().toLowerCase(Locale.ROOT);
        return dimension.isBlank() ? "overworld" : dimension;
    }

    private static List<String> getLoreLines(ItemStack stack) {
        return ItemLoreReader.read(stack);
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
