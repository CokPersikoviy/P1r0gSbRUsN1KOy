package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
                && (isEditorPreview()
                || (DiamondWorldProtocolClient.isCurrentFishingLocation() && DiamondWorldProtocolClient.hasFishingNibbles()));
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
        if (!DiamondWorldProtocolClient.isCurrentFishingLocation() || !DiamondWorldProtocolClient.hasFishingNibbles()) {
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
        Set<String> locationIds = new LinkedHashSet<>(locationNames.keySet());
        locationIds.addAll(nibbles.keySet());

        for (String locationId : locationIds.stream()
                .sorted(Comparator
                        .comparingDouble((String id) -> nibbles.getOrDefault(id, Double.NEGATIVE_INFINITY))
                        .reversed()
                        .thenComparing(id -> DiamondWorldProtocolClient.getFishingLocationName(id), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            String name = DiamondWorldProtocolClient.getFishingLocationName(locationId);
            Double nibble = nibbles.get(locationId);
            lines.add(name + " - " + (nibble != null ? String.format(Locale.ROOT, "%.1f%%", nibble) : "-"));
        }
        return lines;
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

