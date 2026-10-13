package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.boss.BossIconInfo;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.boss.BossStaticIconLookup;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.utils.MouseUtils;
import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.boss.BossTimerFilter;
import ru.wilyfox.client.boss.BossTimerRowFormatter;
import ru.wilyfox.client.hud.config.BossTimerElement;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import static ru.wilyfox.utils.Formatting.formatMillis;
import static ru.wilyfox.utils.Formatting.formatMillisSigned;

public class BossHudWidget extends AbstractWidget {
    /** Prefixed to raid-boss names in the timer during a mythical event. */
    private static final String RAID_MARKER = "✦ ";
    /** Prefixed to bosses the clan currently holds (captured / location busy). */
    private static final String CAPTURE_MARKER = "✗ ";
    /** Event boss (not a real respawn timer) — never listed in the boss timer. */
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int COLUMN_GAP = 6;
    private static final int NATIVE_ICON_SIZE = 16;
    private static final int ICON_SIZE = 14;
    private static final int ICON_TEXT_GAP = 4;
    private static final int ICON_Y_OFFSET = -3;
    private static final int EMPTY_WIDTH = 110;
    private static final int EMPTY_HEIGHT = 28;

    private final BossRepository repository;
    private final Map<BossIconInfo, ItemStack> iconCache = new HashMap<>();
    private final Map<BossInfo, RowText> rowCache = new IdentityHashMap<>();
    private final Map<BossInfo, ResolvedIcon> resolvedIcons = new IdentityHashMap<>();

    private static final class RowText {
        final String level;
        String prefix;
        String suffix;
        String name;
        String compact;
        String timer;
        Component nameComponent, compactComponent;
        BossTimerRowFormatter.Appearance appearance;
        long seconds = Long.MIN_VALUE;
        boolean spawned;
        int nameWidth;
        int levelWidth;
        int compactWidth;
        int timerWidth;
        RowText(BossInfo boss) { level = "[" + boss.getLevel() + "]"; }
    }

    private record ResolvedIcon(BossIconInfo protocol, long revision, ItemStack stack) {}

    // Per-frame cache: the visible-boss list + its measured dimensions were rebuilt on every call, and
    // isVisible()/getWidth()/getHeight()/render() ask for them several times per frame (and the layout
    // change-detector calls getWidth/getHeight again). In a boss fight that was ~1 ms/frame of pure
    // recompute + the biggest frame spikes. Compute once per HUD frame (keyed on HudFrameClock), reuse.
    private long cachedFrameId = Long.MIN_VALUE;
    private long cachedFrameTime;
    private List<BossInfo> cachedVisibleBosses;
    private int cachedUnscaledWidth;
    private int cachedUnscaledHeight;

    public BossHudWidget(int x, int y, HudLayer layer, BossRepository repository) {
        super(x, y, layer);
        this.repository = repository;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        boolean showName = ConfigManager.get().bossWidget.showName;
        boolean showIcons = ConfigManager.get().bossWidget.showIcons;
        boolean showLevel = ConfigManager.get().bossWidget.showLevel;
        boolean showTimer = ConfigManager.get().bossWidget.showTimer;
        boolean fullAlignment = ConfigManager.get().bossWidget.fullAligment;

        List<BossInfo> visibleBosses = getVisibleBosses();
        if (visibleBosses.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int maxNameWidth = fullAlignment && showName ? getMaxNameWidth(visibleBosses, mc) : 0;
        int maxLevelWidth = fullAlignment && showLevel ? getMaxLevelWidth(visibleBosses, mc) : 0;
        int compactMarkerWidth = !showName ? getMaxCompactMarkerWidth(visibleBosses, mc) : 0;
        int lineStep = getLineStep(mc);

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPanel(context, getUnscaledWidth(), getUnscaledHeight());

        int contentY = PADDING_Y;
        if (showTitle()) {
            context.text(mc.font, "Boss Timers", PADDING_X, contentY, BossTimerRowFormatter.color(ConfigManager.get().bossWidget, BossTimerElement.TITLE, WidgetTheme.TITLE));
            contentY += mc.font.lineHeight + LINE_GAP + 2;
        }

        for (int line = 0; line < visibleBosses.size(); line++) {
            BossInfo boss = visibleBosses.get(line);
            int y = contentY + line * lineStep;
            RowText row = rowCache.get(boss);
            ItemStack icon = showIcons && !ConfigManager.get().render.lightweightHud ? getBossIcon(boss) : ItemStack.EMPTY;
            renderRow(context, mc, row, icon, y, maxNameWidth, maxLevelWidth, compactMarkerWidth);
        }

        // Hover feedback for the chat-click teleport: a 1px accent underline under the row the mouse is
        // over — two-thirds of the panel width, centred — shown only while chat is open (the click context).
        if (mc.gui.screen() instanceof ChatScreen) {
            int hoverRow = bossRowAt(MouseUtils.getMouseX(), MouseUtils.getMouseY());
            if (hoverRow >= 0 && hoverRow < visibleBosses.size()) {
                int contentWidth = getUnscaledWidth() - PADDING_X * 2;
                int underlineWidth = Math.max(1, contentWidth * 2 / 3);
                int underlineX = PADDING_X + (contentWidth - underlineWidth) / 2;
                int underlineY = contentY + hoverRow * lineStep + mc.font.lineHeight;
                context.fill(underlineX, underlineY, underlineX + underlineWidth, underlineY + 1, WidgetTheme.ACCENT_LINE);
            }
        }

        context.pose().popMatrix();
    }

    private static void renderRow(GuiGraphicsExtractor context, Minecraft mc, RowText row, ItemStack icon,
                                  int y, int maxNameWidth, int maxLevelWidth, int compactMarkerWidth) {
        var config = ConfigManager.get().bossWidget;
        boolean showName = config.showName, showIcons = config.showIcons, showLevel = config.showLevel, showTimer = config.showTimer;
        boolean fullAlignment = config.fullAligment, spawned = row.spawned;
        String compactMarkerText = showName ? "" : row.compact, levelText = row.level, timerText = row.timer;
        int currentX = PADDING_X;
        int nameColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY;
        int levelColor = BossTimerRowFormatter.color(ConfigManager.get().bossWidget, BossTimerElement.LEVEL, spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY);
        int timerColor = BossTimerRowFormatter.color(ConfigManager.get().bossWidget, BossTimerElement.TIMER, spawned ? WidgetTheme.HARD_ACCENT : WidgetTheme.TEXT_SOFT);
        int iconY = y + Math.max(0, (mc.font.lineHeight - ICON_SIZE) / 2) + ICON_Y_OFFSET;

        if (showIcons) {
            if (!ConfigManager.get().render.lightweightHud) {
                renderBossIcon(context, icon, currentX, iconY);
            }
            currentX += ICON_SIZE;

            if (showName || compactMarkerWidth > 0 || showLevel || showTimer) {
                currentX += ICON_TEXT_GAP;
            }
        }

        if (!showName && compactMarkerWidth > 0) {
            if (!compactMarkerText.isEmpty()) {
                context.text(mc.font, row.compactComponent, currentX, y, nameColor);
            }
            currentX += compactMarkerWidth;

            if (showLevel || showTimer) {
                currentX += COLUMN_GAP;
            }
        }

        if (showName) {
            context.text(mc.font, row.nameComponent, currentX, y, nameColor);

            if (fullAlignment) {
                currentX += maxNameWidth;
            } else {
                currentX += row.nameWidth;
            }

            if (showLevel || showTimer) {
                currentX += COLUMN_GAP;
            }
        }

        if (showLevel) {
            context.text(mc.font, levelText, currentX, y, levelColor);
            currentX += fullAlignment ? maxLevelWidth : row.levelWidth;

            if (showTimer) {
                currentX += COLUMN_GAP;
            }
        }

        if (showTimer) {
            context.text(mc.font, timerText, currentX, y, timerColor);
        }
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth() * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight() * getScale());
    }

    /** Build the visible-boss list + its measured dimensions at most once per HUD frame. */
    private void ensureFrameCache() {
        long frame = HudFrameClock.current();
        if (frame == cachedFrameId && cachedVisibleBosses != null) {
            return;
        }
        cachedFrameTime = System.currentTimeMillis();
        List<BossInfo> bosses = computeVisibleBosses();
        cachedVisibleBosses = bosses;
        // Keep only visible identities: packet replacements and world changes cannot grow these caches.
        rowCache.keySet().retainAll(bosses);
        resolvedIcons.keySet().retainAll(bosses);
        for (BossInfo boss : bosses) refreshRow(boss);
        cachedUnscaledWidth = computeUnscaledWidth(bosses);
        cachedUnscaledHeight = computeUnscaledHeight(bosses);
        cachedFrameId = frame;
    }

    private void refreshRow(BossInfo boss) {
        RowText row = rowCache.computeIfAbsent(boss, RowText::new);
        String prefix = bossStatusPrefix(boss);
        String suffix = bossCollectibleSuffix(boss);
        boolean changed = !Objects.equals(row.prefix, prefix) || !Objects.equals(row.suffix, suffix);
        row.prefix = prefix; row.suffix = suffix;
        long remaining = getDisplayRespawnAt(boss) - cachedFrameTime;
        boolean spawned = remaining < 0L;
        long seconds = Math.abs(remaining) / 1000L;
        if (row.timer == null || row.seconds != seconds || row.spawned != spawned) {
            row.seconds = seconds;
            row.spawned = spawned;
            row.timer = spawned ? formatMillisSigned(getDisplayRespawnAt(boss), cachedFrameTime)
                    : formatMillis(getDisplayRespawnAt(boss), cachedFrameTime);
        }
        var appearance = BossTimerRowFormatter.appearance(ConfigManager.get().bossWidget,
                row.spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY);
        if (changed || !appearance.equals(row.appearance)) updateLabel(row, boss.getName(), appearance);
        // Measure against the current font each frame so resource-pack reloads remain correct.
        var font = Minecraft.getInstance().font;
        row.nameWidth = font.width(row.name);
        row.levelWidth = font.width(row.level);
        row.compactWidth = font.width(row.compact);
        row.timerWidth = font.width(row.timer);
    }

    @Override
    public boolean isVisible() {
        return isInLayout() && (!getVisibleBosses().isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Boss Timers";
    }

    public boolean handleChatClick(double mouseX, double mouseY) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
            return false;
        }

        int row = bossRowAt(mouseX, mouseY);
        if (row < 0) {
            return false;
        }

        List<BossInfo> visibleBosses = getVisibleBosses();
        if (row >= visibleBosses.size()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        int level = visibleBosses.get(row).getLevel();
        minecraft.player.connection.sendCommand("boss " + level);
        return true;
    }

    /** Index of the boss row under a screen-space point (mapped through the widget's start + scale), or
     *  -1 if the point isn't over a row. Shared by the chat-click teleport and the hover underline so
     *  both agree on which row is targeted. */
    private int bossRowAt(double mouseX, double mouseY) {
        List<BossInfo> bosses = getVisibleBosses();
        if (bosses.isEmpty()) {
            return -1;
        }

        float sc = getScale();
        if (sc <= 0.0f) {
            return -1;
        }

        double localX = (mouseX - getStartX()) / sc;
        double localY = (mouseY - getStartY()) / sc;
        if (localX < 0 || localX > getUnscaledWidth()) {
            return -1;
        }

        int lineStep = getLineStep(Minecraft.getInstance());
        int contentY = PADDING_Y;
        if (showTitle()) {
            contentY += Minecraft.getInstance().font.lineHeight + LINE_GAP + 2;
        }

        for (int i = 0; i < bosses.size(); i++) {
            int rowTop = contentY + i * lineStep;
            if (localY >= rowTop && localY < rowTop + lineStep) {
                return i;
            }
        }
        return -1;
    }

    private int getUnscaledWidth() {
        ensureFrameCache();
        return cachedUnscaledWidth;
    }

    private int computeUnscaledWidth(List<BossInfo> visibleBosses) {
        Minecraft mc = Minecraft.getInstance();

        boolean showName = ConfigManager.get().bossWidget.showName;
        boolean showIcons = ConfigManager.get().bossWidget.showIcons;
        boolean showLevel = ConfigManager.get().bossWidget.showLevel;
        boolean showTimer = ConfigManager.get().bossWidget.showTimer;
        boolean fullAlignment = ConfigManager.get().bossWidget.fullAligment;

        if (visibleBosses.isEmpty()) {
            return EMPTY_WIDTH;
        }

        int maxNameWidth = fullAlignment && showName ? getMaxNameWidth(visibleBosses, mc) : 0;
        int maxLevelWidth = fullAlignment && showLevel ? getMaxLevelWidth(visibleBosses, mc) : 0;
        int compactMarkerWidth = !showName ? getMaxCompactMarkerWidth(visibleBosses, mc) : 0;
        int maxWidth = showTitle() ? mc.font.width("Boss Timers") : 0;

        for (BossInfo boss : visibleBosses) {
            RowText row = rowCache.get(boss);

            int rowWidth = 0;

            if (showIcons) {
                rowWidth += ICON_SIZE;

                if (showName || compactMarkerWidth > 0 || showLevel || showTimer) {
                    rowWidth += ICON_TEXT_GAP;
                }
            }

            if (!showName && compactMarkerWidth > 0) {
                rowWidth += compactMarkerWidth;

                if (showLevel || showTimer) {
                    rowWidth += COLUMN_GAP;
                }
            }

            if (showName) {
                rowWidth += fullAlignment ? maxNameWidth : row.nameWidth;

                if (showLevel || showTimer) {
                    rowWidth += COLUMN_GAP;
                }
            }

            if (showLevel) {
                rowWidth += fullAlignment ? maxLevelWidth : row.levelWidth;

                if (showTimer) {
                    rowWidth += COLUMN_GAP;
                }
            }

            if (showTimer) {
                rowWidth += row.timerWidth;
            }

            maxWidth = Math.max(maxWidth, rowWidth);
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        ensureFrameCache();
        return cachedUnscaledHeight;
    }

    private int computeUnscaledHeight(List<BossInfo> visibleBosses) {
        int rendered = visibleBosses.size();
        if (rendered == 0) {
            return EMPTY_HEIGHT;
        }

        int lineStep = getLineStep(Minecraft.getInstance());
        int titleBlock = showTitle()
                ? Minecraft.getInstance().font.lineHeight + LINE_GAP + 2
                : 0;
        return PADDING_Y * 2 + 2 + titleBlock + rendered * lineStep;
    }

    private List<BossInfo> getVisibleBosses() {
        ensureFrameCache();
        return cachedVisibleBosses;
    }

    private List<BossInfo> computeVisibleBosses() {
        int maxBosses = ConfigManager.get().bossWidget.maxBosses;
        int playerLevel = DiamondWorldProtocolClient.getCurrentLevel();
        BossTimerSourceMode sourceMode = ConfigManager.get().bossWidget.sourceMode;

        List<BossInfo> result = new ArrayList<>();

        Iterable<BossInfo> source = switch (sourceMode) {
            case WORLD_ONLY -> repository.getAllWorld();
            case PROTOCOL_ONLY -> repository.getAllProtocol();
            case PROTOCOL_PREFERRED -> repository.getAllMerged();
        };

        for (BossInfo boss : source) {
            if (!BossTimerFilter.accepts(boss, playerLevel)) {
                continue;
            }

            if (isExpiredSpawned(boss)) {
                continue;
            }

            result.add(boss);
        }

        // Mythical raid acceleration can move a boss ahead of an earlier raw timer.
        // Apply the display limit only after sorting by the time shown to the player.
        sortAndLimit(result, this::getDisplayRespawnAt, maxBosses);
        return result;
    }

    static void sortAndLimit(List<BossInfo> bosses, ToLongFunction<BossInfo> displayRespawnAt, int limit) {
        bosses.sort(Comparator.comparingLong(displayRespawnAt));
        int count = Math.max(0, limit);
        if (bosses.size() > count) {
            bosses.subList(count, bosses.size()).clear();
        }
    }

    private int getMaxNameWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, rowCache.get(boss).nameWidth);
        }

        return max;
    }

    private int getMaxCompactMarkerWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, rowCache.get(boss).compactWidth);
        }

        return max;
    }

    private int getMaxLevelWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, rowCache.get(boss).levelWidth);
        }

        return max;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private int getLineStep(Minecraft minecraft) {
        if (ConfigManager.get().bossWidget.showIcons) {
            return Math.max(minecraft.font.lineHeight, ICON_SIZE);
        }
        return minecraft.font.lineHeight + LINE_GAP;
    }

    private static void renderBossIcon(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
        float iconScale = ICON_SIZE / (float) NATIVE_ICON_SIZE;
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(iconScale, iconScale);
        WidgetUtils.drawItemIcon(context, stack, 0, 0);
        context.pose().popMatrix();
    }

    private String bossStatusPrefix(BossInfo boss) {
        boolean captured = ConfigManager.get().bossWidget.showCapture && DiamondWorldProtocolClient.getCapturedBossLevels().contains(boss.getLevel());
        boolean mythicalRaid = ConfigManager.get().bossWidget.showMythical && DiamondWorldProtocolClient.isMythicalEventActive()
                && DiamondWorldProtocolClient.isRaidBossLevel(boss.getLevel());
        // Capture and the mythical event are independent statuses; show both when applicable.
        if (captured) return mythicalRaid ? CAPTURE_MARKER + RAID_MARKER : CAPTURE_MARKER;
        return mythicalRaid ? RAID_MARKER : "";
    }

    private String bossCollectibleSuffix(BossInfo boss) {
        if (!ConfigManager.get().bossWidget.showCollectibles) {
            return "";
        }

        Boolean collected = DiamondWorldProtocolClient.hasBossCollectibleByLevel(boss.getLevel());
        if (collected == null) {
            return "";
        }
        return collected ? " ✔" : " ◇";
    }

    private boolean isExpiredSpawned(BossInfo boss) {
        if (ConfigManager.get().bossWidget.showSpawnedUntilKilled) {
            return false; // keep the spawned boss until it respawns (a new future timer arrives)
        }
        long limitMs = Math.max(0, ConfigManager.get().bossWidget.postSpawnShowSeconds) * 1000L;
        return getDisplayRespawnAt(boss) < cachedFrameTime - limitMs;
    }

    private long getDisplayRespawnAt(BossInfo boss) {
        // The protocol/hologram handlers already adjust durations at receipt. Reapplying
        // the factor here would move the deadline on every frame and accelerate twice.
        return boss.getRespawnAt();
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        if (showTitle()) context.text(mc.font, "Boss Timers", PADDING_X, 6, BossTimerRowFormatter.color(ConfigManager.get().bossWidget, BossTimerElement.TITLE, WidgetTheme.TITLE));
        context.text(mc.font, "No active timers", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popMatrix();
    }

    private static boolean showTitle() { return ConfigManager.get().bossWidget.showTitle && WidgetUtils.showWidgetTitles(); }

    private static void updateLabel(RowText row, String name, BossTimerRowFormatter.Appearance appearance) {
        var config = ConfigManager.get().bossWidget;
        boolean capture = row.prefix.contains("✗"), mythical = row.prefix.contains("✦");
        Boolean collected = row.suffix.isEmpty() ? null : row.suffix.contains("✔");
        row.nameComponent = BossTimerRowFormatter.label(config, name, capture, mythical, collected, true, appearance);
        row.compactComponent = BossTimerRowFormatter.label(config, name, capture, mythical, collected, false, appearance);
        row.name = row.nameComponent.getString(); row.compact = row.compactComponent.getString(); row.appearance = appearance;
    }

    /** Fixed examples rendered by the same row renderer, independent of packets/world state. */
    public static final class Preview {
        private final List<BossInfo> bosses = List.of(new BossInfo("Бессмертный Легион", 0, 130),
                new BossInfo("Цербер", 0, 365), new BossInfo("Демонический рыцарь", 0, 480));
        private final List<RowText> rows = bosses.stream().map(RowText::new).toList();
        private final List<ItemStack> icons = List.of(new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.CLOCK));
        public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
            var mc = Minecraft.getInstance(); var config = ConfigManager.get().bossWidget;
            int nameWidth = 0, levelWidth = 0, compactWidth = 0;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i); row.spawned = i == 1; row.timer = i == 0 ? "12:34" : i == 1 ? "-00:15" : "24:08";
                String prefix = i == 0 ? (config.showCapture ? "✗ " : "") + (config.showMythical ? "✦ " : "")
                        : i == 1 && config.showCapture ? "✗ " : "";
                String suffix = config.showCollectibles && i < 2 ? i == 0 ? " ◇" : " ✔" : "";
                var appearance = BossTimerRowFormatter.appearance(config, row.spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY);
                boolean changed = !prefix.equals(row.prefix) || !suffix.equals(row.suffix) || !appearance.equals(row.appearance);
                row.prefix = prefix; row.suffix = suffix;
                if (changed) updateLabel(row, bosses.get(i).getName(), appearance);
                row.nameWidth = mc.font.width(row.name); row.levelWidth = mc.font.width(row.level);
                row.compactWidth = mc.font.width(row.compact); row.timerWidth = mc.font.width(row.timer);
                nameWidth = Math.max(nameWidth, row.nameWidth); levelWidth = Math.max(levelWidth, row.levelWidth);
                compactWidth = Math.max(compactWidth, row.compactWidth);
            }
            graphics.enableScissor(x, y, x + width, y + height);
            graphics.pose().pushMatrix(); graphics.pose().translate(x - PADDING_X, y);
            try {
                int top = 3;
                if (showTitle()) { graphics.text(mc.font, "Boss Timers", PADDING_X, top, BossTimerRowFormatter.color(config, BossTimerElement.TITLE, WidgetTheme.TITLE)); top += mc.font.lineHeight + 3; }
                if (!config.showName && !config.showIcons && !config.showLevel && !config.showTimer && !config.showCapture && !config.showMythical && !config.showCollectibles) {
                    graphics.text(mc.font, "Enable at least one row element", PADDING_X, top, WidgetTheme.STATUS_WARNING); return;
                }
                int step = config.showIcons ? Math.max(mc.font.lineHeight, ICON_SIZE) : mc.font.lineHeight + LINE_GAP;
                for (int i = 0; i < rows.size(); i++) renderRow(graphics, mc, rows.get(i), icons.get(i), top + i * step,
                        config.fullAligment && config.showName ? nameWidth : 0, config.fullAligment && config.showLevel ? levelWidth : 0,
                        config.showName ? 0 : compactWidth);
            } finally { graphics.pose().popMatrix(); graphics.disableScissor(); }
        }
        public String text() {
            var config = ConfigManager.get().bossWidget;
            var lines = new ArrayList<String>();
            if (showTitle()) lines.add("Boss Timers");
            for (int i = 0; i < bosses.size(); i++) {
                var appearance = BossTimerRowFormatter.appearance(config, WidgetTheme.TEXT_PRIMARY);
                String text = BossTimerRowFormatter.label(config, bosses.get(i).getName(), i < 2, i == 0,
                        i == 0 ? Boolean.FALSE : i == 1 ? Boolean.TRUE : null, config.showName, appearance).getString();
                if (config.showLevel) text += (text.isEmpty() ? "" : " ") + "[" + bosses.get(i).getLevel() + "]";
                if (config.showTimer) text += (text.isEmpty() ? "" : " ") + (i == 0 ? "12:34" : i == 1 ? "-00:15" : "24:08");
                lines.add(text);
            }
            return String.join("\n", lines);
        }
    }

    private ItemStack getBossIcon(BossInfo boss) {
        BossIconInfo protocolIcon = DiamondWorldProtocolClient.getBossIconByLevel(boss.getLevel());
        long revision = repository.getIconRevision();
        ResolvedIcon cached = resolvedIcons.get(boss);
        if (cached != null && cached.revision() == revision && Objects.equals(cached.protocol(), protocolIcon)) {
            return cached.stack();
        }
        ItemStack stack;
        if (protocolIcon != null) {
            stack = getOrCreateCachedIcon(protocolIcon);
        } else {
            stack = repository.getDiscoveredIcon(boss);
            if (stack == null || stack.isEmpty()) {
                BossIconInfo icon = BossStaticIconLookup.find(boss);
                stack = icon == null ? new ItemStack(Items.CLOCK) : getOrCreateCachedIcon(icon);
            }
        }
        resolvedIcons.put(boss, new ResolvedIcon(protocolIcon, revision, stack));
        return stack;
    }

    private ItemStack getOrCreateCachedIcon(BossIconInfo icon) {
        ItemStack cached = iconCache.get(icon);
        if (cached != null) return cached;
        if (iconCache.size() >= 64) iconCache.clear();
        ItemStack created = createBossIcon(icon);
        iconCache.put(icon, created);
        return created;
    }

    private ItemStack createBossIcon(BossIconInfo icon) {
        Identifier location = resolveItemLocation(icon.material());
        if (location == null) {
            return new ItemStack(Items.CLOCK);
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(location));
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.CLOCK);
        }

        if (icon.customModelData() > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) icon.customModelData()), List.of(), List.of(), List.of()));
        }

        return stack;
    }

    private Identifier resolveItemLocation(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }

        Identifier direct = Identifier.tryParse(material);
        if (direct != null) {
            return direct;
        }

        String normalized = material.trim().toLowerCase().replace(' ', '_');
        return Identifier.withDefaultNamespace(normalized);
    }
}
