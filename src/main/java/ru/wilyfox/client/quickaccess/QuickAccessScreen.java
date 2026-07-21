package ru.wilyfox.client.quickaccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuickAccessScreen extends Screen {
    private static final int SECTION_GAP = 14;
    private static final int ITEM_SIZE = 42;
    private static final int ITEM_GAP = 6;
    private static final int PANEL_PADDING = 16;
    private static final int LABEL_HEIGHT = 18;
    private static final int EDITOR_GAP = 12;
    private static final int EDITOR_WIDTH = 220;
    private static final int EDITOR_HINT_HEIGHT = 30;
    private static final int EDITOR_MIN_HEIGHT = 348;

    private final Map<String, ItemStack> iconCache = new HashMap<>();
    private final Mode mode;
    private final Screen parent;

    private final List<ItemLayout> itemLayouts = new ArrayList<>();
    private final List<SectionLayout> sectionLayouts = new ArrayList<>();

    private QuickAccessItemConfig hoveredItem;
    private int selectedSection = 0;
    private int selectedItem = -1;
    private int dragSection = -1;
    private int dragItem = -1;
    private boolean dragging;
    private int dragMouseX;
    private int dragMouseY;

    private int selectorPanelX;
    private int selectorPanelY;
    private int selectorPanelWidth;
    private int selectorPanelHeight;
    private int editorPanelX;
    private int editorPanelY;
    private int editorPanelHeight;

    private ThemedTextField sectionTitleBox;
    private ThemedTextField itemTitleBox;
    private ThemedTextField itemCommandBox;
    private ThemedTextField itemIdBox;
    private ThemedTextField itemModelBox;

    public QuickAccessScreen() {
        this(Mode.RUNTIME, null);
    }

    private QuickAccessScreen(Mode mode, Screen parent) {
        super(Component.empty());
        this.mode = mode;
        this.parent = parent;
    }

    public static QuickAccessScreen editor(Screen parent) {
        return new QuickAccessScreen(Mode.EDITOR, parent);
    }

    @Override
    protected void init() {
        ensureConfigState();
        clampSelection();
        recalculatePanels();

        if (isEditorMode()) {
            rebuildEditorWidgets();
        } else {
            clearWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    protected void renderBlurredBackground() {
    }

    @Override
    public void onClose() {
        if (isEditorMode()) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }

        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/QuickAccessScreen/render")) {
            hoveredItem = null;
            dragMouseX = mouseX;
            dragMouseY = mouseY;
            recalculatePanels();
            itemLayouts.clear();
            sectionLayouts.clear();

            graphics.fill(0, 0, this.width, this.height, isEditorMode() ? 0x00000000 : backdropColor());
            HudBlur.beginFrame(graphics); // capture the screen for the frosted panels' blur backdrop
            renderSelectorPanel(graphics, mouseX, mouseY);

            if (isEditorMode()) {
                renderEditorPanel(graphics);
                renderEditorFooter(graphics);
                super.render(graphics, mouseX, mouseY, partialTick);
            }

            if (dragging) {
                renderDraggedPreview(graphics);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ItemLayout clickedItem = findItemLayout(mouseX, mouseY);
            if (clickedItem != null) {
                selectedSection = clickedItem.sectionIndex();
                selectedItem = clickedItem.itemIndex();

                if (isEditorMode()) {
                    dragSection = selectedSection;
                    dragItem = selectedItem;
                    dragging = true;
                    rebuildEditorWidgets();
                    return true;
                }
            }

            if (isEditorMode()) {
                SectionLayout clickedSection = findSectionLayout(mouseX, mouseY);
                if (clickedSection != null) {
                    selectedSection = clickedSection.sectionIndex();
                    selectedItem = -1;
                    rebuildEditorWidgets();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isEditorMode() && dragging && button == 0) {
            dragMouseX = (int) mouseX;
            dragMouseY = (int) mouseY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isEditorMode() && dragging && button == 0) {
            finishDrag(mouseX, mouseY);
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    public QuickAccessItemConfig getHoveredItem() {
        return hoveredItem;
    }

    public boolean isEditorMode() {
        return mode == Mode.EDITOR;
    }

    private void renderSelectorPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;

        HudSurface.drawPanel(graphics, selectorPanelX, selectorPanelY, selectorPanelWidth, selectorPanelHeight,
                WidgetChrome.FROST, HudSurface.nativeRenderer());

        String label;
        int labelColor;
        if (isEditorMode()) {
            label = selectedItem >= 0 ? "Quick Access Editor" : "Quick Access Sections";
            labelColor = WidgetTheme.TITLE;
        } else {
            label = hoveredItem == null ? "Quick Access" : QuickAccessCommandResolver.resolveDisplayTitle(hoveredItem);
            labelColor = hoveredItem == null ? WidgetTheme.TITLE : (canExecute(hoveredItem) ? WidgetTheme.TITLE : WidgetTheme.TEXT_MUTED);
        }

        graphics.drawCenteredString(
                minecraft.font,
                label,
                selectorPanelX + selectorPanelWidth / 2,
                selectorPanelY + 8,
                labelColor
        );

        int currentY = selectorPanelY + PANEL_PADDING + LABEL_HEIGHT;
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            QuickAccessSectionConfig section = sections.get(sectionIndex);
            List<QuickAccessItemConfig> items = section.items.stream().filter(this::isVisible).toList();
            if (items.isEmpty()) {
                continue;
            }

            int headerHeight = minecraft.font.lineHeight;
            sectionLayouts.add(new SectionLayout(sectionIndex, selectorPanelX + PANEL_PADDING, currentY, selectorPanelWidth - PANEL_PADDING * 2, headerHeight + 5));

            int sectionColor = isEditorMode() && selectedSection == sectionIndex && selectedItem < 0
                    ? WidgetTheme.TITLE
                    : WidgetTheme.TEXT_SECONDARY;
            graphics.drawString(minecraft.font, section.title, selectorPanelX + PANEL_PADDING, currentY, sectionColor);
            currentY += headerHeight + 5;

            int currentX = selectorPanelX + PANEL_PADDING;
            for (int itemIndex = 0; itemIndex < section.items.size(); itemIndex++) {
                QuickAccessItemConfig item = section.items.get(itemIndex);
                if (!isVisible(item)) {
                    continue;
                }

                renderItemCard(graphics, minecraft, item, sectionIndex, itemIndex, currentX, currentY, mouseX, mouseY);
                currentX += ITEM_SIZE + ITEM_GAP;
            }

            currentY += ITEM_SIZE + SECTION_GAP;
        }
    }

    private void renderItemCard(GuiGraphics graphics, Minecraft minecraft, QuickAccessItemConfig item, int sectionIndex, int itemIndex, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + ITEM_SIZE && mouseY >= y && mouseY <= y + ITEM_SIZE;
        boolean executable = canExecute(item);
        boolean selected = isEditorMode() && selectedSection == sectionIndex && selectedItem == itemIndex;
        boolean hiddenByDrag = dragging && dragSection == sectionIndex && dragItem == itemIndex;

        int bg = selected
                ? solidSoftPanelColor()
                : (hovered ? solidSoftPanelColor() : (isEditorMode() ? solidBarColor() : WidgetTheme.BAR_BG));
        int top = executable ? (selected || hovered ? WidgetTheme.ACCENT_LINE : solidPanelColor()) : disabledLineColor();

        HudSurface.fillRounded(graphics, x, y, ITEM_SIZE, ITEM_SIZE, 4, bg);
        graphics.fill(x + 4, y, x + ITEM_SIZE - 4, y + 1, top);

        if (!executable) {
            graphics.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, disabledOverlayColor());
        }

        if (hiddenByDrag) {
            graphics.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, dragShadowColor());
        } else {
            ItemStack stack = getDisplayStack(item);
            graphics.renderItem(stack, x + (ITEM_SIZE - 16) / 2, y + (ITEM_SIZE - 16) / 2);
        }

        itemLayouts.add(new ItemLayout(sectionIndex, itemIndex, x, y, ITEM_SIZE, ITEM_SIZE));
        if (hovered) {
            hoveredItem = item;
        }
    }

    private void renderEditorPanel(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.fill(editorPanelX, editorPanelY, editorPanelX + EDITOR_WIDTH, editorPanelY + editorPanelHeight, solidPanelColor());
        graphics.fill(editorPanelX, editorPanelY, editorPanelX + EDITOR_WIDTH, editorPanelY + 1, WidgetTheme.ACCENT_LINE);
        graphics.fill(editorPanelX, editorPanelY, editorPanelX + 1, editorPanelY + editorPanelHeight, WidgetTheme.ACCENT_LINE & 0x66FFFFFF);

        graphics.drawString(minecraft.font, "Inspector", editorPanelX + 10, editorPanelY + 8, WidgetTheme.TITLE);

        int labelX = editorPanelX + 10;
        int labelY = editorPanelY + 32;
        graphics.drawString(minecraft.font, "Section title", labelX, labelY, WidgetTheme.TEXT_SECONDARY);

        if (selectedItem >= 0) {
            graphics.drawString(minecraft.font, "Item title", labelX, labelY + 42, WidgetTheme.TEXT_SECONDARY);
            graphics.drawString(minecraft.font, "Command", labelX, labelY + 84, WidgetTheme.TEXT_SECONDARY);
            graphics.drawString(minecraft.font, "Item id", labelX, labelY + 126, WidgetTheme.TEXT_SECONDARY);
            graphics.drawString(minecraft.font, "Custom model data", labelX, labelY + 168, WidgetTheme.TEXT_SECONDARY);
        } else {
            graphics.drawString(minecraft.font, "Select an item to edit command and icon.", labelX, labelY + 42, WidgetTheme.TEXT_MUTED);
        }

    }

    private void renderEditorFooter(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int footerY = selectorPanelY + selectorPanelHeight + 8;
        int footerWidth = selectorPanelWidth;
        graphics.fill(selectorPanelX, footerY, selectorPanelX + footerWidth, footerY + EDITOR_HINT_HEIGHT, solidSoftPanelColor());
        graphics.fill(selectorPanelX, footerY, selectorPanelX + footerWidth, footerY + 1, WidgetTheme.ACCENT_LINE & 0x66FFFFFF);
        graphics.drawCenteredString(
                minecraft.font,
                "Drag item cards to move them",
                selectorPanelX + footerWidth / 2,
                footerY + 7,
                WidgetTheme.TEXT_MUTED
        );
        graphics.drawCenteredString(
                minecraft.font,
                "Use {player} for crosshair target",
                selectorPanelX + footerWidth / 2,
                footerY + 18,
                WidgetTheme.TEXT_MUTED
        );
    }

    private void renderDraggedPreview(GuiGraphics graphics) {
        QuickAccessItemConfig item = getSelectedItem();
        if (item == null) {
            return;
        }

        int x = dragMouseX - ITEM_SIZE / 2;
        int y = dragMouseY - ITEM_SIZE / 2;
        HudSurface.fillRounded(graphics, x, y, ITEM_SIZE, ITEM_SIZE, 4, dragPreviewColor());
        graphics.fill(x + 4, y, x + ITEM_SIZE - 4, y + 1, WidgetTheme.ACCENT_LINE);
        graphics.renderItem(getDisplayStack(item), x + (ITEM_SIZE - 16) / 2, y + (ITEM_SIZE - 16) / 2);
    }

    private void finishDrag(double mouseX, double mouseY) {
        dragging = false;

        if (dragSection < 0 || dragItem < 0) {
            dragSection = -1;
            dragItem = -1;
            return;
        }

        QuickAccessItemConfig moving = getItem(dragSection, dragItem);
        if (moving == null) {
            dragSection = -1;
            dragItem = -1;
            return;
        }

        ItemLayout itemTarget = findItemLayout(mouseX, mouseY);
        SectionLayout sectionTarget = findSectionLayout(mouseX, mouseY);

        ConfigManager.get().quickAccess.sections.get(dragSection).items.remove(dragItem);

        if (itemTarget != null) {
            int targetSection = itemTarget.sectionIndex();
            int targetIndex = itemTarget.itemIndex();
            if (targetSection == dragSection && dragItem < targetIndex) {
                targetIndex--;
            }
            ConfigManager.get().quickAccess.sections.get(targetSection).items.add(Math.max(0, targetIndex), moving);
            selectedSection = targetSection;
            selectedItem = targetIndex;
        } else if (sectionTarget != null) {
            int targetSection = sectionTarget.sectionIndex();
            List<QuickAccessItemConfig> items = ConfigManager.get().quickAccess.sections.get(targetSection).items;
            items.add(moving);
            selectedSection = targetSection;
            selectedItem = items.size() - 1;
        } else {
            ConfigManager.get().quickAccess.sections.get(dragSection).items.add(Math.min(dragItem, ConfigManager.get().quickAccess.sections.get(dragSection).items.size()), moving);
            selectedSection = dragSection;
            selectedItem = dragItem;
        }

        dragSection = -1;
        dragItem = -1;
        ConfigManager.save();
        rebuildEditorWidgets();
    }

    private void rebuildEditorWidgets() {
        if (!isEditorMode()) {
            return;
        }

        recalculatePanels();
        clearWidgets();

        int fieldX = editorPanelX + 10;
        int fieldWidth = EDITOR_WIDTH - 20;

        QuickAccessSectionConfig section = getSelectedSection();
        if (section == null) {
            return;
        }

        sectionTitleBox = createTextBox(fieldX, editorPanelY + 44, fieldWidth, section.title, value -> section.title = fallback(value, "Section"));
        addRenderableWidget(sectionTitleBox);

        if (selectedItem >= 0) {
            QuickAccessItemConfig item = getSelectedItem();
            itemTitleBox = createTextBox(fieldX, editorPanelY + 86, fieldWidth, item.title, value -> item.title = fallback(value, "Action"));
            itemCommandBox = createTextBox(fieldX, editorPanelY + 128, fieldWidth, item.command, value -> item.command = fallback(value, ""));
            itemIdBox = createTextBox(fieldX, editorPanelY + 170, fieldWidth, item.itemId, value -> item.itemId = fallback(value, "minecraft:paper"));
            itemModelBox = createTextBox(fieldX, editorPanelY + 212, fieldWidth, String.valueOf(item.customModelData), value -> item.customModelData = parseInt(value));

            addRenderableWidget(itemTitleBox);
            addRenderableWidget(itemCommandBox);
            addRenderableWidget(itemIdBox);
            addRenderableWidget(itemModelBox);
        }

        int buttonsY = editorPanelY + editorPanelHeight - 84;
        addRenderableWidget(new ThemedButton(fieldX, buttonsY, 96, 20, "+ Section", () -> {
            QuickAccessSectionConfig created = new QuickAccessSectionConfig();
            created.title = "Section";
            created.items.add(new QuickAccessItemConfig());
            ConfigManager.get().quickAccess.sections.add(created);
            selectedSection = ConfigManager.get().quickAccess.sections.size() - 1;
            selectedItem = -1;
            ConfigManager.save();
            rebuildEditorWidgets();
        }));

        addRenderableWidget(new ThemedButton(fieldX + 102, buttonsY, 72, 20, "+ Item", () -> {
            QuickAccessSectionConfig selected = getSelectedSection();
            if (selected == null) {
                return;
            }
            selected.items.add(new QuickAccessItemConfig());
            selectedItem = selected.items.size() - 1;
            ConfigManager.save();
            rebuildEditorWidgets();
        }));

        addRenderableWidget(new ThemedButton(fieldX, buttonsY + 24, 96, 20, "- Item", () -> {
            QuickAccessSectionConfig selected = getSelectedSection();
            if (selected == null || selectedItem < 0 || selectedItem >= selected.items.size()) {
                return;
            }
            selected.items.remove(selectedItem);
            selectedItem = Math.min(selectedItem, selected.items.size() - 1);
            ConfigManager.save();
            rebuildEditorWidgets();
        }));

        addRenderableWidget(new ThemedButton(fieldX + 102, buttonsY + 24, 72, 20, "- Section", () -> {
            if (ConfigManager.get().quickAccess.sections.size() <= 1) {
                return;
            }
            ConfigManager.get().quickAccess.sections.remove(selectedSection);
            selectedSection = Math.max(0, Math.min(selectedSection, ConfigManager.get().quickAccess.sections.size() - 1));
            selectedItem = -1;
            ConfigManager.save();
            rebuildEditorWidgets();
        }));

        addRenderableWidget(new ThemedButton(
                fieldX,
                editorPanelY + editorPanelHeight - 28,
                fieldWidth,
                20,
                "Done",
                this::onClose
        ));
    }

    private ThemedTextField createTextBox(int x, int y, int width, String initialValue, java.util.function.Consumer<String> onChange) {
        return new ThemedTextField(this.font, x, y, width, 18, initialValue, onChange);
    }

    private void recalculatePanels() {
        List<QuickAccessSectionConfig> visibleSections = ConfigManager.get().quickAccess.sections.stream()
                .filter(section -> section != null && section.items != null && section.items.stream().anyMatch(this::isVisible))
                .toList();

        int contentWidth = getContentWidth(visibleSections);
        int contentHeight = getContentHeight(visibleSections);
        selectorPanelWidth = contentWidth + PANEL_PADDING * 2;
        selectorPanelHeight = contentHeight + PANEL_PADDING * 2 + LABEL_HEIGHT;

        int totalWidth = selectorPanelWidth + (isEditorMode() ? EDITOR_GAP + EDITOR_WIDTH : 0);
        int totalHeight = selectorPanelHeight + (isEditorMode() ? EDITOR_HINT_HEIGHT + 8 : 0);
        selectorPanelX = (this.width - totalWidth) / 2;
        selectorPanelY = (this.height - totalHeight) / 2;
        editorPanelX = selectorPanelX + selectorPanelWidth + EDITOR_GAP;
        editorPanelY = selectorPanelY;
        editorPanelHeight = Math.max(selectorPanelHeight, EDITOR_MIN_HEIGHT);
    }

    private ItemStack getDisplayStack(QuickAccessItemConfig item) {
        String cacheKey = item.itemId + "|" + item.customModelData;
        return iconCache.computeIfAbsent(cacheKey, ignored -> createStack(item));
    }

    private ItemStack createStack(QuickAccessItemConfig item) {
        ResourceLocation location = ResourceLocation.tryParse(item.itemId);
        ItemStack stack = location != null ? new ItemStack(BuiltInRegistries.ITEM.getValue(location)) : ItemStack.EMPTY;
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.PAPER);
        }

        if (item.customModelData > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) item.customModelData), List.of(), List.of(), List.of()));
        }

        return stack;
    }

    private boolean isVisible(QuickAccessItemConfig item) {
        return item != null && item.visible && item.command != null && !item.command.isBlank();
    }

    private boolean canExecute(QuickAccessItemConfig item) {
        return !QuickAccessCommandResolver.requiresTarget(item) || QuickAccessCommandResolver.resolveTargetPlayerName() != null;
    }

    private int getContentWidth(List<QuickAccessSectionConfig> sections) {
        int maxItems = 0;
        for (QuickAccessSectionConfig section : sections) {
            int size = (int) section.items.stream().filter(this::isVisible).count();
            maxItems = Math.max(maxItems, size);
        }

        return Math.max(180, maxItems * ITEM_SIZE + Math.max(0, maxItems - 1) * ITEM_GAP);
    }

    private int getContentHeight(List<QuickAccessSectionConfig> sections) {
        int total = 0;
        for (QuickAccessSectionConfig section : sections) {
            long visibleItems = section.items.stream().filter(this::isVisible).count();
            if (visibleItems == 0) {
                continue;
            }
            total += this.font.lineHeight + 5 + ITEM_SIZE + SECTION_GAP;
        }

        return Math.max(48, total - (sections.isEmpty() ? 0 : SECTION_GAP));
    }

    private void ensureConfigState() {
        if (ConfigManager.get().quickAccess.sections == null || ConfigManager.get().quickAccess.sections.isEmpty()) {
            ConfigManager.get().quickAccess.sections = QuickAccessConfig.createDefaultSections();
            ConfigManager.save();
        }
    }

    private void clampSelection() {
        List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;
        selectedSection = Math.max(0, Math.min(selectedSection, sections.size() - 1));
        QuickAccessSectionConfig section = sections.get(selectedSection);
        if (section.items == null) {
            section.items = new ArrayList<>();
        }
        if (section.items.isEmpty()) {
            selectedItem = -1;
        } else if (selectedItem >= section.items.size()) {
            selectedItem = section.items.size() - 1;
        }
    }

    private QuickAccessSectionConfig getSelectedSection() {
        List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;
        if (selectedSection < 0 || selectedSection >= sections.size()) {
            return null;
        }
        return sections.get(selectedSection);
    }

    private QuickAccessItemConfig getSelectedItem() {
        QuickAccessSectionConfig section = getSelectedSection();
        if (section == null || selectedItem < 0 || selectedItem >= section.items.size()) {
            return null;
        }
        return section.items.get(selectedItem);
    }

    private QuickAccessItemConfig getItem(int sectionIndex, int itemIndex) {
        if (sectionIndex < 0 || sectionIndex >= ConfigManager.get().quickAccess.sections.size()) {
            return null;
        }
        QuickAccessSectionConfig section = ConfigManager.get().quickAccess.sections.get(sectionIndex);
        if (itemIndex < 0 || itemIndex >= section.items.size()) {
            return null;
        }
        return section.items.get(itemIndex);
    }

    private ItemLayout findItemLayout(double mouseX, double mouseY) {
        for (ItemLayout layout : itemLayouts) {
            if (mouseX >= layout.x() && mouseX <= layout.x() + layout.width()
                    && mouseY >= layout.y() && mouseY <= layout.y() + layout.height()) {
                return layout;
            }
        }
        return null;
    }

    private SectionLayout findSectionLayout(double mouseX, double mouseY) {
        for (SectionLayout layout : sectionLayouts) {
            if (mouseX >= layout.x() && mouseX <= layout.x() + layout.width()
                    && mouseY >= layout.y() && mouseY <= layout.y() + layout.height()) {
                return layout;
            }
        }
        return null;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int solidPanelColor() {
        return 0xE0000000 | (WidgetTheme.PANEL_BG & 0x00FFFFFF);
    }

    private int solidSoftPanelColor() {
        return 0xD6000000 | (WidgetTheme.PANEL_BG_SOFT & 0x00FFFFFF);
    }

    private int solidBarColor() {
        return 0xCC000000 | (WidgetTheme.BAR_BG & 0x00FFFFFF);
    }

    private int backdropColor() {
        return 0x66000000 | (WidgetTheme.PANEL_BG & 0x00FFFFFF);
    }

    private int disabledOverlayColor() {
        return 0x66000000 | (WidgetTheme.BAR_BG & 0x00FFFFFF);
    }

    private int disabledLineColor() {
        return 0x99000000 | (WidgetTheme.TEXT_MUTED & 0x00FFFFFF);
    }

    private int dragShadowColor() {
        return 0x88000000 | (WidgetTheme.BAR_BG & 0x00FFFFFF);
    }

    private int dragPreviewColor() {
        return 0xE0000000 | (WidgetTheme.PANEL_BG_SOFT & 0x00FFFFFF);
    }

    private enum Mode {
        RUNTIME,
        EDITOR
    }

    private record ItemLayout(int sectionIndex, int itemIndex, int x, int y, int width, int height) {
    }

    private record SectionLayout(int sectionIndex, int x, int y, int width, int height) {
    }

    private final class ThemedButton extends AbstractWidget {
        private final Runnable onPress;

        private ThemedButton(int x, int y, int width, int height, String label, Runnable onPress) {
            super(x, y, width, height, Component.literal(label));
            this.onPress = onPress;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int bg = hovered ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.BAR_BG;
            int line = hovered ? WidgetTheme.ACCENT_LINE : (WidgetTheme.ACCENT_LINE & 0x66FFFFFF);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, line);
            graphics.drawCenteredString(
                    QuickAccessScreen.this.font,
                    getMessage(),
                    getX() + width / 2,
                    getY() + (height - 8) / 2,
                    hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY
            );
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    private final class ThemedTextField extends AbstractWidget {
        private static final int TEXT_PADDING = 6;

        private final Font font;
        private final java.util.function.Consumer<String> onChange;
        private String value;
        private int cursor;
        private int displayOffset;

        private ThemedTextField(Font font, int x, int y, int width, int height, String initialValue, java.util.function.Consumer<String> onChange) {
            super(x, y, width, height, Component.empty());
            this.font = font;
            this.onChange = onChange;
            this.value = initialValue == null ? "" : initialValue;
            this.cursor = this.value.length();
            syncOffsetToCursor();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean focused = isFocused();
            boolean hovered = isHoveredOrFocused();
            int bg = focused ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.BAR_BG;
            int line = focused ? WidgetTheme.ACCENT_LINE : (hovered ? WidgetTheme.TEXT_SECONDARY : WidgetTheme.TEXT_MUTED);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, line);

            String visible = getVisibleText();
            int textY = getY() + (height - 8) / 2;
            graphics.drawString(font, visible, getX() + TEXT_PADDING, textY, WidgetTheme.TEXT_PRIMARY);

            if (focused) {
                int cursorX = getX() + TEXT_PADDING + font.width(value.substring(displayOffset, cursor));
                graphics.fill(cursorX, getY() + 4, cursorX + 1, getY() + height - 4, WidgetTheme.TITLE);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setFocused(true);
            cursor = getCursorFromMouse(mouseX);
            syncOffsetToCursor();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused()) {
                return false;
            }

            if (Screen.isPaste(keyCode)) {
                insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }

            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) {
                        value = value.substring(0, cursor - 1) + value.substring(cursor);
                        cursor--;
                        changed();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < value.length()) {
                        value = value.substring(0, cursor) + value.substring(cursor + 1);
                        changed();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    cursor = Math.max(0, cursor - 1);
                    syncOffsetToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    cursor = Math.min(value.length(), cursor + 1);
                    syncOffsetToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    cursor = 0;
                    syncOffsetToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    cursor = value.length();
                    syncOffsetToCursor();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (!isFocused() || Character.isISOControl(codePoint)) {
                return false;
            }

            insert(String.valueOf(codePoint));
            return true;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused) {
                syncOffsetToCursor();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        private void insert(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            value = value.substring(0, cursor) + text + value.substring(cursor);
            cursor += text.length();
            changed();
        }

        private void changed() {
            syncOffsetToCursor();
            onChange.accept(value);
            ConfigManager.save();
        }

        private void syncOffsetToCursor() {
            int availableWidth = width - TEXT_PADDING * 2;
            displayOffset = Math.max(0, Math.min(displayOffset, cursor));

            while (font.width(value.substring(displayOffset, cursor)) > availableWidth && displayOffset < cursor) {
                displayOffset++;
            }

            while (displayOffset > 0 && font.width(value.substring(displayOffset - 1, cursor)) <= availableWidth) {
                displayOffset--;
            }
        }

        private String getVisibleText() {
            int end = value.length();
            while (end > displayOffset && font.width(value.substring(displayOffset, end)) > width - TEXT_PADDING * 2) {
                end--;
            }
            return value.substring(displayOffset, end);
        }

        private int getCursorFromMouse(double mouseX) {
            int localX = (int) mouseX - getX() - TEXT_PADDING;
            if (localX <= 0) {
                return displayOffset;
            }

            int best = displayOffset;
            for (int index = displayOffset; index <= value.length(); index++) {
                int textWidth = font.width(value.substring(displayOffset, index));
                if (textWidth >= localX) {
                    return index;
                }
                best = index;
            }
            return best;
        }
    }
}
