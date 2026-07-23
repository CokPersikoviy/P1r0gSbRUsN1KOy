package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.wilyfox.utils.Formatting.formatMillis;
import static ru.wilyfox.utils.Formatting.formatMillisSigned;
import static ru.wilyfox.utils.Formatting.stripMinecraftFormatting;

public class BossHudWidget extends AbstractWidget {
    private static final double MYTHICAL_RAID_SPEED_MULTIPLIER = 1.52D;
    /** Prefixed to raid-boss names in the timer during a mythical event. */
    private static final String RAID_MARKER = "✦ ";
    /** Prefixed to bosses the clan currently holds (captured / location busy). */
    private static final String CAPTURE_MARKER = "✗ ";
    /** Event boss (not a real respawn timer) — never listed in the boss timer. */
    private static final String EXCLUDED_BOSS = "древний страж";
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int ICON_ROW_GAP = 2;
    private static final int COLUMN_GAP = 6;
    private static final int NATIVE_ICON_SIZE = 16;
    private static final int ICON_SIZE = 14;
    private static final int ICON_TEXT_GAP = 4;
    private static final int ICON_Y_OFFSET = -3;
    private static final int EMPTY_WIDTH = 110;
    private static final int EMPTY_HEIGHT = 28;

    private final BossRepository repository;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    // Per-frame cache: the visible-boss list + its measured dimensions were rebuilt on every call, and
    // isVisible()/getWidth()/getHeight()/render() ask for them several times per frame (and the layout
    // change-detector calls getWidth/getHeight again). In a boss fight that was ~1 ms/frame of pure
    // recompute + the biggest frame spikes. Compute once per HUD frame (keyed on HudFrameClock), reuse.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<BossInfo> cachedVisibleBosses;
    private int cachedUnscaledWidth;
    private int cachedUnscaledHeight;

    public BossHudWidget(int x, int y, HudLayer layer, BossRepository repository) {
        super(x, y, layer);
        this.repository = repository;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
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

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, getUnscaledWidth(), getUnscaledHeight());

        int contentY = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Boss Timers", PADDING_X, contentY, WidgetTheme.TITLE);
            contentY += mc.font.lineHeight + LINE_GAP + 2;
        }

        for (int line = 0; line < visibleBosses.size(); line++) {
            BossInfo boss = visibleBosses.get(line);
            int y = contentY + line * lineStep;
            boolean spawned = isSpawned(boss);
            long displayRespawnAt = getDisplayRespawnAt(boss);

            String nameText = bossDisplayName(boss);
            String compactMarkerText = showName ? "" : bossCompactMarkers(boss);
            String levelText = "[" + boss.getLevel() + "]";
            String timerText = spawned ? formatMillisSigned(displayRespawnAt) : formatMillis(displayRespawnAt);

            int currentX = PADDING_X;
            int nameColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY;
            int levelColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;
            int timerColor = spawned ? WidgetTheme.HARD_ACCENT : WidgetTheme.TEXT_SOFT;
            int iconY = y + Math.max(0, (mc.font.lineHeight - ICON_SIZE) / 2) + ICON_Y_OFFSET;

            if (showIcons) {
                renderBossIcon(context, getBossIcon(boss), currentX, iconY);
                currentX += ICON_SIZE;

                if (showName || showLevel || showTimer) {
                    currentX += ICON_TEXT_GAP;
                }
            }

            if (!showName && compactMarkerWidth > 0) {
                if (!compactMarkerText.isEmpty()) {
                    context.drawString(mc.font, compactMarkerText, currentX, y, nameColor);
                }
                currentX += compactMarkerWidth;

                if (showLevel || showTimer) {
                    currentX += COLUMN_GAP;
                }
            }

            if (showName) {
                context.drawString(mc.font, nameText, currentX, y, nameColor);

                if (fullAlignment) {
                    currentX += maxNameWidth;
                } else {
                    currentX += mc.font.width(nameText);
                }

                if (showLevel || showTimer) {
                    currentX += COLUMN_GAP;
                }
            }

            if (showLevel) {
                context.drawString(mc.font, levelText, currentX, y, levelColor);
                currentX += fullAlignment ? maxLevelWidth : mc.font.width(levelText);

                if (showTimer) {
                    currentX += COLUMN_GAP;
                }
            }

            if (showTimer) {
                context.drawString(mc.font, timerText, currentX, y, timerColor);
            }
        }

        // Hover feedback for the chat-click teleport: a 1px accent underline under the row the mouse is
        // over — two-thirds of the panel width, centred — shown only while chat is open (the click context).
        if (mc.screen instanceof ChatScreen) {
            int hoverRow = bossRowAt(MouseUtils.getMouseX(), MouseUtils.getMouseY());
            if (hoverRow >= 0 && hoverRow < visibleBosses.size()) {
                int contentWidth = getUnscaledWidth() - PADDING_X * 2;
                int underlineWidth = Math.max(1, contentWidth * 2 / 3);
                int underlineX = PADDING_X + (contentWidth - underlineWidth) / 2;
                int underlineY = contentY + hoverRow * lineStep + mc.font.lineHeight;
                context.fill(underlineX, underlineY, underlineX + underlineWidth, underlineY + 1, WidgetTheme.ACCENT_LINE);
            }
        }

        context.pose().popPose();
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
        List<BossInfo> bosses = computeVisibleBosses();
        cachedVisibleBosses = bosses;
        cachedUnscaledWidth = computeUnscaledWidth(bosses);
        cachedUnscaledHeight = computeUnscaledHeight(bosses);
        cachedFrameId = frame;
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().bossWidget.active && (!getVisibleBosses().isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Boss Timers";
    }

    public boolean handleChatClick(double mouseX, double mouseY) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen)) {
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
        if (WidgetUtils.showWidgetTitles()) {
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
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Boss Timers") : 0;

        for (BossInfo boss : visibleBosses) {
            boolean spawned = isSpawned(boss);
            long displayRespawnAt = getDisplayRespawnAt(boss);
            String nameText = bossDisplayName(boss);
            String levelText = "[" + boss.getLevel() + "]";
            String timerText = spawned ? formatMillisSigned(displayRespawnAt) : formatMillis(displayRespawnAt);

            int rowWidth = 0;

            if (showIcons) {
                rowWidth += ICON_SIZE;

                if (showName || showLevel || showTimer) {
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
                rowWidth += fullAlignment ? maxNameWidth : mc.font.width(nameText);

                if (showLevel || showTimer) {
                    rowWidth += COLUMN_GAP;
                }
            }

            if (showLevel) {
                rowWidth += fullAlignment ? maxLevelWidth : mc.font.width(levelText);

                if (showTimer) {
                    rowWidth += COLUMN_GAP;
                }
            }

            if (showTimer) {
                rowWidth += mc.font.width(timerText);
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
        int titleBlock = WidgetUtils.showWidgetTitles()
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
        int minLevel = ConfigManager.get().bossWidget.minLevel;
        int maxLevel = ConfigManager.get().bossWidget.maxLevel;
        BossTimerSourceMode sourceMode = ConfigManager.get().bossWidget.sourceMode;

        List<BossInfo> result = new ArrayList<>();

        Iterable<BossInfo> source = switch (sourceMode) {
            case WORLD_ONLY -> repository.getAllWorld();
            case PROTOCOL_ONLY -> repository.getAllProtocol();
            case PROTOCOL_PREFERRED -> repository.getAllMerged();
        };

        for (BossInfo boss : source) {
            if (result.size() >= maxBosses) {
                break;
            }

            if (isExcludedBoss(boss)) {
                continue;
            }

            if (boss.getLevel() < minLevel || boss.getLevel() > maxLevel) {
                continue;
            }

            if (isExpiredSpawned(boss)) {
                continue;
            }

            result.add(boss);
        }

        result.sort(Comparator.comparingLong(this::getDisplayRespawnAt));
        return result;
    }

    private int getMaxNameWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, mc.font.width(bossDisplayName(boss)));
        }

        return max;
    }

    private int getMaxCompactMarkerWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, mc.font.width(bossCompactMarkers(boss)));
        }

        return max;
    }

    private int getMaxLevelWidth(List<BossInfo> bosses, Minecraft mc) {
        int max = 0;

        for (BossInfo boss : bosses) {
            max = Math.max(max, mc.font.width("[" + boss.getLevel() + "]"));
        }

        return max;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private int getLineStep(Minecraft minecraft) {
        if (ConfigManager.get().bossWidget.showIcons) {
            return Math.max(minecraft.font.lineHeight, ICON_SIZE) + ICON_ROW_GAP;
        }
        return minecraft.font.lineHeight + LINE_GAP;
    }

    private void renderBossIcon(GuiGraphics context, ItemStack stack, int x, int y) {
        float iconScale = ICON_SIZE / (float) NATIVE_ICON_SIZE;
        context.pose().pushPose();
        context.pose().translate(x, y, 0.0F);
        context.pose().scale(iconScale, iconScale, 1.0F);
        context.renderItem(stack, 0, 0);
        context.pose().popPose();
    }

    private boolean isSpawned(BossInfo boss) {
        return getDisplayRespawnAt(boss) < System.currentTimeMillis();
    }

    private static boolean isExcludedBoss(BossInfo boss) {
        String name = boss.getName();
        return name != null
                && stripMinecraftFormatting(name).toLowerCase(java.util.Locale.ROOT).contains(EXCLUDED_BOSS);
    }

    /** Boss name shown in the timer — captured bosses get a cross, raid bosses a star (mythic event). */
    private String bossDisplayName(BossInfo boss) {
        return bossStatusPrefix(boss) + boss.getName() + bossCollectibleSuffix(boss);
    }

    private String bossCompactMarkers(BossInfo boss) {
        String status = bossStatusPrefix(boss).trim();
        String collectible = bossCollectibleSuffix(boss).trim();
        if (status.isEmpty()) {
            return collectible;
        }
        if (collectible.isEmpty()) {
            return status;
        }
        return status + " " + collectible;
    }

    private String bossStatusPrefix(BossInfo boss) {
        // Captured (clan holds it / location busy) takes priority over the raid-event star.
        if (DiamondWorldProtocolClient.getCapturedBossLevels().contains(boss.getLevel())) {
            return CAPTURE_MARKER;
        }
        if (DiamondWorldProtocolClient.isMythicalEventActive()
                && DiamondWorldProtocolClient.isRaidBossLevel(boss.getLevel())) {
            return RAID_MARKER;
        }
        return "";
    }

    private String bossCollectibleSuffix(BossInfo boss) {
        if (!ConfigManager.get().bossWidget.showCollectibles) {
            return "";
        }

        Boolean collected = DiamondWorldProtocolClient.hasBossCollectibleByLevel(boss.getLevel());
        return Boolean.TRUE.equals(collected) ? " ✔" : "";
    }

    private boolean isExpiredSpawned(BossInfo boss) {
        if (ConfigManager.get().bossWidget.showSpawnedUntilKilled) {
            return false; // keep the spawned boss until it respawns (a new future timer arrives)
        }
        long limitMs = Math.max(0, ConfigManager.get().bossWidget.postSpawnShowSeconds) * 1000L;
        return getDisplayRespawnAt(boss) < System.currentTimeMillis() - limitMs;
    }

    private long getDisplayRespawnAt(BossInfo boss) {
        long respawnAt = boss.getRespawnAt();
        if (!DiamondWorldProtocolClient.isMythicalEventActive()) {
            return respawnAt;
        }

        if (!DiamondWorldProtocolClient.isRaidBossLevel(boss.getLevel())) {
            return respawnAt;
        }

        long now = System.currentTimeMillis();
        long remaining = respawnAt - now;
        if (remaining <= 0L) {
            return respawnAt;
        }

        long acceleratedRemaining = Math.max(0L, Math.round(remaining / MYTHICAL_RAID_SPEED_MULTIPLIER));
        return now + acceleratedRemaining;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Boss Timers", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active timers", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private ItemStack getBossIcon(BossInfo boss) {
        BossIconInfo protocolIcon = DiamondWorldProtocolClient.getBossIconByLevel(boss.getLevel());
        if (protocolIcon != null) {
            return getOrCreateCachedIcon(protocolIcon);
        }

        ItemStack discovered = repository.getDiscoveredIcon(boss);
        if (discovered != null && !discovered.isEmpty()) {
            return discovered;
        }

        BossIconInfo icon = BossStaticIconLookup.find(boss);
        if (icon == null) {
            return new ItemStack(Items.CLOCK);
        }

        return getOrCreateCachedIcon(icon);
    }

    private ItemStack getOrCreateCachedIcon(BossIconInfo icon) {
        String cacheKey = icon.material() + "|" + icon.customModelData();
        ItemStack cached = iconCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ItemStack created = createBossIcon(icon);
        iconCache.put(cacheKey, created);
        return created;
    }

    private ItemStack createBossIcon(BossIconInfo icon) {
        ResourceLocation location = resolveItemLocation(icon.material());
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

    private ResourceLocation resolveItemLocation(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }

        ResourceLocation direct = ResourceLocation.tryParse(material);
        if (direct != null) {
            return direct;
        }

        String normalized = material.trim().toLowerCase().replace(' ', '_');
        return ResourceLocation.withDefaultNamespace(normalized);
    }
}
