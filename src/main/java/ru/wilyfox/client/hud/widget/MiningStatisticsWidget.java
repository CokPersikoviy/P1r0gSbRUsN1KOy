package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.MiningStatisticsConfig;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.statistic.MiningStatisticsStore;
import ru.wilyfox.client.statistic.MiningStatisticsTracker;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MiningStatisticsWidget extends AbstractWidget {
    private static final int PAD = 6, GAP = 12;
    // Item components are bound after Fabric entrypoints on 26.2; create icons at first render.
    private ItemStack[] icons;
    private final MiningStatisticsStore store;
    private List<Row> rows = List.of();
    private String time = "", status = "", totalHeader = "", rateHeader = "", empty = "";
    private MiningStatisticsStore.State state;
    private long refreshedAt = Long.MIN_VALUE;
    private int labelWidth, totalWidth, rateWidth, rowHeight, iconSpace;
    private boolean titled;
    private int cachedSettings;
    private final String[] units = new String[3];
    private int summaryPrefixWidth;
    public MiningStatisticsWidget(int x, int y, HudLayer layer) { this(x, y, layer, MiningStatisticsTracker.STORE); }
    public MiningStatisticsWidget(int x, int y, HudLayer layer, MiningStatisticsStore store) {
        super(x, y, layer); this.store = store;
    }
    public void invalidate() { refreshedAt = Long.MIN_VALUE; }
    private void refresh() {
        long now = System.nanoTime();
        var c = ConfigManager.get().miningStatistics;
        int settings = settingsKey(c);
        if (refreshedAt != Long.MIN_VALUE && settings == cachedSettings && now - refreshedAt < 200_000_000L) return;
        cachedSettings = settings;
        refreshedAt = now;
        try (var profile = ModProfiler.getInstance().scope("hud/MiningStatisticsWidget/statistics")) {
            var snapshot = store.snapshot(c);
            state = snapshot.state();
            time = (c.showLabels ? tr("time") + " " : "") + duration(snapshot.seconds());
            status = tr(switch (state) { case WAITING -> "waiting"; case RUNNING -> "running"; case AUTO_PAUSED -> "auto_paused"; case MANUAL_PAUSED -> "paused"; });
            totalHeader = tr("total"); rateHeader = tr("per_hour"); empty = tr("empty");
            units[0] = tr("blocks_unit"); units[1] = "$"; units[2] = tr("shards_unit");
            titled = c.showTitle && WidgetUtils.showWidgetTitles() && !c.title.isBlank();
            var next = new ArrayList<Row>(3);
            if (c.showBlocks) next.add(row(0, tr("blocks"), snapshot.blocks(), snapshot.blocksPerHour(), c));
            if (c.showMoney) next.add(row(1, tr("money"), snapshot.money(), snapshot.moneyPerHour(), c));
            if (c.showShards) next.add(row(2, tr("shards"), snapshot.shards(), snapshot.shardsPerHour(), c));
            rows = List.copyOf(next);
            var font = Minecraft.getInstance().font;
            boolean summary = c.layout == MiningStatisticsConfig.Layout.SUMMARY;
            iconSpace = c.showIcons && !ConfigManager.get().render.lightweightHud ? summary ? 32 : 16 : 0;
            rowHeight = Math.max(font.lineHeight, iconSpace == 0 || summary ? 0 : 12) + c.rowGap;
            labelWidth = c.showLabels ? rows.stream().mapToInt(row -> font.width(row.label)).max().orElse(0) : 0;
            totalWidth = c.showTotals && c.showHeaders ? font.width(totalHeader) : 0;
            rateWidth = c.showRates && c.showHeaders ? font.width(rateHeader) : 0;
            int contentWidth = 0;
            for (var row : rows) {
                if (c.showTotals) totalWidth = Math.max(totalWidth, font.width(row.total));
                if (c.showRates) rateWidth = Math.max(rateWidth, font.width(row.rate));
                contentWidth = Math.max(contentWidth, iconSpace + (c.showLabels ? labelWidth + GAP : 0) + font.width(row.combined));
            }
            if (c.layout == MiningStatisticsConfig.Layout.TABLE) {
                contentWidth = iconSpace + labelWidth;
                if (c.showTotals) contentWidth += GAP + totalWidth;
                if (c.showRates) contentWidth += GAP + rateWidth;
            }
            if (summary) {
                summaryPrefixWidth = c.showLabels ? Math.max(c.showTotals ? font.width(totalHeader + ":") : 0,
                        c.showRates ? font.width(rateHeader + ":") : 0) + 5 : 0;
                int totals = 0, rates = 0;
                for (var row : rows) {
                    String unit = c.showLabels ? " " + units[row.metric] : "";
                    totals += font.width(row.total + unit);
                    rates += font.width(row.rate + unit);
                }
                contentWidth = iconSpace + summaryPrefixWidth + (c.showTotals && c.showRates ? Math.max(totals, rates)
                        : c.showTotals ? totals : rates) + Math.max(0, rows.size() - 1) * font.width(" / ");
            }
            if (rows.isEmpty() || !c.showTotals && !c.showRates) contentWidth = Math.max(contentWidth, font.width(empty) + (summary ? iconSpace : 0));
            width = Math.max(c.minimumWidth, PAD * 2 + contentWidth);
            if (titled) width = Math.max(width, PAD * 2 + font.width(c.title) + (summary ? iconSpace : 0));
            if (c.showTime || c.showStatus) width = Math.max(width, PAD * 2 + (c.showTime ? font.width(time) : 0)
                    + (c.showStatus ? font.width(status) : 0) + (c.showTime && c.showStatus ? GAP : 0) + (summary ? iconSpace : 0));
            int rowCount = rows.isEmpty() || !c.showTotals && !c.showRates ? 1 : rows.size();
            if (summary && !rows.isEmpty() && (c.showTotals || c.showRates)) rowCount = (c.showTotals ? 1 : 0) + (c.showRates ? 1 : 0);
            int contentHeight = c.layout == MiningStatisticsConfig.Layout.COMPACT ? rowCount * rowHeight
                    : rowCount * rowHeight + (c.layout == MiningStatisticsConfig.Layout.TABLE && c.showHeaders ? font.lineHeight + c.rowGap : 0);
            height = PAD * 2 + contentHeight - c.rowGap + (titled ? font.lineHeight + 4 : 0)
                    + (c.showTime || c.showStatus ? font.lineHeight + 4 : 0);
            if (summary && iconSpace > 0) height = Math.max(height, PAD * 2 + 28);
        }
    }
    private static Row row(int metric, String label, double total, double rate, MiningStatisticsConfig c) {
        String totalText = format(total, c), rateText = format(rate, c);
        String combined = c.showTotals ? totalText : "";
        if (c.showRates) combined += (c.showTotals ? " / " : "") + rateText + "/h";
        if (c.layout == MiningStatisticsConfig.Layout.ROWS) {
            combined = c.showTotals ? tr("total") + ": " + totalText : "";
            if (c.showRates) combined += (c.showTotals ? " · " : "") + tr("per_hour") + ": " + rateText;
        }
        return new Row(metric, label, totalText, rateText, combined);
    }
    private static int settingsKey(MiningStatisticsConfig c) {
        int flags = (c.showTitle ? 1 : 0) | (c.showTime ? 2 : 0) | (c.showStatus ? 4 : 0) | (c.showBlocks ? 8 : 0)
                | (c.showMoney ? 16 : 0) | (c.showShards ? 32 : 0) | (c.showTotals ? 64 : 0) | (c.showRates ? 128 : 0)
                | (c.showLabels ? 256 : 0) | (c.showIcons ? 512 : 0) | (c.showHeaders ? 1024 : 0)
                | (WidgetUtils.showWidgetTitles() ? 2048 : 0) | (ConfigManager.get().render.lightweightHud ? 4096 : 0);
        int key = 31 * flags + c.title.hashCode();
        key = 31 * key + c.layout.ordinal(); key = 31 * key + c.numberFormat.ordinal();
        key = 31 * key + c.decimals; key = 31 * key + c.rowGap;
        return 31 * key + c.minimumWidth;
    }
    public static String format(double value, MiningStatisticsConfig c) {
        value = Double.isFinite(value) ? Math.max(0, value) : 0;
        String suffix = "";
        if (c.numberFormat == MiningStatisticsConfig.NumberFormat.SHORT) {
            String[] units = {"", "K", "M", "B", "T", "Q"};
            int unit = 0;
            while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
            suffix = units[unit];
        }
        var format = new DecimalFormat("#,##0" + (c.decimals > 0 ? "." + "#".repeat(c.decimals) : ""), DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setGroupingUsed(c.numberFormat == MiningStatisticsConfig.NumberFormat.FULL);
        return format.format(value) + suffix;
    }
    private static String duration(long seconds) { return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60); }
    private static String tr(String suffix) { return Component.translatable("froghelper.mining." + suffix).getString(); }
    @Override public int getWidth() { refresh(); return Math.round(width * scale); }
    @Override public int getHeight() { refresh(); return Math.round(height * scale); }
    @Override public boolean isVisible() { return isInLayout(); }
    @Override public String getDisplayName() { return "Mining Statistics"; }
    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        refresh();
        var c = ConfigManager.get().miningStatistics;
        var font = Minecraft.getInstance().font;
        int labelColor = c.customColors ? c.labelColor : WidgetTheme.TEXT_SECONDARY;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(startX, startY).scale(scale, scale);
            HudSurface.drawPanel(graphics, width, height);
            if (c.layout == MiningStatisticsConfig.Layout.SUMMARY) { renderSummary(graphics, c, labelColor); return; }
            int y = PAD;
            if (titled) { graphics.text(font, c.title, PAD, y, c.customColors ? c.titleColor : WidgetTheme.TITLE); y += font.lineHeight + 4; }
            if (c.showTime || c.showStatus) {
                if (c.showTime) graphics.text(font, time, PAD, y, c.customColors ? c.timeColor : WidgetTheme.TEXT_SECONDARY);
                if (c.showStatus) graphics.text(font, status, c.showTime ? width - PAD - font.width(status) : PAD, y,
                        c.customColors ? state == MiningStatisticsStore.State.RUNNING ? c.runningColor : c.pausedColor
                                : state == MiningStatisticsStore.State.RUNNING ? WidgetTheme.STATUS_SUCCESS : WidgetTheme.STATUS_WARNING);
                y += font.lineHeight + 4;
            }
            int totalRight = PAD + iconSpace + labelWidth + GAP + totalWidth;
            int rateRight = PAD + iconSpace + labelWidth + GAP + (c.showTotals ? totalWidth + GAP : 0) + rateWidth;
            if (c.layout == MiningStatisticsConfig.Layout.TABLE && c.showHeaders) {
                if (c.showTotals) graphics.text(font, totalHeader, totalRight - font.width(totalHeader), y, labelColor);
                if (c.showRates) graphics.text(font, rateHeader, rateRight - font.width(rateHeader), y, labelColor);
                y += font.lineHeight + c.rowGap;
            }
            if (rows.isEmpty() || !c.showTotals && !c.showRates) { graphics.text(font, empty, PAD, y, WidgetTheme.TEXT_MUTED); return; }
            for (var row : rows) {
                if (iconSpace > 0) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(PAD, y - 1).scale(.75f, .75f);
                    ensureIcons();
                    WidgetUtils.drawItemIcon(graphics, icons[row.metric], 0, 0);
                    graphics.pose().popMatrix();
                }
                if (c.showLabels) graphics.text(font, row.label, PAD + iconSpace, y, labelColor);
                int color = metricColor(row.metric, c);
                if (c.layout == MiningStatisticsConfig.Layout.TABLE) {
                    if (c.showTotals) graphics.text(font, row.total, totalRight - font.width(row.total), y, color);
                    if (c.showRates) graphics.text(font, row.rate, rateRight - font.width(row.rate), y, color);
                } else graphics.text(font, row.combined, PAD + iconSpace + (c.showLabels ? labelWidth + GAP : 0), y, color);
                y += rowHeight;
            }
        } finally { graphics.pose().popMatrix(); }
    }
    private void ensureIcons() {
        if (icons == null) icons = new ItemStack[]{new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.BARRIER)};
    }
    private static int metricColor(int metric, MiningStatisticsConfig c) {
        return c.customColors ? switch (metric) { case 0 -> c.blocksColor; case 1 -> c.moneyColor; default -> c.shardsColor; }
                : metric == 1 ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SOFT;
    }
    private void renderSummary(GuiGraphicsExtractor graphics, MiningStatisticsConfig c, int labelColor) {
        var font = Minecraft.getInstance().font;
        int x = PAD + iconSpace, y = PAD;
        if (iconSpace > 0) {
            ensureIcons(); graphics.pose().pushMatrix();
            graphics.pose().translate(PAD, Math.max(PAD, (height - 28) / 2)).scale(1.75f, 1.75f);
            WidgetUtils.drawItemIcon(graphics, icons[state == MiningStatisticsStore.State.RUNNING ? 0 : 3], 0, 0);
            graphics.pose().popMatrix();
        }
        if (titled) { graphics.text(font, c.title, x, y, c.customColors ? c.titleColor : WidgetTheme.TITLE); y += font.lineHeight + 4; }
        if (c.showTime || c.showStatus) {
            if (c.showTime) graphics.text(font, time, x, y, c.customColors ? c.timeColor : WidgetTheme.TEXT_SECONDARY);
            if (c.showStatus) graphics.text(font, status, c.showTime ? width - PAD - font.width(status) : x, y,
                    c.customColors ? state == MiningStatisticsStore.State.RUNNING ? c.runningColor : c.pausedColor
                            : state == MiningStatisticsStore.State.RUNNING ? WidgetTheme.STATUS_SUCCESS : WidgetTheme.STATUS_WARNING);
            y += font.lineHeight + 4;
        }
        if (rows.isEmpty() || !c.showTotals && !c.showRates) { graphics.text(font, empty, x, y, WidgetTheme.TEXT_MUTED); return; }
        if (c.showRates) { summaryLine(graphics, c, x, y, true, labelColor); y += rowHeight; }
        if (c.showTotals) summaryLine(graphics, c, x, y, false, labelColor);
    }
    private void summaryLine(GuiGraphicsExtractor graphics, MiningStatisticsConfig c, int x, int y, boolean rate, int labelColor) {
        var font = Minecraft.getInstance().font;
        if (c.showLabels) graphics.text(font, (rate ? rateHeader : totalHeader) + ":", x, y, labelColor);
        x += summaryPrefixWidth;
        boolean first = true;
        for (var row : rows) {
            if (!first) { graphics.text(font, " / ", x, y, labelColor); x += font.width(" / "); }
            String value = (rate ? row.rate : row.total) + (c.showLabels ? " " + units[row.metric] : "");
            graphics.text(font, value, x, y, metricColor(row.metric, c)); x += font.width(value);
            first = false;
        }
    }
    private record Row(int metric, String label, String total, String rate, String combined) {}
}
