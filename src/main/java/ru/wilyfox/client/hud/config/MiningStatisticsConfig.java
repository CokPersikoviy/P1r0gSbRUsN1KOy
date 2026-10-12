package ru.wilyfox.client.hud.config;

import java.util.EnumSet;
import java.util.Set;

public final class MiningStatisticsConfig {
    public enum Layout { SUMMARY, TABLE, ROWS, COMPACT }
    public enum NumberFormat { SHORT, FULL }
    public enum Source { BLOCKS, BARRELS, RUNES, BOMBS, PETS, WANDS, MULTITOOL }
    public Layout layout = Layout.SUMMARY;
    public NumberFormat numberFormat = NumberFormat.SHORT;
    public String title = "Mining Statistics";
    public boolean showTitle, showStatus;
    public boolean showTime = true, showHeaders = true;
    public boolean showBlocks = true, showMoney = true, showShards = true;
    public boolean showTotals = true, showRates = true, showLabels = true, showIcons = true;
    public boolean autoPause = true, trackMoney = true, customColors;
    public int idleSeconds = 10, decimals = 1, rowGap = 3, minimumWidth = 190;
    public int titleColor = 0xFFE4E4E4, labelColor = 0xFFBBBBBB, blocksColor = 0xFF78CFFF;
    public int moneyColor = 0xFF87E6AA, shardsColor = 0xFFCDA3FF, timeColor = 0xFFBBBBBB;
    public int runningColor = 0xFF87E6AA, pausedColor = 0xFFE6DCC8;
    public int pauseKey = -1, resetKey = -1, pauseModifiers, resetModifiers;
    public Set<Source> sources = EnumSet.allOf(Source.class);

    public void sanitize() {
        if (layout == null) layout = Layout.SUMMARY;
        if (numberFormat == null) numberFormat = NumberFormat.SHORT;
        if (title == null) title = "Mining Statistics";
        title = title.replaceAll("[\\p{Cntrl}§]", "");
        if (title.length() > 48) title = title.substring(0, 48);
        if (sources == null) sources = EnumSet.allOf(Source.class);
        sources.remove(null);
        idleSeconds = Math.clamp(idleSeconds, 1, 300);
        decimals = Math.clamp(decimals, 0, 2);
        rowGap = Math.clamp(rowGap, 0, 10);
        minimumWidth = Math.clamp(minimumWidth, 60, 500);
        pauseModifiers &= 7; resetModifiers &= 7;
    }
}
