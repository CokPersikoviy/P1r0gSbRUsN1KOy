package ru.wilyfox.client.quickaccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.List;

public class QuickAccessEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 300;
    private static final int LIST_WIDTH = 180;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 3;

    private final Screen parent;

    private int panelX;
    private int panelY;
    private int selectedSection = 0;
    private int selectedItem = -1;

    private EditBox sectionTitleBox;
    private EditBox itemTitleBox;
    private EditBox itemCommandBox;
    private EditBox itemIdBox;
    private EditBox itemModelBox;

    public QuickAccessEditorScreen(Screen parent) {
        super(Component.literal("Quick Access Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureConfigState();
        clampSelection();

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        clearWidgets();
        initToolbar();
        initEditorFields();
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
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/QuickAccessEditorScreen/render")) {
            graphics.fill(0, 0, this.width, this.height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x50));

            panelX = (this.width - PANEL_WIDTH) / 2;
            panelY = (this.height - PANEL_HEIGHT) / 2;

            // Not the in-game HUD, so capture the screen here for the frosted panel's blur backdrop.
            HudBlur.beginFrame(graphics);
            HudSurface.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, WidgetChrome.FROST, HudSurface.nativeRenderer());
            graphics.fill(panelX + LIST_WIDTH, panelY + HEADER_HEIGHT, panelX + LIST_WIDTH + 1, panelY + PANEL_HEIGHT - 8, WidgetTheme.BAR_BG);

            graphics.drawString(this.font, "Quick Access", panelX + 10, panelY + 8, WidgetTheme.TITLE);
            graphics.drawString(this.font, "Editor", panelX + 92, panelY + 8, WidgetTheme.TEXT_SECONDARY);

            renderSelectionList(graphics, mouseX, mouseY);

            super.render(graphics, mouseX, mouseY, partialTick);

            renderEditorLabels(graphics);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleListClick(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderSelectionList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;
        int listX = panelX + 8;
        int listY = panelY + HEADER_HEIGHT + 8;
        int rowY = listY;

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            QuickAccessSectionConfig section = sections.get(sectionIndex);
            boolean sectionSelected = selectedSection == sectionIndex && selectedItem < 0;
            boolean sectionHovered = isHovered(mouseX, mouseY, listX, rowY, LIST_WIDTH - 16, ROW_HEIGHT);

            renderListRow(graphics, listX, rowY, LIST_WIDTH - 16, ROW_HEIGHT, sectionSelected, sectionHovered, section.title);
            rowY += ROW_HEIGHT + ROW_GAP;

            for (int itemIndex = 0; itemIndex < section.items.size(); itemIndex++) {
                QuickAccessItemConfig item = section.items.get(itemIndex);
                boolean itemSelected = selectedSection == sectionIndex && selectedItem == itemIndex;
                boolean itemHovered = isHovered(mouseX, mouseY, listX + 12, rowY, LIST_WIDTH - 28, ROW_HEIGHT);
                String label = item.title == null || item.title.isBlank() ? "Action" : item.title;
                renderListRow(graphics, listX + 12, rowY, LIST_WIDTH - 28, ROW_HEIGHT, itemSelected, itemHovered, label);
                rowY += ROW_HEIGHT + ROW_GAP;
            }

            rowY += 4;
        }
    }

    private void renderListRow(GuiGraphics graphics, int x, int y, int width, int height, boolean selected, boolean hovered, String text) {
        int bg = selected ? WidgetTheme.PANEL_BG_SOFT : (hovered ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG);
        int textColor = selected ? WidgetTheme.TITLE : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY);

        graphics.fill(x, y, x + width, y + height, bg);
        if (selected) {
            graphics.fill(x, y, x + width, y + 1, WidgetTheme.ACCENT_LINE);
        }

        graphics.drawString(this.font, text, x + 6, y + (height - this.font.lineHeight) / 2, textColor);
    }

    private boolean handleListClick(double mouseX, double mouseY) {
        List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;
        int listX = panelX + 8;
        int listY = panelY + HEADER_HEIGHT + 8;
        int rowY = listY;

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            if (isHovered(mouseX, mouseY, listX, rowY, LIST_WIDTH - 16, ROW_HEIGHT)) {
                selectedSection = sectionIndex;
                selectedItem = -1;
                init();
                return true;
            }
            rowY += ROW_HEIGHT + ROW_GAP;

            for (int itemIndex = 0; itemIndex < sections.get(sectionIndex).items.size(); itemIndex++) {
                if (isHovered(mouseX, mouseY, listX + 12, rowY, LIST_WIDTH - 28, ROW_HEIGHT)) {
                    selectedSection = sectionIndex;
                    selectedItem = itemIndex;
                    init();
                    return true;
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }

            rowY += 4;
        }

        return false;
    }

    private void initToolbar() {
        int buttonY = panelY + PANEL_HEIGHT - 28;
        int buttonX = panelX + 8;

        addRenderableWidget(Button.builder(Component.literal("+ Section"), button -> {
            QuickAccessSectionConfig section = new QuickAccessSectionConfig();
            section.title = "Section";
            section.items.add(new QuickAccessItemConfig());
            ConfigManager.get().quickAccess.sections.add(section);
            selectedSection = ConfigManager.get().quickAccess.sections.size() - 1;
            selectedItem = -1;
            ConfigManager.save();
            init();
        }).bounds(buttonX, buttonY, 78, 20).build());

        addRenderableWidget(Button.builder(Component.literal("+ Item"), button -> {
            QuickAccessSectionConfig section = getSelectedSection();
            if (section == null) {
                return;
            }

            QuickAccessItemConfig item = new QuickAccessItemConfig();
            section.items.add(item);
            selectedItem = section.items.size() - 1;
            ConfigManager.save();
            init();
        }).bounds(buttonX + 84, buttonY, 66, 20).build());

        addRenderableWidget(Button.builder(Component.literal("- Item"), button -> {
            QuickAccessSectionConfig section = getSelectedSection();
            if (section == null || selectedItem < 0 || selectedItem >= section.items.size()) {
                return;
            }

            section.items.remove(selectedItem);
            selectedItem = Math.min(selectedItem, section.items.size() - 1);
            ConfigManager.save();
            init();
        }).bounds(buttonX + 156, buttonY, 66, 20).build());

        addRenderableWidget(Button.builder(Component.literal("- Section"), button -> {
            List<QuickAccessSectionConfig> sections = ConfigManager.get().quickAccess.sections;
            if (sections.size() <= 1 || selectedSection < 0 || selectedSection >= sections.size()) {
                return;
            }

            sections.remove(selectedSection);
            selectedSection = Math.min(selectedSection, sections.size() - 1);
            selectedItem = -1;
            ConfigManager.save();
            init();
        }).bounds(buttonX + 228, buttonY, 78, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(panelX + PANEL_WIDTH - 68, buttonY, 60, 20)
                .build());
    }

    private void initEditorFields() {
        int formX = panelX + LIST_WIDTH + 18;
        int formY = panelY + HEADER_HEIGHT + 18;
        int fieldWidth = PANEL_WIDTH - LIST_WIDTH - 36;

        QuickAccessSectionConfig section = getSelectedSection();
        if (section == null) {
            return;
        }

        sectionTitleBox = createTextBox(formX, formY + 10, fieldWidth, section.title, value -> section.title = fallback(value, "Section"));
        addRenderableWidget(sectionTitleBox);

        if (selectedItem < 0 || selectedItem >= section.items.size()) {
            return;
        }

        QuickAccessItemConfig item = section.items.get(selectedItem);

        itemTitleBox = createTextBox(formX, formY + 52, fieldWidth, item.title, value -> item.title = fallback(value, "Action"));
        itemCommandBox = createTextBox(formX, formY + 94, fieldWidth, item.command, value -> item.command = fallback(value, ""));
        itemIdBox = createTextBox(formX, formY + 136, fieldWidth, item.itemId, value -> item.itemId = fallback(value, "minecraft:paper"));
        itemModelBox = createTextBox(formX, formY + 178, fieldWidth, String.valueOf(item.customModelData), value -> {
            item.customModelData = parseInt(value);
        });

        addRenderableWidget(itemTitleBox);
        addRenderableWidget(itemCommandBox);
        addRenderableWidget(itemIdBox);
        addRenderableWidget(itemModelBox);
    }

    private void renderEditorLabels(GuiGraphics graphics) {
        int formX = panelX + LIST_WIDTH + 18;
        int formY = panelY + HEADER_HEIGHT + 18;

        graphics.drawString(this.font, "Section title", formX, formY, WidgetTheme.TEXT_SECONDARY);

        QuickAccessSectionConfig section = getSelectedSection();
        if (section == null || selectedItem < 0 || selectedItem >= section.items.size()) {
            graphics.drawString(this.font, "Select an item to edit its command and icon.", formX, formY + 52, WidgetTheme.TEXT_MUTED);
            return;
        }

        graphics.drawString(this.font, "Item title", formX, formY + 42, WidgetTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, "Command", formX, formY + 84, WidgetTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, "Item id", formX, formY + 126, WidgetTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, "Custom model data", formX, formY + 168, WidgetTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, "Use {player} to target player under crosshair.", formX, panelY + PANEL_HEIGHT - 44, WidgetTheme.TEXT_MUTED);
    }

    private EditBox createTextBox(int x, int y, int width, String initialValue, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.empty());
        box.setValue(initialValue == null ? "" : initialValue);
        box.setResponder(value -> {
            onChange.accept(value);
            ConfigManager.save();
        });
        return box;
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
            section.items = new java.util.ArrayList<>();
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

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
}
