package ru.wilyfox.client.visuals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.audio.UiSounds;

/** Actions use the profile's real nickname; rendered badges never enter commands. */
public final class VisualsTabScreen extends Screen {
    private boolean tabReleased;
    private String selected;
    private int menuX, menuY;
    private static final int MENU_WIDTH = 150, ROW = 23;
    public VisualsTabScreen() { super(Component.literal("Player list")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float tick) { minecraft.gui.hud.extractDeferredSubtitles(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        if (selected == null) {
            var hovered = VisualsTab.hovered();
            if (hovered != null) {
                String label = hovered.getProfile().name() + " - " + hovered.getLatency() + " ms";
                int tx = Math.clamp(x + 8, 2, Math.max(2, width - font.width(label) - 10)), ty = Math.min(y + 12, height - 16);
                HudSurface.fillRounded(g, tx - 4, ty - 3, font.width(label) + 8, 15, 3, WidgetTheme.PANEL_BG);
                g.text(font, label, tx, ty, WidgetTheme.TEXT_PRIMARY, false);
            }
            return;
        }
        g.nextStratum();
        HudSurface.fillRounded(g, menuX, menuY, MENU_WIDTH, ROW * 5 + 4, 5, WidgetTheme.PANEL_BG);
        g.text(font, selected, menuX + 8, menuY + 7, WidgetTheme.TITLE, false);
        String[] actions = {"Statistics", "Profile", "Message", "Copy nickname"};
        for (int i = 0; i < 4; i++) {
            int py = menuY + ROW * (i + 1);
            boolean hovered = x >= menuX && x < menuX + MENU_WIDTH && y >= py && y < py + ROW;
            if (hovered) HudSurface.fillRounded(g, menuX + 3, py, MENU_WIDTH - 6, ROW - 1, 3, WidgetTheme.PANEL_BG_SOFT);
            int color = i < 2 && !onDW() ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_PRIMARY;
            g.text(font, actions[i], menuX + 8, py + 7, color, false);
        }
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { if (selected != null) selected = null; else onClose(); return true; }
        if (minecraft.options.keyPlayerList.matches(e)) { if (tabReleased) onClose(); return true; }
        if (ConfigManager.get().visuals.tab.cursorKey == e.key() && VisualsCamera.modifiers(ConfigManager.get().visuals.tab.cursorModifiers)) { onClose(); return true; }
        if (e.key() == 263 || e.key() == 262 || minecraft.options.keyLeft.matches(e) || minecraft.options.keyRight.matches(e)) { selected = null; VisualsTab.turn(e.key() == 263 || minecraft.options.keyLeft.matches(e) ? -1 : 1); return true; }
        return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e) { if (minecraft.options.keyPlayerList.matches(e)) tabReleased = true; return super.keyReleased(e); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { selected = null; VisualsTab.scrollBy(dy != 0 ? dy : -dx); return true; }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (ConfigManager.get().visuals.tab.cursorKey == RunesBagConfig.MOUSE_CODE_OFFSET + e.button() && VisualsCamera.modifiers(ConfigManager.get().visuals.tab.cursorModifiers)) { onClose(); return true; }
        if (e.button() != 0 && e.button() != 1) return true;
        if (selected != null) {
            int row = (int) (e.y() - menuY) / ROW - 1;
            if (e.x() >= menuX && e.x() < menuX + MENU_WIDTH && e.y() >= menuY + ROW && row >= 0 && row < 4) {
                action(row); return true;
            }
            selected = null; return true;
        }
        var player = VisualsTab.hovered();
        if (player != null) {
            selected = player.getProfile().name();
            menuX = Math.clamp((int) e.x(), 2, Math.max(2, width - MENU_WIDTH - 2));
            menuY = Math.clamp((int) e.y(), 2, Math.max(2, height - ROW * 5 - 6)); UiSounds.click();
        }
        return true;
    }
    private boolean onDW() {
        var server = minecraft.getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(java.util.Locale.ROOT).contains("diamondworld");
    }
    private void action(int row) {
        if (selected == null) return;
        if (row == 3) { minecraft.keyboardHandler.setClipboard(selected); selected = null; UiSounds.click(); return; }
        if (!selected.matches("[A-Za-z0-9_]{1,16}")) return;
        if (row < 2 && !onDW()) return;
        String nick = selected; UiSounds.click();
        switch (row) {
            case 0, 1 -> {
                minecraft.gui.setScreen(null);
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand((row == 0 ? "statistics " : "profile ") + nick);
            }
            case 2 -> minecraft.gui.setScreen(new ChatScreen("/m " + nick + " ", false));
        }
    }
}
