package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private long dataRevision = Long.MIN_VALUE;
    private FishingNibblesSort cachedSort;
    private List<String> cachedLines = List.of();
    private List<net.minecraft.util.FormattedCharSequence> visualLines = List.of();
    private List<String> measuredLines;
    private net.minecraft.client.gui.Font measuredFont;
    private net.minecraft.locale.Language measuredLanguage;
    private boolean measuredTitles;
    private int measuredWidth, measuredHeight;
    private int[] lineWidths = new int[0];

    public FishingNibblesWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isInLayout()) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<String> lines = buildLines();

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        if (lines.isEmpty()) {
            renderPlaceholder(context, mc);
            context.pose().popMatrix();
            return;
        }

        HudSurface.drawPanel(context, getUnscaledWidth(lines, mc), getUnscaledHeight(lines, mc));

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.text(mc.font, "Fishing Nibbles", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + LINE_GAP + 1;
        }

        int panelWidth = getUnscaledWidth(lines, mc);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isDimensionHeader(line)) {
                int centeredX = Math.max(PADDING_X, (panelWidth - lineWidths[i]) / 2);
                context.text(mc.font, visualLines.get(i), centeredX, y, WidgetTheme.TEXT_MUTED);
            } else {
                context.text(mc.font, visualLines.get(i), PADDING_X, y, WidgetTheme.TEXT_SOFT);
            }
            y += mc.font.lineHeight + LINE_GAP;
        }

        context.pose().popMatrix();
    }

    @Override
    public boolean isVisible() {
        return isInLayout()
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
        long revision = DiamondWorldProtocolClient.getFishingRevision();
        var sort = ConfigManager.get().fishing.nibblesSort;
        if (dataRevision != revision || cachedSort != sort) {
            try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/FishingNibbles/lines")) {
                cachedLines = List.copyOf(rebuildLines()); dataRevision = revision; cachedSort = sort;
                measuredLines = null;
            }
        }
        return cachedLines;
    }

    private List<String> rebuildLines() {
        if (!DiamondWorldProtocolClient.hasFishingNibbles()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        Map<String, Double> nibbles = DiamondWorldProtocolClient.getFishingNibbles();
        Map<String, String> locationNames = DiamondWorldProtocolClient.getFishingLocationNames();
        Comparator<Map.Entry<String, Double>> comparator = ConfigManager.get().fishing.nibblesSort == FishingNibblesSort.NIBBLE
                ? Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                : dimensionComparator();

        int previousDimension = Integer.MIN_VALUE;
        for (Map.Entry<String, Double> entry : nibbles.entrySet().stream()
                .filter(entry -> locationNames.containsKey(entry.getKey()))
                .sorted(comparator.thenComparing(entry -> locationNames.get(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            if (ConfigManager.get().fishing.nibblesSort == FishingNibblesSort.DIMENSION) {
                int dimension = dimensionOrder(entry.getKey());
                if (dimension != previousDimension) {
                    lines.add("- " + dimensionName(dimension) + " -");
                    previousDimension = dimension;
                }
            }
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

    static Comparator<Map.Entry<String, Double>> dimensionComparator() {
        return Comparator.<Map.Entry<String, Double>>comparingInt(entry -> dimensionOrder(entry.getKey()))
                .thenComparing(Map.Entry.<String, Double>comparingByValue().reversed());
    }

    private static int dimensionOrder(String id) {
        return switch (id) {
            case "bay", "azurepond", "citycanal", "swamp", "ambergrot" -> 0;
            case "basalt", "netherval", "magma" -> 1;
            case "endwharf", "silence", "crystal" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private static String dimensionName(int dimension) {
        return switch (dimension) {
            case 0 -> "Overworld";
            case 1 -> "Nether";
            case 2 -> "End";
            default -> "Other";
        };
    }

    private static boolean isDimensionHeader(String line) {
        return line.startsWith("- ") && line.endsWith(" -");
    }

    private void measure(List<String> lines, Minecraft mc) {
        boolean titles = WidgetUtils.showWidgetTitles();
        var language = net.minecraft.locale.Language.getInstance();
        if (measuredLines == lines && measuredFont == mc.font && measuredLanguage == language && measuredTitles == titles) return;
        try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/FishingNibbles/layout")) {
            visualLines = lines.stream().map(line -> language.getVisualOrder(net.minecraft.network.chat.Component.literal(line))).toList();
            measuredWidth = titles ? mc.font.width("Fishing Nibbles") : 0;
            lineWidths = new int[lines.size()];
            for (int i = 0; i < lines.size(); i++) {
                lineWidths[i] = mc.font.width(lines.get(i)); measuredWidth = Math.max(measuredWidth, lineWidths[i]);
            }
            measuredWidth = lines.isEmpty() ? EMPTY_WIDTH : measuredWidth + PADDING_X * 2;
            int lineHeight = mc.font.lineHeight + LINE_GAP;
            measuredHeight = lines.isEmpty() ? EMPTY_HEIGHT : lines.size() * lineHeight + PADDING_Y * 2 + (titles ? lineHeight + 1 : 0);
            measuredLines = lines; measuredFont = mc.font; measuredLanguage = language; measuredTitles = titles;
        }
    }
    private int getUnscaledWidth(List<String> lines, Minecraft mc) { measure(lines, mc); return measuredWidth; }
    private int getUnscaledHeight(List<String> lines, Minecraft mc) { measure(lines, mc); return measuredHeight; }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.text(mc.font, "Fishing Nibbles", PADDING_X, 6, WidgetTheme.TITLE);
        context.text(mc.font, "No fishing data", PADDING_X, 15, WidgetTheme.TEXT_MUTED);
    }
}
