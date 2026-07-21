package ru.wilyfox.client.rune;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuneSetEffectOverlay {
    private static final String MENU_NAME = "";
    private static final List<Integer> RUNE_BAG_SLOTS = List.of(11, 13, 15, 27, 35);
    private static final List<Integer> RUNE_SET_SLOTS = List.of(0, 1, 3, 4, 5, 6, 8);
    // First lore line of the "Мешок для рун" item, which carries the whole set's already-aggregated
    // effect. Unique to that item among everything in the bag/inventory, so it's a safe anchor.
    private static final String BAG_MARKER = "используется для активации";
    private static final Pattern PROPERTY_LINE = Pattern.compile("(.*): (.*)");
    private static final String ACTIVE_ABILITY_MARKER = "Активная способность";
    private static final String PASSIVE_ABILITY_MARKER = "Пассивная способность";

    private RuneSetEffectOverlay() {
    }

    public static boolean isRuneBagScreen(Component title) {
        return title != null && title.getString().contains(MENU_NAME);
    }

    /**
     * The active set's buff overlay: title = the selected set's name (e.g. "Сет рун №4"), lines = the
     * whole set's aggregated effect. Prefer the game's pre-aggregated "Мешок для рун" item (accurate —
     * includes set-level bonuses that aren't on individual runes); when that item isn't in the inventory
     * (the case the overlay used to fail on), fall back to aggregating from the equipped rune papers.
     */
    public static OverlayData collect(AbstractContainerMenu menu) {
        String setName = findSelectedSetName(menu);
        List<String> buffs = collectSetBuffLines(menu);
        if (buffs.isEmpty()) {
            buffs = collectSetBuffLinesFromRunes(menu); // fallback: aggregate the equipped rune papers ourselves
        }

        if (setName == null && buffs.isEmpty()) {
            return null;
        }

        return new OverlayData(setName == null ? "Эффект сета рун" : setName, buffs);
    }

    /**
     * Whether the rune bag's contents are actually loaded (any set selector slot is populated). Guards
     * the optimistic active-runes sync so it doesn't clear the HUD on the empty frames right after the
     * screen opens, while still allowing a genuinely emptied set to clear it.
     */
    public static boolean isRuneBagLoaded(AbstractContainerMenu menu) {
        for (int slotIndex : RUNE_SET_SLOTS) {
            if (slotIndex >= 0 && slotIndex < menu.slots.size() && menu.getSlot(slotIndex).hasItem()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Active runes read directly from the rune-bag's rune slots (paper items). Reflects every in-bag
     * change live — swapping a rune, filling an empty slot, removing one, or selecting a whole set — so
     * the HUD updates the instant the bag refreshes, instead of waiting for the activerunes packet
     * (which lags several seconds under server load).
     */
    public static List<String> collectActiveRuneNames(AbstractContainerMenu menu) {
        List<String> runes = new ArrayList<>();
        for (int slotIndex : RUNE_BAG_SLOTS) {
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (!stack.is(Items.PAPER)) {
                continue;
            }

            runes.add(stack.getHoverName().getString());
        }
        return runes;
    }

    /** Feeds {@link RuneSetCooldownStore} (used by the ActiveRunes bar) from the bag's set slots. */
    public static void updateCooldownStore(AbstractContainerMenu menu) {
        long longestCooldown = 0L;

        for (int slotIndex : RUNE_SET_SLOTS) {
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) {
                continue;
            }

            for (String line : getLoreLines(slot.getItem())) {
                long parsed = Formatting.parseTimeToMillis(line);
                if (parsed > longestCooldown) {
                    longestCooldown = parsed;
                }
            }
        }

        if (longestCooldown > 0L) {
            // The bag lore shows the cooldown at whole-second (ceil'd) granularity, e.g. actual 8.3s reads
            // "9 секунд". Treating that as exact makes the bar linger ~0.5-1s after the set is already
            // swappable. Centre the estimate on the second it represents (V-1, V] -> V-0.5s.
            RuneSetCooldownStore.update(Math.max(0L, longestCooldown - LORE_ROUNDING_BIAS_MS));
        }
    }

    /** Half a second — recenters the whole-second (ceil'd) bag-lore cooldown so the bar isn't ~1s late. */
    private static final long LORE_ROUNDING_BIAS_MS = 500L;

    public static boolean isPlayerInventoryScreen(Object screen) {
        return screen instanceof InventoryScreen;
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

    private static String findSelectedSetName(AbstractContainerMenu menu) {
        for (int slotIndex : RUNE_SET_SLOTS) {
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            List<String> lore = getLoreLines(stack);
            if (!lore.isEmpty() && lore.get(0).contains("Используется")) {
                return Formatting.stripMinecraftFormatting(stack.getHoverName().getString()).trim();
            }
        }

        return null;
    }

    /** Pulls the aggregated set effect from the "Мешок для рун" item's lore. */
    private static List<String> collectSetBuffLines(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) {
                continue;
            }

            List<String> lore = getLoreLines(slot.getItem());
            if (!lore.isEmpty() && lore.get(0).toLowerCase().contains(BAG_MARKER)) {
                return extractSetBuffLines(lore);
            }
        }

        return List.of();
    }

    private static List<String> extractSetBuffLines(List<String> lore) {
        List<String> lines = new ArrayList<>();

        for (String line : lore) {
            String clean = line.trim();
            if (clean.isEmpty()) {
                continue;
            }

            String lower = clean.toLowerCase();
            if (lower.contains(BAG_MARKER) || lower.contains("нельзя передать")) {
                continue;
            }

            // Stat lines ("Сила: +95%"), the active/passive ability headers ("… способности:"), and the
            // "● …" passive-ability bullets under them.
            if (clean.contains(":") || clean.startsWith("●")) {
                lines.add(clean);
            }
        }

        return lines;
    }

    /**
     * Aggregate the whole set's effect straight from the equipped rune papers (RUNE_BAG_SLOTS), the same
     * way the game builds the "Мешок для рун" item — so it works without that item present. Numeric stats
     * are summed per name (mirrors EvoPlus's RuneProperty); a line is treated as a stat only if its value
     * matches a stat pattern (so "Тип: …", "Минимальный уровень: 455", "Длительность: 5 сек." are skipped).
     * The active/passive ability runes are appended for parity with the item's display.
     */
    private static List<String> collectSetBuffLinesFromRunes(AbstractContainerMenu menu) {
        Map<String, AggregatedProperty> stats = new LinkedHashMap<>();
        String activeAbility = null;
        List<String> passiveAbilities = new ArrayList<>();

        for (int slotIndex : RUNE_BAG_SLOTS) {
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (!stack.is(Items.PAPER)) {
                continue;
            }

            List<String> lore = getLoreLines(stack);
            for (String line : lore) {
                Matcher matcher = PROPERTY_LINE.matcher(line);
                if (matcher.matches()) {
                    stats.computeIfAbsent(matcher.group(1).trim(), AggregatedProperty::new)
                            .append(matcher.group(2).trim());
                }
            }

            if (lore.contains(ACTIVE_ABILITY_MARKER)) {
                activeAbility = runeShortName(stack);
            }
            if (lore.contains(PASSIVE_ABILITY_MARKER)) {
                passiveAbilities.add(runeShortName(stack));
            }
        }

        List<String> lines = new ArrayList<>();
        stats.values().stream()
                .filter(property -> !property.isEmpty())
                .sorted((a, b) -> Double.compare(Math.abs(b.value), Math.abs(a.value)))
                .map(AggregatedProperty::format)
                .forEach(lines::add);

        if (activeAbility != null) {
            lines.add("Активная способность: " + activeAbility);
        }
        if (!passiveAbilities.isEmpty()) {
            lines.add("Пассивные способности:");
            for (String passive : passiveAbilities) {
                lines.add("● " + passive);
            }
        }

        return lines;
    }

    /** Rune display name without its trailing level ("Садист III" -> "Садист"), as the set item shows it. */
    private static String runeShortName(ItemStack stack) {
        String name = Formatting.stripMinecraftFormatting(stack.getHoverName().getString()).trim();
        return name.replaceAll("\\s+[IVXLCDM]+$", "").trim();
    }

    private static List<String> getLoreLines(ItemStack stack) {
        // Always NORMAL: ADVANCED appends the registry-id line ("minecraft:paper"), durability, etc.,
        // which the buff parser would otherwise pick up as a fake property line.
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.of(Minecraft.getInstance().player.level()),
                Minecraft.getInstance().player,
                TooltipFlag.NORMAL
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

    /** One aggregated numeric stat across the set's runes. Mirrors EvoPlus's RuneProperty value types. */
    private static final class AggregatedProperty {
        private static final Pattern FLAT = Pattern.compile("^([-+][\\d.,]+)(?: \\(([-+][\\d.,]+)\\))?(?: \\| ([-+][\\d.,]+))?$");
        private static final Pattern PERCENT = Pattern.compile("^([-+][\\d.,]+)%(?: \\(([-+][\\d.,]+)%\\))?(?: \\| ([-+][\\d.,]+)%)?$");
        private static final Pattern MULTIPLY = Pattern.compile("^x([\\d.,]+)(?: \\| x([\\d.,]+))?$");
        private static final Pattern MINER = Pattern.compile("^1 к (\\d+)$");

        private final String name;
        private Kind kind = Kind.PRESENCE;
        private double value = 0.0;
        private boolean present;

        private AggregatedProperty(String name) {
            this.name = name;
        }

        /** Accumulate a raw value; returns whether it matched a known stat pattern (else it's not a stat). */
        private boolean append(String rawValue) {
            if (rawValue.equals("+")) {
                present = true;
                kind = Kind.PRESENCE;
                return true;
            }

            Matcher percent = PERCENT.matcher(rawValue);
            if (percent.matches()) {
                kind = Kind.PERCENT;
                value += parseGroup(percent, 1) + parseGroup(percent, 2) + parseGroup(percent, 3);
                return true;
            }

            Matcher flat = FLAT.matcher(rawValue);
            if (flat.matches()) {
                kind = Kind.FLAT;
                value += parseGroup(flat, 1) + parseGroup(flat, 2) + parseGroup(flat, 3);
                return true;
            }

            Matcher multiply = MULTIPLY.matcher(rawValue);
            if (multiply.matches()) {
                kind = Kind.MULTIPLY;
                value += parseGroup(multiply, 1) + parseGroup(multiply, 2) - 1.0;
                return true;
            }

            Matcher miner = MINER.matcher(rawValue);
            if (miner.matches()) {
                kind = Kind.MINER;
                value += 1.0 / Double.parseDouble(miner.group(1));
                return true;
            }

            return false;
        }

        private boolean isEmpty() {
            return !present && Math.abs(value) < 0.0001;
        }

        private String format() {
            return name + ": " + switch (kind) {
                case PRESENCE -> "+";
                case FLAT -> signed(value);
                case PERCENT -> signed(value) + "%";
                case MULTIPLY -> "x" + trim(value);
                case MINER -> "1 к " + (value == 0.0 ? "0" : String.valueOf((int) Math.round(1.0 / value)));
            };
        }

        private static double parseGroup(Matcher matcher, int group) {
            String value = matcher.group(group);
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value.replace(',', '.'));
        }

        private static String signed(double value) {
            String formatted = trim(value);
            return value >= 0 ? "+" + formatted : formatted;
        }

        private static String trim(double value) {
            String formatted = String.format(java.util.Locale.US, "%.2f", value);
            return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    private enum Kind {
        PRESENCE,
        FLAT,
        PERCENT,
        MULTIPLY,
        MINER
    }
}
