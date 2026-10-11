package ru.wilyfox.client.hud.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.audio.UiSoundScreen;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.widget.*;
import java.util.List;

public final class ConfigProfilesScreen extends UiSoundScreen {
    private final Screen previous;
    private final boolean wasEditing, wasSettings;
    private int scroll;
    private Component status = Component.empty();
    private int panelWidth() { return Math.min(380, width - 16); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int rows() { return Math.max(1, Math.min(ConfigProfiles.entries().size(), Math.min(12, (height - 104) / 26))); }
    private int panelY() { return Math.max(8, (height - (rows() * 26 + 82)) / 2); }
    public ConfigProfilesScreen(Screen previous) {
        super(Component.translatable("config.froghelper.title")); this.previous = previous;
        var renderer = ru.wilyfox.client.Client.getInstance().getHudRenderer();
        wasEditing = renderer.isEditing(); wasSettings = renderer.isSettingsOpen();
    }
    @Override public boolean isPauseScreen() { return false; }
    private List<ConfigProfileStore.Entry> entries() {
        var entries = ConfigProfiles.entries();
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - rows()));
        return entries;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float tick) {
        var font = minecraft.font; var entries = entries();
        int x = panelX(), y = panelY(), w = panelWidth();
        graphics.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x80));
        HudBlur.beginFrame(graphics);
        HudSurface.drawPanel(graphics, x, y, w, rows() * 26 + 82, WidgetChrome.FROST, HudSurface.nativeRenderer());
        graphics.text(font, title, x + 12, y + 10, WidgetTheme.TITLE);
        graphics.text(font, Component.translatable("config.froghelper.hint"), x + 12, y + 25, WidgetTheme.TEXT_MUTED);
        for (int row = 0; row < rows() && row + scroll < entries.size(); row++) {
            var entry = entries.get(row + scroll); int top = y + 44 + row * 26;
            boolean main = ConfigProfileStore.MAIN.equals(entry.id()), active = entry.id().equals(ConfigProfiles.activeId());
            boolean hovered = mx >= x + 10 && mx < x + w - 10 && my >= top && my < top + 22;
            HudSurface.fillRounded(graphics, x + 10, top, w - 20, 22, 3, hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
            var label = main ? Component.translatable("config.froghelper.main") : Component.literal(entry.author());
            String shown = font.plainSubstrByWidth(label.getString(), Math.max(20, w - 154));
            graphics.text(font, shown, x + 18, top + 7, active ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);
            graphics.text(font, Component.translatable(active ? "config.froghelper.active" : "config.froghelper.select"), x + w - 124, top + 7, WidgetTheme.TEXT_SOFT);
            if (!main) graphics.text(font, "×", x + w - 27, top + 7, WidgetTheme.TEXT_MUTED);
            if (hovered) graphics.setTooltipForNextFrame(font, main ? Component.translatable("config.froghelper.main_hint")
                    : Component.translatable("config.froghelper.author", entry.author()), mx, my);
        }
        int footer = y + 48 + rows() * 26;
        var lines = font.split(status, w - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.text(font, lines.get(i), x + 12, footer + i * font.lineHeight, WidgetTheme.TEXT_SOFT);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int x = panelX(), y = panelY(), w = panelWidth();
        int row = (int) ((event.y() - y - 44) / 26); var entries = entries();
        if (event.y() < y + 44 || row < 0 || row >= rows() || row + scroll >= entries.size()
                || event.y() >= y + 44 + row * 26 + 22 || event.x() < x + 10 || event.x() >= x + w - 10) return false;
        var entry = entries.get(row + scroll);
        try {
            if (event.x() >= x + w - 40) {
                if (ConfigProfileStore.MAIN.equals(entry.id())) return true;
                ConfigProfiles.delete(entry.id()); status = Component.translatable("config.froghelper.deleted");
            } else { ConfigProfiles.select(entry.id()); status = Component.translatable("config.froghelper.applied", entry.name()); }
            UiSounds.click();
        } catch (Exception failure) { status = Component.translatable("config.froghelper.failed"); }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int before = scroll; scroll -= (int) Math.signum(sy); entries(); if (before != scroll) UiSounds.scroll(); return true;
    }
    @Override public void onClose() {
        minecraft.gui.setScreen(previous);
        var renderer = ru.wilyfox.client.Client.getInstance().getHudRenderer();
        renderer.setEditing(wasEditing); renderer.setSettings(wasSettings);
    }
}
