package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.config.AutoMessageEntryConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

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

    public void render(GuiGraphicsExtractor context, double mouseX, double mouseY) {
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

    private void renderPanelBackground(GuiGraphicsExtractor context) {
        // The whole window is one frosted-glass panel (Frost surface language) — blur + rounded + the
        // theme accent. The sidebar/content areas below add their own tint so the busy text stays legible.
        HudSurface.drawPanel(context, x, y, width, height, WidgetChrome.FROST, HudSurface.nativeRenderer());
    }

    private void renderHeader(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();

        context.text(mc.font, "FrogHelper", x + 10, y + 8, WidgetTheme.TITLE);
        context.text(mc.font, "Settings", x + 68, y + 8, WidgetTheme.TEXT_SECONDARY);
    }

    private void renderSidebar(GuiGraphicsExtractor context, double mouseX, double mouseY) {
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

            context.text(mc.font, category.getTitle(), sidebarX + 10, tabY + 6, textColor);
            tabY += 24;
        }

        context.disableScissor();
        renderCategoryScrollbar(context, sidebarX, sidebarY, sidebarHeight, mouseX, mouseY);
    }

    private void renderContent(GuiGraphicsExtractor context, double mouseX, double mouseY) {
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
                context.setTooltipForNextFrame(Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal(tooltip), (int) mouseX, (int) mouseY);
                break;
            }
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor context, int contentX, int contentY, int contentWidth, int contentHeight, double mouseX, double mouseY) {
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

    private void renderCategoryScrollbar(GuiGraphicsExtractor context, int sidebarX, int sidebarY, int sidebarHeight, double mouseX, double mouseY) {
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

        HudSettingsFeatureSections.populate(componentsByCategory, this::rebuildAutoMessageComponents);
        HudSettingsCoreSections.populate(componentsByCategory);

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
                            () -> getAutoMessageEntry(index).useMarketCooldown,
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
                    new ToggleSettingsComponent(
                            0, 0, 0, 0,
                            "Trade chat timing",
                            () -> getAutoMessageEntry(index).useMarketCooldown,
                            value -> getAutoMessageEntry(index).useMarketCooldown = value
                    ).withIndent(36).withVisibility(() -> autoMessageSlotExpanded.get(index))
            );

            autoMessageComponents.add(
                    new StepperSettingsComponent(
                            0, 0, 0, 0,
                            "Delay s",
                            () -> getAutoMessageEntry(index).delaySeconds,
                            value -> getAutoMessageEntry(index).delaySeconds = value,
                            1, 3600, 5
                    ).withIndent(36).withVisibility(() ->
                            autoMessageSlotExpanded.get(index) && !getAutoMessageEntry(index).useMarketCooldown)
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
        for (SettingsComponent component : getInteractiveComponents()) {
            if (!component.isHovered(mouseX, mouseY)) {
                component.onClickOutside();
            }
        }

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

    public boolean charTyped(int codePoint, int modifiers) {
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
