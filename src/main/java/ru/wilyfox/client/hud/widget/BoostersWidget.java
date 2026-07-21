package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.utils.Formatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BoostersWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ROW_GAP = 3;
    private static final int COLUMN_GAP = 8;
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private final BoosterStore store;

    public BoostersWidget(int x, int y, HudLayer layer, BoosterStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (ConfigManager.get().boosters.compact) {
            renderCompact(context, mc);
        } else {
            renderDetailed(context, mc);
        }
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        int width = ConfigManager.get().boosters.compact ? getCompactWidth(mc) : getDetailedWidth(mc);
        return Math.round(width * getScale());
    }

    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        int height = ConfigManager.get().boosters.compact ? getCompactHeight(mc) : getDetailedHeight(mc);
        return Math.round(height * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().boosters.active && (store.hasAnyActive() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Boosters";
    }

    private void renderCompact(GuiGraphics context, Minecraft mc) {
        List<BoosterView> views = getViews();
        int width = getCompactWidth(mc);
        int height = getCompactHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Boosters", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 4;
        }

        for (BoosterView view : views) {
            context.drawString(mc.font, formatCompactLine(view), PADDING_X, y, WidgetTheme.TEXT_SOFT);
            y += mc.font.lineHeight + ROW_GAP;
        }

        context.pose().popPose();
    }

    private void renderDetailed(GuiGraphics context, Minecraft mc) {
        List<BoosterView> views = getViews();
        int width = getDetailedWidth(mc);
        int height = getDetailedHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);

        int contentY = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Boosters", PADDING_X, contentY, WidgetTheme.TITLE);
            contentY += mc.font.lineHeight + 4;
        }

        int x = PADDING_X;
        for (BoosterView view : views) {
            int columnWidth = getDetailedColumnWidth(mc, view);
            renderDetailedColumn(context, mc, view, x, contentY, columnWidth);
            x += columnWidth + COLUMN_GAP;
        }

        context.pose().popPose();
    }

    private void renderDetailedColumn(GuiGraphics context, Minecraft mc, BoosterView view, int x, int y, int width) {
        BoosterStore.Snapshot snapshot = view.snapshot();
        context.drawString(mc.font, view.label() + " x" + formatMultiplier(snapshot.totalMultiplier()), x, y, WidgetTheme.TEXT_PRIMARY);
        y += mc.font.lineHeight + 2;

        if (snapshot.entries().isEmpty()) {
            context.drawString(mc.font, "No active boosts", x, y, WidgetTheme.TEXT_MUTED);
            return;
        }

        for (BoosterStore.Entry entry : snapshot.entries()) {
            String left = "Active x" + formatMultiplier(entry.multiplier());
            String right = Formatting.formatMillis(entry.endsAt());
            context.drawString(mc.font, left, x, y, WidgetTheme.TEXT_SOFT);
            context.drawString(mc.font, right, x + width - mc.font.width(right), y, WidgetTheme.TEXT_SECONDARY);
            y += mc.font.lineHeight + 1;
        }
    }

    private int getCompactWidth(Minecraft mc) {
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Boosters") : 0;
        for (BoosterView view : getViews()) {
            maxWidth = Math.max(maxWidth, mc.font.width(formatCompactLine(view)));
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getCompactHeight(Minecraft mc) {
        int titleBlock = WidgetUtils.showWidgetTitles() ? mc.font.lineHeight + 4 : 0;
        int count = Math.max(1, getViews().size());
        return PADDING_Y * 2 + titleBlock + count * mc.font.lineHeight + Math.max(0, count - 1) * ROW_GAP;
    }

    private int getDetailedWidth(Minecraft mc) {
        List<BoosterView> views = getViews();
        int contentWidth = views.stream().mapToInt(view -> getDetailedColumnWidth(mc, view)).sum();
        return PADDING_X * 2 + contentWidth + Math.max(0, views.size() - 1) * COLUMN_GAP;
    }

    private int getDetailedColumnWidth(Minecraft mc, BoosterView view) {
        BoosterStore.Snapshot snapshot = view.snapshot();
        int maxWidth = mc.font.width(view.label() + " x" + formatMultiplier(snapshot.totalMultiplier()));
        maxWidth = Math.max(maxWidth, mc.font.width("No active boosts"));
        for (BoosterStore.Entry entry : snapshot.entries()) {
            String line = "Active x" + formatMultiplier(entry.multiplier()) + " " + Formatting.formatMillis(entry.endsAt());
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }
        return maxWidth;
    }

    private int getDetailedHeight(Minecraft mc) {
        int lines = getViews().stream()
                .mapToInt(view -> 1 + Math.max(1, view.snapshot().entries().size()))
                .max()
                .orElse(2);
        int titleBlock = WidgetUtils.showWidgetTitles() ? mc.font.lineHeight + 4 : 0;
        return PADDING_Y * 2 + titleBlock + lines * (mc.font.lineHeight + 1);
    }

    private String formatCompactLine(BoosterView view) {
        BoosterStore.Snapshot snapshot = view.snapshot();
        BoosterStore.Entry latest = snapshot.latest();
        if (latest == null) {
            return view.label() + ": no active boosts";
        }
        return view.label() + ": x" + formatMultiplier(snapshot.totalMultiplier())
                + " (" + Formatting.formatMillis(latest.endsAt()) + ")";
    }

    private List<BoosterView> getViews() {
        List<BoosterView> views = new ArrayList<>();
        for (BoosterStore.Kind kind : BoosterStore.Kind.values()) {
            BoosterStore.Snapshot snapshot = store.getSnapshot(kind);
            if (snapshot.hasAny() || isEditorPreview()) {
                views.add(new BoosterView(kindLabel(kind), snapshot));
            }
        }
        return views;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private static String kindLabel(BoosterStore.Kind kind) {
        return switch (kind) {
            case SHARD -> "Shards";
            case MONEY -> "Money";
            case SHAFT -> "Shaft";
        };
    }

    private static String formatMultiplier(double multiplier) {
        return MULTIPLIER_FORMAT.format(multiplier);
    }

    private record BoosterView(String label, BoosterStore.Snapshot snapshot) {
    }
}
