package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.discord.DiscordRpcService;
import ru.wilyfox.client.hud.config.AutoMessageEntryConfig;
import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.ThemePreset;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.quickaccess.QuickAccessScreen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HudSettingsPanel {
    private int x;
    private int y;

    private final int width = 420;
    private final int height = 280;

    private final int sidebarWidth = 120;
    private final int headerHeight = 24;
    private final int contentPadding = 10;
    private final int rowHeight = 22;
    private final int rowSpacing = 5;

    private final Map<SettingsCategory, List<SettingsComponent>> componentsByCategory = new EnumMap<>(SettingsCategory.class);
    private SettingsCategory activeCategory = SettingsCategory.BOSS_TIMERS;

    private boolean initialized = false;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int categoryScrollOffset = 0;
    private int maxCategoryScroll = 0;

    private boolean scrollbarDragging = false;
    private int scrollbarDragOffset = 0;
    private boolean categoryScrollbarDragging = false;
    private int categoryScrollbarDragOffset = 0;
    private final List<Boolean> autoMessageSlotExpanded = new ArrayList<>();

    // Upper bound for the boss min/max level steppers: the shipped ceiling, auto-extended once the
    // server reports higher-level bosses so the sliders can always reach the newest content.
    private static int bossLevelCeiling() {
        return Math.max(BossWidgetConfig.MAX_LEVEL_CEILING, DiamondWorldProtocolClient.getHighestKnownBossLevel());
    }

    public void render(GuiGraphics context, double mouseX, double mouseY) {
        ensureInitialized();

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;

        renderPanelBackground(context);
        renderHeader(context);
        renderSidebar(context, mouseX, mouseY);
        renderContent(context, mouseX, mouseY);
    }

    private void renderPanelBackground(GuiGraphics context) {
        // The whole window is one frosted-glass panel (Frost surface language) — blur + rounded + the
        // theme accent. The sidebar/content areas below add their own tint so the busy text stays legible.
        HudSurface.drawPanel(context, x, y, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer());
    }

    private void renderHeader(GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();

        context.drawString(mc.font, "FrogHelper", x + 10, y + 8, WidgetTheme.TITLE);
        context.drawString(mc.font, "Settings", x + 68, y + 8, WidgetTheme.TEXT_SECONDARY);
    }

    private void renderSidebar(GuiGraphics context, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        int sidebarX = x + 8;
        int sidebarY = y + headerHeight + 8;
        int sidebarHeight = height - headerHeight - 16;
        int listX = sidebarX + 4;
        int listY = sidebarY + 4;
        int listWidth = sidebarWidth - 12;
        int listHeight = sidebarHeight - 8;

        HudSurface.fillRounded(context, sidebarX, sidebarY, sidebarWidth, sidebarHeight, 4, WidgetTheme.PANEL_BG_SOFT);

        updateCategoryScrollBounds(sidebarHeight);

        int tabY = sidebarY + 6 - categoryScrollOffset;
        context.enableScissor(
                listX,
                listY,
                listX + listWidth,
                listY + listHeight
        );

        for (SettingsCategory category : SettingsCategory.values()) {
            boolean hovered = mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4
                    && mouseY >= tabY && mouseY <= tabY + 20;
            boolean active = category == activeCategory;

            int bg;
            int textColor;

            if (active) {
                bg = WidgetTheme.PANEL_BG;
                textColor = WidgetTheme.TITLE;
            } else if (hovered) {
                bg = WidgetTheme.PANEL_BG_SOFT;
                textColor = WidgetTheme.TEXT_SOFT;
            } else {
                bg = WidgetTheme.BAR_BG;
                textColor = WidgetTheme.TEXT_SECONDARY;
            }

            HudSurface.fillRounded(context, sidebarX + 4, tabY, sidebarWidth - 8, 20, 4, bg);

            // Верхний акцент только у активной категории
            if (active) {
                context.fill(sidebarX + 8, tabY, sidebarX + sidebarWidth - 8, tabY + 1, WidgetTheme.ACCENT_LINE);
            }

            context.drawString(mc.font, category.getTitle(), sidebarX + 10, tabY + 6, textColor);
            tabY += 24;
        }

        context.disableScissor();
        renderCategoryScrollbar(context, sidebarX, sidebarY, sidebarHeight, mouseX, mouseY);
    }

    private void renderContent(GuiGraphics context, double mouseX, double mouseY) {
        int contentX = getContentX();
        int contentY = getContentY();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();

        HudSurface.fillRounded(context, contentX, contentY, contentWidth, contentHeight, 4, WidgetTheme.PANEL_BG_SOFT);

        List<SettingsComponent> activeComponents = componentsByCategory.getOrDefault(activeCategory, List.of());
        activeComponents = activeComponents.stream()
                .filter(SettingsComponent::isVisible)
                .toList();

        int innerX = contentX + contentPadding;
        int innerY = contentY + contentPadding - scrollOffset;
        int innerWidth = contentWidth - contentPadding * 2 - 8;

        updateScrollBounds(activeComponents, contentHeight);

        context.enableScissor(
                contentX + 1,
                contentY + 1,
                contentX + contentWidth - 6,
                contentY + contentHeight - 1
        );

        for (SettingsComponent component : activeComponents) {
            int componentIndent = Math.min(component.getIndent(), Math.max(0, innerWidth - 40));
            int componentHeight = component.getPreferredHeight();
            component.setPosition(innerX + componentIndent, innerY);
            component.setSize(innerWidth - componentIndent, componentHeight);
            component.render(context, (int) mouseX, (int) mouseY);

            innerY += componentHeight + rowSpacing;
        }

        context.disableScissor();

        renderScrollbar(context, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY);

        // Tooltip for a hovered component whose label was truncated — after scissor so it isn't clipped.
        for (SettingsComponent component : activeComponents) {
            String tooltip = component.getTooltip((int) mouseX, (int) mouseY);
            if (tooltip != null) {
                context.renderTooltip(Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal(tooltip), (int) mouseX, (int) mouseY);
                break;
            }
        }
    }

    private void renderScrollbar(GuiGraphics context, int contentX, int contentY, int contentWidth, int contentHeight, double mouseX, double mouseY) {
        int barX = contentX + contentWidth - 4;
        int barY = contentY + 4;
        int barHeight = contentHeight - 8;

        boolean hovered = isOverScrollbar(mouseX, mouseY);

        HudSurface.fillRounded(context, barX, barY, 2, barHeight, 1,
                hovered || scrollbarDragging ? WidgetTheme.TEXT_MUTED : WidgetTheme.BAR_BG);

        if (maxScroll <= 0) {
            return;
        }

        int thumbHeight = getScrollbarThumbHeight(contentHeight);
        int thumbY = getScrollbarThumbY(contentY, contentHeight);

        HudSurface.fillRounded(context, barX - 1, thumbY, 4, thumbHeight, 2,
                scrollbarDragging ? WidgetTheme.TEXT_PRIMARY : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY));
    }

    private void renderCategoryScrollbar(GuiGraphics context, int sidebarX, int sidebarY, int sidebarHeight, double mouseX, double mouseY) {
        int barX = sidebarX + sidebarWidth - 4;
        int barY = sidebarY + 4;
        int barHeight = sidebarHeight - 8;

        boolean hovered = isOverCategoryScrollbar(mouseX, mouseY);

        HudSurface.fillRounded(context, barX, barY, 2, barHeight, 1,
                hovered || categoryScrollbarDragging ? WidgetTheme.TEXT_MUTED : WidgetTheme.BAR_BG);

        if (maxCategoryScroll <= 0) {
            return;
        }

        int thumbHeight = getCategoryScrollbarThumbHeight(sidebarHeight);
        int thumbY = getCategoryScrollbarThumbY(sidebarY, sidebarHeight);

        HudSurface.fillRounded(context, barX - 1, thumbY, 4, thumbHeight, 2,
                categoryScrollbarDragging ? WidgetTheme.TEXT_PRIMARY : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY));
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        for (SettingsCategory category : SettingsCategory.values()) {
            componentsByCategory.put(category, new ArrayList<>());
        }

        for (int i = 0; i < 7; i++) {
            final int index = i;
            componentsByCategory.get(SettingsCategory.RUNES_BAG_KEYBINDS).add(
                    new KeybindSettingsComponent(
                            0, 0, 0, 0,
                            "Select Rune Set " + (i + 1),
                            () -> ConfigManager.get().runesBag.setSelectorKeys[index],
                            key -> ConfigManager.get().runesBag.setSelectorKeys[index] = key
                    )
            );
        }

        componentsByCategory.get(SettingsCategory.QUICK_ACCESS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().quickAccess.active,
                        value -> ConfigManager.get().quickAccess.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.QUICK_ACCESS).add(
                new ActionSettingsComponent(
                        "Open Editor",
                        () -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.setScreen(QuickAccessScreen.editor(minecraft.screen));
                        }
                )
        );

        rebuildAutoMessageComponents();

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().bossWidget.active,
                        value -> ConfigManager.get().bossWidget.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Keep Spawned Until Killed",
                        () -> ConfigManager.get().bossWidget.showSpawnedUntilKilled,
                        value -> ConfigManager.get().bossWidget.showSpawnedUntilKilled = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Keep After Spawn (s)",
                        () -> ConfigManager.get().bossWidget.postSpawnShowSeconds,
                        value -> ConfigManager.get().bossWidget.postSpawnShowSeconds = value,
                        0, 600
                ).withVisibility(() -> !ConfigManager.get().bossWidget.showSpawnedUntilKilled)
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new CycleSettingsComponent<>(
                        0, 0, 0, 0,
                        "Timer source",
                        () -> ConfigManager.get().bossWidget.sourceMode,
                        value -> ConfigManager.get().bossWidget.sourceMode = value,
                        BossTimerSourceMode.values(),
                        BossTimerSourceMode::getTitle
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Max bosses",
                        () -> ConfigManager.get().bossWidget.maxBosses,
                        value -> ConfigManager.get().bossWidget.maxBosses = value,
                        1, 50, 1
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Min level",
                        () -> ConfigManager.get().bossWidget.minLevel,
                        value -> ConfigManager.get().bossWidget.minLevel = value,
                        15, HudSettingsPanel::bossLevelCeiling, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Max level",
                        () -> ConfigManager.get().bossWidget.maxLevel,
                        value -> ConfigManager.get().bossWidget.maxLevel = value,
                        15, HudSettingsPanel::bossLevelCeiling, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show name",
                        () -> ConfigManager.get().bossWidget.showName,
                        value -> ConfigManager.get().bossWidget.showName = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show icons",
                        () -> ConfigManager.get().bossWidget.showIcons,
                        value -> ConfigManager.get().bossWidget.showIcons = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show level",
                        () -> ConfigManager.get().bossWidget.showLevel,
                        value -> ConfigManager.get().bossWidget.showLevel = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show timer",
                        () -> ConfigManager.get().bossWidget.showTimer,
                        value -> ConfigManager.get().bossWidget.showTimer = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Full alignment",
                        () -> ConfigManager.get().bossWidget.fullAligment,
                        value -> ConfigManager.get().bossWidget.fullAligment = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Pre-respawn message",
                        () -> ConfigManager.get().bossRespawnMessages.preRespawnMessage,
                        value -> ConfigManager.get().bossRespawnMessages.preRespawnMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Pre-respawn seconds",
                        () -> ConfigManager.get().bossRespawnMessages.preRespawnSeconds,
                        value -> ConfigManager.get().bossRespawnMessages.preRespawnSeconds = value,
                        0, 360, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Spawned message",
                        () -> ConfigManager.get().bossRespawnMessages.spawnMessage,
                        value -> ConfigManager.get().bossRespawnMessages.spawnMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Curse message",
                        () -> ConfigManager.get().bossRespawnMessages.curseMessage,
                        value -> ConfigManager.get().bossRespawnMessages.curseMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP message",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthMessage,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP percent",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthPercent,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthPercent = value,
                        1, 100, 1
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP cooldown",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthCooldownSeconds,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthCooldownSeconds = value,
                        1, 60, 1
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().playerHealthBars.active,
                        value -> ConfigManager.get().playerHealthBars.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Offset",
                        () -> ConfigManager.get().playerHealthBars.verticalOffset,
                        value -> ConfigManager.get().playerHealthBars.verticalOffset = value,
                        -60, 60
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Opacity %",
                        () -> ConfigManager.get().playerHealthBars.opacityPercent,
                        value -> ConfigManager.get().playerHealthBars.opacityPercent = value,
                        10, 100
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Distance fade",
                        () -> ConfigManager.get().playerHealthBars.distanceFade,
                        value -> ConfigManager.get().playerHealthBars.distanceFade = value
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Bar size %",
                        () -> ConfigManager.get().playerHealthBars.sizePercent,
                        value -> ConfigManager.get().playerHealthBars.sizePercent = value,
                        50, 250
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Hard accent threshold %",
                        () -> ConfigManager.get().playerHealthBars.hardAccentThresholdPercent,
                        value -> ConfigManager.get().playerHealthBars.hardAccentThresholdPercent = value,
                        0, 100
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Accent strength %",
                        () -> ConfigManager.get().playerHealthBars.accentStrengthPercent,
                        value -> ConfigManager.get().playerHealthBars.accentStrengthPercent = value,
                        0, 100
                )
        );

        componentsByCategory.get(SettingsCategory.CLICKER).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "CPS",
                        () -> ConfigManager.get().clicker.cps,
                        value -> ConfigManager.get().clicker.cps = value,
                        1, 20
                )
        );

        componentsByCategory.get(SettingsCategory.CLICKER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Use item",
                        () -> ConfigManager.get().clicker.useItem,
                        value -> ConfigManager.get().clicker.useItem = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show markers",
                        () -> ConfigManager.get().fishing.showFishingMarkers,
                        value -> ConfigManager.get().fishing.showFishingMarkers = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show fishing nibbles widget",
                        () -> ConfigManager.get().fishing.showFishingNibblesWidget,
                        value -> ConfigManager.get().fishing.showFishingNibblesWidget = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "AutoFish",
                        () -> ConfigManager.get().fishing.autoFish,
                        value -> ConfigManager.get().fishing.autoFish = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "AutoFish delay",
                        () -> ConfigManager.get().fishing.autoFishDelayTicks,
                        value -> ConfigManager.get().fishing.autoFishDelayTicks = value,
                        1, 20
                )
        );

        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new ToggleSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "OverlayRender",
        //                 () -> ConfigManager.get().wayPoints.overlayRender,
        //                 value -> ConfigManager.get().wayPoints.overlayRender = value
        //         )
        // );

        for (ThemePreset preset : ThemePreset.values()) {
            componentsByCategory.get(SettingsCategory.THEME).add(
                    new ThemePresetSettingsComponent(0, 0, 0, 0, preset)
            );
        }

        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Custom red",
                        () -> ConfigManager.get().theme.customAccentRed,
                        value -> ConfigManager.get().theme.customAccentRed = value,
                        0, 255
                )
        );

        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Custom green",
                        () -> ConfigManager.get().theme.customAccentGreen,
                        value -> ConfigManager.get().theme.customAccentGreen = value,
                        0, 255
                )
        );

        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Custom blue",
                        () -> ConfigManager.get().theme.customAccentBlue,
                        value -> ConfigManager.get().theme.customAccentBlue = value,
                        0, 255
                )
        );

        componentsByCategory.get(SettingsCategory.THEME).add(new BreakLineSettingsComponent("Hard Accent"));
        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Hard accent red",
                        () -> ConfigManager.get().theme.hardAccentRed,
                        value -> ConfigManager.get().theme.hardAccentRed = value,
                        0, 255
                )
        );
        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Hard accent green",
                        () -> ConfigManager.get().theme.hardAccentGreen,
                        value -> ConfigManager.get().theme.hardAccentGreen = value,
                        0, 255
                )
        );
        componentsByCategory.get(SettingsCategory.THEME).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Hard accent blue",
                        () -> ConfigManager.get().theme.hardAccentBlue,
                        value -> ConfigManager.get().theme.hardAccentBlue = value,
                        0, 255
                )
        );

        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new SliderSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Ping distance",
        //                 () -> ConfigManager.get().wayPoints.maxDistance,
        //                 value -> ConfigManager.get().wayPoints.maxDistance = value,
        //                 5, 100
        //         )
        // );
        //
        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new SliderSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Entity offset",
        //                 () -> ConfigManager.get().wayPoints.entityOffsetPixels,
        //                 value -> ConfigManager.get().wayPoints.entityOffsetPixels = value,
        //                 0, 100
        //         )
        // );
        //
        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new ToggleSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Show author",
        //                 () -> ConfigManager.get().wayPoints.showAuthor,
        //                 value -> ConfigManager.get().wayPoints.showAuthor = value
        //         )
        // );
        //
        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new ToggleSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Show entity name",
        //                 () -> ConfigManager.get().wayPoints.showEntityName,
        //                 value -> ConfigManager.get().wayPoints.showEntityName = value
        //         )
        // );
        //
        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new ToggleSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Show location",
        //                 () -> ConfigManager.get().wayPoints.showLocation,
        //                 value -> ConfigManager.get().wayPoints.showLocation = value
        //         )
        // );
        //
        // componentsByCategory.get(SettingsCategory.WAYPOINTS).add(
        //         new ToggleSettingsComponent(
        //                 0, 0, 0, 0,
        //                 "Show distance",
        //                 () -> ConfigManager.get().wayPoints.showDistance,
        //                 value -> ConfigManager.get().wayPoints.showDistance = value
        //         )
        // );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().popUps.active,
                        value -> ConfigManager.get().popUps.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Visible at once",
                        () -> ConfigManager.get().popUps.maxVisible,
                        value -> ConfigManager.get().popUps.maxVisible = value,
                        1, 6, 1
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Hold ms",
                        () -> ConfigManager.get().popUps.holdMillis,
                        value -> ConfigManager.get().popUps.holdMillis = value,
                        500, 10000, 100
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Fade-in ms",
                        () -> ConfigManager.get().popUps.fadeInMillis,
                        value -> ConfigManager.get().popUps.fadeInMillis = value,
                        0, 3000, 20
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Fade-out ms",
                        () -> ConfigManager.get().popUps.fadeOutMillis,
                        value -> ConfigManager.get().popUps.fadeOutMillis = value,
                        0, 3000, 20
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Chat copied",
                        () -> ConfigManager.get().popUps.chatCopyEvent,
                        value -> ConfigManager.get().popUps.chatCopyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Private message",
                        () -> ConfigManager.get().popUps.privateMessageEvent,
                        value -> ConfigManager.get().popUps.privateMessageEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Boss spawned",
                        () -> ConfigManager.get().popUps.bossSpawnEvent,
                        value -> ConfigManager.get().popUps.bossSpawnEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Ability ready",
                        () -> ConfigManager.get().popUps.abilityReadyEvent,
                        value -> ConfigManager.get().popUps.abilityReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Staff ready",
                        () -> ConfigManager.get().popUps.wandReadyEvent,
                        value -> ConfigManager.get().popUps.wandReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Wind Staff",
                        () -> ConfigManager.get().popUps.windStaffReadyEvent,
                        value -> ConfigManager.get().popUps.windStaffReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Seller ready",
                        () -> ConfigManager.get().popUps.sellerReadyEvent,
                        value -> ConfigManager.get().popUps.sellerReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Miner returned",
                        () -> ConfigManager.get().popUps.minerReturnedEvent,
                        value -> ConfigManager.get().popUps.minerReturnedEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Rune set ready",
                        () -> ConfigManager.get().popUps.runeSetReadyEvent,
                        value -> ConfigManager.get().popUps.runeSetReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Potion expired",
                        () -> ConfigManager.get().popUps.potionExpiredEvent,
                        value -> ConfigManager.get().popUps.potionExpiredEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Booster expired",
                        () -> ConfigManager.get().popUps.boosterExpiredEvent,
                        value -> ConfigManager.get().popUps.boosterExpiredEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new StatusSettingsComponent(
                        "RPC status",
                        DiscordRpcService::getStatus
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Privacy mode",
                        () -> ConfigManager.get().discordRpc.privacyMode,
                        value -> ConfigManager.get().discordRpc.privacyMode = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show elapsed time",
                        () -> ConfigManager.get().discordRpc.showElapsedTime,
                        value -> ConfigManager.get().discordRpc.showElapsedTime = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show server",
                        () -> ConfigManager.get().discordRpc.showServer,
                        value -> ConfigManager.get().discordRpc.showServer = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show mirror",
                        () -> ConfigManager.get().discordRpc.showMirror,
                        value -> ConfigManager.get().discordRpc.showMirror = value
                ).withIndent(18).withVisibility(() -> ConfigManager.get().discordRpc.showServer)
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show location",
                        () -> ConfigManager.get().discordRpc.showLocation,
                        value -> ConfigManager.get().discordRpc.showLocation = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show level",
                        () -> ConfigManager.get().discordRpc.showLevel,
                        value -> ConfigManager.get().discordRpc.showLevel = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show combo",
                        () -> ConfigManager.get().discordRpc.showCombo,
                        value -> ConfigManager.get().discordRpc.showCombo = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show game event",
                        () -> ConfigManager.get().discordRpc.showGameEvent,
                        value -> ConfigManager.get().discordRpc.showGameEvent = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Enable auto RPC",
                        () -> ConfigManager.get().discordRpc.active,
                        value -> ConfigManager.get().discordRpc.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(new BreakLineSettingsComponent("Visual"));
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Debug",
                        () -> ConfigManager.get().render.debug,
                        value -> ConfigManager.get().render.debug = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Hide Cosmetics (First Person)",
                        () -> ConfigManager.get().render.hideFirstPersonCosmetics,
                        value -> ConfigManager.get().render.hideFirstPersonCosmetics = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "FrogHelper Badge On Nametags",
                        () -> ConfigManager.get().render.modUserBadge,
                        value -> ConfigManager.get().render.modUserBadge = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "FrogHelper Mesh Sync (PM)",
                        () -> ConfigManager.get().render.modUserMesh,
                        value -> ConfigManager.get().render.modUserMesh = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "FrogHelper Group Widget",
                        () -> ConfigManager.get().social.widgetActive,
                        value -> ConfigManager.get().social.widgetActive = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Hide block particles",
                        () -> ConfigManager.get().render.hideBlockBreakParticles,
                        value -> ConfigManager.get().render.hideBlockBreakParticles = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Hide lightning",
                        () -> ConfigManager.get().render.hideLightningEffect,
                        value -> ConfigManager.get().render.hideLightningEffect = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Hide hurt shake",
                        () -> ConfigManager.get().render.hideHurtCameraShake,
                        value -> ConfigManager.get().render.hideHurtCameraShake = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Hide fire overlay",
                        () -> ConfigManager.get().render.hideFireOverlay,
                        value -> ConfigManager.get().render.hideFireOverlay = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show server in TAB",
                        () -> ConfigManager.get().render.showCurrentServerInTab,
                        value -> ConfigManager.get().render.showCurrentServerInTab = value
                )
        );

        componentsByCategory.get(SettingsCategory.RENDER).add(new BreakLineSettingsComponent("Animation"));
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Static hand",
                        () -> ConfigManager.get().render.staticHand,
                        value -> ConfigManager.get().render.staticHand = value
                )
        );

        componentsByCategory.get(SettingsCategory.RENDER).add(new BreakLineSettingsComponent("Highlights"));
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Alchemy markers",
                        () -> ConfigManager.get().render.showAlchemyIngredientMarkers,
                        value -> ConfigManager.get().render.showAlchemyIngredientMarkers = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Dungeon decorations highlight",
                        () -> ConfigManager.get().render.dungeonDecorationHighlight,
                        value -> ConfigManager.get().render.dungeonDecorationHighlight = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Useful items highlight",
                        () -> ConfigManager.get().render.usefulItemsHighlight,
                        value -> ConfigManager.get().render.usefulItemsHighlight = value
                ).withWarningTooltip("Performance sensitive")
        );

        componentsByCategory.get(SettingsCategory.RENDER).add(new BreakLineSettingsComponent("Utility"));
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Tone-down chat",
                        () -> ConfigManager.get().render.toneDownChat,
                        value -> ConfigManager.get().render.toneDownChat = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Chat timestamps",
                        () -> ConfigManager.get().render.chatTimestamps,
                        value -> ConfigManager.get().render.chatTimestamps = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Copy chat by RMB",
                        () -> ConfigManager.get().render.copyChatMessages,
                        value -> ConfigManager.get().render.copyChatMessages = value
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Extra chat history",
                        () -> ConfigManager.get().render.extraChatHistoryLines,
                        value -> ConfigManager.get().render.extraChatHistoryLines = value,
                        0, 10000, 50
                )
        );
        componentsByCategory.get(SettingsCategory.RENDER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Auto thx",
                        () -> ConfigManager.get().render.autoThanks,
                        value -> ConfigManager.get().render.autoThanks = value
                )
        );

        componentsByCategory.get(SettingsCategory.WIDGET).add(new BreakLineSettingsComponent("Core"));
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "UnClutter (hide widget titles)",
                        () -> ConfigManager.get().render.unclutterWidgets,
                        value -> ConfigManager.get().render.unclutterWidgets = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new CycleSettingsComponent<>(
                        0, 0, 0, 0,
                        "Background",
                        () -> ConfigManager.get().render.widgetChrome,
                        value -> ConfigManager.get().render.widgetChrome = value,
                        WidgetChrome.values(),
                        WidgetChrome::label
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Native Renderer (no blur, if glass lags)",
                        () -> ConfigManager.get().render.nativeRenderer,
                        value -> ConfigManager.get().render.nativeRenderer = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Render BossBar as Widget",
                        () -> ConfigManager.get().bossBar.active,
                        value -> ConfigManager.get().bossBar.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Render Scoreboard as Widget",
                        () -> ConfigManager.get().scoreboard.active,
                        value -> ConfigManager.get().scoreboard.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Chat Queue Widget",
                        () -> ConfigManager.get().outgoingChatQueue.active,
                        value -> ConfigManager.get().outgoingChatQueue.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Protocol Graph Widget",
                        () -> ConfigManager.get().protocolGraphWidget.active,
                        value -> ConfigManager.get().protocolGraphWidget.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Level Progress Widget",
                        () -> ConfigManager.get().levelProgress.active,
                        value -> ConfigManager.get().levelProgress.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Block Counter Widget",
                        () -> ConfigManager.get().blocksPerSecondWidget.active,
                        value -> ConfigManager.get().blocksPerSecondWidget.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Enable Estimated TPS Monitor",
                        () -> ConfigManager.get().estimatedTps.enabled,
                        value -> ConfigManager.get().estimatedTps.enabled = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Estimated TPS Widget",
                        () -> ConfigManager.get().estimatedTps.active,
                        value -> ConfigManager.get().estimatedTps.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.WIDGET).add(new BreakLineSettingsComponent("Recipes"));
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Potion Recipe Widget",
                        () -> ConfigManager.get().potionRecipe.active,
                        value -> ConfigManager.get().potionRecipe.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Craft Recipe Widget",
                        () -> ConfigManager.get().craftRecipe.active,
                        value -> ConfigManager.get().craftRecipe.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Compact Craft Recipe",
                        () -> ConfigManager.get().craftRecipe.compact,
                        value -> ConfigManager.get().craftRecipe.compact = value
                )
        );

        componentsByCategory.get(SettingsCategory.WIDGET).add(new BreakLineSettingsComponent("Status"));
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Boosters Widget",
                        () -> ConfigManager.get().boosters.active,
                        value -> ConfigManager.get().boosters.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Compact Boosters",
                        () -> ConfigManager.get().boosters.compact,
                        value -> ConfigManager.get().boosters.compact = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "BG Opacity",
                        () -> ConfigManager.get().theme.widgetBackgroundOpacityPercent,
                        value -> ConfigManager.get().theme.widgetBackgroundOpacityPercent = value,
                        0, 50
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new BreakLineSettingsComponent("Visibility")
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Potions Widget",
                        () -> ConfigManager.get().potionTimers.active,
                        value -> ConfigManager.get().potionTimers.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Potion Keep After Expiry (s)",
                        () -> ConfigManager.get().potionTimers.belowZeroSeconds,
                        value -> ConfigManager.get().potionTimers.belowZeroSeconds = value,
                        0, 60
                ).withVisibility(() -> ConfigManager.get().potionTimers.active)
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Seller Cooldowns",
                        () -> ConfigManager.get().sellerCooldown.active,
                        value -> ConfigManager.get().sellerCooldown.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Combo Progress",
                        () -> ConfigManager.get().comboProgress.active,
                        value -> ConfigManager.get().comboProgress.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Wand Cooldown Widget",
                        () -> ConfigManager.get().wandCooldown.active,
                        value -> ConfigManager.get().wandCooldown.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Ability Cooldown Widget",
                        () -> ConfigManager.get().abilityCooldown.active,
                        value -> ConfigManager.get().abilityCooldown.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Active Runes Widget",
                        () -> ConfigManager.get().activeRunes.active,
                        value -> ConfigManager.get().activeRunes.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Active Pets Widget",
                        () -> ConfigManager.get().activePets.active,
                        value -> ConfigManager.get().activePets.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Miners Widget",
                        () -> ConfigManager.get().activeMiners.active,
                        value -> ConfigManager.get().activeMiners.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Boss Damage Widget",
                        () -> ConfigManager.get().bossDamage.active,
                        value -> ConfigManager.get().bossDamage.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Visibility Status Widget",
                        () -> ConfigManager.get().visibilityStatus.active,
                        value -> ConfigManager.get().visibilityStatus.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Compact Visibility Status",
                        () -> ConfigManager.get().visibilityStatus.compact,
                        value -> ConfigManager.get().visibilityStatus.compact = value
                )
        );

        componentsByCategory.get(SettingsCategory.WIDGET).add(new BreakLineSettingsComponent("Maps & Inspect"));
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Dungeon / Siege Map Widget",
                        () -> ConfigManager.get().dungeonMap.active,
                        value -> ConfigManager.get().dungeonMap.active = value
                )
        );
        componentsByCategory.get(SettingsCategory.WIDGET).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show Entity Inspect Widget",
                        () -> ConfigManager.get().entityInspect.active,
                        value -> ConfigManager.get().entityInspect.active = value
                )
        );

        initialized = true;
    }

    private void rebuildAutoMessageComponents() {
        ensureAutoMessageSlotState();

        List<SettingsComponent> autoMessageComponents = componentsByCategory.get(SettingsCategory.AUTO_MESSAGES);
        autoMessageComponents.clear();

        autoMessageComponents.add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().autoMessages.active,
                        value -> ConfigManager.get().autoMessages.active = value
                )
        );

        for (int i = 0; i < ConfigManager.get().autoMessages.entries.size(); i++) {
            final int index = i;
            final String slotLabel = "Slot " + (i + 1);

            if (i > 0) {
                autoMessageComponents.add(new BreakLineSettingsComponent(slotLabel));
            }

            autoMessageComponents.add(
                    new SlotHeaderSettingsComponent(
                            slotLabel,
                            () -> autoMessageSlotExpanded.get(index),
                            () -> autoMessageSlotExpanded.set(index, !autoMessageSlotExpanded.get(index))
                    )
            );

            autoMessageComponents.add(
                    new AutoMessageSlotPreviewComponent(
                            () -> getAutoMessageEntry(index).message,
                            () -> getAutoMessageEntry(index).active,
                            () -> getAutoMessageEntry(index).delaySeconds
                    ).withIndent(18).withVisibility(() -> !autoMessageSlotExpanded.get(index))
            );

            autoMessageComponents.add(
                    new TextInputSettingsComponent(
                            0, 0, 0, 0,
                            "Message",
                            () -> getAutoMessageEntry(index).message,
                            value -> getAutoMessageEntry(index).message = value,
                            256
                    ).withIndent(18).withVisibility(() -> autoMessageSlotExpanded.get(index))
            );

            autoMessageComponents.add(
                    new ToggleSettingsComponent(
                            0, 0, 0, 0,
                            "Active",
                            () -> getAutoMessageEntry(index).active,
                            value -> getAutoMessageEntry(index).active = value
                    ).withIndent(36).withVisibility(() -> autoMessageSlotExpanded.get(index))
            );

            autoMessageComponents.add(
                    new StepperSettingsComponent(
                            0, 0, 0, 0,
                            "Delay s",
                            () -> getAutoMessageEntry(index).delaySeconds,
                            value -> getAutoMessageEntry(index).delaySeconds = value,
                            1, 3600, 5
                    ).withIndent(36).withVisibility(() -> autoMessageSlotExpanded.get(index))
            );

            autoMessageComponents.add(
                    new ActionSettingsComponent(
                            "- Remove slot",
                            () -> removeAutoMessageSlot(index)
                    ).withIndent(36).withVisibility(() -> autoMessageSlotExpanded.get(index) && ConfigManager.get().autoMessages.entries.size() > 1)
            );
        }

        autoMessageComponents.add(
                new ActionSettingsComponent(
                        "+ Add slot",
                        this::addAutoMessageSlot
                )
        );
    }

    private void ensureAutoMessageSlotState() {
        int slotCount = ConfigManager.get().autoMessages.entries.size();
        while (autoMessageSlotExpanded.size() < slotCount) {
            autoMessageSlotExpanded.add(autoMessageSlotExpanded.isEmpty());
        }
        while (autoMessageSlotExpanded.size() > slotCount) {
            autoMessageSlotExpanded.remove(autoMessageSlotExpanded.size() - 1);
        }
    }

    private void addAutoMessageSlot() {
        ConfigManager.get().autoMessages.entries.add(new AutoMessageEntryConfig());
        autoMessageSlotExpanded.add(Boolean.TRUE);
        ConfigManager.save();
        rebuildAutoMessageComponents();
    }

    private void removeAutoMessageSlot(int index) {
        if (ConfigManager.get().autoMessages.entries.size() <= 1) {
            return;
        }

        if (index < 0 || index >= ConfigManager.get().autoMessages.entries.size()) {
            return;
        }

        ConfigManager.get().autoMessages.entries.remove(index);
        if (index < autoMessageSlotExpanded.size()) {
            autoMessageSlotExpanded.remove(index);
        }
        ensureAutoMessageSlotState();
        ConfigManager.save();
        rebuildAutoMessageComponents();
    }

    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (!isInside(mouseX, mouseY)) {
            return false;
        }

        if (handleSidebarClick(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && handleCategoryScrollbarPress(mouseX, mouseY)) {
            return true;
        }

        if (button == 0 && handleScrollbarPress(mouseX, mouseY)) {
            return true;
        }

        if (isInsideContent(mouseX, mouseY)) {
            for (SettingsComponent component : getInteractiveComponents()) {
                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;

        if (categoryScrollbarDragging && button == 0) {
            categoryScrollbarDragging = false;
            handled = true;
        }

        if (scrollbarDragging && button == 0) {
            scrollbarDragging = false;
            handled = true;
        }

        for (SettingsComponent component : getInteractiveComponents()) {
            if (component.mouseReleased(mouseX, mouseY, button)) {
                handled = true;
            }
        }

        return handled || isInside(mouseX, mouseY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingsComponent component : getInteractiveComponents()) {
            if (component.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (SettingsComponent component : getInteractiveComponents()) {
            if (component.charTyped(codePoint, modifiers)) {
                return true;
            }
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button == 0 && categoryScrollbarDragging) {
            updateCategoryScrollFromScrollbar(mouseY);
            return true;
        }

        if (button == 0 && scrollbarDragging) {
            updateScrollFromScrollbar(mouseY);
            return true;
        }

        for (SettingsComponent component : getInteractiveComponents()) {
            if (component.mouseDragged(mouseX, mouseY, button, 0, 0)) {
                return true;
            }
        }

        return isInside(mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isInsideSidebar(mouseX, mouseY)) {
            if (maxCategoryScroll <= 0) {
                return true;
            }

            categoryScrollOffset -= (int) (scrollY * 12);
            clampCategoryScroll();
            return true;
        }

        if (isInsideContent(mouseX, mouseY)) {
            if (maxScroll <= 0) {
                return true;
            }

            scrollOffset -= (int) (scrollY * 12);
            clampScroll();
            return true;
        }

        return false;
    }

    private boolean handleSidebarClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int sidebarX = x + 8;
        int sidebarY = y + headerHeight + 8;
        int tabY = sidebarY + 6 - categoryScrollOffset;
        int visibleTop = sidebarY + 4;
        int visibleBottom = sidebarY + getSidebarHeight() - 4;

        for (SettingsCategory category : SettingsCategory.values()) {
            boolean hovered = mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4
                    && mouseY >= tabY && mouseY <= tabY + 20;

            if (hovered && tabY + 20 >= visibleTop && tabY <= visibleBottom) {
                activeCategory = category;
                scrollOffset = 0;
                scrollbarDragging = false;
                categoryScrollbarDragging = false;
                return true;
            }

            tabY += 24;
        }

        return false;
    }

    private boolean handleScrollbarPress(double mouseX, double mouseY) {
        if (!isOverScrollbar(mouseX, mouseY) || maxScroll <= 0) {
            return false;
        }

        int thumbY = getScrollbarThumbY(getContentY(), getContentHeight());
        int thumbHeight = getScrollbarThumbHeight(getContentHeight());

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            scrollbarDragging = true;
            scrollbarDragOffset = (int) mouseY - thumbY;
            return true;
        }

        int contentY = getContentY();
        int contentHeight = getContentHeight();
        int barY = contentY + 4;
        int barHeight = contentHeight - 8;
        int thumbTravel = barHeight - thumbHeight;

        if (thumbTravel <= 0) {
            return true;
        }

        double target = ((mouseY - barY) - thumbHeight / 2.0) / thumbTravel;
        scrollOffset = (int) Math.round(target * maxScroll);
        clampScroll();

        return true;
    }

    private boolean handleCategoryScrollbarPress(double mouseX, double mouseY) {
        if (!isOverCategoryScrollbar(mouseX, mouseY) || maxCategoryScroll <= 0) {
            return false;
        }

        int thumbY = getCategoryScrollbarThumbY(getSidebarY(), getSidebarHeight());
        int thumbHeight = getCategoryScrollbarThumbHeight(getSidebarHeight());

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            categoryScrollbarDragging = true;
            categoryScrollbarDragOffset = (int) mouseY - thumbY;
            return true;
        }

        int sidebarY = getSidebarY();
        int sidebarHeight = getSidebarHeight();
        int barY = sidebarY + 4;
        int barHeight = sidebarHeight - 8;
        int thumbTravel = barHeight - thumbHeight;

        if (thumbTravel <= 0) {
            return true;
        }

        double target = ((mouseY - barY) - thumbHeight / 2.0) / thumbTravel;
        categoryScrollOffset = (int) Math.round(target * maxCategoryScroll);
        clampCategoryScroll();

        return true;
    }

    private void updateScrollFromScrollbar(double mouseY) {
        int contentY = getContentY();
        int contentHeight = getContentHeight();

        int barY = contentY + 4;
        int barHeight = contentHeight - 8;
        int thumbHeight = getScrollbarThumbHeight(contentHeight);
        int thumbTravel = barHeight - thumbHeight;

        if (thumbTravel <= 0) {
            scrollOffset = 0;
            return;
        }

        double thumbTop = mouseY - scrollbarDragOffset;
        double progress = (thumbTop - barY) / thumbTravel;

        scrollOffset = (int) Math.round(progress * maxScroll);
        clampScroll();
    }

    private void updateCategoryScrollFromScrollbar(double mouseY) {
        int sidebarY = getSidebarY();
        int sidebarHeight = getSidebarHeight();

        int barY = sidebarY + 4;
        int barHeight = sidebarHeight - 8;
        int thumbHeight = getCategoryScrollbarThumbHeight(sidebarHeight);
        int thumbTravel = barHeight - thumbHeight;

        if (thumbTravel <= 0) {
            categoryScrollOffset = 0;
            return;
        }

        double thumbTop = mouseY - categoryScrollbarDragOffset;
        double progress = (thumbTop - barY) / thumbTravel;

        categoryScrollOffset = (int) Math.round(progress * maxCategoryScroll);
        clampCategoryScroll();
    }

    private void updateScrollBounds(List<SettingsComponent> components, int contentHeight) {
        int totalContentHeight = 0;
        for (SettingsComponent component : components) {
            totalContentHeight += component.getPreferredHeight() + rowSpacing;
        }
        maxScroll = Math.max(0, totalContentHeight - (contentHeight - contentPadding * 2));
        clampScroll();
    }

    private void updateCategoryScrollBounds(int sidebarHeight) {
        int totalContentHeight = SettingsCategory.values().length * 24;
        maxCategoryScroll = Math.max(0, totalContentHeight - (sidebarHeight - 12));
        clampCategoryScroll();
    }

    private void clampScroll() {
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void clampCategoryScroll() {
        if (categoryScrollOffset < 0) {
            categoryScrollOffset = 0;
        }
        if (categoryScrollOffset > maxCategoryScroll) {
            categoryScrollOffset = maxCategoryScroll;
        }
    }

    private List<SettingsComponent> getInteractiveComponents() {
        if (!initialized) {
            return List.of();
        }

        int contentY = getContentY();
        int contentHeight = getContentHeight();

        return componentsByCategory.getOrDefault(activeCategory, List.of()).stream()
                .filter(SettingsComponent::isVisible)
                .filter(component ->
                        component.getY() + component.getHeight() >= contentY + 1 &&
                                component.getY() <= contentY + contentHeight - 1
                )
                .toList();
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private boolean isInsideContent(double mouseX, double mouseY) {
        int contentX = getContentX();
        int contentY = getContentY();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();

        return mouseX >= contentX && mouseX <= contentX + contentWidth
                && mouseY >= contentY && mouseY <= contentY + contentHeight;
    }

    private boolean isInsideSidebar(double mouseX, double mouseY) {
        int sidebarX = x + 8;
        int sidebarY = getSidebarY();
        int sidebarHeight = getSidebarHeight();

        return mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
                && mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int contentX = getContentX();
        int contentY = getContentY();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();

        int barX = contentX + contentWidth - 6;
        int barY = contentY + 4;
        int barHeight = contentHeight - 8;

        return mouseX >= barX && mouseX <= barX + 8
                && mouseY >= barY && mouseY <= barY + barHeight;
    }

    private boolean isOverCategoryScrollbar(double mouseX, double mouseY) {
        int sidebarX = x + 8;
        int sidebarY = getSidebarY();
        int sidebarHeight = getSidebarHeight();

        int barX = sidebarX + sidebarWidth - 6;
        int barY = sidebarY + 4;
        int barHeight = sidebarHeight - 8;

        return mouseX >= barX && mouseX <= barX + 8
                && mouseY >= barY && mouseY <= barY + barHeight;
    }

    private int getScrollbarThumbHeight(int contentHeight) {
        return Math.max(20, (int) ((contentHeight - 8) * ((double) (contentHeight - 20) / (contentHeight - 20 + maxScroll))));
    }

    private int getScrollbarThumbY(int contentY, int contentHeight) {
        int barY = contentY + 4;
        int barHeight = contentHeight - 8;
        int thumbHeight = getScrollbarThumbHeight(contentHeight);
        int thumbTravel = barHeight - thumbHeight;

        if (maxScroll <= 0 || thumbTravel <= 0) {
            return barY;
        }

        return barY + (int) Math.round(thumbTravel * (scrollOffset / (double) maxScroll));
    }

    private int getCategoryScrollbarThumbHeight(int sidebarHeight) {
        return Math.max(20, (int) ((sidebarHeight - 8) * ((double) (sidebarHeight - 12) / (sidebarHeight - 12 + maxCategoryScroll))));
    }

    private int getCategoryScrollbarThumbY(int sidebarY, int sidebarHeight) {
        int barY = sidebarY + 4;
        int barHeight = sidebarHeight - 8;
        int thumbHeight = getCategoryScrollbarThumbHeight(sidebarHeight);
        int thumbTravel = barHeight - thumbHeight;

        if (maxCategoryScroll <= 0 || thumbTravel <= 0) {
            return barY;
        }

        return barY + (int) Math.round(thumbTravel * (categoryScrollOffset / (double) maxCategoryScroll));
    }

    private int getSidebarY() {
        return y + headerHeight + 8;
    }

    private int getSidebarHeight() {
        return height - headerHeight - 16;
    }

    private int getContentX() {
        return x + sidebarWidth + 20;
    }

    private int getContentY() {
        return y + headerHeight + 8;
    }

    private int getContentWidth() {
        return width - sidebarWidth - 28;
    }

    private int getContentHeight() {
        return height - headerHeight - 16;
    }

    private AutoMessageEntryConfig getAutoMessageEntry(int index) {
        return ConfigManager.get().autoMessages.entries.get(index);
    }
}
