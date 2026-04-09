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

public class BossHudWidget extends AbstractWidget {
    private static final double MYTHICAL_RAID_SPEED_MULTIPLIER = 1.52D;
    private static final long SPAWNED_VISIBLE_MS = 30_000L;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int COLUMN_GAP = 6;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final int ICON_Y_OFFSET = -3;
    private static final int EMPTY_WIDTH = 110;
    private static final int EMPTY_HEIGHT = 28;

    private final BossRepository repository;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

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
        int lineStep = mc.font.lineHeight + LINE_GAP;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, getUnscaledWidth(), getUnscaledHeight(), WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, getUnscaledWidth(), 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int contentY = PADDING_Y;
        context.drawString(mc.font, "Boss Timers", PADDING_X, contentY, WidgetTheme.TITLE);
        contentY += lineStep + 2;

        for (int line = 0; line < visibleBosses.size(); line++) {
            BossInfo boss = visibleBosses.get(line);
            int y = contentY + line * lineStep;
            boolean spawned = isSpawned(boss);
            long displayRespawnAt = getDisplayRespawnAt(boss);

            String nameText = boss.getName();
            String levelText = "[" + boss.getLevel() + "]";
            String timerText = spawned ? formatMillisSigned(displayRespawnAt) : formatMillis(displayRespawnAt);

            int currentX = PADDING_X;
            int nameColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY;
            int levelColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;
            int timerColor = spawned ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SOFT;
            int iconY = y + Math.max(0, (mc.font.lineHeight - ICON_SIZE) / 2) + ICON_Y_OFFSET;

            if (showIcons) {
                context.renderItem(getBossIcon(boss), currentX, iconY);
                currentX += ICON_SIZE;

                if (showName || showLevel || showTimer) {
                    currentX += ICON_TEXT_GAP;
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
                if (!showName && !showLevel) {
                    currentX = PADDING_X;
                }

                context.drawString(mc.font, timerText, currentX, y, timerColor);
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

        if (!isHovered(mouseX, mouseY)) {
            return false;
        }

        List<BossInfo> visibleBosses = getVisibleBosses();
        if (visibleBosses.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        int level = visibleBosses.getFirst().getLevel();
        minecraft.player.connection.sendCommand("boss " + level);
        return true;
    }

    private int getUnscaledWidth() {
        Minecraft mc = Minecraft.getInstance();

        boolean showName = ConfigManager.get().bossWidget.showName;
        boolean showIcons = ConfigManager.get().bossWidget.showIcons;
        boolean showLevel = ConfigManager.get().bossWidget.showLevel;
        boolean showTimer = ConfigManager.get().bossWidget.showTimer;
        boolean fullAlignment = ConfigManager.get().bossWidget.fullAligment;

        List<BossInfo> visibleBosses = getVisibleBosses();
        if (visibleBosses.isEmpty()) {
            return EMPTY_WIDTH;
        }

        int maxNameWidth = fullAlignment && showName ? getMaxNameWidth(visibleBosses, mc) : 0;
        int maxLevelWidth = fullAlignment && showLevel ? getMaxLevelWidth(visibleBosses, mc) : 0;
        int maxWidth = mc.font.width("Boss Timers");

        for (BossInfo boss : visibleBosses) {
            boolean spawned = isSpawned(boss);
            long displayRespawnAt = getDisplayRespawnAt(boss);
            String nameText = boss.getName();
            String levelText = "[" + boss.getLevel() + "]";
            String timerText = spawned ? formatMillisSigned(displayRespawnAt) : formatMillis(displayRespawnAt);

            int rowWidth = 0;

            if (showIcons) {
                rowWidth += ICON_SIZE;

                if (showName || showLevel || showTimer) {
                    rowWidth += ICON_TEXT_GAP;
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
        int rendered = getVisibleBosses().size();
        if (rendered == 0) {
            return EMPTY_HEIGHT;
        }

        int lineStep = Minecraft.getInstance().font.lineHeight + LINE_GAP;
        return PADDING_Y * 2 + 2 + lineStep + 2 + rendered * lineStep;
    }

    private List<BossInfo> getVisibleBosses() {
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
            max = Math.max(max, mc.font.width(boss.getName()));
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

    private boolean isSpawned(BossInfo boss) {
        return getDisplayRespawnAt(boss) < System.currentTimeMillis();
    }

    private boolean isExpiredSpawned(BossInfo boss) {
        long now = System.currentTimeMillis();
        return getDisplayRespawnAt(boss) < now - SPAWNED_VISIBLE_MS;
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

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, "Boss Timers", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active timers", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private ItemStack getBossIcon(BossInfo boss) {
        ItemStack discovered = repository.getDiscoveredIcon(boss);
        if (discovered != null && !discovered.isEmpty()) {
            return discovered;
        }

        var icon = BossStaticIconLookup.find(boss);
        if (icon == null) {
            return new ItemStack(Items.CLOCK);
        }

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

