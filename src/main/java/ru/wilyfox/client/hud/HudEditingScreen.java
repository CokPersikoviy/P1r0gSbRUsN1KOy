package ru.wilyfox.client.hud;

import ru.wilyfox.client.audio.UiSoundScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditingScreen extends UiSoundScreen {
    private final HudRenderer hudRenderer;

    public HudEditingScreen(HudRenderer hudRenderer) {
        super(Component.empty());
        this.hudRenderer = hudRenderer;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        // Не вызываем super.extractRenderState(), чтобы не было затемнения/ванильного фона
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        // The HUD editor draws over the world without the default screen blur.
    }

    @Override
    public void removed() {
        super.removed();
        hudRenderer.setEditing(false);
        hudRenderer.setSettings(false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        hudRenderer.onMousePressed(event.x(), event.y(), event.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        // Screen events follow mouse movement, rather than the 20 Hz simulation tick.
        hudRenderer.onMouseDragged(event.x(), event.y(), width, height, event.button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        hudRenderer.onMouseReleased(event.button(), width, height, event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return hudRenderer.onMouseScrolled(mouseX, mouseY, scrollY, ru.wilyfox.utils.InputModifiers.hasControlDown(), ru.wilyfox.utils.InputModifiers.hasShiftDown());
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        return hudRenderer.onKeyPressed(keyCode, scanCode, modifiers) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        int codePoint = event.codepoint();
        int modifiers = 0;
        return hudRenderer.onCharTyped(codePoint, modifiers) || super.charTyped(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        return hudRenderer.onKeyReleased(event.key(), event.modifiers()) || super.keyReleased(event);
    }
}


