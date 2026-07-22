package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.FishingNibblesSort;
import ru.wilyfox.client.hud.config.FishingWidgetVisibility;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishingNibblesWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int EMPTY_WIDTH = 166;
    private static final int EMPTY_HEIGHT = 28;

    public FishingNibblesWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().fishing.showFishingNibblesWidget) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<String> lines = buildLines();

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        if (lines.isEmpty()) {
            renderPlaceholder(context, mc);
            context.pose().popPose();
            return;
        }

        HudSurface.drawPanel(context, getUnscaledWidth(lines, mc), getUnscaledHeight(lines, mc));

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Fishing Nibbles", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + LINE_GAP + 1;
        }

        for (String line : lines) {
            context.drawString(mc.font, line, PADDING_X, y, WidgetTheme.TEXT_SOFT);
            y += mc.font.lineHeight + LINE_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().fishing.showFishingNibblesWidget
                && (isEditorPreview() || (matchesVisibility(ConfigManager.get().fishing.nibblesVisibility)
                && DiamondWorldProtocolClient.hasFishingNibbles()));
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = buildLines();
        return Math.round(getUnscaledWidth(lines, mc) * getScale());
    }

    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = buildLines();
        return Math.round(getUnscaledHeight(lines, mc) * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Fishing Nibbles";
    }

    private List<String> buildLines() {
        if (!DiamondWorldProtocolClient.hasFishingNibbles()) {
            if (!isEditorPreview()) {
                return List.of();
            }

            return List.of(
                    "Amber Grot - 150.0%",
                    "Nether Valley - 150.0%",
                    "Silence - 150.0%",
                    "Crystal Gorge - 100.0%",
                    "City Canal - 100.0%"
            );
        }

        List<String> lines = new ArrayList<>();
        Map<String, Double> nibbles = DiamondWorldProtocolClient.getFishingNibbles();
        Map<String, String> locationNames = DiamondWorldProtocolClient.getFishingLocationNames();
        Comparator<Map.Entry<String, Double>> comparator = ConfigManager.get().fishing.nibblesSort == FishingNibblesSort.NIBBLE
                ? Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                : Comparator.<Map.Entry<String, Double>>comparingInt(entry -> dimensionOrder(entry.getKey()))
                        .thenComparing(Map.Entry.<String, Double>comparingByValue().reversed());

        for (Map.Entry<String, Double> entry : nibbles.entrySet().stream()
                .filter(entry -> locationNames.containsKey(entry.getKey()))
                .sorted(comparator.thenComparing(entry -> locationNames.get(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            String name = locationNames.get(entry.getKey());
            lines.add(name + " - " + String.format(Locale.ROOT, "%.1f%%", entry.getValue()));
        }
        return lines;
    }

    private static boolean matchesVisibility(FishingWidgetVisibility visibility) {
        Minecraft mc = Minecraft.getInstance();
        return switch (visibility) {
            case ALWAYS -> true;
            case FISHING_WARP -> DiamondWorldProtocolClient.isCurrentFishingLocation();
            case FISHING_ROD -> mc.player != null && mc.player.getMainHandItem().is(Items.FISHING_ROD);
        };
    }

    private static int dimensionOrder(String id) {
        return switch (id) {
            case "overworld" -> 0;
            case "nether" -> 1;
            case "end" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private int getUnscaledWidth(List<String> lines, Minecraft mc) {
        if (lines.isEmpty()) {
            return EMPTY_WIDTH;
        }

        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Fishing Nibbles") : 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(List<String> lines, Minecraft mc) {
        if (lines.isEmpty()) {
            return EMPTY_HEIGHT;
        }

        int lineHeight = mc.font.lineHeight + LINE_GAP;
        int titleBlock = WidgetUtils.showWidgetTitles() ? lineHeight + 1 : 0;
        return lines.size() * lineHeight + PADDING_Y * 2 + titleBlock;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Fishing Nibbles", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No fishing data", PADDING_X, 15, WidgetTheme.TEXT_MUTED);
    }
}
