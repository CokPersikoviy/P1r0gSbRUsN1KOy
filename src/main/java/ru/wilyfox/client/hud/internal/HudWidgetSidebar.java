package ru.wilyfox.client.hud.internal;

import ru.wilyfox.client.audio.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.List;
import java.util.Locale;

/** Editor-only widget library. It never changes the coordinate system of the saved HUD. */
public final class HudWidgetSidebar {
    private static final int X = 8, Y = 8, ROW_HEIGHT = 23, LIST_TOP = 57, FOOTER_HEIGHT = 24;
    private final HudRenderer renderer;
    private boolean collapsed;
    private boolean layoutMenu;
    private int layoutScroll;
    private Widget hoveredSoundWidget;
    private boolean hidden;
    private boolean searchFocused;
    private String search = "";
    private List<Widget> filtered = List.of();
    private long cachedRevision = -1;
    private int scroll;
    private int width = 180, height = 220;

    public HudWidgetSidebar(HudRenderer renderer) { this.renderer = renderer; }

    public void open() { searchFocused = false; hidden = false; layoutMenu = false; }

    public void toggleVisibility() {
        hidden = !hidden;
        layoutMenu = false;
        UiSounds.openClose();
        searchFocused = false;
    }

    private boolean isShown() { return !hidden && !renderer.isWidgetDragging(); }

    public void render(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        UiSounds.update();
        if (!isShown()) { hoveredSoundWidget = null; return; }
        refreshBounds();
        refreshWidgets();
        var font = Minecraft.getInstance().font;
        if (collapsed) {
            HudSurface.fillRounded(graphics, X, Y, 96, 24, 5, WidgetTheme.PANEL_BG);
            graphics.text(font, "Widgets >", X + 10, Y + 8, WidgetTheme.TITLE);
            return;
        }

        HudSurface.fillRounded(graphics, X, Y, width, height, 6, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xF2));
        graphics.text(font, fit(renderer.getSelectedLayoutName(), width - 62), X + 10, Y + 10, WidgetTheme.TITLE);
        if (!renderer.getSelectedLayout().isEmpty()) graphics.text(font, "...", X + width - 42, Y + 10, WidgetTheme.TEXT_ACCENT);
        graphics.text(font, "<", X + width - 17, Y + 10, WidgetTheme.TEXT_SOFT);
        int searchY = Y + 29;
        HudSurface.fillRounded(graphics, X + 7, searchY, width - 14, 21, 4, WidgetTheme.PANEL_BG_SOFT);
        if (searchFocused) graphics.fill(X + 7, searchY + 20, X + width - 7, searchY + 21, WidgetTheme.ACCENT_LINE);
        String label = search.isEmpty() && !searchFocused ? "Search widgets..." : search + (searchFocused ? "_" : "");
        graphics.text(font, fit(label, width - 24), X + 12, searchY + 7,
                search.isEmpty() && !searchFocused ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_PRIMARY);

        int listTop = Y + LIST_TOP, listBottom = Y + height - FOOTER_HEIGHT;
        clampScroll();
        graphics.enableScissor(X + 5, listTop, X + width - 5, listBottom);
        int first = scroll / ROW_HEIGHT;
        int last = Math.min(filtered.size(), (scroll + listBottom - listTop + ROW_HEIGHT - 1) / ROW_HEIGHT);
        Widget soundWidget = null;
        for (int i = first; i < last; i++) {
            Widget widget = filtered.get(i);
            int rowY = listTop + i * ROW_HEIGHT - scroll;
            boolean added = renderer.isWidgetAdded(widget);
            boolean hovered = mouseX >= X + 6 && mouseX < X + width - 6 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            if (hovered && mouseY >= listTop && mouseY < listBottom) soundWidget = widget;
            boolean selected = widget == renderer.getSelectedWidget();
            int background = hovered || selected ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.withAlpha(WidgetTheme.PANEL_BG_SOFT, 0x66);
            HudSurface.fillRounded(graphics, X + 6, rowY, width - 12, ROW_HEIGHT - 2, 3, background);
            if (added) graphics.fill(X + 6, rowY + 3, X + 8, rowY + ROW_HEIGHT - 5, WidgetTheme.ACCENT_LINE);
            graphics.text(font, fit(widget.getDisplayName(), width - 46), X + 12, rowY + 7,
                    added ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY);
            graphics.text(font, added ? "-" : "+", X + width - 22, rowY + 7,
                    added ? WidgetTheme.STATUS_WARNING : WidgetTheme.TEXT_ACCENT);
        }
        if (soundWidget != null && soundWidget != hoveredSoundWidget) UiSounds.hover();
        hoveredSoundWidget = soundWidget;
        if (filtered.isEmpty()) graphics.text(font, "No matches", X + 12, listTop + 7, WidgetTheme.TEXT_MUTED);
        graphics.disableScissor();

        int totalHeight = filtered.size() * ROW_HEIGHT;
        int viewport = listBottom - listTop;
        if (totalHeight > viewport) {
            int thumb = Math.max(12, viewport * viewport / totalHeight);
            int thumbY = listTop + (viewport - thumb) * scroll / Math.max(1, totalHeight - viewport);
            graphics.fill(X + width - 4, thumbY, X + width - 2, thumbY + thumb, WidgetTheme.TEXT_MUTED);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(X + 10, Y + height - 15);
        graphics.pose().scale(.8f, .8f);
        graphics.text(font, renderer.getLayoutWidgetCount() + "/" + renderer.getRegisteredWidgetCount(),
                0, 0, WidgetTheme.TEXT_MUTED);
        graphics.pose().popMatrix();

        if (renderer.getLayoutWidgetCount() == 0 && !layoutMenu) {
            int canvasCenter = X + width + (Minecraft.getInstance().getWindow().getGuiScaledWidth() - X - width) / 2;
            int canvasY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
            graphics.centeredText(font, "Your layout is empty", canvasCenter, canvasY - 10, WidgetTheme.TITLE);
            graphics.centeredText(font, "Add widgets from the panel", canvasCenter, canvasY + 5, WidgetTheme.TEXT_SECONDARY);
        }
        if (layoutMenu) renderLayoutMenu(graphics);
    }

    private int layoutMenuHeight() { return Math.min(height - 30, (ConfigManager.get().locationLayouts.size() + 2) * ROW_HEIGHT + 8); }
    private void renderLayoutMenu(GuiGraphicsExtractor graphics) {
        var font = Minecraft.getInstance().font;
        int top = Y + 26, h = layoutMenuHeight();
        HudSurface.fillRounded(graphics, X + 4, top, width - 8, h, 4, WidgetTheme.PANEL_BG);
        graphics.enableScissor(X + 5, top + 4, X + width - 5, top + h - 4);
        var ids = new java.util.ArrayList<>(ConfigManager.get().locationLayouts.keySet());
        for (int i = 0; i < ids.size() + 2; i++) {
            int rowY = top + 4 + i * ROW_HEIGHT - layoutScroll;
            if (rowY + ROW_HEIGHT < top || rowY >= top + h) continue;
            String id = i == 0 ? "" : i <= ids.size() ? ids.get(i - 1) : null;
            String name = id == null ? "+ New layout" : id.isEmpty() ? "Main Layout" : ConfigManager.get().locationLayouts.get(id).name;
            boolean selected = id != null && id.equals(renderer.getSelectedLayout());
            if (selected) graphics.fill(X + 7, rowY, X + width - 7, rowY + ROW_HEIGHT - 2, WidgetTheme.PANEL_BG_SOFT);
            graphics.text(font, fit(name, width - 30), X + 12, rowY + 7, selected ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_PRIMARY);
        }
        graphics.disableScissor();
    }

    public boolean contains(double x, double y) {
        if (!isShown()) return false;
        refreshBounds();
        return x >= X && x < X + (collapsed ? 96 : width) && y >= Y && y < Y + (collapsed ? 24 : height);
    }

    public boolean mousePressed(double x, double y, int button) {
        if (layoutMenu) {
            int top = Y + 26;
            if (button == 0 && x >= X + 4 && x < X + width - 4 && y >= top + 4 && y < top + layoutMenuHeight() - 4) {
                var ids = new java.util.ArrayList<>(ConfigManager.get().locationLayouts.keySet());
                int index = ((int) y - top - 4 + layoutScroll) / ROW_HEIGHT;
                layoutMenu = false; UiSounds.click();
                if (index == 0) renderer.selectLayout("");
                else if (index <= ids.size()) renderer.selectLayout(ids.get(index - 1));
                else renderer.createLayout();
            } else { layoutMenu = false; }
            return true;
        }
        if (!contains(x, y)) { searchFocused = false; return false; }
        if (button == 1 && !collapsed && y >= Y + LIST_TOP && y < Y + height - FOOTER_HEIGHT) {
            refreshWidgets();
            int index = ((int) y - Y - LIST_TOP + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < filtered.size()) renderer.openWidgetSettings(filtered.get(index));
            return true;
        }
        if (button != 0) return true;
        if (collapsed) { collapsed = false; UiSounds.click(); return true; }
        if (y < Y + 26 && x >= X + width - 28) {
            collapsed = true;
            UiSounds.click();
            searchFocused = false;
            return true;
        }
        if (y < Y + 26) {
            searchFocused = false; UiSounds.click();
            if (x >= X + width - 51 && !renderer.getSelectedLayout().isEmpty()) renderer.openLayoutSettings();
            else { layoutMenu = true; layoutScroll = 0; }
            return true;
        }
        searchFocused = y >= Y + 29 && y < Y + 50;
        refreshWidgets();
        if (y >= Y + LIST_TOP && y < Y + height - FOOTER_HEIGHT) {
            int index = ((int) y - Y - LIST_TOP + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < filtered.size()) {
                Widget widget = filtered.get(index);
                UiSounds.click();
                if (!renderer.isWidgetAdded(widget)) renderer.addWidget(widget);
                else if (x >= X + width - 31) renderer.removeWidget(widget);
                else renderer.queueWidgetDragFromLibrary(widget, x, y);
            }
        }
        return true;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (!contains(x, y)) return false;
        if (layoutMenu) {
            int max = Math.max(0, (ConfigManager.get().locationLayouts.size() + 2) * ROW_HEIGHT - (layoutMenuHeight() - 8));
            int previous = layoutScroll;
            layoutScroll = Math.clamp(layoutScroll - (int) Math.round(amount * ROW_HEIGHT * 2), 0, max);
            if (previous != layoutScroll) UiSounds.scroll();
            return true;
        }
        if (!collapsed) {
            refreshWidgets();
            int previous = scroll;
            scroll -= (int) Math.round(amount * ROW_HEIGHT * 2);
            clampScroll();
            if (previous != scroll) UiSounds.scroll();
        }
        return true;
    }

    public boolean keyPressed(int key) {
        if (isShown() && layoutMenu) {
            if (key == GLFW.GLFW_KEY_ESCAPE) layoutMenu = false;
            return true;
        }
        if (!isShown() || !searchFocused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
            invalidateSearch();
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            search = "";
            invalidateSearch();
        }
        return true;
    }

    public boolean charTyped(int codePoint) {
        if (!isShown() || !searchFocused) return false;
        if (!Character.isISOControl(codePoint) && search.codePointCount(0, search.length()) < 48) {
            search += new String(Character.toChars(codePoint));
            invalidateSearch();
        }
        return true;
    }

    public int canvasLeft() { refreshBounds(); return hidden || collapsed ? 8 : X + width + 12; }

    private void invalidateSearch() { cachedRevision = -1; scroll = 0; }

    private void refreshWidgets() {
        if (cachedRevision == renderer.getWidgetRegistryRevision()) return;
        String query = search.toLowerCase(Locale.ROOT).strip();
        filtered = renderer.getWidgets().stream()
                .filter(widget -> widget.getDisplayName().toLowerCase(Locale.ROOT).contains(query)).toList();
        cachedRevision = renderer.getWidgetRegistryRevision();
        clampScroll();
    }

    private void refreshBounds() {
        var window = Minecraft.getInstance().getWindow();
        width = Math.min(198, Math.max(146, window.getGuiScaledWidth() / 3));
        height = Math.max(142, window.getGuiScaledHeight() - 16);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() * ROW_HEIGHT - (height - LIST_TOP - FOOTER_HEIGHT))));
    }

    private String fit(String text, int availableWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= availableWidth) return text;
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + "...") > availableWidth) end--;
        return text.substring(0, end) + "...";
    }
}
