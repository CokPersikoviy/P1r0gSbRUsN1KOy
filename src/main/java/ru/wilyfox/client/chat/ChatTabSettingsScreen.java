package ru.wilyfox.client.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.widget.ChatWidget;

/** The same window settings opened from a docked tab or a detached header. */
public final class ChatTabSettingsScreen extends Screen {
    private final Screen parent;
    private final ChatWidget widget;
    private final HudSettingsPanel panel = new HudSettingsPanel();
    public ChatTabSettingsScreen(Screen parent, ChatWidget widget) {
        super(Component.translatable("froghelper.chat.tab_settings")); this.parent = parent; this.widget = widget;
    }
    @Override protected void init() { panel.openWidget(widget, this::onClose); }
    @Override public void onClose() { panel.finishInteraction(); minecraft.gui.setScreen(parent); }
    @Override public void removed() {
        panel.finishInteraction(); ConfigManager.layoutChanged(); ConfigManager.save();
        if (ChatDock.canEdit(widget.getConfigKey()) && ChatDock.selectedKey().equals(widget.getConfigKey())) ChatDock.select(widget.getConfigKey());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) { panel.render(graphics, x, y); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) { return panel.mousePressed(event.x(), event.y(), event.button()); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return panel.mouseReleased(event.x(), event.y(), event.button()); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return panel.mouseDragged(event.x(), event.y(), event.button()); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return panel.mouseScrolled(x, y, dy); }
    @Override public boolean keyPressed(KeyEvent event) { return panel.keyPressed(event.key(), event.scancode(), event.modifiers()) || super.keyPressed(event); }
    @Override public boolean charTyped(CharacterEvent event) { return panel.charTyped(event.codepoint(), 0); }
}
